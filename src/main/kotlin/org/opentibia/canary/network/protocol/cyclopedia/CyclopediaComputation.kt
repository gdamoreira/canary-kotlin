package org.opentibia.canary.network.protocol.cyclopedia

import kotlin.math.floor
import org.opentibia.canary.combat.CombatType
import org.opentibia.canary.combat.condition.Skill
import org.opentibia.canary.creatures.Slots_t
import org.opentibia.canary.items.Item
import org.opentibia.canary.systems.imbuement.ImbuementInfo
import org.opentibia.canary.systems.proficiency.WeaponProficiencyCriticalBonus
import org.opentibia.canary.systems.wheel.WheelAvatarSkill
import org.opentibia.canary.systems.wheel.WheelMajor
import org.opentibia.canary.systems.wheel.WheelStat

/**
 * `Cipbia_Elementals_t` (`src/utils/utils_definitions.hpp:40-56`) plus the two
 * conversion helpers from `src/utils/tools.cpp`:
 *
 *  - `getCipbiaElement(CombatType_t)`   (tools.cpp:2124-2155)
 *  - `getCombatFromCipbiaElement(...)`  (tools.cpp:2091-2122)
 *
 * The two functions are intentionally not inverses: the C++ client element
 * order (fire=1, earth=2, energy=3) differs from the server `CombatType_t`
 * order (fire=1, earth=2, energy=3 ...), and `COMBAT_NEUTRALDAMAGE` maps to the
 * agony element while every unmapped type falls back to `_UNDEFINED`.
 */
object CipbiaElement {
    const val PHYSICAL: Int = 0
    const val FIRE: Int = 1
    const val EARTH: Int = 2
    const val ENERGY: Int = 3
    const val ICE: Int = 4
    const val HOLY: Int = 5
    const val DEATH: Int = 6
    const val HEALING: Int = 7
    const val DROWN: Int = 8
    const val LIFEDRAIN: Int = 9
    const val MANADRAIN: Int = 10
    const val AGONY: Int = 11
    const val UNDEFINED: Int = 12
    const val HEALING_2: Int = 18

    /** `getCipbiaElement(CombatType_t combatType)` (tools.cpp:2124-2155). */
    fun fromCombatType(combatType: CombatType): Int = when (combatType) {
        CombatType.PHYSICALDAMAGE -> PHYSICAL
        CombatType.FIREDAMAGE -> FIRE
        CombatType.EARTHDAMAGE -> EARTH
        CombatType.ENERGYDAMAGE -> ENERGY
        CombatType.ICEDAMAGE -> ICE
        CombatType.HOLYDAMAGE -> HOLY
        CombatType.DEATHDAMAGE -> DEATH
        CombatType.HEALING -> HEALING
        CombatType.DROWNDAMAGE -> DROWN
        CombatType.LIFEDRAIN -> LIFEDRAIN
        CombatType.MANADRAIN -> MANADRAIN
        CombatType.AGONYDAMAGE, CombatType.NEUTRALDAMAGE -> AGONY
        else -> UNDEFINED
    }

    /** `getCombatFromCipbiaElement(Cipbia_Elementals_t)` (tools.cpp:2091-2122). */
    fun toCombatType(element: Int): CombatType = when (element) {
        PHYSICAL -> CombatType.PHYSICALDAMAGE
        FIRE -> CombatType.FIREDAMAGE
        EARTH -> CombatType.EARTHDAMAGE
        ENERGY -> CombatType.ENERGYDAMAGE
        ICE -> CombatType.ICEDAMAGE
        HOLY -> CombatType.HOLYDAMAGE
        DEATH -> CombatType.DEATHDAMAGE
        HEALING, HEALING_2 -> CombatType.HEALING
        DROWN -> CombatType.DROWNDAMAGE
        LIFEDRAIN -> CombatType.LIFEDRAIN
        MANADRAIN -> CombatType.MANADRAIN
        AGONY -> CombatType.AGONYDAMAGE
        else -> CombatType.NONE
    }
}

