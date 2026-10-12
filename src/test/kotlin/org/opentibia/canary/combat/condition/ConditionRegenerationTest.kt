package org.opentibia.canary.combat.condition

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ConditionRegenerationTest {

    private var clock = 0L
    private val now: () -> Long = { clock }

    private fun regeneration(
        healthGain: Int,
        healthTicks: Int,
        manaGain: Int,
        manaTicks: Int,
        buff: Boolean = false,
        rateSpellCooldown: Double = 1.0,
    ): ConditionRegeneration {
        val regen = ConditionRegeneration(
            ConditionId.CONDITIONID_COMBAT,
            ConditionType.CONDITION_REGENERATION,
            ticks = 10000,
            buff = buff,
            now = now,
            rateSpellCooldown = rateSpellCooldown,
        )
        regen.setParam(ConditionParam.CONDITION_PARAM_HEALTHGAIN, healthGain)
        regen.setParam(ConditionParam.CONDITION_PARAM_HEALTHTICKS, healthTicks)
        regen.setParam(ConditionParam.CONDITION_PARAM_MANAGAIN, manaGain)
        regen.setParam(ConditionParam.CONDITION_PARAM_MANATICKS, manaTicks)
        return regen
    }

    @Test
    fun `health and mana tick on independent intervals`() {
        val target = FakeConditionTarget(health = 100, maxHealth = 1000, mana = 100, maxMana = 500)
        val regen = regeneration(healthGain = 5, healthTicks = 1000, manaGain = 3, manaTicks = 2000)

        regen.startCondition(target)
        assertThat(target.statsSent).isEqualTo(1)

        regen.executeCondition(target, 1000)
        assertThat(target.healthValue).isEqualTo(105)
        assertThat(target.manaValue).isEqualTo(100)

        regen.executeCondition(target, 1000)
        assertThat(target.healthValue).isEqualTo(110)
        assertThat(target.manaValue).isEqualTo(103)
    }

    @Test
    fun `protection zone gates regeneration until the daily streak unlocks it`() {
        val target = FakeConditionTarget(
            health = 100,
            maxHealth = 1000,
            mana = 100,
            maxMana = 500,
            zone = ZoneType.ZONE_PROTECTION,
        )
        target.storage[ConditionRegeneration.STORAGE_STREAK] = 0
        val regen = regeneration(healthGain = 5, healthTicks = 1000, manaGain = 3, manaTicks = 1000)

        regen.startCondition(target)
        regen.executeCondition(target, 1000)
        assertThat(target.healthValue).isEqualTo(100)
        assertThat(target.manaValue).isEqualTo(100)

        // HP regeneration unlocks at streak 2, mana only at streak 3.
        target.storage[ConditionRegeneration.STORAGE_STREAK] = 2
        regen.executeCondition(target, 1000)
        assertThat(target.healthValue).isEqualTo(105)
        assertThat(target.manaValue).isEqualTo(100)
    }

    @Test
    fun `daily reward doubles health regeneration in a protection zone`() {
        val target = FakeConditionTarget(
            health = 100,
            maxHealth = 1000,
            zone = ZoneType.ZONE_PROTECTION,
        )
        target.storage[ConditionRegeneration.STORAGE_STREAK] = 5
        val regen = regeneration(healthGain = 5, healthTicks = 1000, manaGain = 3, manaTicks = 2000)

        regen.startCondition(target)
        regen.executeCondition(target, 1000)

        assertThat(target.healthValue).isEqualTo(110)
    }

    @Test
    fun `buff regeneration divides ticks by the spell cooldown rate`() {
        val target = FakeConditionTarget(health = 100, maxHealth = 1000)
        val regen = regeneration(
            healthGain = 5,
            healthTicks = 1000,
            manaGain = 0,
            manaTicks = 1000,
            buff = true,
            rateSpellCooldown = 0.5,
        )

        assertThat(regen.getHealthTicks(target)).isEqualTo(2000)

        regen.startCondition(target)
        regen.executeCondition(target, 1000)
        assertThat(target.healthValue).isEqualTo(100)

        regen.executeCondition(target, 1000)
        assertThat(target.healthValue).isEqualTo(105)
    }

    @Test
    fun `stacking copies the gain configuration`() {
        val active = regeneration(healthGain = 1, healthTicks = 5000, manaGain = 1, manaTicks = 5000)
        active.startCondition(FakeConditionTarget())

        val incoming = regeneration(healthGain = 7, healthTicks = 1000, manaGain = 4, manaTicks = 1000)
        active.addCondition(FakeConditionTarget(), incoming)

        assertThat(active.healthGain).isEqualTo(7)
        assertThat(active.healthTicks).isEqualTo(1000)
        assertThat(active.manaGain).isEqualTo(4)
        assertThat(active.manaTicks).isEqualTo(1000)
    }
}
