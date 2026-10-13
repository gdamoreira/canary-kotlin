package org.opentibia.canary.game.scheduling

/** `DISPATCHER_MAX_TASK_COST` (src/game/scheduling/dispatcher_types.hpp:17). */
const val DISPATCHER_MAX_TASK_COST: Int = 32

/** `DISPATCHER_TASK_EXPIRATION` (src/game/scheduling/dispatcher.hpp:24). */
const val DISPATCHER_TASK_EXPIRATION: Int = 2000

/** `SCHEDULER_MINTICKS` (src/game/scheduling/dispatcher.hpp:25). */
const val SCHEDULER_MINTICKS: Long = 50

/** `DISPATCHER_LANE_QUEUE_CAPACITY` (src/game/scheduling/dispatcher.hpp:26). */
const val DISPATCHER_LANE_QUEUE_CAPACITY: Int = 16384

/** `DISPATCHER_CREATURE_ASYNC_BUCKET_RESERVE` (src/game/scheduling/dispatcher.hpp:27). */
const val DISPATCHER_CREATURE_ASYNC_BUCKET_RESERVE: Int = 32

/**
 * `enum class DispatcherLane` (src/game/scheduling/dispatcher_types.hpp:19-33).
 *
 * The C++ `Last` sentinel is not a lane and is omitted; [DispatcherLane.entries]
 * plays the role of `static_cast<size_t>(DispatcherLane::Last)`.
 */
enum class DispatcherLane {
    ProtocolInput,
    PlayerWalk,
    PlayerAction,
    WorldCommit,
    WorkerCompletion,
    VisibleMonster,
    BackgroundMonster,
    VisibleMonsterAI,
    MonsterAI,
    Deferred,
    Maintenance,
    GenericParallel,
}

/** `enum class ExecutionMode` (src/game/scheduling/dispatcher_types.hpp:35-39). */
enum class ExecutionMode {
    Serial,
    BarrierParallel,
    BackgroundCompletion,
}

/** `struct TaskMeta` (src/game/scheduling/dispatcher_types.hpp:41-48). */
data class TaskMeta(
    val lane: DispatcherLane = DispatcherLane.WorldCommit,
    val executionMode: ExecutionMode = ExecutionMode.Serial,
    val producerToken: Long = 0,
    val readyAt: Long = 0,
    val generation: Long = 0,
    val estimatedCost: Int = 1,
)

/** `defaultExecutionMode` (src/game/scheduling/dispatcher_types.hpp:50-58). */
fun defaultExecutionMode(lane: DispatcherLane): ExecutionMode = when (lane) {
    DispatcherLane.VisibleMonsterAI, DispatcherLane.MonsterAI, DispatcherLane.GenericParallel -> ExecutionMode.BarrierParallel
    DispatcherLane.WorkerCompletion -> ExecutionMode.BackgroundCompletion
    else -> ExecutionMode.Serial
}

/** `isMovementCommit` (src/game/scheduling/dispatcher_types.hpp:60-62). */
fun isMovementCommit(lane: DispatcherLane): Boolean = when (lane) {
    DispatcherLane.PlayerWalk, DispatcherLane.VisibleMonster, DispatcherLane.BackgroundMonster -> true
    else -> false
}

/** `isPlayerVisible` (src/game/scheduling/dispatcher_types.hpp:64-66). */
fun isPlayerVisible(lane: DispatcherLane): Boolean = when (lane) {
    DispatcherLane.ProtocolInput,
    DispatcherLane.PlayerWalk,
    DispatcherLane.PlayerAction,
    DispatcherLane.WorldCommit,
    DispatcherLane.WorkerCompletion,
    DispatcherLane.VisibleMonster,
    DispatcherLane.VisibleMonsterAI,
    -> true
    else -> false
}

/** `barrierContinuationLane` (src/game/scheduling/dispatcher_types.hpp:68-70). */
fun barrierContinuationLane(lane: DispatcherLane): DispatcherLane =
    if (lane == DispatcherLane.MonsterAI) DispatcherLane.Deferred else DispatcherLane.WorldCommit

/** `usesProducerFairness` (src/game/scheduling/dispatcher_types.hpp:72-74). */
fun usesProducerFairness(lane: DispatcherLane): Boolean = when (lane) {
    DispatcherLane.ProtocolInput, DispatcherLane.PlayerWalk, DispatcherLane.PlayerAction -> true
    else -> false
}
