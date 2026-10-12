package org.opentibia.canary.combat.condition

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ConditionCooldownTest {

    private var clock = 0L
    private val now: () -> Long = { clock }

    @Test
    fun `spell cooldown notifies the player on start`() {
        val target = FakeConditionTarget()
        val cooldown = ConditionSpellCooldown(
            ConditionId.CONDITIONID_COMBAT,
            ConditionType.CONDITION_SPELLCOOLDOWN,
            ticks = 1000,
            subId = 5,
            now = now,
        )

        // ConditionSpellCooldown::startCondition (condition.cpp:2905-2917).
        assertThat(cooldown.startCondition(target)).isTrue()
        assertThat(target.spellCooldowns).containsExactly(5 to 1000)
    }

    @Test
    fun `spell cooldown without a subId does not notify`() {
        val target = FakeConditionTarget()
        val cooldown = ConditionSpellCooldown(
            ConditionId.CONDITIONID_COMBAT,
            ConditionType.CONDITION_SPELLCOOLDOWN,
            ticks = 1000,
            subId = 0,
            now = now,
        )

        assertThat(cooldown.startCondition(target)).isTrue()
        assertThat(target.spellCooldowns).isEmpty()
    }

    @Test
    fun `spell cooldown re-notifies with the stacked duration`() {
        val target = FakeConditionTarget()
        val active = ConditionSpellCooldown(
            ConditionId.CONDITIONID_COMBAT,
            ConditionType.CONDITION_SPELLCOOLDOWN,
            ticks = 1000,
            subId = 5,
            now = now,
        )
        val incoming = ConditionSpellCooldown(
            ConditionId.CONDITIONID_COMBAT,
            ConditionType.CONDITION_SPELLCOOLDOWN,
            ticks = 3000,
            subId = 5,
            now = now,
        )

        // ConditionSpellCooldown::addCondition (condition.cpp:2885-2896).
        active.addCondition(target, incoming)

        assertThat(active.ticks).isEqualTo(3000)
        assertThat(target.spellCooldowns).containsExactly(5 to 3000)
    }

    @Test
    fun `spell group cooldown notifies with the group enum`() {
        val target = FakeConditionTarget()
        val cooldown = ConditionSpellGroupCooldown(
            ConditionId.CONDITIONID_COMBAT,
            ConditionType.CONDITION_SPELLGROUPCOOLDOWN,
            ticks = 2000,
            subId = 2,
            now = now,
        )

        // ConditionSpellGroupCooldown::startCondition (condition.cpp:2943-2955).
        assertThat(cooldown.startCondition(target)).isTrue()
        assertThat(target.spellGroupCooldowns).containsExactly(SpellGroup.SPELLGROUP_HEALING to 2000)
    }

    @Test
    fun `spell group cooldown re-notifies with the stacked duration`() {
        val target = FakeConditionTarget()
        val active = ConditionSpellGroupCooldown(
            ConditionId.CONDITIONID_COMBAT,
            ConditionType.CONDITION_SPELLGROUPCOOLDOWN,
            ticks = 1000,
            subId = 1,
            now = now,
        )
        val incoming = ConditionSpellGroupCooldown(
            ConditionId.CONDITIONID_COMBAT,
            ConditionType.CONDITION_SPELLGROUPCOOLDOWN,
            ticks = 4000,
            subId = 1,
            now = now,
        )

        active.addCondition(target, incoming)

        assertThat(active.ticks).isEqualTo(4000)
        assertThat(target.spellGroupCooldowns).containsExactly(SpellGroup.SPELLGROUP_ATTACK to 4000)
    }

    @Test
    fun `cooldown clones keep their concrete type`() {
        val spell = ConditionSpellCooldown(
            ConditionId.CONDITIONID_COMBAT,
            ConditionType.CONDITION_SPELLCOOLDOWN,
            ticks = 1000,
            subId = 5,
            now = now,
        )
        val group = ConditionSpellGroupCooldown(
            ConditionId.CONDITIONID_COMBAT,
            ConditionType.CONDITION_SPELLGROUPCOOLDOWN,
            ticks = 1000,
            subId = 2,
            now = now,
        )

        assertThat(spell.clone()).isInstanceOf(ConditionSpellCooldown::class.java)
        assertThat(group.clone()).isInstanceOf(ConditionSpellGroupCooldown::class.java)
    }
}
