package org.opentibia.canary.combat.condition

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Numeric parity of the condition enums with
 * `src/creatures/creatures_definitions.hpp` (values cross the creature
 * condition storage, the wire protocol and the Lua bridge).
 */
class ConditionTypesParityTest {

    @Test
    fun `condition type values mirror ConditionType_t`() {
        // creatures_definitions.hpp:112-157.
        assertThat(ConditionType.CONDITION_NONE.value).isEqualTo(0)
        assertThat(ConditionType.CONDITION_POISON.value).isEqualTo(1)
        assertThat(ConditionType.CONDITION_FIRE.value).isEqualTo(2)
        assertThat(ConditionType.CONDITION_ENERGY.value).isEqualTo(3)
        assertThat(ConditionType.CONDITION_BLEEDING.value).isEqualTo(4)
        assertThat(ConditionType.CONDITION_HASTE.value).isEqualTo(5)
        assertThat(ConditionType.CONDITION_PARALYZE.value).isEqualTo(6)
        assertThat(ConditionType.CONDITION_OUTFIT.value).isEqualTo(7)
        assertThat(ConditionType.CONDITION_INVISIBLE.value).isEqualTo(8)
        assertThat(ConditionType.CONDITION_LIGHT.value).isEqualTo(9)
        assertThat(ConditionType.CONDITION_MANASHIELD.value).isEqualTo(10)
        assertThat(ConditionType.CONDITION_INFIGHT.value).isEqualTo(11)
        assertThat(ConditionType.CONDITION_DRUNK.value).isEqualTo(12)
        assertThat(ConditionType.CONDITION_EXHAUST.value).isEqualTo(13)
        assertThat(ConditionType.CONDITION_REGENERATION.value).isEqualTo(14)
        assertThat(ConditionType.CONDITION_SOUL.value).isEqualTo(15)
        assertThat(ConditionType.CONDITION_DROWN.value).isEqualTo(16)
        assertThat(ConditionType.CONDITION_MUTED.value).isEqualTo(17)
        assertThat(ConditionType.CONDITION_CHANNELMUTEDTICKS.value).isEqualTo(18)
        assertThat(ConditionType.CONDITION_YELLTICKS.value).isEqualTo(19)
        assertThat(ConditionType.CONDITION_ATTRIBUTES.value).isEqualTo(20)
        assertThat(ConditionType.CONDITION_FREEZING.value).isEqualTo(21)
        assertThat(ConditionType.CONDITION_DAZZLED.value).isEqualTo(22)
        assertThat(ConditionType.CONDITION_CURSED.value).isEqualTo(23)
        assertThat(ConditionType.CONDITION_EXHAUST_COMBAT.value).isEqualTo(24)
        assertThat(ConditionType.CONDITION_EXHAUST_HEAL.value).isEqualTo(25)
        assertThat(ConditionType.CONDITION_PACIFIED.value).isEqualTo(26)
        assertThat(ConditionType.CONDITION_SPELLCOOLDOWN.value).isEqualTo(27)
        assertThat(ConditionType.CONDITION_SPELLGROUPCOOLDOWN.value).isEqualTo(28)
        assertThat(ConditionType.CONDITION_ROOTED.value).isEqualTo(29)
        assertThat(ConditionType.CONDITION_FEARED.value).isEqualTo(30)
        assertThat(ConditionType.CONDITION_LESSERHEX.value).isEqualTo(31)
        assertThat(ConditionType.CONDITION_INTENSEHEX.value).isEqualTo(32)
        assertThat(ConditionType.CONDITION_GREATERHEX.value).isEqualTo(33)
        assertThat(ConditionType.CONDITION_BAKRAGORE.value).isEqualTo(34)
        assertThat(ConditionType.CONDITION_GOSHNARTAINT.value).isEqualTo(35)
        assertThat(ConditionType.CONDITION_POWERLESS.value).isEqualTo(36)
        assertThat(ConditionType.CONDITION_AGONY.value).isEqualTo(37)
        assertThat(ConditionType.CONDITION_SERENE.value).isEqualTo(38)
        assertThat(ConditionType.CONDITION_MENTOROTHER.value).isEqualTo(39)
        assertThat(ConditionType.CONDITION_COUNT.value).isEqualTo(40)
        assertThat(ConditionType.entries).hasSize(41)
    }

