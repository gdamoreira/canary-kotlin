package org.opentibia.canary.game.scheduling

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DispatcherWdrrTest {

    private val scheduler = DispatcherWeightedDeficitRoundRobin()

    @Test
    fun `alternates equal weight lanes round robin`() {
        val ready = ready(DispatcherLane.ProtocolInput, DispatcherLane.WorldCommit)
        val quantums = values(DispatcherLane.ProtocolInput to 1, DispatcherLane.WorldCommit to 1)
        val costs = values(DispatcherLane.ProtocolInput to 1, DispatcherLane.WorldCommit to 1)

        val order = List(6) {
            val lane = scheduler.selectLane(ready, quantums, costs)
            lane?.let { scheduler.consume(it, 1) }
            lane
        }

        assertThat(order).containsExactly(
            DispatcherLane.ProtocolInput,
            DispatcherLane.WorldCommit,
            DispatcherLane.ProtocolInput,
            DispatcherLane.WorldCommit,
            DispatcherLane.ProtocolInput,
            DispatcherLane.WorldCommit,
        )
    }

    @Test
    fun `serves the higher weighted lane before the lower weighted lane`() {
        val ready = ready(DispatcherLane.WorldCommit, DispatcherLane.Maintenance)
        val quantums = values(DispatcherLane.WorldCommit to 4, DispatcherLane.Maintenance to 1)
        val costs = values(DispatcherLane.WorldCommit to 1, DispatcherLane.Maintenance to 1)

        val selections = mutableListOf<DispatcherLane>()
        repeat(5) {
            val lane = scheduler.selectLane(ready, quantums, costs)
            requireNotNull(lane)
            scheduler.consume(lane, 1)
            selections += lane
        }

        assertThat(selections).containsExactly(
            DispatcherLane.WorldCommit,
            DispatcherLane.WorldCommit,
            DispatcherLane.WorldCommit,
            DispatcherLane.WorldCommit,
            DispatcherLane.Maintenance,
        )
    }

    @Test
    fun `returns null when no lane is ready`() {
        val ready = BooleanArray(DispatcherWeightedDeficitRoundRobin.LANE_COUNT)
        val quantums = IntArray(DispatcherWeightedDeficitRoundRobin.LANE_COUNT) { 1 }
        val costs = IntArray(DispatcherWeightedDeficitRoundRobin.LANE_COUNT) { 1 }

        assertThat(scheduler.selectLane(ready, quantums, costs)).isNull()
    }

    @Test
    fun `reset lane clears its deficit`() {
        val ready = ready(DispatcherLane.WorldCommit, DispatcherLane.Maintenance)
        val quantums = values(DispatcherLane.WorldCommit to 4, DispatcherLane.Maintenance to 1)
        val costs = values(DispatcherLane.WorldCommit to 1, DispatcherLane.Maintenance to 1)

        scheduler.selectLane(ready, quantums, costs)
        assertThat(scheduler.getDeficit(DispatcherLane.WorldCommit)).isEqualTo(4)

        scheduler.resetLane(DispatcherLane.WorldCommit)
        assertThat(scheduler.getDeficit(DispatcherLane.WorldCommit)).isZero()
    }

    @Test
    fun `consume clamps the cost to the task cost range`() {
        val ready = ready(DispatcherLane.WorldCommit)
        val quantums = values(DispatcherLane.WorldCommit to 4)
        val costs = values(DispatcherLane.WorldCommit to 1)

        scheduler.selectLane(ready, quantums, costs)
        scheduler.consume(DispatcherLane.WorldCommit, 0)
        assertThat(scheduler.getDeficit(DispatcherLane.WorldCommit)).isEqualTo(3)

        scheduler.consume(DispatcherLane.WorldCommit, 100)
        assertThat(scheduler.getDeficit(DispatcherLane.WorldCommit)).isZero()
    }

    @Test
    fun `aged quantum doubles for a lane past the slo and caps at the task cost`() {
        assertThat(DispatcherWeightedDeficitRoundRobin.agedQuantum(4, 0, 50_000)).isEqualTo(4)
        assertThat(DispatcherWeightedDeficitRoundRobin.agedQuantum(4, 49_999, 50_000)).isEqualTo(4)
        assertThat(DispatcherWeightedDeficitRoundRobin.agedQuantum(4, 50_000, 50_000)).isEqualTo(8)
        assertThat(DispatcherWeightedDeficitRoundRobin.agedQuantum(20, 1_000_000, 50_000)).isEqualTo(32)
        assertThat(DispatcherWeightedDeficitRoundRobin.agedQuantum(0, 1_000_000, 50_000)).isEqualTo(2)
        assertThat(DispatcherWeightedDeficitRoundRobin.agedQuantum(4, 1_000_000, 0)).isEqualTo(8)
    }

    private fun ready(vararg lanes: DispatcherLane): BooleanArray {
        val result = BooleanArray(DispatcherWeightedDeficitRoundRobin.LANE_COUNT)
        lanes.forEach { result[it.ordinal] = true }
        return result
    }

    private fun values(vararg entries: Pair<DispatcherLane, Int>): IntArray {
        val result = IntArray(DispatcherWeightedDeficitRoundRobin.LANE_COUNT)
        entries.forEach { (lane, value) -> result[lane.ordinal] = value }
        return result
    }
}
