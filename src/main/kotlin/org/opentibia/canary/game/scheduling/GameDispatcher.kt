package org.opentibia.canary.game.scheduling

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

private val logger = KotlinLogging.logger {}

/** `DEFAULT_DISPATCHER_SLO` (src/game/scheduling/dispatcher.cpp:32). */
private const val DEFAULT_DISPATCHER_SLO_US: Long = 50_000

/**
 * Handle for a [GameDispatcher.schedule]/[GameDispatcher.cycleEvent] registration.
 *
 * Cancellation is re-resolved by event id ([GameDispatcher.cancelScheduledEvent])
 * instead of capturing the dispatcher (AGENTS.md deferred callback lifetime rules).
 */
class ScheduledTask internal constructor(
    internal val eventId: Long,
    private val cancelled: AtomicBoolean,
    private val onCancel: (Long) -> Unit,
) {
    fun cancel() {
        if (cancelled.compareAndSet(false, true)) {
            onCancel(eventId)
        }
    }
}

/**
 * Kotlin port of `Dispatcher` (src/game/scheduling/dispatcher.hpp:112-321) covering
 * the lane-aware WDRR pass (`executeReadyEvents`, dispatcher.cpp:795-877), the lane
 * budgets/quantums (dispatcher.cpp:665-717) and the worker-completion hand-back
 * (dispatcher.cpp:267-293). Scheduled events are promoted against an injectable
 * [clock] instead of `OTSYS_TIME` (dispatcher.cpp:879-904).
 *
 * PARITY GAPS: the C++ thread pool and `asyncWait` barrier execution are not
 * ported; [ExecutionMode.BarrierParallel] lanes are drained sequentially on the
 * single worker. Admission counters, creature async buckets and adaptive budget
 * refresh need the game facade and are served by the standalone
 * [DispatcherAdaptiveBudgetController]/[DispatcherAdmissionCounter] components.
 */