    @Test
    fun `condition param values mirror the complete ConditionParam_t`() {
        // creatures_definitions.hpp:177-264. Values 3, 29 and 33 are commented
        // out in Canary and are therefore absent here too.
        val expected = mapOf(
            ConditionParam.CONDITION_PARAM_OWNER to 1,
            ConditionParam.CONDITION_PARAM_TICKS to 2,
            ConditionParam.CONDITION_PARAM_HEALTHGAIN to 4,
            ConditionParam.CONDITION_PARAM_HEALTHTICKS to 5,
            ConditionParam.CONDITION_PARAM_MANAGAIN to 6,
            ConditionParam.CONDITION_PARAM_MANATICKS to 7,
            ConditionParam.CONDITION_PARAM_DELAYED to 8,
            ConditionParam.CONDITION_PARAM_SPEED to 9,
            ConditionParam.CONDITION_PARAM_LIGHT_LEVEL to 10,
            ConditionParam.CONDITION_PARAM_LIGHT_COLOR to 11,
            ConditionParam.CONDITION_PARAM_SOULGAIN to 12,
            ConditionParam.CONDITION_PARAM_SOULTICKS to 13,
            ConditionParam.CONDITION_PARAM_MINVALUE to 14,
            ConditionParam.CONDITION_PARAM_MAXVALUE to 15,
            ConditionParam.CONDITION_PARAM_STARTVALUE to 16,
            ConditionParam.CONDITION_PARAM_TICKINTERVAL to 17,
            ConditionParam.CONDITION_PARAM_FORCEUPDATE to 18,
            ConditionParam.CONDITION_PARAM_SKILL_MELEE to 19,
            ConditionParam.CONDITION_PARAM_SKILL_FIST to 20,
            ConditionParam.CONDITION_PARAM_SKILL_CLUB to 21,
            ConditionParam.CONDITION_PARAM_SKILL_SWORD to 22,
            ConditionParam.CONDITION_PARAM_SKILL_AXE to 23,
            ConditionParam.CONDITION_PARAM_SKILL_DISTANCE to 24,
            ConditionParam.CONDITION_PARAM_SKILL_SHIELD to 25,
            ConditionParam.CONDITION_PARAM_SKILL_FISHING to 26,
            ConditionParam.CONDITION_PARAM_STAT_MAXHITPOINTS to 27,
            ConditionParam.CONDITION_PARAM_STAT_MAXMANAPOINTS to 28,
            ConditionParam.CONDITION_PARAM_STAT_MAGICPOINTS to 30,
            ConditionParam.CONDITION_PARAM_STAT_MAXHITPOINTSPERCENT to 31,
            ConditionParam.CONDITION_PARAM_STAT_MAXMANAPOINTSPERCENT to 32,
            ConditionParam.CONDITION_PARAM_STAT_MAGICPOINTSPERCENT to 34,
            ConditionParam.CONDITION_PARAM_PERIODICDAMAGE to 35,
            ConditionParam.CONDITION_PARAM_SKILL_MELEEPERCENT to 36,
            ConditionParam.CONDITION_PARAM_SKILL_FISTPERCENT to 37,
            ConditionParam.CONDITION_PARAM_SKILL_CLUBPERCENT to 38,
            ConditionParam.CONDITION_PARAM_SKILL_SWORDPERCENT to 39,
            ConditionParam.CONDITION_PARAM_SKILL_AXEPERCENT to 40,
            ConditionParam.CONDITION_PARAM_SKILL_DISTANCEPERCENT to 41,
            ConditionParam.CONDITION_PARAM_SKILL_SHIELDPERCENT to 42,
            ConditionParam.CONDITION_PARAM_SKILL_FISHINGPERCENT to 43,
            ConditionParam.CONDITION_PARAM_BUFF_SPELL to 44,
            ConditionParam.CONDITION_PARAM_SUBID to 45,
            ConditionParam.CONDITION_PARAM_FIELD to 46,
            ConditionParam.CONDITION_PARAM_SKILL_CRITICAL_HIT_CHANCE to 47,
            ConditionParam.CONDITION_PARAM_SKILL_CRITICAL_HIT_DAMAGE to 48,
            ConditionParam.CONDITION_PARAM_SKILL_LIFE_LEECH_CHANCE to 49,
            ConditionParam.CONDITION_PARAM_SKILL_LIFE_LEECH_AMOUNT to 50,
            ConditionParam.CONDITION_PARAM_SKILL_MANA_LEECH_CHANCE to 51,
            ConditionParam.CONDITION_PARAM_SKILL_MANA_LEECH_AMOUNT to 52,
            ConditionParam.CONDITION_PARAM_DISABLE_DEFENSE to 53,
            ConditionParam.CONDITION_PARAM_STAT_CAPACITYPERCENT to 54,
            ConditionParam.CONDITION_PARAM_MANASHIELD to 55,
            ConditionParam.CONDITION_PARAM_BUFF_DAMAGEDEALT to 56,
            ConditionParam.CONDITION_PARAM_BUFF_DAMAGERECEIVED to 57,
            ConditionParam.CONDITION_PARAM_SOUND_TICK to 58,
            ConditionParam.CONDITION_PARAM_SOUND_ADD to 59,
            ConditionParam.CONDITION_PARAM_CASTER_POSITION to 60,
            ConditionParam.CONDITION_PARAM_DRAIN_BODY to 61,
            ConditionParam.CONDITION_PARAM_ABSORB_PHYSICALPERCENT to 62,
            ConditionParam.CONDITION_PARAM_ABSORB_FIREPERCENT to 63,
            ConditionParam.CONDITION_PARAM_ABSORB_ENERGYPERCENT to 64,
            ConditionParam.CONDITION_PARAM_ABSORB_ICEPERCENT to 65,
            ConditionParam.CONDITION_PARAM_ABSORB_EARTHPERCENT to 66,
            ConditionParam.CONDITION_PARAM_ABSORB_DEATHPERCENT to 67,
            ConditionParam.CONDITION_PARAM_ABSORB_HOLYPERCENT to 68,
            ConditionParam.CONDITION_PARAM_ABSORB_LIFEDRAINPERCENT to 69,
            ConditionParam.CONDITION_PARAM_ABSORB_MANADRAINPERCENT to 70,
            ConditionParam.CONDITION_PARAM_ABSORB_DROWNPERCENT to 71,
            ConditionParam.CONDITION_PARAM_INCREASE_PHYSICALPERCENT to 72,
            ConditionParam.CONDITION_PARAM_INCREASE_FIREPERCENT to 73,
            ConditionParam.CONDITION_PARAM_INCREASE_ENERGYPERCENT to 74,
            ConditionParam.CONDITION_PARAM_INCREASE_ICEPERCENT to 75,
            ConditionParam.CONDITION_PARAM_INCREASE_EARTHPERCENT to 76,
            ConditionParam.CONDITION_PARAM_INCREASE_DEATHPERCENT to 77,
            ConditionParam.CONDITION_PARAM_INCREASE_HOLYPERCENT to 78,
            ConditionParam.CONDITION_PARAM_INCREASE_LIFEDRAINPERCENT to 79,
            ConditionParam.CONDITION_PARAM_INCREASE_MANADRAINPERCENT to 80,
            ConditionParam.CONDITION_PARAM_INCREASE_DROWNPERCENT to 81,
            ConditionParam.CONDITION_PARAM_CHARM_CHANCE_MODIFIER to 82,
            ConditionParam.CONDITION_PARAM_BUFF_HEALINGRECEIVED to 83,
            ConditionParam.CONDITION_PARAM_BUFF_HEALINGDEALT to 84,
            ConditionParam.CONDITION_PARAM_BUFF_HARMONYBONUS to 85,
            ConditionParam.CONDITION_PARAM_BUFF_AUTOATTACKDEALT to 86,
        )

        for ((param, value) in expected) {
            assertThat(param.value).`as`("%s value", param.name).isEqualTo(value)
        }
        assertThat(ConditionParam.entries).hasSize(expected.size)
    }
}
