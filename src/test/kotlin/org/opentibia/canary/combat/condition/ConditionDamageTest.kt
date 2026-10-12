package org.opentibia.canary.combat.condition

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ConditionDamageTest {

    private var clock = 0L

    private fun fire() = ConditionDamage(
        ConditionId.CONDITIONID_COMBAT,
        ConditionType.CONDITION_FIRE,
        now = { clock },
        random = kotlin.random.Random(7),
    )

    @Test
    fun `generateDamageList transcribes the Canary descending bell`() {
        assertThat(ConditionDamage.generateDamageList(10, 1)).containsExactly(
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
        )
        assertThat(ConditionDamage.generateDamageList(1, 1)).containsExactly(1)
        assertThat(ConditionDamage.generateDamageList(20, 2)).containsExactly(
            2, 2, 2, 2, 2, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
        )
    }

    @Test
    fun `addDamage min interval is capped at the creature think interval`() {
        val condition = fire()
        condition.addDamage(1, time = 200, value = -5)

        assertThat(condition.scheduledDamage).hasSize(1)
        assertThat(condition.scheduledDamage.first().interval).isEqualTo(1000)
        assertThat(condition.scheduledDamage.first().timeLeft).isEqualTo(1000)
        assertThat(condition.scheduledDamage.first().value).isEqualTo(-5)
    }

    @Test
    fun `scheduled damage rounds drain on each interval`() {
        val target = FakeConditionTarget(health = 1000, isPlayer = false)
        val condition = fire()
        condition.addDamage(3, time = 1000, value = -5)

        // ticks accumulated to 3000 by addDamage.
        assertThat(condition.ticks).isEqualTo(3000)

        condition.startCondition(target)
        assertThat(target.healthValue).isEqualTo(995) // first round fires immediately
        assertThat(condition.scheduledDamage).hasSize(2)

        assertThat(condition.executeCondition(target, 1000)).isTrue()
        assertThat(target.healthValue).isEqualTo(990)

        assertThat(condition.executeCondition(target, 1000)).isTrue()
        assertThat(target.healthValue).isEqualTo(985)
        assertThat(condition.scheduledDamage).isEmpty()

        // No rounds left: the third tick is a no-op.
        assertThat(condition.executeCondition(target, 1000)).isTrue()
        assertThat(target.healthValue).isEqualTo(985)
    }

    @Test
    fun `periodic damage repeats every tickInterval and never expires`() {
        val target = FakeConditionTarget(health = 1000, isPlayer = false)
        val condition = fire()
        condition.addDamage(rounds = -1, time = 2000, value = -20)

        assertThat(condition.ticks).isEqualTo(-1)
        assertThat(condition.tickInterval).isEqualTo(2000)

        condition.startCondition(target)
        assertThat(target.healthValue).isEqualTo(980)

        clock = 1000
        assertThat(condition.executeCondition(target, 1000)).isTrue()
        assertThat(target.healthValue).isEqualTo(980) // periodDamageTick = 1000

        clock = 2000
        assertThat(condition.executeCondition(target, 1000)).isTrue()
        assertThat(target.healthValue).isEqualTo(960) // periodDamageTick hit 2000
    }

    @Test
    fun `suppressed conditions skip the hit`() {
        val target = FakeConditionTarget(health = 1000, isPlayer = false)
        target.suppress = true
        val condition = fire()
        condition.addDamage(1, 1000, -5)

        condition.startCondition(target)

        assertThat(target.healthValue).isEqualTo(1000)
    }

    @Test
    fun `getTotalDamage sums list or averages the range`() {
        val listCondition = fire()
        listCondition.addDamage(3, 1000, -5)
        assertThat(listCondition.getTotalDamage()).isEqualTo(15)

        val rangeCondition = fire()
        rangeCondition.setParam(ConditionParam.CONDITION_PARAM_MINVALUE, 10)
        rangeCondition.setParam(ConditionParam.CONDITION_PARAM_MAXVALUE, 20)
        assertThat(rangeCondition.getTotalDamage()).isEqualTo(15)
    }

    @Test
    fun `lower damage is ignored unless force update is set`() {
        val active = fire()
        active.addDamage(3, 1000, -5)

        // Lower total damage (1 < 15): the refresh is rejected.
        val weak = fire()
        weak.addDamage(1, 1000, -1)
        active.addCondition(null, weak)
        assertThat(active.ticks).isEqualTo(3000)

        // Same weak damage with forceUpdate: the refresh is accepted.
        val forced = fire()
        forced.setParam(ConditionParam.CONDITION_PARAM_FORCEUPDATE, 1)
        forced.addDamage(1, 1000, -1)
        active.addCondition(null, forced)
        assertThat(active.ticks).isEqualTo(1000)
    }

    @Test
    fun `damage icons map to the element`() {
        val condition = fire()
        assertThat(condition.getIcons()).containsExactly(PlayerIcon.Burn)
    }
}
