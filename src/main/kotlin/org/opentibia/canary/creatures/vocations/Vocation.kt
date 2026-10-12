package org.opentibia.canary.creatures.vocations

import kotlin.math.floor
import kotlin.math.pow

/**
 * Mirror of Canary's `class Vocation`
 * (src/creatures/players/vocations/vocation.hpp:17, vocation.cpp).
 *
 * One instance is created per `<vocation>` node by [Vocations.loadFromXml], which
 * is declared `friend class Vocations` in C++; the Kotlin `internal` backing fields
 * model that package-private write coupling while the getters below are public,
 * exactly like their C++ counterparts.
 *
 * The `getReqSkillTries` / `getReqMana` formulas, the `skillBase` table and the
 * `minSkillLevel` bound are transcribed verbatim from vocation.cpp:236-307
 * (`SKILL_LAST` is Canary's `SKILL_MANA_LEECH_AMOUNT`).
 *
 * Field types follow the C++ declarations: the skill/mana multipliers are `float`
 * in Canary and are promoted to `double` inside `std::pow<double>`, so they are
 * kept as [Float] here and widened at the call site to preserve bit-exact results.
 *
 * Not ported (require `WheelGemSupremeModifier_t` / `Item` registries):
 * `getSupremeGemModifiers` and `getWheelGemId` (vocation.cpp:385-411). The
 * `<gem>` nodes are captured in [wheelGems] for a later wiring.
 */
class Vocation(private val idValue: Int) {

    internal var name: String = "none"
    internal var description: String = ""
    internal var clientId: Int = 0
    internal var baseId: Int = 0
    internal var magicShield: Boolean = false
    internal var gainCap: Int = 500
    internal var gainHP: Int = 5
    internal var gainMana: Int = 5
    internal var gainHealthTicks: Int = 6
    internal var gainHealthAmount: Int = 1
    internal var gainManaTicks: Int = 6
    internal var gainManaAmount: Int = 1
    internal var manaMultiplier: Float = 4.0f
    internal var attackSpeed: Int = 1500
    internal var baseSpeed: Int = 110
    internal var soulMax: Int = 100
    internal var gainSoulTicks: Int = 120000
    internal var fromVocation: Int = VOCATION_NONE
    internal var combat: Boolean = true
    internal var avatarLookType: Int = 0

    internal var meleeDamageMultiplier: Float = 1.0f
    internal var distDamageMultiplier: Float = 1.0f
    internal var defenseMultiplier: Float = 1.0f
    internal var armorMultiplier: Float = 1.0f

    internal var mitigationFactor: Float = 1.0f
    internal var mitigationPrimaryShield: Float = 1.0f
    internal var mitigationSecondaryShield: Float = 1.0f

    internal var pvpDamageReceivedMultiplier: Float = 1.0f
    internal var pvpDamageDealtMultiplier: Float = 1.0f

    /**
     * `float skillMultipliers[SKILL_LAST + 1]` (vocation.hpp:92). The default
     * initializer is the one Canary ships; XML `<skill>` children override it.
     */
    internal val skillMultipliers: FloatArray =
        floatArrayOf(1.5f, 2.0f, 2.0f, 2.0f, 2.0f, 1.5f, 1.1f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f)

    /** `std::map<WheelGemQuality_t, std::string> wheelGems` (vocation.hpp:87). */
    internal val wheelGems: MutableMap<Int, String> = linkedMapOf()

    /** `Vocation::getId` (vocation.cpp:309). */
    fun getId(): Int = idValue

    /** `Vocation::getClientId` (vocation.cpp:313). */
    fun getClientId(): Int = clientId

    /** `Vocation::getBaseId` (vocation.cpp:317). */
    fun getBaseId(): Int = baseId

    /** `Vocation::getVocName` (vocation.cpp:239). */
    fun getVocName(): String = name

    /** `Vocation::getVocDescription` (vocation.cpp:243). */
    fun getVocDescription(): String = description

    /** `Vocation::getAvatarLookType` (vocation.cpp:321). */
    fun getAvatarLookType(): Int = avatarLookType

    /** `Vocation::getHPGain` (vocation.cpp:325). */
    fun getHPGain(): Int = gainHP

    /** `Vocation::getManaGain` (vocation.cpp:329). */
    fun getManaGain(): Int = gainMana

    /** `Vocation::getCapGain` (vocation.cpp:333). */
    fun getCapGain(): Int = gainCap

    /** `Vocation::getSoulMax` (vocation.cpp:353). */
    fun getSoulMax(): Int = soulMax

    /** `Vocation::getBaseAttackSpeed` (vocation.cpp:361). */
    fun getBaseAttackSpeed(): Int = attackSpeed

