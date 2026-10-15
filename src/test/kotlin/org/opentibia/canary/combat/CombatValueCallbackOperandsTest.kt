package org.opentibia.canary.combat

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.Random

/**
 * Transcript tests for the `ValueCallback::getMinMaxValues` operand push
 * (src/creatures/combat/combat.cpp:1982-2068):
 *
 *  - `COMBAT_FORMULA_LEVELMAGIC` pushes `(level, magicLevelSkill)`
 *    (combat.cpp:2001-2006);
 *  - `COMBAT_FORMULA_SKILL` pushes `(attackSkill, attackValue, attackFactor)`
 *    resolved through `Weapon::calculateSkillFormula`
 *    (src/items/weapons/weapons.cpp:450-485, combat.cpp:2016-2028);
 *  - the callback result is split into primary/secondary damage when the
 *    resolved weapon carries an element (combat.cpp:2046-2063).
 */
class CombatValueCallbackOperandsTest {

    /** `ValueCallback(COMBAT_FORMULA_SKILL)` (`Combat::setCallback`, combat.cpp:679-681). */
    private fun skillCallback(
        get: (useCharges: Boolean, operands: CombatCallbackOperands.Skill) -> Pair<Int, Int>?,
    ): CombatValueCallback = object : CombatValueCallback {
        override val type: FormulaType = FormulaType.SKILL

        override fun getMinMaxValues(useCharges: Boolean, operands: CombatCallbackOperands): Pair<Int, Int>? =
            when (operands) {
                is CombatCallbackOperands.Skill -> get(useCharges, operands)
                else -> null
            }
    }

    private fun formula(
        useCharges: Boolean,
        attackSkill: Int,
        attackValue: Int,
        attackFactor: Float,
        elementAttack: Int,
        elementType: CombatType,
        shouldCalculateSecondaryDamage: Boolean,
    ): CombatSkillFormula = CombatSkillFormula { seenUseCharges ->
        assertThat(seenUseCharges).isEqualTo(useCharges)
        SkillFormulaValues(
            attackSkill = attackSkill,
            attackValue = attackValue,
            attackFactor = attackFactor,
            elementAttack = elementAttack,
            elementType = elementType,
            shouldCalculateSecondaryDamage = shouldCalculateSecondaryDamage,
        )
    }

    @Test
    fun `skill operands reach the callback`() {
        var seenOperands: CombatCallbackOperands.Skill? = null
        val callback = skillCallback { _, operands ->
            seenOperands = operands
            20 to 20
        }
        val skillFormula = formula(
            useCharges = false,
            attackSkill = 87,
            attackValue = 42,
            attackFactor = 1.5f,
            elementAttack = 0,
            elementType = CombatType.NONE,
            shouldCalculateSecondaryDamage = false,
        )

        val damage = Combat.getCombatDamage(
            formulaType = FormulaType.SKILL,
            valueCallback = callback,
            skillFormula = skillFormula,
            random = Random(1),
        )

        assertThat(seenOperands)
            .isEqualTo(CombatCallbackOperands.Skill(attackSkill = 87, attackValue = 42, attackFactor = 1.5f))
        assertThat(damage.primaryValue).isEqualTo(20)
        assertThat(damage.secondaryType).isEqualTo(CombatType.NONE)
        assertThat(damage.secondaryValue).isZero()
    }

    @Test
    fun `skill operands keep the C++ defaults when no weapon resolves`() {
        // combat.cpp:2017-2027: `attackSkill = 0`, `attackValue = 7`, `attackFactor = 0`.
        var seenOperands: CombatCallbackOperands.Skill? = null
        val callback = skillCallback { _, operands ->
            seenOperands = operands
            10 to 10
        }

        val damage = Combat.getCombatDamage(
            formulaType = FormulaType.SKILL,
            valueCallback = callback,
            skillFormula = CombatSkillFormula { null },
            random = Random(1),
        )

        assertThat(seenOperands)
            .isEqualTo(CombatCallbackOperands.Skill(attackSkill = 0, attackValue = 7, attackFactor = 0f))
        assertThat(damage.primaryValue).isEqualTo(10)
        assertThat(damage.secondaryType).isEqualTo(CombatType.NONE)
    }

    @Test
    fun `an elemental skill weapon splits the callback result into primary and secondary`() {
        // combat.cpp:2048-2055: `factor = elementAttack / attackValue`,
        // `elementDamage = round(defaultDmg * factor)`,
        // `physDmg = round(defaultDmg * (1 - factor))`.
        var seenOperands: CombatCallbackOperands.Skill? = null
        val callback = skillCallback { _, operands ->
            seenOperands = operands
            100 to 100
        }
        val skillFormula = formula(
            useCharges = false,
            attackSkill = 50,
            attackValue = 50,
            attackFactor = 1.0f,
            elementAttack = 10,
            elementType = CombatType.FIREDAMAGE,
            shouldCalculateSecondaryDamage = true,
        )

        val damage = Combat.getCombatDamage(
            formulaType = FormulaType.SKILL,
            valueCallback = callback,
            skillFormula = skillFormula,
            random = Random(1),
        )

        // attackValue already includes the element: 50 total, 10 elemental.
        assertThat(seenOperands)
            .isEqualTo(CombatCallbackOperands.Skill(attackSkill = 50, attackValue = 50, attackFactor = 1.0f))
        assertThat(damage.secondaryType).isEqualTo(CombatType.FIREDAMAGE)
        assertThat(damage.primaryValue).isEqualTo(80)
        assertThat(damage.secondaryValue).isEqualTo(20)
    }

    @Test
    fun `the use charges flag reaches the skill formula resolution`() {
        // combat.cpp:2019-2021 -> `Weapon::calculateSkillFormula(..., useCharges)`.
        var seenCharges: Boolean? = null
        var seenOperands: CombatCallbackOperands.Skill? = null
        val callback = skillCallback { _, operands ->
            seenOperands = operands
            1 to 1
        }
        val skillFormula = CombatSkillFormula { useCharges ->
            seenCharges = useCharges
            SkillFormulaValues(
                attackSkill = 1,
                attackValue = 9,
                attackFactor = 0.5f,
                elementAttack = 0,
                elementType = CombatType.NONE,
                shouldCalculateSecondaryDamage = false,
            )
        }

        Combat.getCombatDamage(
            formulaType = FormulaType.SKILL,
            valueCallback = callback,
            skillFormula = skillFormula,
            useCharges = true,
            random = Random(1),
        )

        assertThat(seenCharges).isTrue()
        assertThat(seenOperands)
            .isEqualTo(CombatCallbackOperands.Skill(attackSkill = 1, attackValue = 9, attackFactor = 0.5f))
    }

    @Test
    fun `a level magic callback never consults the skill formula seam`() {
        val callback = object : CombatValueCallback {
            override val type: FormulaType = FormulaType.LEVELMAGIC

            override fun getMinMaxValues(useCharges: Boolean, operands: CombatCallbackOperands): Pair<Int, Int>? =
                3 to 3
        }
        var consulted = false
        val skillFormula = CombatSkillFormula {
            consulted = true
            null
        }

        val damage = Combat.getCombatDamage(
            formulaType = FormulaType.LEVELMAGIC,
            valueCallback = callback,
            skillFormula = skillFormula,
            random = Random(1),
        )

        assertThat(consulted).isFalse()
        assertThat(damage.primaryValue).isEqualTo(3)
    }
}
