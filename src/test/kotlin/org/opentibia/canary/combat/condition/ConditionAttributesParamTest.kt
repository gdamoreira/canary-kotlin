package org.opentibia.canary.combat.condition

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Complete `ConditionAttributes::setParam` coverage
 * (`src/creatures/combat/condition.cpp:1049-1345`) for the params not exercised
 * by [ConditionAttributesTest].
 */
class ConditionAttributesParamTest {

    private var clock = 0L

    private fun attributes() = ConditionAttributes(
        ConditionId.CONDITIONID_COMBAT,
        ConditionType.CONDITION_ATTRIBUTES,
        ticks = 10000,
        now = { clock },
    )

    private fun applied(param: ConditionParam, value: Int, target: FakeConditionTarget = FakeConditionTarget()): FakeConditionTarget {
        val attrs = attributes()
        attrs.setParam(param, value)
        attrs.startCondition(target)
        return target
    }

    @Test
    fun `every flat skill param writes its skill slot`() {
        val cases = mapOf(
            Skill.SKILL_FIST to ConditionParam.CONDITION_PARAM_SKILL_FIST,
            Skill.SKILL_CLUB to ConditionParam.CONDITION_PARAM_SKILL_CLUB,
            Skill.SKILL_SWORD to ConditionParam.CONDITION_PARAM_SKILL_SWORD,
            Skill.SKILL_AXE to ConditionParam.CONDITION_PARAM_SKILL_AXE,
            Skill.SKILL_DISTANCE to ConditionParam.CONDITION_PARAM_SKILL_DISTANCE,
            Skill.SKILL_SHIELD to ConditionParam.CONDITION_PARAM_SKILL_SHIELD,
            Skill.SKILL_FISHING to ConditionParam.CONDITION_PARAM_SKILL_FISHING,
            Skill.SKILL_CRITICAL_HIT_CHANCE to ConditionParam.CONDITION_PARAM_SKILL_CRITICAL_HIT_CHANCE,
            Skill.SKILL_CRITICAL_HIT_DAMAGE to ConditionParam.CONDITION_PARAM_SKILL_CRITICAL_HIT_DAMAGE,
            Skill.SKILL_LIFE_LEECH_CHANCE to ConditionParam.CONDITION_PARAM_SKILL_LIFE_LEECH_CHANCE,
            Skill.SKILL_LIFE_LEECH_AMOUNT to ConditionParam.CONDITION_PARAM_SKILL_LIFE_LEECH_AMOUNT,
            Skill.SKILL_MANA_LEECH_CHANCE to ConditionParam.CONDITION_PARAM_SKILL_MANA_LEECH_CHANCE,
            Skill.SKILL_MANA_LEECH_AMOUNT to ConditionParam.CONDITION_PARAM_SKILL_MANA_LEECH_AMOUNT,
        )

        for ((skill, param) in cases) {
            val target = applied(param, 7)
            assertThat(target.varSkills[skill]).`as`("%s from %s", skill, param.name).isEqualTo(7)
        }
    }

    @Test
    fun `every percent skill param resolves against the base skill`() {
        val cases = mapOf(
            Skill.SKILL_FIST to ConditionParam.CONDITION_PARAM_SKILL_FISTPERCENT,
            Skill.SKILL_CLUB to ConditionParam.CONDITION_PARAM_SKILL_CLUBPERCENT,
            Skill.SKILL_SWORD to ConditionParam.CONDITION_PARAM_SKILL_SWORDPERCENT,
            Skill.SKILL_AXE to ConditionParam.CONDITION_PARAM_SKILL_AXEPERCENT,
            Skill.SKILL_DISTANCE to ConditionParam.CONDITION_PARAM_SKILL_DISTANCEPERCENT,
            Skill.SKILL_SHIELD to ConditionParam.CONDITION_PARAM_SKILL_SHIELDPERCENT,
            Skill.SKILL_FISHING to ConditionParam.CONDITION_PARAM_SKILL_FISHINGPERCENT,
        )

        for ((skill, param) in cases) {
            val target = FakeConditionTarget()
            target.baseSkills[skill] = 20
            applied(param, 150, target)
            assertThat(target.varSkills[skill]).`as`("%s from %s", skill, param.name).isEqualTo(10)
        }
    }

    @Test
    fun `melee percent param resolves the three melee skills against their bases`() {
        val target = FakeConditionTarget()
        target.baseSkills[Skill.SKILL_CLUB] = 10
        target.baseSkills[Skill.SKILL_AXE] = 20
        target.baseSkills[Skill.SKILL_SWORD] = 30

        applied(ConditionParam.CONDITION_PARAM_SKILL_MELEEPERCENT, 150, target)

        assertThat(target.varSkills[Skill.SKILL_CLUB]).isEqualTo(5)
        assertThat(target.varSkills[Skill.SKILL_AXE]).isEqualTo(10)
        assertThat(target.varSkills[Skill.SKILL_SWORD]).isEqualTo(15)
    }

    @Test
    fun `every flat stat param writes its stat slot`() {
        val cases = mapOf(
            Stat.STAT_MAXHITPOINTS to ConditionParam.CONDITION_PARAM_STAT_MAXHITPOINTS,
            Stat.STAT_MAXMANAPOINTS to ConditionParam.CONDITION_PARAM_STAT_MAXMANAPOINTS,
            Stat.STAT_MAGICPOINTS to ConditionParam.CONDITION_PARAM_STAT_MAGICPOINTS,
        )

        for ((stat, param) in cases) {
            val target = applied(param, 7)
            assertThat(target.varStats[stat]).`as`("%s from %s", stat, param.name).isEqualTo(7)
        }
    }

