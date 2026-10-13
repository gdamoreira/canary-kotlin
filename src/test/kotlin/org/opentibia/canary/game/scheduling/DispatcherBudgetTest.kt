package org.opentibia.canary.game.scheduling

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DispatcherBudgetTest {

    private val controller = DispatcherAdaptiveBudgetController()

    @Test
    fun `normal load keeps configured budgets and reports no change`() {
        val configured = DispatcherBudgetSet()

        val decision = controller.update(configured, visibleP99Us = 1_000, oldestVisibleReadyAgeUs = 500)

        assertThat(decision.state).isEqualTo(DispatcherLoadState.Normal)
        assertThat(decision.stateChanged).isFalse()
        assertThat(decision.controlLatencyUs).isEqualTo(1_000)
        assertThat(decision.budgets).isEqualTo(configured)
    }

    @Test
    fun `slo breach enters constrained and halves budgets`() {
        val configured = DispatcherBudgetSet()

        val decision = controller.update(configured, visibleP99Us = 50_000, oldestVisibleReadyAgeUs = 0)

        assertThat(decision.state).isEqualTo(DispatcherLoadState.Constrained)
        assertThat(decision.stateChanged).isTrue()
        assertThat(decision.controlLatencyUs).isEqualTo(50_000)
        assertThat(decision.budgets).isEqualTo(
            DispatcherBudgetSet(
                creatureWalkTasks = 64,
                walkParallelTasks = 4,
                creatureAsyncTasksPerBucket = 8,
                deferredGameplayTasks = 8,
                workerCompletions = 32,
                sliceRuntimeUs = 1_000,
            ),
        )
    }

    @Test
    fun `emergency breach quarters budgets`() {
        val configured = DispatcherBudgetSet()

        controller.update(configured, visibleP99Us = 50_000, oldestVisibleReadyAgeUs = 0)
        val decision = controller.update(configured, visibleP99Us = 100_000, oldestVisibleReadyAgeUs = 0)

        assertThat(decision.state).isEqualTo(DispatcherLoadState.Emergency)
        assertThat(decision.budgets).isEqualTo(
            DispatcherBudgetSet(
                creatureWalkTasks = 32,
                walkParallelTasks = 2,
                creatureAsyncTasksPerBucket = 4,
                deferredGameplayTasks = 4,
                workerCompletions = 16,
                sliceRuntimeUs = 500,
            ),
        )
    }

    @Test
    fun `older visible backlog drives the control latency`() {
        val configured = DispatcherBudgetSet()

        val decision = controller.update(configured, visibleP99Us = 1_000, oldestVisibleReadyAgeUs = 50_000)

        assertThat(decision.controlLatencyUs).isEqualTo(50_000)
        assertThat(decision.state).isEqualTo(DispatcherLoadState.Constrained)
    }

    @Test
    fun `emergency recovers to constrained after four healthy windows`() {
        val configured = DispatcherBudgetSet()
        controller.update(configured, visibleP99Us = 100_000, oldestVisibleReadyAgeUs = 0)

        repeat(3) {
            val decision = controller.update(configured, visibleP99Us = 30_000, oldestVisibleReadyAgeUs = 0)
            assertThat(decision.state).isEqualTo(DispatcherLoadState.Emergency)
            assertThat(decision.stateChanged).isFalse()
        }

        val recovered = controller.update(configured, visibleP99Us = 30_000, oldestVisibleReadyAgeUs = 0)
        assertThat(recovered.state).isEqualTo(DispatcherLoadState.Constrained)
        assertThat(recovered.stateChanged).isTrue()
    }

    @Test
    fun `constrained recovers to normal only under the relaxed threshold`() {
        val configured = DispatcherBudgetSet()
        controller.update(configured, visibleP99Us = 50_000, oldestVisibleReadyAgeUs = 0)

        repeat(3) {
            val decision = controller.update(configured, visibleP99Us = 35_000, oldestVisibleReadyAgeUs = 0)
            assertThat(decision.state).isEqualTo(DispatcherLoadState.Constrained)
        }

        val recovered = controller.update(configured, visibleP99Us = 34_999, oldestVisibleReadyAgeUs = 0)
        assertThat(recovered.state).isEqualTo(DispatcherLoadState.Normal)
        assertThat(recovered.stateChanged).isTrue()
    }

    @Test
    fun `normalizes zero budgets to safe minimums`() {
        val configured = DispatcherBudgetSet(
            creatureWalkTasks = 0,
            walkParallelTasks = 0,
            creatureAsyncTasksPerBucket = 0,
            deferredGameplayTasks = 0,
            workerCompletions = 0,
            sliceRuntimeUs = 0,
        )

        val decision = controller.update(configured, visibleP99Us = 0, oldestVisibleReadyAgeUs = 0)

        assertThat(decision.budgets).isEqualTo(
            DispatcherBudgetSet(
                creatureWalkTasks = 1,
                walkParallelTasks = 1,
                creatureAsyncTasksPerBucket = 1,
                deferredGameplayTasks = 1,
                workerCompletions = 1,
                sliceRuntimeUs = 1,
            ),
        )
    }

    @Test
    fun `raises an alert threshold that is not above the slo`() {
        val configured = DispatcherBudgetSet()

        val decision = controller.update(configured, visibleP99Us = 50_001, oldestVisibleReadyAgeUs = 0, sloUs = 50_000, emergencyUs = 10)

        assertThat(decision.state).isEqualTo(DispatcherLoadState.Emergency)
    }
}
