package org.opentibia.canary.combat.condition

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ConditionSpeedTest {

    private var clock = 0L
    private val now: () -> Long = { clock }

    @Test
    fun `haste applies its delta on start and reverses it on end`() {
        val target = FakeConditionTarget(baseSpeed = 220)
        val speed = ConditionSpeed(
            ConditionId.CONDITIONID_COMBAT,
            ConditionType.CONDITION_HASTE,
            ticks = 10000,
            initChangeSpeed = 100,
            now = now,
        )

        speed.startCondition(target)
        assertThat(target.speedDeltaTotal).isEqualTo(100)

        speed.endCondition(target)
        assertThat(target.speedDeltaTotal).isEqualTo(0)
    }

    @Test
    fun `setParam speed selects haste or paralyze by sign`() {
        val speed = ConditionSpeed(
            ConditionId.CONDITIONID_COMBAT,
            ConditionType.CONDITION_HASTE,
            ticks = 10000,
            now = now,
        )

        speed.setParam(ConditionParam.CONDITION_PARAM_SPEED, 50)
        assertThat(speed.type).isEqualTo(ConditionType.CONDITION_HASTE)
        assertThat(speed.speedDelta).isEqualTo(50)

        speed.setParam(ConditionParam.CONDITION_PARAM_SPEED, -50)
        assertThat(speed.type).isEqualTo(ConditionType.CONDITION_PARALYZE)
        assertThat(speed.speedDelta).isEqualTo(-50)
    }

    @Test
    fun `paralyze clamps the delta below the 40 speed floor`() {
        val target = FakeConditionTarget(baseSpeed = 220)
        val paralyze = ConditionSpeed(
            ConditionId.CONDITIONID_COMBAT,
            ConditionType.CONDITION_PARALYZE,
            ticks = 10000,
            initChangeSpeed = 0,
            now = now,
        )

        paralyze.startCondition(target)

        // Formula yields 0 - 220 = -220, clamped to 40 - 220 = -180.
        assertThat(paralyze.speedDelta).isEqualTo(-180)
        assertThat(target.speedDeltaTotal).isEqualTo(-180)
    }

    @Test
    fun `haste formula is evaluated against base speed`() {
        val target = FakeConditionTarget(baseSpeed = 100)
        val haste = ConditionSpeed(
            ConditionId.CONDITIONID_COMBAT,
            ConditionType.CONDITION_HASTE,
            ticks = 10000,
            initChangeSpeed = 0,
            now = now,
        )
        haste.setFormulaVars(newMina = 2.0f, newMinb = 10.0f, newMaxa = 2.0f, newMaxb = 10.0f)

        haste.startCondition(target)

        // difference = 100 - 40 = 60; min = max = 2*60 + 10 = 130; 130 - 100 = 30.
        assertThat(haste.speedDelta).isEqualTo(30)
        assertThat(target.speedDeltaTotal).isEqualTo(30)
    }

    @Test
    fun `stacking applies only the speed delta difference`() {
        val target = FakeConditionTarget(baseSpeed = 220)
        val active = ConditionSpeed(
            ConditionId.CONDITIONID_COMBAT,
            ConditionType.CONDITION_HASTE,
            ticks = 10000,
            initChangeSpeed = 100,
            now = now,
        )
        active.startCondition(target)
        assertThat(target.speedDeltaTotal).isEqualTo(100)

        val incoming = ConditionSpeed(
            ConditionId.CONDITIONID_COMBAT,
            ConditionType.CONDITION_HASTE,
            ticks = 20000,
            initChangeSpeed = 50,
            now = now,
        )
        active.addCondition(target, incoming)

        assertThat(active.speedDelta).isEqualTo(50)
        assertThat(target.speedDeltaTotal).isEqualTo(50)
        assertThat(active.ticks).isEqualTo(20000)
    }

    @Test
    fun `speed icons map to haste and paralyze`() {
        val haste = ConditionSpeed(
            ConditionId.CONDITIONID_COMBAT,
            ConditionType.CONDITION_HASTE,
            ticks = 10000,
            now = now,
        )
        assertThat(haste.getIcons()).containsExactly(PlayerIcon.Haste)

        val paralyze = ConditionSpeed(
            ConditionId.CONDITIONID_COMBAT,
            ConditionType.CONDITION_PARALYZE,
            ticks = 10000,
            now = now,
        )
        assertThat(paralyze.getIcons()).containsExactly(PlayerIcon.Paralyze)
    }
}
