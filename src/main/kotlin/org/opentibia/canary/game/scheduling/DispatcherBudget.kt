package org.opentibia.canary.game.scheduling

/** `enum class DispatcherLoadState` (src/game/scheduling/dispatcher_budget.hpp:18-22). */
enum class DispatcherLoadState {
    Normal,
    Constrained,
    Emergency,
}

/** `struct DispatcherBudgetSet` (src/game/scheduling/dispatcher_budget.hpp:24-33). */
data class DispatcherBudgetSet(
    val creatureWalkTasks: Int = 128,
    val walkParallelTasks: Int = 8,
    val creatureAsyncTasksPerBucket: Int = 16,
    val deferredGameplayTasks: Int = 16,
    val workerCompletions: Int = 64,
    val sliceRuntimeUs: Long = 2000,
)

/** `struct DispatcherBudgetDecision` (src/game/scheduling/dispatcher_budget.hpp:35-40). */
data class DispatcherBudgetDecision(
    val budgets: DispatcherBudgetSet,
    val state: DispatcherLoadState,
    val controlLatencyUs: Long,
    val stateChanged: Boolean,
)

/** `DispatcherAdaptiveBudgetController` (src/game/scheduling/dispatcher_budget.hpp:42-61, dispatcher_budget.cpp:40-90). */
class DispatcherAdaptiveBudgetController {
    val state: DispatcherLoadState get() = currentState

    private var currentState: DispatcherLoadState = DispatcherLoadState.Normal
    private var healthyWindows = 0

    fun update(
        configured: DispatcherBudgetSet,
        visibleP99Us: Long,
        oldestVisibleReadyAgeUs: Long,
        sloUs: Long = 50_000,
        emergencyUs: Long = 100_000,
    ): DispatcherBudgetDecision {
        val slo = maxOf(1L, sloUs)
        val emergency = maxOf(slo + 1, emergencyUs)
        val controlLatency = maxOf(visibleP99Us, oldestVisibleReadyAgeUs)
        val previousState = currentState

        if (controlLatency >= emergency) {
            currentState = DispatcherLoadState.Emergency
            healthyWindows = 0
        } else if (controlLatency >= slo) {
            if (currentState == DispatcherLoadState.Normal) {
                currentState = DispatcherLoadState.Constrained
            }
            healthyWindows = 0
        } else {
            val recoveryThreshold = if (currentState == DispatcherLoadState.Emergency) slo else slo * 7 / 10
            if (currentState != DispatcherLoadState.Normal && controlLatency <= recoveryThreshold) {
                healthyWindows++
                if (healthyWindows >= HEALTHY_WINDOWS_TO_RELAX) {
                    currentState = if (currentState == DispatcherLoadState.Emergency) {
                        DispatcherLoadState.Constrained
                    } else {
                        DispatcherLoadState.Normal
                    }
                    healthyWindows = 0
                }
            } else {
                healthyWindows = 0
            }
        }

        return DispatcherBudgetDecision(
            budgets = budgetsForState(configured, currentState),
            state = currentState,
            controlLatencyUs = controlLatency,
            stateChanged = currentState != previousState,
        )
    }

    private fun budgetsForState(configured: DispatcherBudgetSet, state: DispatcherLoadState): DispatcherBudgetSet {
        val normalized = normalizeBudgets(configured)
        if (state == DispatcherLoadState.Normal) {
            return normalized
        }

        val emergency = state == DispatcherLoadState.Emergency
        val divisor = if (emergency) 4 else 2
        return normalized.copy(
            creatureWalkTasks = scaledBudget(normalized.creatureWalkTasks, divisor, if (emergency) 16 else 32),
            walkParallelTasks = scaledBudget(normalized.walkParallelTasks, divisor, if (emergency) 1 else 2),
            creatureAsyncTasksPerBucket = scaledBudget(normalized.creatureAsyncTasksPerBucket, divisor, if (emergency) 2 else 4),
            deferredGameplayTasks = scaledBudget(normalized.deferredGameplayTasks, divisor, if (emergency) 1 else 4),
            workerCompletions = scaledBudget(normalized.workerCompletions, divisor, if (emergency) 4 else 8),
            sliceRuntimeUs = scaledRuntime(normalized.sliceRuntimeUs, divisor, if (emergency) 250 else 500),
        )
    }

    private companion object {
        const val HEALTHY_WINDOWS_TO_RELAX = 4

        fun scaledBudget(configured: Int, divisor: Int, minimum: Int): Int {
            val scaled = maxOf(1, configured / divisor)
            return minOf(configured, maxOf(minimum, scaled))
        }

        fun scaledRuntime(configured: Long, divisor: Int, minimum: Long): Long {
            val scaled = maxOf(1L, configured / divisor)
            return minOf(configured, maxOf(minimum, scaled))
        }

        fun normalizeBudgets(budgets: DispatcherBudgetSet): DispatcherBudgetSet = budgets.copy(
            creatureWalkTasks = maxOf(1, budgets.creatureWalkTasks),
            walkParallelTasks = maxOf(1, budgets.walkParallelTasks),
            creatureAsyncTasksPerBucket = maxOf(1, budgets.creatureAsyncTasksPerBucket),
            deferredGameplayTasks = maxOf(1, budgets.deferredGameplayTasks),
            workerCompletions = maxOf(1, budgets.workerCompletions),
            sliceRuntimeUs = maxOf(1L, budgets.sliceRuntimeUs),
        )
    }
}