/**
 * `CipbiaSkills_t` (`src/utils/utils_definitions.hpp:28-38`) plus the two
 * conversion helpers from `src/utils/tools.cpp`:
 *
 *  - `getCipbiaSkill(skills_t)`          (tools.cpp:2066-2089)
 *  - `getSkillsFromCipbiaSkill(...)`     (tools.cpp:2041-2064)
 *
 * `SKILL_NONE` has no Kotlin enum entry, so [toSkill] returns `null` for it.
 */
object CipbiaSkill {
    const val NONE: Int = 0
    const val MAGIC_LEVEL: Int = 1
    const val SHIELD: Int = 6
    const val DISTANCE: Int = 7
    const val SWORD: Int = 8
    const val CLUB: Int = 9
    const val AXE: Int = 10
    const val FIST: Int = 11
    const val FISHING: Int = 13

    /** `getCipbiaSkill(skills_t skill)` (tools.cpp:2066-2089). */
    fun fromSkill(skill: Skill): Int = when (skill) {
        Skill.SKILL_MAGLEVEL -> MAGIC_LEVEL
        Skill.SKILL_SHIELD -> SHIELD
        Skill.SKILL_DISTANCE -> DISTANCE
        Skill.SKILL_SWORD -> SWORD
        Skill.SKILL_CLUB -> CLUB
        Skill.SKILL_AXE -> AXE
        Skill.SKILL_FIST -> FIST
        Skill.SKILL_FISHING -> FISHING
        else -> NONE
    }

    /** `getSkillsFromCipbiaSkill(CipbiaSkills_t)` (tools.cpp:2041-2064); `SKILL_NONE` is `null`. */
    fun toSkill(cipbiaSkill: Int): Skill? = when (cipbiaSkill) {
        MAGIC_LEVEL -> Skill.SKILL_MAGLEVEL
        SHIELD -> Skill.SKILL_SHIELD
        DISTANCE -> Skill.SKILL_DISTANCE
        SWORD -> Skill.SKILL_SWORD
        CLUB -> Skill.SKILL_CLUB
        AXE -> Skill.SKILL_AXE
        FIST -> Skill.SKILL_FIST
        FISHING -> Skill.SKILL_FISHING
        else -> null
    }
}

/** `struct BaseCritical` (`src/creatures/players/player.hpp:108-111`). */
data class BaseCritical(
    val chance: Double = 0.05,
    val damage: Double = 0.1,
)

/**
 * `struct SkillsEquipment` (`src/creatures/players/player.hpp:103-106`), one
 * entry per `skills_t` in `Player::getSkillsEquipment()` (player.cpp:13214).
 */
data class SkillsEquipment(
    val equipment: Double = 0.0,
    val imbuement: Double = 0.0,
)

/**
 * `Item::getImbuementInfo` (`src/items/item.cpp:472`) seam. The
 * `ImbuementEngine`-backed implementation returns `null` when the slot holds no
 * imbuement or the remaining duration is 0; both are the cases the C++ helpers
 * skip, so the fold is behavior-preserving.
 */
fun interface ImbuementInfoResolver {
    fun getImbuementInfo(item: Item, slot: Int): ImbuementInfo?
}

/**
 * The `PlayerWheel` surface the cyclopedia helpers read. Every member
 * transcribes the exact C++ symbol:
 *
 *  - [getStat] / [getResistance] / [getMajorStatConditional] /
 *    [checkAvatarSkill]: `PlayerWheel::getStat` (player_wheel.cpp:3843),
 *    `getResistance` (3853), `getMajorStatConditional` (3886) and
 *    `checkAvatarSkill` (3301).
 *  - [getBonusMomentum]: `PlayerWheel::getBonusData().momentum`
 *    (wheel_definitions.hpp:289).
 */
interface CyclopediaWheelStats {
    fun getStat(stat: WheelStat): Int

    fun getResistance(combatType: CombatType): Int

    fun getMajorStatConditional(instant: String, major: WheelMajor): Int

    fun checkAvatarSkill(skill: WheelAvatarSkill): Int

    fun getBonusMomentum(): Double
}

/**
 * The `WeaponProficiency` surface the cyclopedia helpers read:
 * `WeaponProficiency::getSkillBonus` (weapon_proficiency.cpp:1051) and
 * `getGeneralCritical` (weapon_proficiency.cpp:1105).
 */
