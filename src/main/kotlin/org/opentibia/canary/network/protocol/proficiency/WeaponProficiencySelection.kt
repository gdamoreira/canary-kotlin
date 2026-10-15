package org.opentibia.canary.network.protocol.proficiency

import org.opentibia.canary.items.ItemType
import org.opentibia.canary.items.ItemTypeRegistry
import org.opentibia.canary.systems.proficiency.Proficiency
import org.opentibia.canary.systems.proficiency.ProficiencyItemType
import org.opentibia.canary.systems.proficiency.ProficiencyPerk
import org.opentibia.canary.systems.proficiency.WeaponProficiencyData
import org.opentibia.canary.systems.proficiency.WeaponProficiencyExperience
import org.opentibia.canary.systems.proficiency.WeaponProficiencyItemType
import org.opentibia.canary.systems.proficiency.WeaponProficiencyItems

/**
 * Selection-state half of `WeaponProficiency`
 * (src/creatures/players/components/weapon_proficiency.cpp), the part the
 * `ProtocolGame::parseWeaponProficiency` actions `0x02`/`0x03` drive
 * (protocolgame.cpp:3157-3185).
 *
 * The combat-application half lives in the ported
 * `systems.proficiency.WeaponProficiency` engine; this object ports the
 * stateless validation/selection helpers (`getSelectedPerks`,
 * `getUnlockedLevelCount`, `collectValidSelectedPerks`, `clearSelectedPerks`,
 * `setSelectedPerk`, `isUpgradeAvailable`, `getExperienceArray`) over the
 * session `Player::m_weaponProficiency` map (`PlayerFeatures.weaponProficiencies`)
 * and the injected `proficiencies.json` registry + `Item::items` view.
 *
 * The C++ `clearAllStats`/`applyPerks` mutation targets the live combat engine
 * instance, which the session state does not hold yet (PARITY GAP: combat
 * application); the protocol flow still runs those calls at the same points.
 */
object WeaponProficiencySelection {

    /** `AMMO_BOLT` (`Ammo_t`, src/utils/utils_definitions.hpp:485) read by `getExperienceArray`. */
    private const val AMMO_BOLT = 1

    /** `WeaponType_t` sword/axe/club read by `usesKnightProficiencyTable` (weapon_proficiency.cpp:61-67). */
    private val KNIGHT_WEAPON_TYPES = setOf(1, 2, 3)

    /** `WeaponProficiency::getTrackedWeaponIds` (weapon_proficiency.cpp:320-329): ascending. */
    fun getTrackedWeaponIds(state: Map<Int, WeaponProficiencyData>): List<Int> = state.keys.sorted()

    /**
     * `WeaponProficiency::getSelectedPerks` (weapon_proficiency.cpp:571-573)
     * through `collectValidSelectedPerks` (weapon_proficiency.cpp:901-948).
     */
    fun getSelectedPerks(
        state: Map<Int, WeaponProficiencyData>,
        weaponId: Int,
        proficiencies: Map<Int, Proficiency>,
        items: WeaponProficiencyItems,
    ): List<ProficiencyPerk> {
        if (!isValidWeaponId(weaponId, items)) {
            return emptyList()
        }

        val playerProficiency = state[weaponId] ?: return emptyList()
        val proficiencyInfo = proficiencies[items.get(weaponId).proficiencyId] ?: return emptyList()

        val unlockedLevelCount = getUnlockedLevelCount(state, weaponId, proficiencies, items)
        if (unlockedLevelCount == 0) {
            return emptyList()
        }

        val storedPerks = playerProficiency.perks
        val validPerks = mutableListOf<ProficiencyPerk>()
        val usedLevels = BooleanArray(proficiencyInfo.level.size)

        for (storedPerk in storedPerks) {
            val level = storedPerk.level
            val index = storedPerk.index

            if (level < 0 || index < 0 || level >= unlockedLevelCount ||
                level >= proficiencyInfo.level.size || usedLevels[level]
            ) {
                continue
            }

            val levelPerks = proficiencyInfo.level[level].perks
            if (index >= levelPerks.size) {
                continue
            }

            validPerks.add(levelPerks[index])
            usedLevels[level] = true
        }

        validPerks.sortWith(compareBy({ it.level }, { it.index }))
        return validPerks
    }

