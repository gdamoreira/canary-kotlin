package org.opentibia.canary.creatures.players

import org.opentibia.canary.combat.CombatType
import org.opentibia.canary.items.Item
import org.opentibia.canary.utils.Tools

/**
 * Self-contained mirror of the Player specialized-magic and magic-shield
 * capacity state (src/creatures/players/player.cpp:7606-7687).
 *
 * The class is intentionally *not* `Player.kt` (per task constraints). Canary
 * keeps the `specializedMagicLevel[COMBAT_COUNT]`,
 * `magicShieldCapacityFlat` and `magicShieldCapacityPercent` fields directly on
 * `Player` (player.hpp:1821-1824); the state is exposed as an injectable seam
 * so the host player can delegate without a global (AGENTS.md 1.1). Every
 * external dependency is a constructor seam: [getEquippedItems] replaces
 * `Player::getEquippedItems` (player.cpp:5949), [wheelSpecializedMagic]
 * replaces `m_wheelPlayer.getSpecializedMagic(combat)`,
 * [proficiencySpecializedMagic] replaces
 * `m_weaponProficiency.getSpecializedMagic(combat)`, [getCharges] replaces
 * `Item::getCharges` (src/items/item.hpp:107) and [transformItemCharges]
 * replaces `g_game().transformItem(item, item->getID(), charges - 1)`
 * (player.cpp:7624).
 *
 * The methods keep the exact `Player::` names of the C++ symbols they
 * transcribe, so every behavior is auditable against the C++ line references.
 */
class PlayerMagicAffinityState(
    /** `Player::getEquippedItems()` (player.cpp:5949). */
    private val getEquippedItems: () -> List<Item> = { emptyList() },
    /** `PlayerWheel::getSpecializedMagic(combat)` seam. */
    private val wheelSpecializedMagic: (CombatType) -> Int = { 0 },
    /** `WeaponProficiency::getSpecializedMagic(combat)` seam. */
    private val proficiencySpecializedMagic: (CombatType) -> Int = { 0 },
    /** `Item::getCharges()` (src/items/item.hpp:107). */
    private val getCharges: (Item) -> Int = { 0 },
    /** `g_game().transformItem(item, item->getID(), charges - 1)` (player.cpp:7624). */
    private val transformItemCharges: (Item) -> Unit = {},
) {

    /** `Player::specializedMagicLevel[COMBAT_COUNT]` (player.hpp:1823). */
    private val specializedMagicLevel = IntArray(COMBAT_COUNT)

    /** `Player::magicShieldCapacityFlat` (player.hpp:1821). */
    private var magicShieldCapacityFlat = 0

    /** `Player::magicShieldCapacityPercent` (player.hpp:1822). */
    private var magicShieldCapacityPercent = 0

    /**
     * `Player::getSpecializedMagicLevel` (player.cpp:7606-7629): stored value,
     * wheel and proficiency bonuses, then every equipped item ability, charging
     * `useCharges` items as they are read.
     */
    fun getSpecializedMagicLevel(combat: CombatType, useCharges: Boolean): Int {
        val combatIndex = Tools.combatTypeToIndex(combat)
        var result = specializedMagicLevel[combatIndex]
        result += wheelSpecializedMagic(combat)
        result += proficiencySpecializedMagic(combat)

        for (item in getEquippedItems()) {
            val abilities = item.type?.abilities ?: continue
            val specialized = abilities.specializedMagicLevel[combatIndex]
            if (specialized > 0) {
                result += specialized
                if (useCharges && getCharges(item) != 0) {
                    transformItemCharges(item)
                }
            }
        }

        return result
    }

    /** `Player::setSpecializedMagicLevel` (player.cpp:7631-7633). */
    fun setSpecializedMagicLevel(combat: CombatType, value: Int) {
        val combatIndex = Tools.combatTypeToIndex(combat)
        specializedMagicLevel[combatIndex] = maxOf(0, specializedMagicLevel[combatIndex] + value)
    }

    /** `Player::getMagicShieldCapacityFlat` (player.cpp:7635-7656). */
    fun getMagicShieldCapacityFlat(useCharges: Boolean): Int {
        var result = magicShieldCapacityFlat
        for (item in getEquippedItems()) {
            val abilities = item.type?.abilities ?: continue
            val magicCapacity = abilities.magicShieldCapacityFlat
            if (magicCapacity != 0) {
                result += magicCapacity
                if (useCharges && getCharges(item) != 0) {
                    transformItemCharges(item)
                }
            }
        }
        return result
    }

    /** `Player::setMagicShieldCapacityFlat` (player.cpp:7658-7660). */
    fun setMagicShieldCapacityFlat(value: Int) {
        magicShieldCapacityFlat += value
    }

    /** `Player::getMagicShieldCapacityPercent` (player.cpp:7662-7683). */
    fun getMagicShieldCapacityPercent(useCharges: Boolean): Int {
        var result = magicShieldCapacityPercent
        for (item in getEquippedItems()) {
            val abilities = item.type?.abilities ?: continue
            val magicPercent = abilities.magicShieldCapacityPercent
            if (magicPercent != 0) {
                result += magicPercent
                if (useCharges && getCharges(item) != 0) {
                    transformItemCharges(item)
                }
            }
        }
        return result
    }

    /** `Player::setMagicShieldCapacityPercent` (player.cpp:7685-7687). */
    fun setMagicShieldCapacityPercent(value: Int) {
        magicShieldCapacityPercent += value
    }

    companion object {
        /** `COMBAT_COUNT` (`src/creatures/creatures_definitions.hpp:847`). */
        private const val COMBAT_COUNT = 14
    }
}