interface CyclopediaWeaponProficiencyStats {
    fun getSkillBonus(skill: Skill): Int

    fun getGeneralCritical(): WeaponProficiencyCriticalBonus
}

/**
 * The `Player` surface the cyclopedia computation helpers read, with the C++
 * line reference for every member. The `!player`/`oldProtocol` sender guards
 * stay with the caller; only [oldProtocol] is modelled here because
 * `getForgeSkillStat` itself checks it (protocolgame.cpp:11137).
 */
interface CyclopediaComputationPlayer {
    /** `ProtocolGame::oldProtocol` (`getForgeSkillStat`, protocolgame.cpp:11137). */
    val oldProtocol: Boolean

    /** `Player::getWeapon(ignoreAmmo)` (player.hpp:847). */
    fun getWeapon(ignoreAmmo: Boolean = false): Item?

    /** `Player::getInventoryItem(slot)` (player.hpp). */
    fun getInventoryItem(slot: Slots_t): Item?

    /** `Player::isItemAbilityEnabled(slot)` (player.cpp:344). */
    fun isItemAbilityEnabled(slot: Slots_t): Boolean

    /** `Player::getSkillLevel(skill)` (player.hpp). */
    fun getSkillLevel(skill: Skill): Int

    /** `Creature::getAbsorbPercent(combat)` (creature.cpp:1979). */
    fun getAbsorbPercent(combatType: CombatType): Int

    /** `Player::getBaseCritical()` (player.cpp:13244). */
    fun getBaseCritical(): BaseCritical

    /** `Player::wheel()` (player.hpp). */
    fun wheel(): CyclopediaWheelStats

    /** `Player::weaponProficiency()` (player.hpp). */
    fun weaponProficiency(): CyclopediaWeaponProficiencyStats
}

/** The `handleImbuementDamage` block: `addDouble(damage)`, `addByte(element)`. */
data class ImbuementDamage(
    val damage: Double,
    val element: Int,
)

/**
 * Output of `calculateAbsorbValues` (protocolgame.cpp:310-376): [entries] is
 * the written pair list in combat-index order and [fromPlayerSkills] preserves
 * whether the `0x04` prefix byte was suppressed (`true` = suppressed, as in
 * `AddPlayerSkills`; `false` = `sendCyclopediaCharacterDefenceStats`).
 */
data class AbsorbValues(
    val entries: List<CyclopediaCharacterWriter.AbsorbEntry>,
    val fromPlayerSkills: Boolean = false,
)

/**
 * The cyclopedia computation helpers of `ProtocolGame`, ported verbatim from
 * src/server/network/protocol/protocolgame.cpp:
 *
 *  - `getForgeSkillStat`             (protocolgame.cpp:11136-11169)
 *  - `handleImbuementDamage`         (protocolgame.cpp:270-300)
 *  - `calculateAbsorbValues`         (protocolgame.cpp:310-376)
 *  - `addCyclopediaSkills`           (protocolgame.cpp:426-450)
 *  - `addCyclopediaCriticalSkill`    (protocolgame.cpp:452-470)
 *
 * The C++ helpers write straight into a `NetworkMessage`; the Kotlin port
 * returns the resolved values so `CyclopediaCharacterWriter` frames can carry
 * them without the caller re-deriving wire values. The arithmetic (including
 * the `uint16_t` wrap of the absorb modifiers and their `int16_t` reinterpret)
 * is transcribed exactly.
 */