    /** `WeaponProficiency::clearSelectedPerks` (weapon_proficiency.cpp:575-583). */
    fun clearSelectedPerks(state: MutableMap<Int, WeaponProficiencyData>, weaponId: Int) {
        if (weaponId == 0) {
            return
        }

        state[weaponId]?.perks?.clear()
    }

    /**
     * `WeaponProficiency::setSelectedPerk` (weapon_proficiency.cpp:585-640): the
     * selected perk is validated against the stored proficiency, the registry
     * and the unlocked level count before the duplicate-level check, then the
     * registry perk is appended. Errors log through [logError]/[logWarn] like the
     * C++ `g_logger()` calls.
     */
    fun setSelectedPerk(
        state: MutableMap<Int, WeaponProficiencyData>,
        level: Int,
        perkIndex: Int,
        weaponId: Int,
        /** `Player::getWeaponId(true)` used when `weaponId == 0` (weapon_proficiency.cpp:586-588). */
        equippedWeaponId: Int = 0,
        proficiencies: Map<Int, Proficiency>,
        items: WeaponProficiencyItems,
        logError: (String) -> Unit = {},
        logWarn: (String) -> Unit = {},
    ) {
        val resolvedWeaponId = if (weaponId == 0) equippedWeaponId else weaponId
        if (resolvedWeaponId == 0) {
            logError("[setSelectedPerk] - Invalid weapon ID: $resolvedWeaponId")
            return
        }

        if (!isValidWeaponId(resolvedWeaponId, items)) {
            logError("[setSelectedPerk] - Weapon ID out of range: $resolvedWeaponId")
            return
        }

        val playerProficiency = state[resolvedWeaponId]
        if (playerProficiency == null) {
            logError("[setSelectedPerk] - No stored proficiency data for weapon ID: $resolvedWeaponId")
            return
        }

        val proficiencyId = items.get(resolvedWeaponId).proficiencyId
        val proficiencyInfo = proficiencies[proficiencyId]
        if (proficiencyInfo == null) {
            logError("[setSelectedPerk] - Proficiency not found for weapon ID: $resolvedWeaponId")
            return
        }

        val unlockedLevelCount = getUnlockedLevelCount(state, resolvedWeaponId, proficiencies, items)
        if (level >= unlockedLevelCount) {
            logWarn(
                "[setSelectedPerk] - Attempt to select locked proficiency level $level " +
                    "for weapon ID: $resolvedWeaponId",
            )
            return
        }

        if (level >= proficiencyInfo.level.size) {
            logError(
                "[setSelectedPerk] - Proficiency level exceeds maximum size for weapon ID: $resolvedWeaponId",
            )
            return
        }

        val selectedLevel = proficiencyInfo.level[level]
        if (perkIndex >= selectedLevel.perks.size) {
            logError(
                "[setSelectedPerk] - Proficiency level $level exceeds maximum perks size " +
                    "for weapon ID: $resolvedWeaponId",
            )
            return
        }

        val hasLevelSelected = playerProficiency.perks.any { it.level == level }
        if (hasLevelSelected) {
            logWarn(
                "[setSelectedPerk] - Duplicate proficiency level $level selection ignored " +
                    "for weapon ID: $resolvedWeaponId",
            )
            return
        }

        playerProficiency.perks.add(selectedLevel.perks[perkIndex])
    }

