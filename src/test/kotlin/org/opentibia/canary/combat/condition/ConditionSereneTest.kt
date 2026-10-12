package org.opentibia.canary.combat.condition

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ConditionSereneTest {

    private var clock = 0L
    private val now: () -> Long = { clock }

    @Test
    fun `start sends the serenity flag on and end turns it off`() {
        val target = FakeConditionTarget()
        val serene = ConditionSerene(
            ConditionId.CONDITIONID_COMBAT,
            ConditionType.CONDITION_SERENE,
            ticks = 10000,
            now = now,
        )

        assertThat(serene.startCondition(target)).isTrue()
        // ConditionSerene::startCondition -> Player::sendMonkData(Serenity, 0x01).
        assertThat(target.monkData).containsExactly(MonkData.Serenity to 1)

        serene.endCondition(target)
        // ConditionSerene::endCondition -> Player::sendMonkData(Serenity, 0x00).
        assertThat(target.monkData).containsExactly(MonkData.Serenity to 1, MonkData.Serenity to 0)
    }

    @Test
    fun `non player targets do not receive monk data`() {
        val target = FakeConditionTarget(isPlayer = false)
        val serene = ConditionSerene(
            ConditionId.CONDITIONID_COMBAT,
            ConditionType.CONDITION_SERENE,
            ticks = 10000,
            now = now,
        )

        assertThat(serene.startCondition(target)).isTrue()
        serene.endCondition(target)

        assertThat(target.monkData).isEmpty()
    }

    @Test
    fun `add stacks the duration`() {
        val active = ConditionSerene(
            ConditionId.CONDITIONID_COMBAT,
            ConditionType.CONDITION_SERENE,
            ticks = 1000,
            now = now,
        )
        val incoming = ConditionSerene(
            ConditionId.CONDITIONID_COMBAT,
            ConditionType.CONDITION_SERENE,
            ticks = 5000,
            now = now,
        )

        // ConditionSerene::addCondition (condition.cpp:684-692).
        active.addCondition(null, incoming)

        assertThat(active.ticks).isEqualTo(5000)
    }

    @Test
    fun `clone preserves the duration and type`() {
        val serene = ConditionSerene(
            ConditionId.CONDITIONID_COMBAT,
            ConditionType.CONDITION_SERENE,
            ticks = 4000,
            buff = true,
            subId = 1,
            now = now,
        )

        val copy = serene.clone()
        assertThat(copy).isInstanceOf(ConditionSerene::class.java)
        assertThat(copy.ticks).isEqualTo(4000)
        assertThat(copy.isBuff).isTrue()
    }
}
