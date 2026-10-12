package org.opentibia.canary.combat.condition

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ConditionMiscTest {

    private var clock = 0L
    private val now: () -> Long = { clock }

    @Test
    fun `soul regenerates on its interval outside protection zones`() {
        val target = FakeConditionTarget()
        val soul = ConditionSoul(
            ConditionId.CONDITIONID_COMBAT,
            ConditionType.CONDITION_SOUL,
            ticks = 10000,
            now = now,
        )
        soul.setParam(ConditionParam.CONDITION_PARAM_SOULGAIN, 2)
        soul.setParam(ConditionParam.CONDITION_PARAM_SOULTICKS, 3000)

        soul.startCondition(target)
        soul.executeCondition(target, 1000)
        soul.executeCondition(target, 1000)
        assertThat(target.soul).isEqualTo(0)

        soul.executeCondition(target, 1000)
        assertThat(target.soul).isEqualTo(2)
    }

    @Test
    fun `soul is gated inside a protection zone`() {
        val target = FakeConditionTarget(zone = ZoneType.ZONE_PROTECTION)
        val soul = ConditionSoul(
            ConditionId.CONDITIONID_COMBAT,
            ConditionType.CONDITION_SOUL,
            ticks = 10000,
            now = now,
        )
        soul.setParam(ConditionParam.CONDITION_PARAM_SOULGAIN, 2)
        soul.setParam(ConditionParam.CONDITION_PARAM_SOULTICKS, 1000)

        soul.startCondition(target)
        soul.executeCondition(target, 1000)

        assertThat(target.soul).isEqualTo(0)
    }

    @Test
    fun `mana shield sets and clears the shield pool`() {
        val target = FakeConditionTarget()
        val shield = ConditionManaShield(
            ConditionId.CONDITIONID_COMBAT,
            ConditionType.CONDITION_MANASHIELD,
            ticks = 10000,
            now = now,
        )
        shield.setParam(ConditionParam.CONDITION_PARAM_MANASHIELD, 500)

        shield.startCondition(target)
        assertThat(target.manaShieldValue).isEqualTo(500)
        assertThat(target.maxManaShieldValue).isEqualTo(500)
        assertThat(shield.getIcons()).containsExactly(PlayerIcon.NewManaShield)

        shield.endCondition(target)
        assertThat(target.manaShieldValue).isEqualTo(0)
        assertThat(target.maxManaShieldValue).isEqualTo(0)
    }

    @Test
    fun `factory mirrors createCondition for the ported subset`() {
        val damage = Condition.createCondition(ConditionId.CONDITIONID_COMBAT, ConditionType.CONDITION_FIRE, 1000)
        assertThat(damage).isInstanceOf(ConditionDamage::class.java)

        val paralyze = Condition.createCondition(
            ConditionId.CONDITIONID_COMBAT,
            ConditionType.CONDITION_PARALYZE,
            ticks = 1000,
            param = -50,
        )
        assertThat(paralyze).isInstanceOf(ConditionSpeed::class.java)
        assertThat((paralyze as ConditionSpeed).type).isEqualTo(ConditionType.CONDITION_PARALYZE)
        assertThat(paralyze.speedDelta).isEqualTo(-50)

        val regeneration = Condition.createCondition(
            ConditionId.CONDITIONID_COMBAT,
            ConditionType.CONDITION_REGENERATION,
            1000,
        )
        assertThat(regeneration).isInstanceOf(ConditionRegeneration::class.java)

        val attributes = Condition.createCondition(
            ConditionId.CONDITIONID_COMBAT,
            ConditionType.CONDITION_ATTRIBUTES,
            1000,
        )
        assertThat(attributes).isInstanceOf(ConditionAttributes::class.java)

        val generic = Condition.createCondition(
            ConditionId.CONDITIONID_COMBAT,
            ConditionType.CONDITION_DRUNK,
            1000,
        )
        assertThat(generic).isInstanceOf(ConditionGeneric::class.java)
        assertThat(generic!!.type).isEqualTo(ConditionType.CONDITION_DRUNK)
    }

    @Test
    fun `factory mirrors createCondition for the visual and cooldown types`() {
        val serene = Condition.createCondition(ConditionId.CONDITIONID_COMBAT, ConditionType.CONDITION_SERENE, 1000)
        assertThat(serene).isInstanceOf(ConditionSerene::class.java)

        val light = Condition.createCondition(
            ConditionId.CONDITIONID_COMBAT,
            ConditionType.CONDITION_LIGHT,
            ticks = 1000,
            param = 0x0205,
        )
        assertThat(light).isInstanceOf(ConditionLight::class.java)
        // condition.cpp:360: level = param & 0xFF, color = (param & 0xFF00) >> 8.
        assertThat((light as ConditionLight).lightInfo).isEqualTo(LightInfo(5, 2))

        val invisible = Condition.createCondition(ConditionId.CONDITIONID_COMBAT, ConditionType.CONDITION_INVISIBLE, 1000)
        assertThat(invisible).isInstanceOf(ConditionInvisible::class.java)

        val outfit = Condition.createCondition(ConditionId.CONDITIONID_COMBAT, ConditionType.CONDITION_OUTFIT, 1000)
        assertThat(outfit).isInstanceOf(ConditionOutfit::class.java)

        val spell = Condition.createCondition(ConditionId.CONDITIONID_COMBAT, ConditionType.CONDITION_SPELLCOOLDOWN, 1000)
        assertThat(spell).isInstanceOf(ConditionSpellCooldown::class.java)

        val group = Condition.createCondition(ConditionId.CONDITIONID_COMBAT, ConditionType.CONDITION_SPELLGROUPCOOLDOWN, 1000)
        assertThat(group).isInstanceOf(ConditionSpellGroupCooldown::class.java)
    }

    @Test
    fun `createDamageCondition accepts only damage types`() {
        assertThat(Condition.createDamageCondition(ConditionId.CONDITIONID_COMBAT, ConditionType.CONDITION_POISON)).isNotNull()
        assertThat(Condition.createDamageCondition(ConditionId.CONDITIONID_COMBAT, ConditionType.CONDITION_HASTE)).isNull()
    }
}