    /** `WeaponProficiency::getUnlockedLevelCount` (weapon_proficiency.cpp:867-899). */
    fun getUnlockedLevelCount(
        state: Map<Int, WeaponProficiencyData>,
        weaponId: Int,
        proficiencies: Map<Int, Proficiency>,
        items: WeaponProficiencyItems,
    ): Int {
        if (!isValidWeaponId(weaponId, items)) {
            return 0
        }

        val playerProficiency = state[weaponId] ?: return 0
        val proficiencyInfo = proficiencies[items.get(weaponId).proficiencyId] ?: return 0

        val experienceArray = getExperienceArray(weaponId, items)
        if (proficiencyInfo.maxLevel == 0) {
            return 0
        }

        val limit = minOf(experienceArray.size, proficiencyInfo.maxLevel)
        var unlockedLevels = 0
        for (i in 0 until limit) {
            if (playerProficiency.experience < experienceArray[i]) {
                break
            }

            unlockedLevels++
        }

        return unlockedLevels
    }

    /** `WeaponProficiency::isUpgradeAvailable` (weapon_proficiency.cpp:823-864). */
    fun isUpgradeAvailable(
        state: Map<Int, WeaponProficiencyData>,
        weaponId: Int,
        proficiencies: Map<Int, Proficiency>,
        items: WeaponProficiencyItems,
    ): Boolean {
        if (!isValidWeaponId(weaponId, items)) {
            return false
        }

        val experienceArray = getExperienceArray(weaponId, items)
        val proficiencyInfo = proficiencies[items.get(weaponId).proficiencyId] ?: return false
        val playerProficiency = state[weaponId] ?: return false

        val selectedPerks = getSelectedPerks(state, weaponId, proficiencies, items)

        val limit = minOf(experienceArray.size, proficiencyInfo.maxLevel)
        for (i in 0 until limit) {
            if (playerProficiency.experience < experienceArray[i]) {
                break
            }

            if (selectedPerks.size < i + 1) {
                return true
            }
        }

        return false
    }

    /** `WeaponProficiency::getExperienceArray` (weapon_proficiency.cpp:664-680). */
    fun getExperienceArray(weaponId: Int, items: WeaponProficiencyItems): IntArray {
        if (!isValidWeaponId(weaponId, items)) {
            return WeaponProficiencyExperience.STANDARD_EXPERIENCE
        }

        val itemType = items.get(weaponId)
        if (itemType.ammoType == AMMO_BOLT) {
            return WeaponProficiencyExperience.CROSSBOW_EXPERIENCE
        }
        if (usesKnightProficiencyTable(itemType)) {
            return WeaponProficiencyExperience.KNIGHT_EXPERIENCE
        }
        return WeaponProficiencyExperience.STANDARD_EXPERIENCE
    }

    /** `WeaponProficiency::isValidWeaponId` (`weaponId < Item::items.size()`). */
    fun isValidWeaponId(weaponId: Int, items: WeaponProficiencyItems): Boolean =
        weaponId > 0 && weaponId < items.count

    /** `usesKnightProficiencyTable` (weapon_proficiency.cpp:61-67). */
    private fun usesKnightProficiencyTable(itemType: ProficiencyItemType): Boolean {
        if (itemType.weaponType !in KNIGHT_WEAPON_TYPES) {
            return false
        }

        return itemType.vocationString.lowercase().contains("knight")
    }
}

/**
 * `Item::items` (`Items` registry) viewed through the proficiency component's
 * [WeaponProficiencyItems] seam, so the selection helpers read the same
 * `ammoType`/`weaponType`/`vocationString`/`proficiencyId` fields the C++ reads
 * directly. An unknown id yields the neutral entry, mirroring the C++ default
 * `ItemType` at index 0 (only reached for out-of-range ids, which
 * `isValidWeaponId` rejects first).
 */
class ItemRegistryProficiencyItems(
    private val registry: ItemTypeRegistry,
) : WeaponProficiencyItems {

    override val count: Int get() = registry.size()

    override fun get(weaponId: Int): ProficiencyItemType =
        registry.get(weaponId)?.let(::ItemTypeProficiencyView) ?: WeaponProficiencyItemType()
}

/** [ProficiencyItemType] view over one `Item::items` entry. */
private class ItemTypeProficiencyView(private val type: ItemType) : ProficiencyItemType {
    override val ammoType: Int get() = type.ammoType
    override val weaponType: Int get() = type.weaponType.value
    override val vocationString: String get() = type.vocationString
    override val proficiencyId: Int get() = type.proficiencyId
}
