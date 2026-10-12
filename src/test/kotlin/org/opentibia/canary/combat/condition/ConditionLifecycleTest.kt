package org.opentibia.canary.combat.condition

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ConditionLifecycleTest {

    private var clock = 0L
    private val now: () -> Long = { clock }

    private fun generic(
        ticks: Int,
        type: ConditionType = ConditionType.CONDITION_DRUNK,
        buff: Boolean = false,
    ) = ConditionGeneric(ConditionId.CONDITIONID_DEFAULT, type, ticks, buff, now = now)

    @Test
    fun `finite condition expires once endTime passes`() {
        val condition = generic(ticks = 1000)

        assertThat(condition.endTime).isEqualTo(0L)
        assertThat(condition.startCondition(null)).isTrue()
        assertThat(condition.endTime).isEqualTo(1000L)

        clock = 0
        assertThat(condition.executeCondition(null, 400)).isTrue()
        assertThat(condition.ticks).isEqualTo(600)

        clock = 1001
        assertThat(condition.executeCondition(null, 100)).isFalse()
        assertThat(condition.ticks).isEqualTo(500)
    }

    @Test
    fun `immortal condition never expires and keeps negative ticks`() {
        val condition = generic(ticks = -1)

        assertThat(condition.endTime).isEqualTo(Long.MAX_VALUE)
        assertThat(condition.startCondition(null)).isTrue()
        assertThat(condition.endTime).isEqualTo(Long.MAX_VALUE)

        clock = 10_000
        repeat(50) {
            assertThat(condition.executeCondition(null, 1000)).isTrue()
        }
        assertThat(condition.ticks).isEqualTo(-1)
    }

    @Test
    fun `stacking replaces a longer remaining duration`() {
        val active = generic(ticks = 5000)
        active.startCondition(null)
        assertThat(active.endTime).isEqualTo(5000L)

        active.addCondition(null, generic(ticks = 10000))

        assertThat(active.ticks).isEqualTo(10000)
        assertThat(active.endTime).isEqualTo(10000L)
    }

    @Test
    fun `stacking ignores a shorter remaining duration`() {
        val active = generic(ticks = 5000)
        active.startCondition(null)

        active.addCondition(null, generic(ticks = 1000))

        assertThat(active.ticks).isEqualTo(5000)
        assertThat(active.endTime).isEqualTo(5000L)
    }

    @Test
    fun `immortal condition refuses a finite refresh`() {
        val active = generic(ticks = -1)

        active.addCondition(null, generic(ticks = 1000))

        assertThat(active.ticks).isEqualTo(-1)
    }

    @Test
    fun `setTicks restamps endTime relative to now`() {
        val condition = generic(ticks = 1000)
        clock = 500
        condition.setTicks(2000)

        assertThat(condition.ticks).isEqualTo(2000)
        assertThat(condition.endTime).isEqualTo(2500L)
    }

    @Test
    fun `isRemovableOnDeath follows the exact Canary set`() {
        val taint = generic(ticks = 1000, type = ConditionType.CONDITION_GOSHNARTAINT)
        assertThat(taint.isRemovableOnDeath()).isFalse()

        val drunk = generic(ticks = 1000)
        assertThat(drunk.isRemovableOnDeath()).isTrue()

        val persistent = ConditionGeneric(
            ConditionId.CONDITIONID_DEFAULT,
            ConditionType.CONDITION_BAKRAGORE,
            1000,
            isPersistent = true,
            now = now,
        )
        assertThat(persistent.isRemovableOnDeath()).isFalse()
    }

    @Test
    fun `isPersistent requires default or combat id`() {
        val defaultId = generic(ticks = 1000)
        assertThat(defaultId.isPersistent()).isTrue()

        val head = ConditionGeneric(ConditionId.CONDITIONID_HEAD, ConditionType.CONDITION_DRUNK, 1000, now = now)
        assertThat(head.isPersistent()).isFalse()

        val immortal = generic(ticks = -1)
        assertThat(immortal.isPersistent()).isFalse()
    }

    @Test
    fun `generic icons follow the type switch`() {
        val drunk = generic(ticks = 1000)
        assertThat(drunk.getIcons()).containsExactly(PlayerIcon.Drunk)

        val infight = generic(ticks = 1000, type = ConditionType.CONDITION_INFIGHT)
        assertThat(infight.getIcons()).containsExactly(PlayerIcon.Swords)

        val buffed = generic(ticks = 1000, buff = true)
        assertThat(buffed.getIcons()).containsExactlyInAnyOrder(PlayerIcon.Drunk, PlayerIcon.PartyBuff)
    }

    @Test
    fun `taint icons select the amplification subId`() {
        val taint = ConditionGeneric(
            ConditionId.CONDITIONID_DEFAULT,
            ConditionType.CONDITION_GOSHNARTAINT,
            1000,
            subId = 3,
            now = now,
        )
        assertThat(taint.getIcons()).containsExactly(PlayerIcon.GoshnarTaint3)
    }
}
