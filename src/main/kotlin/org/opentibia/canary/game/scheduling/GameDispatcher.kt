package org.opentibia.canary.game.scheduling

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.opentibia.canary.config.ConfigKeys
import org.opentibia.canary.config.ConfigManager
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicLongArray
import kotlin.math.ceil

private val logger = KotlinLogging.logger {}

/** `DEFAULT_DISPATCHER_SLO` (src/game/scheduling/dispatcher.cpp:32). */
private const val DEFAULT_DISPATCHER_SLO_US: Long = 50_000

/** `DEFAULT_DISPATCHER_SLO` (src/game/scheduling/dispatcher.cpp:32), milliseconds. */
private const val DEFAULT_DISPATCHER_SLO_MS: Int = 50

/** `DEFAULT_DISPATCHER_EMERGENCY` (src/game/scheduling/dispatcher.cpp:33), milliseconds. */
private const val DEFAULT_DISPATCHER_EMERGENCY_MS: Int = 100

/** `defaultDispatcherBudgets` (src/game/scheduling/dispatcher.cpp:31). */
private val DEFAULT_DISPATCHER_BUDGETS = DispatcherBudgetSet()

/** `DISPATCHER_ADAPTIVE_BUDGET_INTERVAL` (src/game/scheduling/dispatcher.cpp:28). */
private const val DISPATCHER_ADAPTIVE_BUDGET_INTERVAL_MS: Long = 250

/** `DISPATCHER_QUEUE_LATENCY_LOG_THRESHOLD` (src/game/scheduling/dispatcher.cpp:26). */
private const val DISPATCHER_QUEUE_LATENCY_LOG_THRESHOLD_MS: Long = 250

/** `DISPATCHER_TELEMETRY_LOG_INTERVAL` (src/game/scheduling/dispatcher.cpp:27). */
internal const val DISPATCHER_TELEMETRY_LOG_INTERVAL_MS: Long = 5_000

/** `DISPATCHER_TELEMETRY_WARMUP` (src/game/scheduling/dispatcher.cpp:30). */
private const val DISPATCHER_TELEMETRY_WARMUP_MS: Long = 1_000

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
 * budgets/quantums (dispatcher.cpp:665-717), the worker-completion hand-back
 * (dispatcher.cpp:267-293) and the barrier-parallel execution with the
 * `asyncWait` partition fan-out (dispatcher.cpp:226-265, 629-663). Scheduled
 * events are promoted against an injectable [clock] instead of `OTSYS_TIME`
 * (dispatcher.cpp:879-904).
 *
 * The creature async admission path mirrors `addBarrierEvent`/
 * `addCreatureAsyncEvent`/`tryReserveLaneSlot` (dispatcher.cpp:719-793,
 * 1104-1139): barrier lanes reserve a per-lane admission slot, execute through
 * [barrierDispatcher] partitions, and report rejection to the caller so the
 * shared buckets can `rollbackAsyncTaskBucket`.
 *
 * The serial entry points reserve admission the same way (`reserveDispatcherSlot`,
 * dispatcher.cpp:750-762): [dispatch] mirrors `addEvent` (dispatcher.cpp:1002-1027)
 * and [schedule]/[cycleEvent] mirror `scheduleEvent` (dispatcher.cpp:1147-1177);
 * `stopEvent` releases the reservation (dispatcher.cpp:1171-1179) and a cycle
 * holds its slot across reschedules (dispatcher.cpp:906-939).
 *
 * The worker loop owns `refreshAdaptiveBudgets` (dispatcher.cpp:547-627) on the
 * `DISPATCHER_ADAPTIVE_BUDGET_INTERVAL` cadence, [observeInternalWork]
 * (dispatcher.cpp:378-388) gates every lateness observation behind the
 * queue-latency logging toggle, [logQueueLatency] (dispatcher.cpp:324-363) and
 * [logRuntimeTelemetry] (dispatcher.cpp:390-490) emit the periodic debug
 * logging on the `DISPATCHER_TELEMETRY_LOG_INTERVAL` cadence, and [context]
 * exposes the same per-task `DispatcherContext` `Creature::safeCall` reads for
 * its barrier continuation (creature.cpp:2195-2217). The task ThreadLocal is
 * carried as a coroutine `ThreadContextElement` so the context survives a
 * suspension resumed on another barrier worker thread.
 *
 * PARITY GAPS:
 *  - The production `setQueueLatencyLoggingEnabled(true)` call site
 *    (`CanaryServer`, canary_server.cpp:311) belongs to the composition root
 *    and is not wired here; tests enable the toggle through
 *    [setQueueLatencyLoggingEnabled].
 *  - `MonsterPipelinesHost` still records its lateness lambdas into its own
 *    `ConcurrentTimedWork` sinks (`postThinkLateness`/
 *    `movementRefreshLateness`) instead of calling [observeInternalWork]; the
 *    rebinding is owned by the monster host (out of `game.scheduling` scope).
 */
