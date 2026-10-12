package org.opentibia.canary.combat.condition

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ConditionInvisibleTest {

    private var clock = 0L
    private val now: () -> Long = { clock }

    @Test
    fun `start hides the creature and end restores visibility`() {
        val target = FakeConditionTarget()
        val invisible = ConditionInvisible(
            ConditionId.CONDITIONID_COMBAT,
            ConditionType.CONDITION_INVISIBLE,
            ticks = 10000,
            now = now,
        )

        assertThat(invisible.startCondition(target)).isTrue()
        // ConditionInvisible::startCondition -> g_game().internalCreatureChangeVisible(false).
        assertThat(target.visible).isFalse()
        assertThat(target.visibilityChanges).containsExactly(false)

        invisible.endCondition(target)
        // ConditionInvisible::endCondition -> visible(true) when not already invisible.
        assertThat(target.visible).isTrue()
        assertThat(target.visibilityChanges).containsExactly(false, true)
    }

    @Test
    fun `end does nothing while the creature still reports the invisible condition`() {
        val target = FakeConditionTarget()
        val invisible = ConditionInvisible(
            ConditionId.CONDITIONID_COMBAT,
            ConditionType.CONDITION_INVISIBLE,
            ticks = 10000,
            now = now,
        )

        // Creature::isInvisible() reports an active CONDITION_INVISIBLE, so the
        // guard in condition.cpp:2653-2657 skips the visibility restore.
        target.isInvisibleValue = true
        invisible.endCondition(target)

        assertThat(target.visibilityChanges).isEmpty()
    }

    @Test
    fun `clone preserves type ticks and subId`() {
        val invisible = ConditionInvisible(
            ConditionId.CONDITIONID_HEAD,
            ConditionType.CONDITION_INVISIBLE,
            ticks = 7000,
            buff = true,
            subId = 3,
            now = now,
        )

        val copy = invisible.clone()
        assertThat(copy).isInstanceOf(ConditionInvisible::class.java)
        assertThat(copy.type).isEqualTo(ConditionType.CONDITION_INVISIBLE)
        assertThat(copy.ticks).isEqualTo(7000)
        assertThat(copy.subId).isEqualTo(3)
    }
}
