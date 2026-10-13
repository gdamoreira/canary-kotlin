package org.opentibia.canary.game.scheduling

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DispatcherTypesTest {

    @Test
    fun `default execution mode follows the lane`() {
        assertThat(defaultExecutionMode(DispatcherLane.VisibleMonsterAI)).isEqualTo(ExecutionMode.BarrierParallel)
        assertThat(defaultExecutionMode(DispatcherLane.MonsterAI)).isEqualTo(ExecutionMode.BarrierParallel)
        assertThat(defaultExecutionMode(DispatcherLane.GenericParallel)).isEqualTo(ExecutionMode.BarrierParallel)
        assertThat(defaultExecutionMode(DispatcherLane.WorkerCompletion)).isEqualTo(ExecutionMode.BackgroundCompletion)
        assertThat(defaultExecutionMode(DispatcherLane.WorldCommit)).isEqualTo(ExecutionMode.Serial)
        assertThat(defaultExecutionMode(DispatcherLane.Deferred)).isEqualTo(ExecutionMode.Serial)
    }

    @Test
    fun `movement commit lanes match canary`() {
        assertThat(isMovementCommit(DispatcherLane.PlayerWalk)).isTrue()
        assertThat(isMovementCommit(DispatcherLane.VisibleMonster)).isTrue()
        assertThat(isMovementCommit(DispatcherLane.BackgroundMonster)).isTrue()
        assertThat(isMovementCommit(DispatcherLane.WorldCommit)).isFalse()
        assertThat(isMovementCommit(DispatcherLane.MonsterAI)).isFalse()
    }

    @Test
    fun `player visible lanes match canary`() {
        val visible = listOf(
            DispatcherLane.ProtocolInput,
            DispatcherLane.PlayerWalk,
            DispatcherLane.PlayerAction,
            DispatcherLane.WorldCommit,
            DispatcherLane.WorkerCompletion,
            DispatcherLane.VisibleMonster,
            DispatcherLane.VisibleMonsterAI,
        )
        for (lane in DispatcherLane.entries) {
            assertThat(isPlayerVisible(lane)).isEqualTo(lane in visible)
        }
    }

    @Test
    fun `barrier continuation routes monster ai to deferred and others to world commit`() {
        assertThat(barrierContinuationLane(DispatcherLane.MonsterAI)).isEqualTo(DispatcherLane.Deferred)
        assertThat(barrierContinuationLane(DispatcherLane.VisibleMonsterAI)).isEqualTo(DispatcherLane.WorldCommit)
        assertThat(barrierContinuationLane(DispatcherLane.GenericParallel)).isEqualTo(DispatcherLane.WorldCommit)
    }

    @Test
    fun `producer fairness only applies to player input lanes`() {
        assertThat(usesProducerFairness(DispatcherLane.ProtocolInput)).isTrue()
        assertThat(usesProducerFairness(DispatcherLane.PlayerWalk)).isTrue()
        assertThat(usesProducerFairness(DispatcherLane.PlayerAction)).isTrue()
        assertThat(usesProducerFairness(DispatcherLane.WorldCommit)).isFalse()
        assertThat(usesProducerFairness(DispatcherLane.Maintenance)).isFalse()
    }
}