class GameDispatcher(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default.limitedParallelism(1)),
    private val clock: () -> Long = System::currentTimeMillis,
    private val monsterComputeService: MonsterComputeService? = null,
    private val timeSourceUs: () -> Long = { System.nanoTime() / 1_000 },
) {
    private class QueuedEvent(
        val id: Long,
        override val meta: TaskMeta,
        override val context: String,
        override val enqueuedAt: Long,
        val expiresAt: Long,
        val cycle: Boolean,
        val cycleDelayMs: Long,
        val cancelled: AtomicBoolean?,
        val task: suspend () -> Unit,
    ) : DispatcherQueueEntry {
        override val readyAt: Long get() = meta.readyAt
    }

    private data class LaneExecutionResult(val tasks: Int, val cost: Int, val runtimeUs: Long = 0)

    private val inbox = ConcurrentLinkedQueue<QueuedEvent>()
    private val scheduledEvents = ConcurrentHashMap<Long, QueuedEvent>()
    private val laneQueues = Array(DispatcherWeightedDeficitRoundRobin.LANE_COUNT) { ArrayDeque<QueuedEvent>() }
    private val wakeSignal = Channel<Unit>(Channel.CONFLATED)
    private val running = AtomicBoolean(false)
    private val weightedScheduler = DispatcherWeightedDeficitRoundRobin()
    private val lastProducerTokens = LongArray(DispatcherWeightedDeficitRoundRobin.LANE_COUNT)
    private val configuredBudgets = DispatcherBudgetSet()
    private val activeBudgets = DispatcherBudgetSet()
    private val eventIdSequence = AtomicLong(0)
    private val dispatcherCycle = AtomicLong(0)
    private var workerJob: Job? = null

    internal val laneTelemetry = Array(DispatcherWeightedDeficitRoundRobin.LANE_COUNT) { LaneTelemetry() }

    /** World-confinement gate: tasks are accepted only while the worker is running. */
    val isRunning: Boolean get() = running.get()

    fun start() {
        if (running.compareAndSet(false, true)) {
            val weakSelf = WeakReference(this)
            monsterComputeService?.setCompletionNotifier { weakSelf.get()?.wake() }
            workerJob = scope.launch {
                while (isActive && running.get()) {
                    drainInbox()
                    promoteScheduledEvents()
                    executeReadyEvents()
                    if (!running.get()) break

                    if (hasReadyWork()) {
                        continue
                    }

                    val waitMs = timeUntilNextScheduledEvent()
                    if (waitMs == null) {
                        wakeSignal.receive()
                    } else {
                        withTimeoutOrNull(waitMs) { wakeSignal.receive() }
                    }
                }
            }
        }
    }

    fun dispatch(lane: DispatcherLane = DispatcherLane.WorldCommit, task: suspend () -> Unit) {
        if (!running.get()) return

        val target = executableLane(lane)
        val now = clock()
        val id = nextEventId()
        inbox.add(
            QueuedEvent(
                id = id,
                meta = TaskMeta(
                    lane = target,
                    executionMode = defaultExecutionMode(target),
                    readyAt = now,
                    generation = id,
                ),
                context = "GameDispatcher::dispatch",
                enqueuedAt = now,
                expiresAt = 0,
                cycle = false,
                cycleDelayMs = 0,
                cancelled = null,
                task = task,
            ),
        )
        wake()
    }

    fun schedule(delayMs: Long, lane: DispatcherLane = DispatcherLane.WorldCommit, task: suspend () -> Unit): ScheduledTask =
        scheduleEvent(delayMs, lane, "GameDispatcher::schedule", cycle = false, task)

    fun cycleEvent(
        delayMs: Long,
        lane: DispatcherLane = DispatcherLane.WorldCommit,
        context: String = "GameDispatcher::cycleEvent",
        task: suspend () -> Unit,
    ): ScheduledTask = scheduleEvent(delayMs, lane, context, cycle = true, task)

    fun shutdown() {
        if (running.compareAndSet(true, false)) {
            wake()
            workerJob?.cancel()
            scope.cancel()
        }
    }

    internal fun getDispatcherCycle(): Long = dispatcherCycle.get()

    /** Re-evaluates the injected clock and deferred queues; also the worker signal. */
    internal fun wake() {
        wakeSignal.trySend(Unit)
    }

    private fun scheduleEvent(
        delayMs: Long,
        lane: DispatcherLane,
        context: String,
        cycle: Boolean,
        task: suspend () -> Unit,
    ): ScheduledTask {
        val id = nextEventId()
        val cancelled = AtomicBoolean(false)
        val weakSelf = WeakReference(this)
        val handle = ScheduledTask(id, cancelled) { eventId -> weakSelf.get()?.cancelScheduledEvent(eventId) }
        if (running.get()) {
            val target = executableLane(lane)
            val now = clock()
            scheduledEvents[id] = QueuedEvent(
                id = id,
                meta = TaskMeta(
                    lane = target,
                    executionMode = defaultExecutionMode(target),
                    readyAt = now + delayMs,
                    generation = id,
                ),
                context = context,
                enqueuedAt = now,
                expiresAt = 0,
                cycle = cycle,
                cycleDelayMs = delayMs,
                cancelled = cancelled,
                task = task,
            )
            wake()
        } else {
            cancelled.set(true)
        }
        return handle
    }

    private fun cancelScheduledEvent(eventId: Long) {
        scheduledEvents.remove(eventId)
        wake()
    }

    private fun executableLane(lane: DispatcherLane): DispatcherLane =
        if (lane == DispatcherLane.WorkerCompletion) DispatcherLane.WorldCommit else lane

    private fun nextEventId(): Long {
        val id = eventIdSequence.incrementAndGet()
        if (id == 0L) {
            eventIdSequence.set(1)
            return 1
        }
        return id
    }

    private fun drainInbox() {
        while (true) {
            val event = inbox.poll() ?: break
            laneQueues[event.meta.lane.ordinal].addLast(event)
        }
    }

    private fun promoteScheduledEvents() {
        val now = clock()
        for (event in scheduledEvents.values) {
            if (event.readyAt > now) {
                continue
            }
            if (!scheduledEvents.remove(event.id, event)) {
                continue
            }
            if (event.cancelled?.get() == true) {
                continue
            }
            laneQueues[event.meta.lane.ordinal].addLast(event)
        }
    }

    private fun hasReadyWork(): Boolean {
        if (!inbox.isEmpty()) {
            return true
        }
        if (monsterComputeService != null && monsterComputeService.getCompletionCount() > 0) {
            return true
        }
        for (queue in laneQueues) {
            if (queue.isNotEmpty()) {
                return true
            }
        }
        return false
    }

    private fun timeUntilNextScheduledEvent(): Long? {
        var soonest: Long? = null
        val now = clock()
        for (event in scheduledEvents.values) {
            if (event.cancelled?.get() == true) {
                continue
            }
            val remaining = (event.readyAt - now).coerceAtLeast(0)
            if (soonest == null || remaining < soonest) {
                soonest = remaining
            }
        }
        return soonest
    }

    private suspend fun executeReadyEvents() {
        val laneCount = DispatcherWeightedDeficitRoundRobin.LANE_COUNT
        val tasksExecuted = IntArray(laneCount)
        val exhausted = BooleanArray(laneCount)
        val serialRuntimeUs = LongArray(laneCount)
        val nextReadyAgeUs = LongArray(laneCount)
        val now = clock()
        var remainingPassTasks = 0

        for (laneId in 0 until laneCount) {
            val lane = DispatcherLane.entries[laneId]
            val queue = laneQueues[laneId]
            if (queue.isNotEmpty()) {
                val candidateIndex = candidateIndex(lane, queue, laneId)
                nextReadyAgeUs[laneId] = DispatcherPolicy.elapsed(queue[candidateIndex].readyAt, now)
            }
            remainingPassTasks += minOf(laneTaskBudget(lane), Int.MAX_VALUE - remainingPassTasks)
        }
        nextReadyAgeUs[DispatcherLane.WorkerCompletion.ordinal] =
            monsterComputeService?.getStats()?.oldestCompletionReadyAgeUs ?: 0

        while (remainingPassTasks > 0) {
            val ready = BooleanArray(laneCount)
            val quantums = IntArray(laneCount)
            val nextCosts = IntArray(laneCount)
            val candidateIndices = IntArray(laneCount)
            for (laneId in 0 until laneCount) {
                val lane = DispatcherLane.entries[laneId]
                if (exhausted[laneId] || tasksExecuted[laneId] >= laneTaskBudget(lane)) {
                    continue
                }

                if (lane == DispatcherLane.WorkerCompletion) {
                    ready[laneId] = (monsterComputeService?.getCompletionCount() ?: 0) > 0
                    nextCosts[laneId] = 1
                } else {
                    val queue = laneQueues[laneId]
                    if (queue.isNotEmpty()) {
                        candidateIndices[laneId] = candidateIndex(lane, queue, laneId)
                        ready[laneId] = true
                        nextCosts[laneId] = queue[candidateIndices[laneId]].meta.estimatedCost
                            .coerceIn(1, DISPATCHER_MAX_TASK_COST)
                    }
                }
                quantums[laneId] = DispatcherWeightedDeficitRoundRobin.agedQuantum(
                    laneQuantum(lane),
                    nextReadyAgeUs[laneId],
                    DEFAULT_DISPATCHER_SLO_US,
                )
            }

            val lane = weightedScheduler.selectLane(ready, quantums, nextCosts) ?: break
            val laneId = lane.ordinal
            val remainingTasks = laneTaskBudget(lane) - tasksExecuted[laneId]
            val availableCost = weightedScheduler.getDeficit(lane)

            val result = when {
                lane == DispatcherLane.WorkerCompletion -> executeWorkerCompletionLane(remainingTasks, availableCost)
                laneQueues[laneId].isNotEmpty() &&
                    laneQueues[laneId][candidateIndices[laneId]].meta.executionMode == ExecutionMode.BarrierParallel ->
                    executeBarrierLane(lane, remainingTasks, availableCost)
                else -> {
                    val maxRuntimeUs = if (isPlayerVisible(lane)) {
                        Long.MAX_VALUE
                    } else {
                        activeBudgets.sliceRuntimeUs - minOf(activeBudgets.sliceRuntimeUs, serialRuntimeUs[laneId])
                    }
                    executeSerialLane(lane, remainingTasks, availableCost, maxRuntimeUs).also {
                        serialRuntimeUs[laneId] += it.runtimeUs
                    }
                }
            }

            if (result.tasks == 0 || result.cost == 0) {
                exhausted[laneId] = true
                weightedScheduler.resetLane(lane)
                continue
            }

            tasksExecuted[laneId] += result.tasks
            remainingPassTasks -= minOf(remainingPassTasks, result.tasks)
            weightedScheduler.consume(lane, result.cost)
            if (!isPlayerVisible(lane) && serialRuntimeUs[laneId] >= activeBudgets.sliceRuntimeUs) {
                exhausted[laneId] = true
                weightedScheduler.resetLane(lane)
            }
        }
    }

    private fun candidateIndex(lane: DispatcherLane, queue: ArrayDeque<QueuedEvent>, laneId: Int): Int =
        if (usesProducerFairness(lane)) {
            DispatcherPolicy.selectProducerFairIndex(queue, lastProducerTokens[laneId])
        } else {
            0
        }

    private suspend fun executeSerialLane(
        lane: DispatcherLane,
        maxTasks: Int,
        maxCost: Int,
        maxRuntimeUs: Long,
    ): LaneExecutionResult {
        val laneId = lane.ordinal
        val queue = laneQueues[laneId]
        if (queue.isEmpty() || maxTasks == 0 || maxCost == 0) {
            return LaneExecutionResult(0, 0)
        }

        val hasRuntimeBudget = maxRuntimeUs != Long.MAX_VALUE
        val startedAtUs = timeSourceUs()
        val deadlineUs = if (hasRuntimeBudget) startedAtUs + maxRuntimeUs else Long.MAX_VALUE
        var tasks = 0
        var cost = 0

        while (queue.isNotEmpty() && tasks < maxTasks) {
            if (tasks > 0 && hasRuntimeBudget && timeSourceUs() >= deadlineUs) {
                break
            }

            val index = candidateIndex(lane, queue, laneId)
            val event = queue[index]
            val taskCost = event.meta.estimatedCost.coerceIn(1, DISPATCHER_MAX_TASK_COST)
            if (taskCost > maxCost - cost) {
                break
            }

            if (index == 0) {
                queue.removeFirst()
            } else {
                queue.removeAt(index)
            }
            lastProducerTokens[laneId] = event.meta.producerToken
            executeQueuedEvent(event, lane)
            tasks++
            cost += taskCost
        }

        val runtimeUs = if (hasRuntimeBudget) timeSourceUs() - startedAtUs else 0
        if (tasks > 0) {
            laneTelemetry[laneId].groupRuntime.observe(runtimeUs, tasks.toLong())
        }
        return LaneExecutionResult(tasks, cost, runtimeUs)
    }

    private suspend fun executeBarrierLane(
        lane: DispatcherLane,
        maxTasks: Int,
        maxCost: Int,
    ): LaneExecutionResult {
        val laneId = lane.ordinal
        val queue = laneQueues[laneId]
        if (queue.isEmpty() || maxTasks == 0 || maxCost == 0) {
            return LaneExecutionResult(0, 0)
        }

        var selected = 0
        var cost = 0
        while (selected < queue.size && selected < maxTasks) {
            val taskCost = queue[selected].meta.estimatedCost.coerceIn(1, DISPATCHER_MAX_TASK_COST)
            if (taskCost > maxCost - cost) {
                break
            }
            cost += taskCost
            selected++
        }
        if (selected == 0) {
            return LaneExecutionResult(0, 0)
        }

        val startedAtUs = timeSourceUs()
        repeat(selected) {
            executeQueuedEvent(queue.removeFirst(), lane)
        }
        val runtimeUs = timeSourceUs() - startedAtUs
        laneTelemetry[laneId].barrierRuntime.observe(runtimeUs, selected.toLong())
        laneTelemetry[laneId].groupRuntime.observe(runtimeUs, selected.toLong())
        return LaneExecutionResult(selected, cost, runtimeUs)
    }

    private fun executeWorkerCompletionLane(maxTasks: Int, maxCost: Int): LaneExecutionResult {
        val service = monsterComputeService ?: return LaneExecutionResult(0, 0)
        if (maxTasks == 0 || maxCost == 0) {
            return LaneExecutionResult(0, 0)
        }

        val startedAtUs = timeSourceUs()
        val taskLimit = minOf(maxTasks, maxCost, service.getCompletionCount())
        val drained = service.drainCompletions(taskLimit) { _, completion -> completion?.invoke() }
        val runtimeUs = timeSourceUs() - startedAtUs
        laneTelemetry[DispatcherLane.WorkerCompletion.ordinal].groupRuntime.observe(runtimeUs, drained.toLong())
        dispatcherCycle.addAndGet(drained.toLong())
        return LaneExecutionResult(drained, drained)
    }

    private suspend fun executeQueuedEvent(event: QueuedEvent, lane: DispatcherLane) {
        val laneId = lane.ordinal
        val queueWaitUs = DispatcherPolicy.elapsed(event.readyAt, clock()) * 1_000
        laneTelemetry[laneId].queueWait.observe(queueWaitUs, 1, event.context)

        val startedAtUs = timeSourceUs()
        var executed = false
        if (event.cancelled?.get() == true) {
            logger.debug { "Skipping cancelled task '${event.context}'" }
        } else if (event.expiresAt != 0L && clock() > event.expiresAt) {
            logger.info { "The task '${event.context}' has expired, it has not been executed." }
        } else {
            try {
                event.task()
                executed = true
            } catch (e: Exception) {
                logger.error(e) { "Unhandled exception during event execution on GameDispatcher" }
            }
        }
        laneTelemetry[laneId].taskRuntime.observe(timeSourceUs() - startedAtUs, 1, event.context)

        if (executed) {
            dispatcherCycle.incrementAndGet()
            if (event.cycle && event.cancelled?.get() != true) {
                rescheduleCycleEvent(event)
            }
        }
    }

    private fun rescheduleCycleEvent(event: QueuedEvent) {
        val now = clock()
        scheduledEvents[event.id] = QueuedEvent(
            id = event.id,
            meta = event.meta.copy(readyAt = now + event.cycleDelayMs),
            context = event.context,
            enqueuedAt = now,
            expiresAt = 0,
            cycle = true,
            cycleDelayMs = event.cycleDelayMs,
            cancelled = event.cancelled,
            task = event.task,
        )
        wake()
    }

    private fun laneTaskBudget(lane: DispatcherLane): Int = when (lane) {
        DispatcherLane.ProtocolInput,
        DispatcherLane.PlayerWalk,
        DispatcherLane.PlayerAction,
        DispatcherLane.WorldCommit,
        -> 256
        DispatcherLane.WorkerCompletion -> activeBudgets.workerCompletions
        DispatcherLane.VisibleMonster -> configuredBudgets.creatureWalkTasks
        DispatcherLane.BackgroundMonster -> activeBudgets.creatureWalkTasks
        DispatcherLane.VisibleMonsterAI -> configuredBudgets.walkParallelTasks
        DispatcherLane.MonsterAI,
        DispatcherLane.GenericParallel,
        -> activeBudgets.walkParallelTasks
        DispatcherLane.Deferred -> activeBudgets.deferredGameplayTasks
        DispatcherLane.Maintenance -> 16
    }

    private fun laneQuantum(lane: DispatcherLane): Int = when (lane) {
        DispatcherLane.ProtocolInput,
        DispatcherLane.PlayerWalk,
        -> 8
        DispatcherLane.PlayerAction,
        DispatcherLane.WorldCommit,
        -> 6
        DispatcherLane.WorkerCompletion,
        DispatcherLane.VisibleMonster,
        DispatcherLane.VisibleMonsterAI,
        -> 4
        DispatcherLane.BackgroundMonster -> 1
        DispatcherLane.MonsterAI,
        DispatcherLane.Deferred,
        -> 2
        DispatcherLane.Maintenance,
        DispatcherLane.GenericParallel,
        -> 1
    }
}

/** `Dispatcher::LaneTelemetry` (src/game/scheduling/dispatcher.hpp:297-302). */
internal class LaneTelemetry {
    val queueWait = ConcurrentTimedWork()
    val taskRuntime = ConcurrentTimedWork()
    val groupRuntime = ConcurrentTimedWork()
    val barrierRuntime = ConcurrentTimedWork()
}