class GameDispatcher(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default.limitedParallelism(1)),
    private val clock: () -> Long = System::currentTimeMillis,
    private val monsterComputeService: MonsterComputeService? = null,
    private val timeSourceUs: () -> Long = { System.nanoTime() / 1_000 },
    /** `ThreadPool` worker view for `asyncWait` (`generatePartition`, dispatcher.cpp:247-261). */
    private val barrierDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val barrierWorkerCount: Int = Runtime.getRuntime().availableProcessors(),
    /** `g_configManager().getNumber(ConfigKey_t)` view for `getConfiguredDispatcherBudgets` (dispatcher.cpp:38-49). */
    private val config: ConfigManager? = null,
    /** `g_game().getGameState() == GAME_STATE_NORMAL` gate (dispatcher.cpp:548). */
    private val isGameStateNormal: () -> Boolean = { true },
    /** `g_game().getPlayersOnline()` gate of the backlog warning (dispatcher.cpp:577). */
    private val playersOnline: () -> Int = { 0 },
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
        /** `Task::dispatcherSlotReserved`/`reservedLane` (task.hpp); `null` when unreserved. */
        val reservedLane: DispatcherLane? = null,
        /** `true` when queued through [scheduleEvent] (`DispatcherType::ScheduledEvent`). */
        val scheduled: Boolean = false,
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
    private var configuredBudgets = DispatcherBudgetSet()
    private var activeBudgets = DispatcherBudgetSet()
    private val adaptiveBudgetController = DispatcherAdaptiveBudgetController()
    private val backlogWarningPolicy = DispatcherBacklogWarningPolicy()
    private val playerVisibleReadyLatency = ConcurrentLatencyHistogram()
    private val internalWorkTelemetry = Array(DispatcherInternalWork.entries.size) { ConcurrentTimedWork() }
    private var nextAdaptiveBudgetUpdateAt = 0L

    /** Per-lane `logQueueLatency` cadence (`nextLogAt`/`nextProbeAt`, dispatcher.cpp:325-347). */
    private val laneNextQueueLatencyLogAt = LongArray(DispatcherWeightedDeficitRoundRobin.LANE_COUNT)
    private val laneNextQueueLatencyProbeAt = LongArray(DispatcherWeightedDeficitRoundRobin.LANE_COUNT)

    /** `logRuntimeTelemetry` cadence (`nextLogAt`, dispatcher.cpp:391-401). */
    private var nextRuntimeTelemetryLogAt = 0L

    @Volatile
    private var queueLatencyLoggingEnabled = false

    @Volatile
    private var queueLatencyLoggingStartedAt = 0L

    private val contextThreadLocal = ThreadLocal<DispatcherContext>()

    /**
     * `visibleCreatureAsyncSliceLimits` / `backgroundCreatureAsyncSliceLimits`
     * (dispatcher.hpp:275-276): packed `(tasksPerBucket, maxRuntimeUs)` limits,
     * initialized from the configured/active budgets (`refreshAdaptiveBudgets`,
     * dispatcher.cpp:575-581).
     */
    @Volatile
    internal var visibleCreatureAsyncSliceLimits = CreatureAsyncSliceLimits(
        tasksPerBucket = configuredBudgets.creatureAsyncTasksPerBucket,
        maxRuntimeUs = configuredBudgets.sliceRuntimeUs,
    )

    @Volatile
    internal var backgroundCreatureAsyncSliceLimits = CreatureAsyncSliceLimits(
        tasksPerBucket = activeBudgets.creatureAsyncTasksPerBucket,
        maxRuntimeUs = activeBudgets.sliceRuntimeUs,
    )

    private val eventIdSequence = AtomicLong(0)
    private val dispatcherCycle = AtomicLong(0)
    private val reservedLaneSlots = Array(DispatcherWeightedDeficitRoundRobin.LANE_COUNT) { DispatcherAdmissionCounter() }
    private val rejectedLaneTasks = AtomicLongArray(DispatcherWeightedDeficitRoundRobin.LANE_COUNT)
    private var asyncWaitDisabled = false
    private var workerJob: Job? = null

    internal val laneTelemetry = Array(DispatcherWeightedDeficitRoundRobin.LANE_COUNT) { LaneTelemetry() }

    /** `scheduledLatenessTelemetry` (dispatcher.hpp:308). */
    internal val scheduledLatenessTelemetry = ConcurrentTimedWork()

    /** World-confinement gate: tasks are accepted only while the worker is running. */
    val isRunning: Boolean get() = running.get()

    fun start() {
        if (running.compareAndSet(false, true)) {
            val weakSelf = WeakReference(this)
            monsterComputeService?.setCompletionNotifier { weakSelf.get()?.wake() }
            workerJob = scope.launch {
                while (isActive && running.get()) {
                    val telemetryEnabled = queueLatencyLoggingEnabled
                    val passStartedAtUs = if (telemetryEnabled) timeSourceUs() else 0
                    drainInbox()
                    promoteScheduledEvents()
                    refreshAdaptiveBudgets()
                    executeReadyEvents()
                    if (!running.get()) break
                    if (telemetryEnabled) {
                        observeInternalWork(DispatcherInternalWork.DispatcherPass, 1, timeSourceUs() - passStartedAtUs)
                    }
                    logRuntimeTelemetry()

                    if (hasReadyWork()) {
                        continue
                    }

                    val waitMs = timeUntilNextScheduledEvent()
                    val idleStartedAtUs = if (telemetryEnabled) timeSourceUs() else 0
                    if (waitMs == null) {
                        wakeSignal.receive()
                    } else {
                        withTimeoutOrNull(waitMs) { wakeSignal.receive() }
                    }
                    if (telemetryEnabled) {
                        observeInternalWork(DispatcherInternalWork.DispatcherIdle, 1, timeSourceUs() - idleStartedAtUs)
                    }
                }
            }
        }
    }

    /**
     * `Dispatcher::addEvent` (dispatcher.cpp:1002-1027): reserve the lane
     * admission slot before enqueueing and report rejection so the caller can
     * fall back. PARITY NOTE: unlike the C++ lane normalizer, the port keeps a
     * barrier lane passed to [dispatch] on that barrier execution mode (the
     * [executeReadyEvents] test surface relies on it); [DispatcherLane.WorkerCompletion]
     * still maps to [DispatcherLane.WorldCommit].
     */
    fun dispatch(lane: DispatcherLane = DispatcherLane.WorldCommit, task: suspend () -> Unit): Boolean =
        dispatchEvent(lane, DISPATCHER_LANE_QUEUE_CAPACITY, "GameDispatcher::dispatch", task)

    /** `Dispatcher::addEvent` context-carrying form (dispatcher.cpp:1002-1027). */
    fun dispatch(lane: DispatcherLane, context: String, task: suspend () -> Unit): Boolean =
        dispatchEvent(lane, DISPATCHER_LANE_QUEUE_CAPACITY, context, task)

    /** `Dispatcher::tryReserveLaneSlot` capacity seam (dispatcher.cpp:719-732). */
    internal fun dispatch(lane: DispatcherLane, capacity: Int, task: suspend () -> Unit): Boolean =
        dispatchEvent(lane, capacity, "GameDispatcher::dispatch", task)

    private fun dispatchEvent(lane: DispatcherLane, capacity: Int, context: String, task: suspend () -> Unit): Boolean {
        if (!running.get()) {
            return false
        }

        val target = executableLane(lane)
        if (!tryReserveLaneSlot(target, capacity)) {
            return false
        }

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
                context = context,
                enqueuedAt = now,
                expiresAt = 0,
                cycle = false,
                cycleDelayMs = 0,
                cancelled = null,
                task = task,
                reservedLane = target,
            ),
        )
        wake()
        return true
    }

    fun schedule(delayMs: Long, lane: DispatcherLane = DispatcherLane.WorldCommit, task: suspend () -> Unit): ScheduledTask =
        scheduleEvent(delayMs, lane, "GameDispatcher::schedule", cycle = false, task)

    /** `Dispatcher::tryReserveLaneSlot` capacity seam for [schedule] (dispatcher.cpp:719-732). */
    internal fun schedule(delayMs: Long, lane: DispatcherLane, capacity: Int, task: suspend () -> Unit): ScheduledTask =
        scheduleEvent(delayMs, lane, capacity, "GameDispatcher::schedule", cycle = false, task)

    fun cycleEvent(
        delayMs: Long,
        lane: DispatcherLane = DispatcherLane.WorldCommit,
        context: String = "GameDispatcher::cycleEvent",
        task: suspend () -> Unit,
    ): ScheduledTask = scheduleEvent(delayMs, lane, context, cycle = true, task)

    /**
     * `Dispatcher::addBarrierEvent` (dispatcher.cpp:1104-1139): admission-gated
     * `BarrierParallel` enqueue. An invalid lane falls back to
     * [DispatcherLane.GenericParallel] (dispatcher.cpp:1119-1121) and a
     * saturated lane rejects with `false`.
     */
    fun addBarrierEvent(lane: DispatcherLane = DispatcherLane.GenericParallel, task: suspend () -> Unit): Boolean =
        addBarrierEvent(lane, DISPATCHER_LANE_QUEUE_CAPACITY, task)

    /**
     * `Dispatcher::addCreatureAsyncEvent` (dispatcher.cpp:1108-1113): the
     * creature AI lanes only, with the
     * `DISPATCHER_LANE_QUEUE_CAPACITY + DISPATCHER_CREATURE_ASYNC_BUCKET_RESERVE`
     * admission bound.
     */
    fun addCreatureAsyncEvent(lane: DispatcherLane, task: suspend () -> Unit): Boolean {
        if (lane != DispatcherLane.VisibleMonsterAI && lane != DispatcherLane.MonsterAI) {
            return false
        }
        return addBarrierEvent(lane, DISPATCHER_LANE_QUEUE_CAPACITY + DISPATCHER_CREATURE_ASYNC_BUCKET_RESERVE, task)
    }

    /** `Dispatcher::getCreatureAsyncSliceLimits` (dispatcher.hpp:152-156). */
    fun getCreatureAsyncSliceLimits(playerVisible: Boolean): CreatureAsyncSliceLimits =
        if (playerVisible) visibleCreatureAsyncSliceLimits else backgroundCreatureAsyncSliceLimits

    /** `Dispatcher::context()` (dispatcher.hpp:180-182); the no-task default outside the worker. */
    fun context(): DispatcherContext = contextThreadLocal.get() ?: DispatcherContext.NONE

    /**
     * `Dispatcher::observeInternalWork` (dispatcher.cpp:378-388): the observation
     * is dropped unless `queueLatencyLoggingEnabled` is set.
     */
    fun observeInternalWork(work: DispatcherInternalWork, units: Long, runtimeUs: Long, context: String = "") {
        if (!queueLatencyLoggingEnabled) {
            return
        }

        internalWorkTelemetry[work.ordinal].observe(runtimeUs, units, context)
    }

    /** `internalWorkTelemetry[work]` snapshot (test/telemetry read view). */
    internal fun internalWorkSnapshot(work: DispatcherInternalWork): TimedWorkSnapshot =
        internalWorkTelemetry[work.ordinal].snapshotAndReset()

    /**
     * `Dispatcher::logQueueLatency` (src/game/scheduling/dispatcher.cpp:324-363):
     * the lane's oldest ready task is logged when it exceeds
     * [DISPATCHER_QUEUE_LATENCY_LOG_THRESHOLD_MS], at most once per
     * [DISPATCHER_TELEMETRY_LOG_INTERVAL_MS]. Probes are limited to the
     * `DISPATCHER_ADAPTIVE_BUDGET_INTERVAL` cadence. The Kotlin clock is
     * milliseconds, so the inspected age is already milliseconds.
     */
    internal fun logQueueLatency(lane: DispatcherLane) {
        val laneId = lane.ordinal
        val queue = laneQueues[laneId]
        if (queue.isEmpty()) {
            return
        }

        if (!queueLatencyLoggingEnabled) {
            return
        }

        if (!isGameStateNormal()) {
            return
        }

        val now = clock()
        if (now < laneNextQueueLatencyLogAt[laneId] || now < laneNextQueueLatencyProbeAt[laneId]) {
            return
        }
        laneNextQueueLatencyProbeAt[laneId] = now + DISPATCHER_ADAPTIVE_BUDGET_INTERVAL_MS

        val snapshot = DispatcherPolicy.inspectQueueAt(queue, now, queueLatencyLoggingStartedAt)
        if (snapshot.queued == 0 || snapshot.oldestReadyAgeUs < DISPATCHER_QUEUE_LATENCY_LOG_THRESHOLD_MS) {
            return
        }

        laneNextQueueLatencyLogAt[laneId] = now + DISPATCHER_TELEMETRY_LOG_INTERVAL_MS
        logger.debug {
            "Dispatcher queue latency: lane=${lane.name}, queued=${snapshot.queued}, " +
                "oldest=${snapshot.oldestReadyAgeUs} ms, oldestContext=${snapshot.oldestContext}"
        }
    }

    /**
     * `Dispatcher::logRuntimeTelemetry` (src/game/scheduling/dispatcher.cpp:390-490):
     * every five seconds the lane, scheduled, internal-work and monster-compute
     * snapshots are logged at debug level, each sink reset on snapshot. Only the
     * enabled queue-latency logging and the normal game state pass.
     */
    internal fun logRuntimeTelemetry() {
        if (!queueLatencyLoggingEnabled || !isGameStateNormal()) {
            return
        }

        val now = clock()
        if (now < nextRuntimeTelemetryLogAt) {
            return
        }
        nextRuntimeTelemetryLogAt = now + DISPATCHER_TELEMETRY_LOG_INTERVAL_MS

        for (laneId in laneTelemetry.indices) {
            val telemetry = laneTelemetry[laneId]
            val queue = telemetry.queueWait.snapshotAndReset()
            val task = telemetry.taskRuntime.snapshotAndReset()
            val group = telemetry.groupRuntime.snapshotAndReset()
            val barrier = telemetry.barrierRuntime.snapshotAndReset()
            if (queue.empty && task.empty && group.empty && barrier.empty) {
                continue
            }
            val longestTaskContext = task.longestContext.ifEmpty { "none" }

            logger.debug {
                "Dispatcher telemetry: lane=${DispatcherLane.entries[laneId].name}, " +
                    "queued=${laneReservationCount(DispatcherLane.entries[laneId])}, " +
                    "rejected=${rejectedLaneTasks.get(laneId)}, " +
                    "queueSamples=${queue.latency.samples}, " +
                    "queueP50=${queue.latency.percentile(0.50)} us, " +
                    "queueP95=${queue.latency.percentile(0.95)} us, " +
                    "queueP99=${queue.latency.percentile(0.99)} us, " +
                    "taskSamples=${task.latency.samples}, " +
                    "taskP50=${task.latency.percentile(0.50)} us, " +
                    "taskP95=${task.latency.percentile(0.95)} us, " +
                    "taskP99=${task.latency.percentile(0.99)} us, " +
                    "laneP99=${group.latency.percentile(0.99)} us, " +
                    "barrierP99=${barrier.latency.percentile(0.99)} us, " +
                    "longestTask=${task.longestDurationUs} us, longestTaskContext=$longestTaskContext"
            }
        }

        val scheduled = scheduledLatenessTelemetry.snapshotAndReset()
        if (!scheduled.empty) {
            val latestContext = scheduled.longestContext.ifEmpty { "none" }
            logger.debug {
                "Dispatcher scheduled telemetry: samples=${scheduled.latency.samples}, " +
                    "latenessP50=${scheduled.latency.percentile(0.50)} us, " +
                    "latenessP95=${scheduled.latency.percentile(0.95)} us, " +
                    "latenessP99=${scheduled.latency.percentile(0.99)} us, " +
                    "maxLateness=${scheduled.latency.maxUs} us, latestContext=$latestContext"
            }
        }

        for (workId in internalWorkTelemetry.indices) {
            val work = internalWorkTelemetry[workId].snapshotAndReset()
            if (work.empty) {
                continue
            }

            logger.debug {
                "Dispatcher internal telemetry: work=${DispatcherInternalWork.entries[workId].name}, " +
                    "samples=${work.latency.samples}, units=${work.workUnits}, " +
                    "runtimeP50=${work.latency.percentile(0.50)} us, " +
                    "runtimeP95=${work.latency.percentile(0.95)} us, " +
                    "runtimeP99=${work.latency.percentile(0.99)} us, maxRuntime=${work.latency.maxUs} us"
            }
        }

        val computeStats = monsterComputeService?.getStats()
        if (computeStats?.running == true) {
            logger.debug {
                "Monster compute telemetry: visibleQueued=${computeStats.visibleQueued}, " +
                    "backgroundQueued=${computeStats.backgroundQueued}, " +
                    "visibleCompletions=${computeStats.visibleCompletionsQueued}, " +
                    "backgroundCompletions=${computeStats.backgroundCompletionsQueued}, " +
                    "oldestVisibleCompletion=${computeStats.oldestVisibleCompletionReadyAgeUs} us, " +
                    "oldestBackgroundCompletion=${computeStats.oldestBackgroundCompletionReadyAgeUs} us, " +
                    "visibleOutstanding=${computeStats.visibleOutstanding}, " +
                    "backgroundOutstanding=${computeStats.backgroundOutstanding}, " +
                    "outstanding=${computeStats.outstanding}, active=${computeStats.active}, " +
                    "completionsInFlight=${computeStats.completionsInFlight}, capacity=${computeStats.capacity}, " +
                    "visibleReserve=${computeStats.visibleReserve}, accepted=${computeStats.accepted}, " +
                    "rejected=${computeStats.rejected}, completed=${computeStats.completed}, " +
                    "failed=${computeStats.failed}, canceled=${computeStats.canceled}"
            }
        }
    }

    /**
     * `Dispatcher::setQueueLatencyLoggingEnabled` (dispatcher.cpp:363-376):
     * enabling restarts the warmup window and clears every runtime sink.
     */
    internal fun setQueueLatencyLoggingEnabled(enabled: Boolean) {
        if (!enabled) {
            queueLatencyLoggingEnabled = false
            queueLatencyLoggingStartedAt = 0
            resetRuntimeTelemetry()
            return
        }

        resetRuntimeTelemetry()
        queueLatencyLoggingStartedAt = clock() + DISPATCHER_TELEMETRY_WARMUP_MS
        queueLatencyLoggingEnabled = true
    }

    /** `reservedLaneSlots[lane].size()` (test/telemetry read view). */
    internal fun laneReservationCount(lane: DispatcherLane): Int = reservedLaneSlots[lane.ordinal].size

    /** `rejectedLaneTasks[lane]` (test/telemetry read view). */
    internal fun laneRejectionCount(lane: DispatcherLane): Long = rejectedLaneTasks.get(lane.ordinal)

    fun shutdown() {
        if (running.compareAndSet(true, false)) {
            setQueueLatencyLoggingEnabled(false)
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
    ): ScheduledTask = scheduleEvent(delayMs, lane, DISPATCHER_LANE_QUEUE_CAPACITY, context, cycle, task)

    /** `Dispatcher::scheduleEvent` (dispatcher.cpp:1147-1177): the reservation is held until execution/cancel. */
    internal fun scheduleEvent(
        delayMs: Long,
        lane: DispatcherLane,
        capacity: Int,
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
            if (!tryReserveLaneSlot(target, capacity)) {
                cancelled.set(true)
                return handle
            }
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
                reservedLane = target,
                scheduled = true,
            )
            wake()
        } else {
            cancelled.set(true)
        }
        return handle
    }

    /**
     * `Dispatcher::stopEvent` (dispatcher.cpp:1171-1179): the winner of the
     * scheduled-map removal releases the admission slot; when the worker already
     * promoted the event, the promotion owner releases it instead.
     */
    private fun cancelScheduledEvent(eventId: Long) {
        val event = scheduledEvents.remove(eventId) ?: return
        event.cancelled?.set(true)
        releaseReservedSlot(event)
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

    /** `Dispatcher::addBarrierEvent(f, lane, capacity)` (dispatcher.cpp:1115-1139). */
    internal fun addBarrierEvent(lane: DispatcherLane, capacity: Int, task: suspend () -> Unit): Boolean {
        if (!running.get()) {
            return false
        }

        val target = when (lane) {
            DispatcherLane.VisibleMonsterAI,
            DispatcherLane.MonsterAI,
            DispatcherLane.GenericParallel,
            -> lane
            else -> DispatcherLane.GenericParallel
        }
        if (!tryReserveLaneSlot(target, capacity)) {
            return false
        }

        val now = clock()
        val id = nextEventId()
        inbox.add(
            QueuedEvent(
                id = id,
                meta = TaskMeta(
                    lane = target,
                    executionMode = ExecutionMode.BarrierParallel,
                    readyAt = now,
                    generation = id,
                ),
                context = "GameDispatcher::addBarrierEvent",
                enqueuedAt = now,
                expiresAt = 0,
                cycle = false,
                cycleDelayMs = 0,
                cancelled = null,
                task = task,
                reservedLane = target,
            ),
        )
        wake()
        return true
    }

    /** `Dispatcher::tryReserveLaneSlot` (dispatcher.cpp:719-732). */
    private fun tryReserveLaneSlot(lane: DispatcherLane, capacity: Int = DISPATCHER_LANE_QUEUE_CAPACITY): Boolean {
        if (lane == DispatcherLane.WorkerCompletion) {
            observeLaneRejection(lane, capacity)
            return false
        }

        if (reservedLaneSlots[lane.ordinal].tryReserve(capacity)) {
            return true
        }

        observeLaneRejection(lane, capacity)
        return false
    }

    /** `Dispatcher::observeLaneRejection` (dispatcher.cpp:777-793). */
    private fun observeLaneRejection(lane: DispatcherLane, capacity: Int) {
        val rejected = rejectedLaneTasks.incrementAndGet(lane.ordinal)
        if (rejected == 1L || (rejected and (rejected - 1)) == 0L) {
            logger.warn {
                "Dispatcher lane admission rejected: lane=$lane, capacity=$capacity, " +
                    "queued=${reservedLaneSlots[lane.ordinal].size}, rejected=$rejected"
            }
        }
    }

    /** `Dispatcher::releaseDispatcherSlot` (dispatcher.cpp:764-775) for a queued event. */
    private fun releaseReservedSlot(event: QueuedEvent) {
        val lane = event.reservedLane ?: return
        reservedLaneSlots[lane.ordinal].release()
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
                releaseReservedSlot(event)
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

        logQueueLatency(lane)

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

    /**
     * `Dispatcher::executeBarrierLane` (dispatcher.cpp:226-265): select the
     * cost-fitting prefix, then run it through `asyncWait` so the batch fans out
     * over [barrierDispatcher] partitions. Each selected event is snapshotted
     * before fan-out so the lane queue is untouched while workers run.
     */
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

        logQueueLatency(lane)
        val selectedEvents = ArrayList<QueuedEvent>(selected)
        repeat(selected) {
            selectedEvents.add(queue.removeFirst())
        }
        val startedAtUs = timeSourceUs()
        asyncWait(selected) { index ->
            executeQueuedEvent(selectedEvents[index], lane)
        }
        val runtimeUs = timeSourceUs() - startedAtUs
        laneTelemetry[laneId].barrierRuntime.observe(runtimeUs, selected.toLong())
        laneTelemetry[laneId].groupRuntime.observe(runtimeUs, selected.toLong())
        return LaneExecutionResult(selected, cost, runtimeUs)
    }

    /**
     * `Dispatcher::asyncWait` (dispatcher.cpp:629-663) over the injectable
     * [barrierDispatcher]: the dispatcher coroutine runs the first partition and
     * awaits the remaining coroutine partitions. A nested barrier falls back to
     * in-place execution (`asyncWaitDisabled`), like the C++ guard against an
     * async call running inside another async call.
     */
    private suspend fun asyncWait(size: Int, task: suspend (Int) -> Unit) {
        if (size == 0) {
            return
        }
        if (asyncWaitDisabled) {
            for (index in 0 until size) {
                task(index)
            }
            return
        }

        val partitions = generatePartition(size)
        if (partitions.size <= 1) {
            for (index in partitions.first()) {
                task(index)
            }
            return
        }

        asyncWaitDisabled = true
        try {
            coroutineScope {
                val concurrentPartitions = partitions.drop(1).map { partition ->
                    async(barrierDispatcher) {
                        for (index in partition) {
                            task(index)
                        }
                    }
                }
                for (index in partitions.first()) {
                    task(index)
                }
                concurrentPartitions.awaitAll()
            }
        } finally {
            asyncWaitDisabled = false
        }
    }

    /** `Dispatcher::generatePartition` (dispatcher.hpp:247-261). */
    private fun generatePartition(size: Int): List<IntRange> {
        if (size == 0) {
            return emptyList()
        }

        val workers = barrierWorkerCount.coerceAtLeast(1)
        val blockSize = ceil(size / workers.toDouble()).toInt().coerceAtLeast(1)
        val partitions = mutableListOf<IntRange>()
        var start = 0
        while (start < size) {
            val end = minOf(size, start + blockSize)
            partitions.add(start until end)
            start += blockSize
        }
        return partitions
    }

    private fun executeWorkerCompletionLane(maxTasks: Int, maxCost: Int): LaneExecutionResult {
        val service = monsterComputeService ?: return LaneExecutionResult(0, 0)
        if (maxTasks == 0 || maxCost == 0) {
            return LaneExecutionResult(0, 0)
        }

        val previousContext = contextThreadLocal.get()
        contextThreadLocal.set(
            DispatcherContext(
                type = DispatcherType.WorkerCompletion,
                lane = DispatcherLane.WorkerCompletion,
                executionMode = ExecutionMode.BackgroundCompletion,
            ),
        )
        val startedAtUs = timeSourceUs()
        val drained: Int
        try {
            val taskLimit = minOf(maxTasks, maxCost, service.getCompletionCount())
            drained = service.drainCompletions(taskLimit) { _, completion -> completion?.invoke() }
        } finally {
            if (previousContext == null) {
                contextThreadLocal.remove()
            } else {
                contextThreadLocal.set(previousContext)
            }
        }
        val runtimeUs = timeSourceUs() - startedAtUs
        laneTelemetry[DispatcherLane.WorkerCompletion.ordinal].groupRuntime.observe(runtimeUs, drained.toLong())
        observeInternalWork(DispatcherInternalWork.WorkerCompletionBatch, drained.toLong(), runtimeUs)
        dispatcherCycle.addAndGet(drained.toLong())
        return LaneExecutionResult(drained, drained)
    }

    private suspend fun executeQueuedEvent(event: QueuedEvent, lane: DispatcherLane) {
        val laneId = lane.ordinal
        val queueWaitUs = DispatcherPolicy.elapsed(event.readyAt, clock()) * 1_000
        if (isPlayerVisible(event.meta.lane) && event.enqueuedAt >= queueLatencyLoggingStartedAt) {
            playerVisibleReadyLatency.observe(queueWaitUs)
        }
        if (queueLatencyLoggingEnabled) {
            laneTelemetry[laneId].queueWait.observe(queueWaitUs, 1, event.context)
            if (event.scheduled) {
                scheduledLatenessTelemetry.observe(queueWaitUs, 1, event.context)
            }
        }

        // A one-shot event releases admission before its callback runs so a
        // successor can replace the chain at capacity (dispatcher.cpp:919-922);
        // a cycle keeps its slot across reschedules (dispatcher.cpp:937-939) and
        // releases only when it stops (dispatcher.cpp:923-925, 941-943).
        if (!event.cycle) {
            releaseReservedSlot(event)
        }

        val context = DispatcherContext(
            type = dispatcherTypeOf(event),
            lane = event.meta.lane,
            executionMode = event.meta.executionMode,
            taskName = event.context,
        )
        val startedAtUs = timeSourceUs()
        var executed = false
        if (event.cancelled?.get() == true) {
            logger.debug { "Skipping cancelled task '${event.context}'" }
        } else if (event.expiresAt != 0L && clock() > event.expiresAt) {
            logger.info { "The task '${event.context}' has expired, it has not been executed." }
        } else {
            try {
                // The ThreadLocal is re-applied on every resumption (and
                // restored on suspension), so `Dispatcher::context()` still
                // describes the task when a barrier partition resumes on a
                // different worker thread.
                withContext(contextThreadLocal.asContextElement(context)) {
                    event.task()
                }
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
                return
            }
        }
        if (event.cycle) {
            releaseReservedSlot(event)
        }
    }

    private fun dispatcherTypeOf(event: QueuedEvent): DispatcherType = when {
        event.meta.executionMode == ExecutionMode.BarrierParallel -> DispatcherType.AsyncEvent
        event.cycle -> DispatcherType.CycleEvent
        event.scheduled -> DispatcherType.ScheduledEvent
        else -> DispatcherType.Event
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
            reservedLane = event.reservedLane,
            scheduled = true,
        )
        wake()
    }

    /** `Dispatcher::refreshAdaptiveBudgets` (dispatcher.cpp:547-627), called once per worker pass. */
    internal fun refreshAdaptiveBudgets() {
        if (!queueLatencyLoggingEnabled || !isGameStateNormal()) {
            return
        }

        val now = clock()
        if (now < nextAdaptiveBudgetUpdateAt) {
            return
        }
        nextAdaptiveBudgetUpdateAt = now + DISPATCHER_ADAPTIVE_BUDGET_INTERVAL_MS

        val visibleWindow = playerVisibleReadyLatency.snapshotAndReset()
        val configured = getConfiguredDispatcherBudgets()
        configuredBudgets = configured
        val visibleP99Us = visibleWindow.percentile(0.99)
        val visibleBacklog = playerVisibleBacklogSnapshot(now)
        val emergencyThresholdUs = getPositiveConfig("DISPATCHER_EMERGENCY_MS", DEFAULT_DISPATCHER_EMERGENCY_MS) * 1_000L
        val decision = adaptiveBudgetController.update(
            configured = configured,
            visibleP99Us = visibleP99Us,
            oldestVisibleReadyAgeUs = visibleBacklog.oldestReadyAgeUs,
            sloUs = getPositiveConfig("DISPATCHER_SLO_MS", DEFAULT_DISPATCHER_SLO_MS) * 1_000L,
            emergencyUs = emergencyThresholdUs,
        )
        activeBudgets = decision.budgets
        val onlinePlayers = playersOnline()
        val shouldWarnBacklog = backlogWarningPolicy.update(
            hasOnlinePlayers = onlinePlayers > 0,
            backlog = visibleBacklog,
            now = now,
            warningThresholdUs = emergencyThresholdUs,
        )

        visibleCreatureAsyncSliceLimits = CreatureAsyncSliceLimits(
            tasksPerBucket = configuredBudgets.creatureAsyncTasksPerBucket,
            maxRuntimeUs = configuredBudgets.sliceRuntimeUs,
        )
        backgroundCreatureAsyncSliceLimits = CreatureAsyncSliceLimits(
            tasksPerBucket = activeBudgets.creatureAsyncTasksPerBucket,
            maxRuntimeUs = activeBudgets.sliceRuntimeUs,
        )

        if (!decision.stateChanged && !shouldWarnBacklog) {
            return
        }
        val stateName = when (decision.state) {
            DispatcherLoadState.Normal -> "normal"
            DispatcherLoadState.Constrained -> "constrained"
            DispatcherLoadState.Emergency -> "emergency"
        }
        val oldestVisibleContext = visibleBacklog.oldestContext.ifEmpty { "none" }
        if (shouldWarnBacklog) {
            logger.warn {
                "Dispatcher sustained player-visible backlog: state=$stateName, onlinePlayers=$onlinePlayers, " +
                    "queuedVisible=${visibleBacklog.queued}, oldestVisible=${visibleBacklog.oldestReadyAgeUs} us, " +
                    "oldestContext=$oldestVisibleContext, visibleSamples=${visibleWindow.samples}, " +
                    "visibleP99=$visibleP99Us us, visibleMax=${visibleWindow.maxUs} us, " +
                    "controlLatency=${decision.controlLatencyUs} us, backgroundCreatureWalk=${activeBudgets.creatureWalkTasks}, " +
                    "backgroundParallel=${activeBudgets.walkParallelTasks}, " +
                    "backgroundCreatureBucket=${activeBudgets.creatureAsyncTasksPerBucket}, " +
                    "deferred=${activeBudgets.deferredGameplayTasks}, completions=${activeBudgets.workerCompletions}, " +
                    "slice=${activeBudgets.sliceRuntimeUs} us"
            }
        } else {
            logger.debug {
                "Dispatcher adaptive budgets: state=$stateName, onlinePlayers=$onlinePlayers, " +
                    "queuedVisible=${visibleBacklog.queued}, oldestVisible=${visibleBacklog.oldestReadyAgeUs} us, " +
                    "oldestContext=$oldestVisibleContext, visibleSamples=${visibleWindow.samples}, " +
                    "visibleP99=$visibleP99Us us, visibleMax=${visibleWindow.maxUs} us, " +
                    "controlLatency=${decision.controlLatencyUs} us, backgroundCreatureWalk=${activeBudgets.creatureWalkTasks}, " +
                    "backgroundParallel=${activeBudgets.walkParallelTasks}, " +
                    "backgroundCreatureBucket=${activeBudgets.creatureAsyncTasksPerBucket}, " +
                    "deferred=${activeBudgets.deferredGameplayTasks}, completions=${activeBudgets.workerCompletions}, " +
                    "slice=${activeBudgets.sliceRuntimeUs} us"
            }
        }
    }

    /** `getConfiguredDispatcherBudgets` (dispatcher.cpp:38-49). */
    private fun getConfiguredDispatcherBudgets(): DispatcherBudgetSet = DispatcherBudgetSet(
        creatureWalkTasks = getPositiveConfig("CREATURE_WALK_TASKS_PER_PASS", DEFAULT_DISPATCHER_BUDGETS.creatureWalkTasks),
        walkParallelTasks = getPositiveConfig("WALK_PARALLEL_TASKS_PER_PASS", DEFAULT_DISPATCHER_BUDGETS.walkParallelTasks),
        creatureAsyncTasksPerBucket = getPositiveConfig(
            "CREATURE_ASYNC_TASKS_PER_BUCKET",
            DEFAULT_DISPATCHER_BUDGETS.creatureAsyncTasksPerBucket,
        ),
        deferredGameplayTasks = getPositiveConfig(
            "DEFERRED_GAMEPLAY_TASKS_PER_PASS",
            DEFAULT_DISPATCHER_BUDGETS.deferredGameplayTasks,
        ),
        workerCompletions = getPositiveConfig("WORKER_COMPLETIONS_PER_PASS", DEFAULT_DISPATCHER_BUDGETS.workerCompletions),
        sliceRuntimeUs = getPositiveConfig(
            "DISPATCHER_SLICE_DURATION_MS",
            (DEFAULT_DISPATCHER_BUDGETS.sliceRuntimeUs / 1_000).toInt(),
        ) * 1_000L,
    )

    /** `getPositiveConfig` (dispatcher.cpp:33-39). */
    private fun getPositiveConfig(name: String, defaultValue: Int): Int {
        val manager = config ?: return defaultValue
        val key = ConfigKeys.byName(name) ?: return defaultValue
        return maxOf(1, manager.getNumber(key, defaultValue))
    }

    /**
     * `Dispatcher::playerVisibleBacklogSnapshot` (dispatcher.cpp:508-545).
     * The Kotlin clock is milliseconds while the policy snapshots are in
     * microseconds (`Task::Clock`), so queue/scheduled ages are converted here;
     * the compute-service ages are already microseconds.
     */
    private fun playerVisibleBacklogSnapshot(now: Long): DispatcherQueueSnapshot {
        val loggingStartedAt = queueLatencyLoggingStartedAt
        var queued = 0
        var oldestReadyAgeUs = 0L
        var oldestContext = ""

        fun merge(snapshot: DispatcherQueueSnapshot) {
            if (snapshot.queued == 0) {
                return
            }

            queued += snapshot.queued
            if (snapshot.oldestReadyAgeUs > oldestReadyAgeUs) {
                oldestReadyAgeUs = snapshot.oldestReadyAgeUs
                oldestContext = snapshot.oldestContext
            }
        }

        for (queue in laneQueues) {
            val snapshot = DispatcherPolicy.inspectPlayerVisibleQueueAt(queue, now, loggingStartedAt)
            merge(snapshot.copy(oldestReadyAgeUs = snapshot.oldestReadyAgeUs * 1_000))
        }
        for (event in scheduledEvents.values) {
            if (event.cancelled?.get() == true || event.enqueuedAt < loggingStartedAt ||
                !isPlayerVisible(event.meta.lane) || event.readyAt > now
            ) {
                continue
            }

            merge(
                DispatcherQueueSnapshot(
                    queued = 1,
                    oldestReadyAgeUs = DispatcherPolicy.elapsed(event.readyAt, now) * 1_000,
                    oldestContext = event.context,
                ),
            )
        }

        val computeStats = monsterComputeService?.getStats()
        if (computeStats != null) {
            merge(
                DispatcherQueueSnapshot(
                    queued = computeStats.visibleCompletionsQueued,
                    oldestReadyAgeUs = computeStats.oldestVisibleCompletionReadyAgeUs,
                    oldestContext = if (computeStats.visibleCompletionsQueued > 0) "MonsterComputeCompletion" else "",
                ),
            )
        }
        return DispatcherQueueSnapshot(queued, oldestReadyAgeUs, oldestContext)
    }

    /** `Dispatcher::resetRuntimeTelemetry` (dispatcher.cpp:498-513). */
    private fun resetRuntimeTelemetry() {
        for (telemetry in laneTelemetry) {
            telemetry.queueWait.reset()
            telemetry.taskRuntime.reset()
            telemetry.groupRuntime.reset()
            telemetry.barrierRuntime.reset()
        }

        for (telemetry in internalWorkTelemetry) {
            telemetry.reset()
        }
        scheduledLatenessTelemetry.reset()
        playerVisibleReadyLatency.reset()
        backlogWarningPolicy.reset()
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