    @Test
    fun `capacity percent resolves against the player capacity`() {
        val target = FakeConditionTarget()
        target.capacityValue = 400.0

        applied(ConditionParam.CONDITION_PARAM_STAT_CAPACITYPERCENT, 150, target)

        assertThat(target.varStats[Stat.STAT_CAPACITY]).isEqualTo(600)
    }

    @Test
    fun `every absorb percent param maps to its combat type`() {
        val cases = mapOf(
            CombatType.COMBAT_PHYSICALDAMAGE to ConditionParam.CONDITION_PARAM_ABSORB_PHYSICALPERCENT,
            CombatType.COMBAT_FIREDAMAGE to ConditionParam.CONDITION_PARAM_ABSORB_FIREPERCENT,
            CombatType.COMBAT_ENERGYDAMAGE to ConditionParam.CONDITION_PARAM_ABSORB_ENERGYPERCENT,
            CombatType.COMBAT_ICEDAMAGE to ConditionParam.CONDITION_PARAM_ABSORB_ICEPERCENT,
            CombatType.COMBAT_EARTHDAMAGE to ConditionParam.CONDITION_PARAM_ABSORB_EARTHPERCENT,
            CombatType.COMBAT_DEATHDAMAGE to ConditionParam.CONDITION_PARAM_ABSORB_DEATHPERCENT,
            CombatType.COMBAT_HOLYDAMAGE to ConditionParam.CONDITION_PARAM_ABSORB_HOLYPERCENT,
            CombatType.COMBAT_LIFEDRAIN to ConditionParam.CONDITION_PARAM_ABSORB_LIFEDRAINPERCENT,
            CombatType.COMBAT_MANADRAIN to ConditionParam.CONDITION_PARAM_ABSORB_MANADRAINPERCENT,
            CombatType.COMBAT_DROWNDAMAGE to ConditionParam.CONDITION_PARAM_ABSORB_DROWNPERCENT,
        )

        for ((combat, param) in cases) {
            val target = applied(param, 10)
            assertThat(target.absorbValues[combat]).`as`("%s from %s", combat, param.name).isEqualTo(10)
        }
    }

    @Test
    fun `every increase percent param maps to its combat type`() {
        val cases = mapOf(
            CombatType.COMBAT_PHYSICALDAMAGE to ConditionParam.CONDITION_PARAM_INCREASE_PHYSICALPERCENT,
            CombatType.COMBAT_FIREDAMAGE to ConditionParam.CONDITION_PARAM_INCREASE_FIREPERCENT,
            CombatType.COMBAT_ENERGYDAMAGE to ConditionParam.CONDITION_PARAM_INCREASE_ENERGYPERCENT,
            CombatType.COMBAT_ICEDAMAGE to ConditionParam.CONDITION_PARAM_INCREASE_ICEPERCENT,
            CombatType.COMBAT_EARTHDAMAGE to ConditionParam.CONDITION_PARAM_INCREASE_EARTHPERCENT,
            CombatType.COMBAT_DEATHDAMAGE to ConditionParam.CONDITION_PARAM_INCREASE_DEATHPERCENT,
            CombatType.COMBAT_HOLYDAMAGE to ConditionParam.CONDITION_PARAM_INCREASE_HOLYPERCENT,
            CombatType.COMBAT_LIFEDRAIN to ConditionParam.CONDITION_PARAM_INCREASE_LIFEDRAINPERCENT,
            CombatType.COMBAT_MANADRAIN to ConditionParam.CONDITION_PARAM_INCREASE_MANADRAINPERCENT,
            CombatType.COMBAT_DROWNDAMAGE to ConditionParam.CONDITION_PARAM_INCREASE_DROWNPERCENT,
        )

        for ((combat, param) in cases) {
            val target = applied(param, 10)
            assertThat(target.increaseValues[combat]).`as`("%s from %s", combat, param.name).isEqualTo(10)
        }
    }

    @Test
    fun `every buff percent param resolves against the current buff`() {
        val cases = mapOf(
            Buff.BUFF_HARMONYBONUS to ConditionParam.CONDITION_PARAM_BUFF_HARMONYBONUS,
            Buff.BUFF_HEALINGDEALT to ConditionParam.CONDITION_PARAM_BUFF_HEALINGDEALT,
            Buff.BUFF_HEALINGRECEIVED to ConditionParam.CONDITION_PARAM_BUFF_HEALINGRECEIVED,
            Buff.BUFF_AUTOATTACKDEALT to ConditionParam.CONDITION_PARAM_BUFF_AUTOATTACKDEALT,
            Buff.BUFF_DAMAGEDEALT to ConditionParam.CONDITION_PARAM_BUFF_DAMAGEDEALT,
            Buff.BUFF_DAMAGERECEIVED to ConditionParam.CONDITION_PARAM_BUFF_DAMAGERECEIVED,
        )

        for ((buff, param) in cases) {
            val target = FakeConditionTarget()
            target.buffValues[buff] = 20
            applied(param, 150, target)
            assertThat(target.buffValues[buff]).`as`("%s from %s", buff, param.name).isEqualTo(10)
        }
    }

    @Test
    fun `negative percent stats clamp to zero`() {
        val target = applied(ConditionParam.CONDITION_PARAM_STAT_MAXHITPOINTSPERCENT, -10)

        assertThat(target.varStats[Stat.STAT_MAXHITPOINTS]).isNull()
    }
}