class CyclopediaComputation(
    private val player: CyclopediaComputationPlayer,
    /** `Item::getImbuementInfo` (item.cpp:472) lookup; no imbuements by default. */
    private val imbuements: ImbuementInfoResolver = ImbuementInfoResolver { _, _ -> null },
    /** `TOGGLE_WHEELSYSTEM` (`calculateAbsorbValues`, protocolgame.cpp:361). */
    private val wheelSystemEnabled: Boolean = true,
) {

    /** `getForgeSkillStat(slot, applyAmplification)` (protocolgame.cpp:11136-11169). */
    fun getForgeSkillStat(slot: Slots_t, applyAmplification: Boolean = true): Double {
        if (player.oldProtocol) {
            return 0.0
        }

        var skill = 0.0
        val item = player.getInventoryItem(slot)
        if (item != null) {
            val itemType = item.type
            if (itemType != null) {
                if (itemType.isWeapon()) {
                    skill = item.getFatalChance()
                }
                if (itemType.isArmor()) {
                    skill = item.getDodgeChance()
                }
                if (itemType.isHelmet()) {
                    skill = item.getMomentumChance()
                }
                if (itemType.isLegs()) {
                    skill = item.getTranscendenceChance()
                }
                if (itemType.isBoots()) {
                    skill = item.getAmplificationChance()
                }
            }
        }

        if (applyAmplification) {
            val boots = player.getInventoryItem(Slots_t.CONST_SLOT_FEET)
            if (slot != Slots_t.CONST_SLOT_FEET && boots != null) {
                skill *= 1 + (boots.getAmplificationChance() / 100)
            }
        }

        return skill / 100
    }

    /**
     * `handleImbuementDamage(msg, player)` (protocolgame.cpp:270-300). Returns
     * `null` for Canary's `addDouble(0); addByte(0)` fallback.
     */
    fun handleImbuementDamage(weapon: Item? = player.getWeapon()): ImbuementDamage? {
        if (weapon != null) {
            val slots = weapon.type?.imbuementSlot ?: 0
            for (i in 0 until slots) {
                val imbuementInfo = imbuements.getImbuementInfo(weapon, i) ?: continue

                if (imbuementInfo.duration > 0) {
                    val imbuement = imbuementInfo.imbuement
                    val hasValidCombat = imbuement.combatType != CombatType.NONE &&
                        imbuement.combatType.value < CombatType.COUNT.value
                    if (hasValidCombat) {
                        return ImbuementDamage(
                            imbuement.elementDamage / 100.0,
                            CipbiaElement.fromCombatType(imbuement.combatType),
                        )
                    }
                }
            }
        }

        return null
    }

    /** `player->wheel().getStat(stat)` (`PlayerWheel::getStat`, player_wheel.cpp:3843). */
    fun getWheelStat(stat: WheelStat): Int = player.wheel().getStat(stat)

    /** `player->wheel().getBonusData().momentum` (wheel_definitions.hpp:289). */
    fun getWheelMomentum(): Double = player.wheel().getBonusMomentum()

    /** `calculateAbsorbValues(player, msg, combats, fromPlayerSkills)` (protocolgame.cpp:310-376). */
    fun calculateAbsorbValues(fromPlayerSkills: Boolean = false): AbsorbValues {
        val damageModifiers = IntArray(CombatType.COUNT.value) { 10000 }

        for (slotValue in Slots_t.FIRST..Slots_t.LAST) {
            val slot = Slots_t.fromValue(slotValue) ?: continue
            if (!player.isItemAbilityEnabled(slot)) {
                continue
            }

            val item = player.getInventoryItem(slot) ?: continue
            val itemType = item.type ?: continue
            val abilities = itemType.abilities ?: continue

            for (i in 0 until CombatType.COUNT.value) {
                damageModifiers[i] = multipliedDamageModifier(
                    damageModifiers[i],
                    abilities.absorbPercent[i].toDouble(),
                )
            }

            val imbuementSlots = itemType.imbuementSlot
            for (slotId in 0 until imbuementSlots) {
                val imbuementInfo = imbuements.getImbuementInfo(item, slotId) ?: continue
                if (imbuementInfo.duration == 0) {
                    continue
                }

                val imbuement = imbuementInfo.imbuement
                for (combat in 0 until CombatType.COUNT.value) {
                    val imbuementAbsorbPercent = imbuement.absorbPercent[combat]
                    if (imbuementAbsorbPercent == 0) {
                        continue
                    }

                    damageModifiers[combat] = multipliedDamageModifier(
                        damageModifiers[combat],
                        imbuementAbsorbPercent.toDouble(),
                    )
                }
            }
        }

        val entries = mutableListOf<CyclopediaCharacterWriter.AbsorbEntry>()
        for (i in 0 until CombatType.COUNT.value) {
            val combatType = CombatType.fromValue(i)
            var modifier = (damageModifiers[i] - 100 * player.getAbsorbPercent(combatType)) and 0xFFFF
            if (wheelSystemEnabled) {
                modifier = (modifier - player.wheel().getResistance(combatType)) and 0xFFFF
            }

            if (modifier != 10000) {
                val clientModifier = (10000 - modifier.toShort().toInt()) / 10000.0
                entries += CyclopediaCharacterWriter.AbsorbEntry(
                    CipbiaElement.fromCombatType(combatType),
                    clientModifier,
                )
            }
        }

        return AbsorbValues(entries, fromPlayerSkills)
    }

    /** `addCyclopediaSkills(player, msg, skill, skillEquipment)` (protocolgame.cpp:426-450). */
    fun addCyclopediaSkills(skill: Skill, skillEquipment: SkillsEquipment): CyclopediaCharacterWriter.LeechSkillEntry {
        val skillTotal = player.getSkillLevel(skill)

        var skillWheel = 0.0
        val playerWheel = player.wheel()
        when (skill) {
            Skill.SKILL_LIFE_LEECH_AMOUNT ->
                skillWheel = playerWheel.getStat(WheelStat.LIFE_LEECH).toDouble()

            Skill.SKILL_MANA_LEECH_AMOUNT ->
                skillWheel = playerWheel.getStat(WheelStat.MANA_LEECH).toDouble()

            Skill.SKILL_CRITICAL_HIT_DAMAGE -> {
                skillWheel = playerWheel.getStat(WheelStat.CRITICAL_DAMAGE).toDouble()
                skillWheel += playerWheel.getMajorStatConditional("Combat Mastery", WheelMajor.CRITICAL_DMG_2)
                skillWheel += playerWheel.getMajorStatConditional("Ballistic Mastery", WheelMajor.CRITICAL_DMG)
                skillWheel += playerWheel.checkAvatarSkill(WheelAvatarSkill.CRITICAL_DAMAGE)
            }

            else -> Unit
        }

        val skillEquipmentValue = skillEquipment.equipment +
            (player.weaponProficiency().getSkillBonus(skill) / 10000.0)

        return CyclopediaCharacterWriter.LeechSkillEntry(
            total = skillTotal / 10000.0,
            equipment = skillEquipmentValue,
            imbuement = skillEquipment.imbuement,
            wheel = skillWheel / 10000.0,
            event = 0.0,
        )
    }

    /** `addCyclopediaCriticalSkill(player, msg, skill, skillEquipment)` (protocolgame.cpp:452-470). */
    fun addCyclopediaCriticalSkill(
        skill: Skill,
        skillEquipment: SkillsEquipment,
    ): CyclopediaCharacterWriter.CriticalSkillEntry {
        val playerBaseCritical = player.getBaseCritical()

        val baseCritical = if (skill == Skill.SKILL_CRITICAL_HIT_CHANCE) {
            playerBaseCritical.chance
        } else {
            playerBaseCritical.damage
        }
        val wheelCritical = player.wheel().getStat(
            if (skill == Skill.SKILL_CRITICAL_HIT_CHANCE) WheelStat.CRITICAL_CHANCE else WheelStat.CRITICAL_DAMAGE,
        ) / 10000.0

        val proficiencyCritical = player.weaponProficiency().getGeneralCritical()
        val proficiencyCriticalValue = if (skill == Skill.SKILL_CRITICAL_HIT_CHANCE) {
            proficiencyCritical.chance
        } else {
            proficiencyCritical.damage
        }
        val skillEquipmentValue = skillEquipment.equipment + proficiencyCriticalValue

        return CyclopediaCharacterWriter.CriticalSkillEntry(
            total = baseCritical + skillEquipmentValue + wheelCritical + skillEquipment.imbuement,
            base = baseCritical,
            equipment = skillEquipmentValue,
            imbuement = skillEquipment.imbuement,
            wheel = wheelCritical,
            concoction = 0.0,
        )
    }

    /**
     * `damageModifiers[i] *= (std::floor(100. - percent) / 100.)` with the
     * `uint16_t` compound-assignment truncation and wrap
     * (protocolgame.cpp:329,353).
     */
    private fun multipliedDamageModifier(modifier: Int, percent: Double): Int =
        (modifier * (floor(100.0 - percent) / 100.0)).toInt() and 0xFFFF
}
