package org.opentibia.canary.combat.condition

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ConditionAttributesTest {

    private var clock = 0L

    private fun attributes() = ConditionAttributes(
        ConditionId.CONDITIONID_COMBAT,
        ConditionType.CONDITION_ATTRIBUTES,
        ticks = 10000,
        now = { clock },
    )

    @Test
    fun `flat skill delta is applied and removed`() {
        val target = FakeConditionTarget()
        val attrs = attributes()
        attrs.setParam(ConditionParam.CONDITION_PARAM_SKILL_SWORD, 5)

        attrs.startCondition(target)
        assertThat(target.varSkills[Skill.SKILL_SWORD]).isEqualTo(5)
        assertThat(target.useDefenseValue).isTrue()

        attrs.endCondition(target)
        assertThat(target.varSkills[Skill.SKILL_SWORD]).isEqualTo(-5)
    }

    @Test
    fun `melee param sets club axe and sword together`() {
        val target = FakeConditionTarget()
        val attrs = attributes()
        attrs.setParam(ConditionParam.CONDITION_PARAM_SKILL_MELEE, 4)

        attrs.startCondition(target)

        assertThat(target.varSkills[Skill.SKILL_CLUB]).isEqualTo(4)
        assertThat(target.varSkills[Skill.SKILL_AXE]).isEqualTo(4)
        assertThat(target.varSkills[Skill.SKILL_SWORD]).isEqualTo(4)
    }

    @Test
    fun `flat stat delta is applied to the player`() {
        val target = FakeConditionTarget()
        val attrs = attributes()
        attrs.setParam(ConditionParam.CONDITION_PARAM_STAT_MAXHITPOINTS, 100)

        attrs.startCondition(target)

        assertThat(target.varStats[Stat.STAT_MAXHITPOINTS]).isEqualTo(100)
    }

    @Test
    fun `percent stat resolves against the base stat`() {
        val target = FakeConditionTarget(maxHealth = 200)
        val attrs = attributes()
        attrs.setParam(ConditionParam.CONDITION_PARAM_STAT_MAXHITPOINTSPERCENT, 150)

        attrs.startCondition(target)

        // 200 * ((150 - 100) / 100) = 100
        assertThat(target.varStats[Stat.STAT_MAXHITPOINTS]).isEqualTo(100)
    }

    @Test
    fun `percent absorb resolves against current absorb`() {
        val target = FakeConditionTarget()
        val attrs = attributes()
        attrs.setParam(ConditionParam.CONDITION_PARAM_ABSORB_FIREPERCENT, 10)

        attrs.startCondition(target)
        assertThat(target.absorbValues[CombatType.COMBAT_FIREDAMAGE]).isEqualTo(10)

        attrs.endCondition(target)
        assertThat(target.absorbValues[CombatType.COMBAT_FIREDAMAGE]).isEqualTo(-10)
    }

    @Test
    fun `percent buff resolves against current buff`() {
        val target = FakeConditionTarget()
        target.buffValues[Buff.BUFF_HEALINGDEALT] = 20
        val attrs = attributes()
        attrs.setParam(ConditionParam.CONDITION_PARAM_BUFF_HEALINGDEALT, 150)

        attrs.startCondition(target)

        // 20 * ((150 - 100) / 100) = 10
        assertThat(target.buffValues[Buff.BUFF_HEALINGDEALT]).isEqualTo(10)
    }

    @Test
    fun `disable defense toggles use-defense`() {
        val target = FakeConditionTarget()
        val attrs = attributes()
        attrs.setParam(ConditionParam.CONDITION_PARAM_DISABLE_DEFENSE, 1)

        attrs.startCondition(target)
        assertThat(target.useDefenseValue).isFalse()

        attrs.endCondition(target)
        assertThat(target.useDefenseValue).isTrue()
    }

    @Test
    fun `charm chance modifier is added on start and removed on end`() {
        val target = FakeConditionTarget()
        target.charmModifier = 3
        val attrs = attributes()
        attrs.setParam(ConditionParam.CONDITION_PARAM_CHARM_CHANCE_MODIFIER, 7)

        attrs.startCondition(target)
        assertThat(target.charmModifier).isEqualTo(10)

        attrs.endCondition(target)
        assertThat(target.charmModifier).isEqualTo(3)
    }

    @Test
    fun `stacking ends the old attributes before applying the new`() {
        val target = FakeConditionTarget()
        val active = attributes()
        active.setParam(ConditionParam.CONDITION_PARAM_SKILL_SWORD, 5)
        active.startCondition(target)

        val incoming = attributes()
        incoming.setParam(ConditionParam.CONDITION_PARAM_SKILL_SWORD, 8)
        active.addCondition(target, incoming)

        assertThat(target.varSkills[Skill.SKILL_SWORD]).isEqualTo(8)
    }
}
