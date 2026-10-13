package org.opentibia.canary.game.scheduling

/**
 * `DispatcherWeightedDeficitRoundRobin` (src/game/scheduling/dispatcher_wdrr.hpp:22-43,
 * dispatcher_wdrr.cpp:16-65).
 */
class DispatcherWeightedDeficitRoundRobin {
    private val deficits = IntArray(LANE_COUNT)
    private var cursor = 0
    private var currentLaneActive = false

    fun selectLane(ready: BooleanArray, quantums: IntArray, nextCosts: IntArray): DispatcherLane? {
        repeat(LANE_COUNT * MAX_TASK_COST) {
            val index = cursor
            val cost = nextCosts[index].coerceIn(1, MAX_TASK_COST)
            if (!ready[index]) {
                deficits[index] = 0
                currentLaneActive = false
                cursor = (cursor + 1) % LANE_COUNT
                return@repeat
            }

            if (!currentLaneActive) {
                val quantum = quantums[index].coerceIn(1, MAX_TASK_COST)
                deficits[index] = minOf(deficits[index], MAX_DEFICIT)
                deficits[index] += minOf(quantum, MAX_DEFICIT - deficits[index])
                currentLaneActive = true
            }

            if (deficits[index] >= cost) {
                return DispatcherLane.entries[index]
            }

            currentLaneActive = false
            cursor = (cursor + 1) % LANE_COUNT
        }
        return null
    }

    fun consume(lane: DispatcherLane, cost: Int) {
        val index = lane.ordinal
        deficits[index] -= minOf(deficits[index], cost.coerceIn(1, MAX_TASK_COST))
    }

    fun resetLane(lane: DispatcherLane) {
        val index = lane.ordinal
        deficits[index] = 0
        if (index == cursor) {
            currentLaneActive = false
            cursor = (cursor + 1) % LANE_COUNT
        }
    }

    fun getDeficit(lane: DispatcherLane): Int = deficits[lane.ordinal]

    companion object {
        val LANE_COUNT: Int = DispatcherLane.entries.size
        const val MAX_TASK_COST: Int = DISPATCHER_MAX_TASK_COST
        val MAX_DEFICIT: Int = DISPATCHER_MAX_TASK_COST * 4

        fun agedQuantum(baseQuantum: Int, oldestReadyAgeUs: Long, sloUs: Long): Int {
            val base = baseQuantum.coerceIn(1, MAX_TASK_COST)
            val slo = maxOf(1L, sloUs)
            if (oldestReadyAgeUs < slo) {
                return base
            }
            return minOf(MAX_TASK_COST, base * 2)
        }
    }
}