    /** `Vocation::getBaseSpeed` (vocation.cpp:369). */
    fun getBaseSpeed(): Int = baseSpeed

    /** `Vocation::getFromVocation` (vocation.cpp:373). */
    fun getFromVocation(): Int = fromVocation

    /** `Vocation::getMagicShield` (vocation.cpp:377). */
    fun getMagicShield(): Boolean = magicShield

    /** `Vocation::canCombat` (vocation.cpp:381). */
    fun canCombat(): Boolean = combat

    /**
     * `Vocation::getReqSkillTries` (vocation.cpp:265). Uses
     * `skillBase[skill] * skillMultipliers[skill]^(level - 11)`, truncated to an
     * unsigned integer. Returns 0 for `level <= minSkillLevel` (10) and for skill
     * ids outside the table.
     */
    fun getReqSkillTries(skill: Int, level: Int): Long {
        if (skill > SKILL_LAST || level <= MIN_SKILL_LEVEL) {
            return 0
        }
        if (skill < 0 || skill >= skillBase.size) {
            return 0
        }
        val multiplier = skillMultipliers[skill].toDouble()
        return (skillBase[skill] * multiplier.pow(level - (MIN_SKILL_LEVEL + 1))).toLong()
    }

    /**
     * `Vocation::getTotalSkillTries` (vocation.cpp:247): sum of
     * [getReqSkillTries] from `minSkillLevel` to `level`. Canary accumulates in
     * `absl::uint128`; the Kotlin [Long] is sufficient for the levels reachable
     * in-game (see PARITY GAPS).
     */
    fun getTotalSkillTries(skill: Int, level: Int): Long {
        if (skill > SKILL_LAST) {
            return 0
        }
        var total = 0L
        for (i in MIN_SKILL_LEVEL..level) {
            total += getReqSkillTries(skill, i)
        }
        return total
    }

    /**
     * `Vocation::getReqMana` (vocation.cpp:295):
     * `floor(1600 * manaMultiplier^(magLevel - 1))`.
     */
    fun getReqMana(magLevel: Int): Long {
        if (magLevel == 0) {
            return 0
        }
        return floor(1600 * manaMultiplier.toDouble().pow((magLevel - 1).toDouble())).toLong()
    }

    /**
     * `Vocation::getTotalMana` (vocation.cpp:280): sum of [getReqMana] from 1 to
     * `magLevel`. Canary accumulates in `absl::uint128` (see PARITY GAPS).
     */
    fun getTotalMana(magLevel: Int): Long {
        if (magLevel == 0) {
            return 0
        }
        var total = 0L
        for (i in 1..magLevel) {
            total += getReqMana(i)
        }
        return total
    }

    companion object {
        /** `minSkillLevel` (vocation.cpp:237). */
        const val MIN_SKILL_LEVEL: Int = 10

        /** `Vocation::skillBase[SKILL_LAST + 1]` (vocation.cpp:236). */
        val skillBase: IntArray = intArrayOf(50, 50, 50, 50, 30, 100, 20, 0, 0, 0, 0, 0, 0)

        // `enum skills_t` (src/creatures/creatures_definitions.hpp:466).
        const val SKILL_FIST: Int = 0
        const val SKILL_CLUB: Int = 1
        const val SKILL_SWORD: Int = 2
        const val SKILL_AXE: Int = 3
        const val SKILL_DISTANCE: Int = 4
        const val SKILL_SHIELD: Int = 5
        const val SKILL_FISHING: Int = 6

        /** `SKILL_LAST = SKILL_MANA_LEECH_AMOUNT` (creatures_definitions.hpp:480,486). */
        const val SKILL_LAST: Int = 12

        // `enum Vocation_t` (src/creatures/creatures_definitions.hpp:790).
        const val VOCATION_NONE: Int = 0
        const val VOCATION_SORCERER: Int = 1
        const val VOCATION_DRUID: Int = 2
        const val VOCATION_PALADIN: Int = 3
        const val VOCATION_KNIGHT: Int = 4
        const val VOCATION_MASTER_SORCERER: Int = 5
        const val VOCATION_ELDER_DRUID: Int = 6
        const val VOCATION_ROYAL_PALADIN: Int = 7
        const val VOCATION_ELITE_KNIGHT: Int = 8
        const val VOCATION_MONK: Int = 9
        const val VOCATION_EXALTED_MONK: Int = 10

        // Cip tibia client ids (creatures_definitions.hpp:805).
        const val VOCATION_KNIGHT_CIP: Int = 1
        const val VOCATION_PALADIN_CIP: Int = 2
        const val VOCATION_SORCERER_CIP: Int = 3
        const val VOCATION_DRUID_CIP: Int = 4
        const val VOCATION_MONK_CIP: Int = 5
    }
}
