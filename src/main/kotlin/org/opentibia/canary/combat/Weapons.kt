package org.opentibia.canary.combat

import java.util.Random
import kotlin.math.ceil

/**
 * Faithful port of the `Weapons` static damage helpers.
 *
 * Sources:
 *  - `Weapons::getMaxMeleeDamage`: src/items/weapons/weapons.cpp:88-91
 *  - `Weapons::getMaxWeaponDamage`: src/items/weapons/weapons.cpp:94-100
 *  - `WeaponMelee::getWeaponDamage`: src/items/weapons/weapons.cpp:645-664
 */
object Weapons {
    /**
     * `static_cast<int32_t>(std::ceil((attackSkill * (attackValue * 0.05)) + (attackValue * 0.5)))`
     * (src/items/weapons/weapons.cpp:88-91).
     */
    fun getMaxMeleeDamage(attackSkill: Int, attackValue: Int): Int =
        ceil(attackSkill * (attackValue * 0.05) + attackValue * 0.5).toInt()

    /**
     * Player weapon max damage (src/items/weapons/weapons.cpp:94-100).
     *
     * melee: `round(0.085 * attackFactor * attackValue * attackSkill + level / 5)`,
     * or `0` when `attackValue <= 0`.
     * distance: `round(0.09 * attackFactor * attackValue * attackSkill + level / 5)`.
     *
     * `level / 5` is integer division exactly as in C++.
     */
    fun getMaxWeaponDamage(
        level: Int,
        attackSkill: Int,
        attackValue: Int,
        attackFactor: Float,
        isMelee: Boolean,
    ): Int {
        val coefficient = if (isMelee) 0.085 else 0.09
        if (isMelee && attackValue <= 0) {
            return 0
        }
        return Math.round(coefficient * attackFactor * attackValue * attackSkill + level / 5).toInt()
    }

    /**
     * Reduced port of `WeaponMelee::getWeaponDamage` (src/items/weapons/weapons.cpp:645-664).
     *
     * `proficiencyAttack`, the equipped item and the vocation lookup are the
     * caller's responsibility, so combined attack is passed explicitly:
     * `combinedAttack = physicalAttack + elementalAttack + proficiencyAttack`.
     *
     * Returns the same negated value Canary applies to the target:
     * `-normal_random(minValue, maxValue)` where `minValue = physicalAttack > 0 ? level / 5 : 0`.
     */
    fun getWeaponDamage(
        level: Int,
        attackSkill: Int,
        physicalAttack: Int,
        elementalAttack: Int,
        attackFactor: Float,
        meleeDamageMultiplier: Double,
        random: Random,
        maxDamage: Boolean = false,
    ): Int {
        val combinedAttack = physicalAttack + elementalAttack
        val maxValue = (getMaxWeaponDamage(level, attackSkill, combinedAttack, attackFactor, isMelee = true) * meleeDamageMultiplier).toInt()
        val minValue = if (physicalAttack > 0) level / 5 else 0

        if (maxDamage) {
            return -maxValue
        }
        return -normalRandom(minValue, maxValue, random)
    }
}

/**
 * Resolved out-parameters of `Weapon::calculateSkillFormula`
 * (src/items/weapons/weapons.cpp:450-485), the operand resolution
 * `ValueCallback::getMinMaxValues` performs for `COMBAT_FORMULA_SKILL`
 * (src/creatures/combat/combat.cpp:2016-2028).
 *
 * The weapon/item lookups (`player->getWeapon()`, `g_weapons().getWeapon(tool)`,
 * `Item::getAttack`/`getWeaponType`/`getElementType`/`getElementDamageValue`,
 * `Player::getWeaponSkill`/`getAttackFactor` and the `useCharges` transform)
 * stay in the weapons subsystem; the seam returns the resolved values.
 */
data class SkillFormulaValues(
    /** `attackSkill` out-parameter (`weapons.cpp:482`, `Player::getWeaponSkill`). */
    val attackSkill: Int,
    /** `attackValue` out-parameter (`weapons.cpp:457-472`), already including ammo and element. */
    val attackValue: Int,
    /** `attackFactor` out-parameter (`weapons.cpp:483`, `Player::getAttackFactor`). */
    val attackFactor: Float,
    /** `elementAttack` out-parameter (`weapons.cpp:470`, the elemental damage value). */
    val elementAttack: Int,
    /** `damage.secondary.type = getElementType()` (`weapons.cpp:465-466`). */
    val elementType: CombatType,
    /** The bool return: `elementType != COMBAT_NONE` (`weapons.cpp:468-473`). */
    val shouldCalculateSecondaryDamage: Boolean,
)

/**
 * `Weapon::calculateSkillFormula` (src/items/weapons/weapons.cpp:450-485)
 * reached from `ValueCallback::getMinMaxValues` for the
 * `COMBAT_FORMULA_SKILL` callback type (combat.cpp:2019-2021).
 *
 * Returns null when `g_weapons().getWeapon(tool)` resolves no weapon, i.e. the
 * C++ `if (weapon)` guard keeps `attackSkill = 0`, `attackValue = 7` and
 * `attackFactor = 0` (combat.cpp:2017-2027). The `useCharges`
 * `g_game().transformItem(tool, id, charges - 1)` side effect stays with the
 * seam provider (`weapons.cpp:475-480`).
 */
fun interface CombatSkillFormula {
    fun calculateSkillFormula(useCharges: Boolean): SkillFormulaValues?
}
