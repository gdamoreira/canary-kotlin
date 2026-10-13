package org.opentibia.canary.game.scheduling

import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private val logger = KotlinLogging.logger {}

/** `MonsterComputeToken` (src/game/scheduling/monster_compute_service.hpp:29). */
typealias MonsterComputeToken = Long

/** `MonsterComputeService::Completion` (src/game/scheduling/monster_compute_service.hpp:87). */
typealias MonsterComputeCompletion = () -> Unit

/** `std::stop_token` passed to [MonsterComputeWork] (monster_compute_service.hpp:91). */
fun interface MonsterComputeStopToken {
    fun isStopRequested(): Boolean
}

/**
 * `MonsterComputeService::Work` (src/game/scheduling/monster_compute_service.hpp:91):
 * runs off-thread over immutable values and returns the completion that mutates
 * the world when the dispatcher later consumes it.
 */
typealias MonsterComputeWork = (MonsterComputeToken, MonsterComputeStopToken) -> MonsterComputeCompletion?

/** `enum class MonsterComputePriority` (src/game/scheduling/monster_compute_service.hpp:31-34). */
enum class MonsterComputePriority {
    Visible,
    Background,
}

/** `enum class MonsterComputeSubmitStatus` (src/game/scheduling/monster_compute_service.hpp:36-42). */
enum class MonsterComputeSubmitStatus {
    Accepted,
    RanInline,
    QueueFull,
    Stopping,
    Invalid,
}

/** `struct MonsterComputeSubmission` (src/game/scheduling/monster_compute_service.hpp:44-51). */
data class MonsterComputeSubmission(
    val status: MonsterComputeSubmitStatus,
    val token: MonsterComputeToken,
) {
    val accepted: Boolean
        get() = status == MonsterComputeSubmitStatus.Accepted || status == MonsterComputeSubmitStatus.RanInline
}

/** `struct MonsterComputeConfig` (src/game/scheduling/monster_compute_service.hpp:53-57). */
data class MonsterComputeConfig(
    val configuredThreads: Int = 0,
    val capacity: Int = 2048,
    val hardwareConcurrency: Int = Runtime.getRuntime().availableProcessors(),
)

/** `struct MonsterComputeStats` (src/game/scheduling/monster_compute_service.hpp:59-83). */
data class MonsterComputeStats(
    val visibleQueued: Int = 0,
    val backgroundQueued: Int = 0,
    val visibleCompletionsQueued: Int = 0,
    val backgroundCompletionsQueued: Int = 0,
    val completionsQueued: Int = 0,
    val visibleOutstanding: Int = 0,
    val backgroundOutstanding: Int = 0,
    val outstanding: Int = 0,
    val active: Int = 0,
    val completionsInFlight: Int = 0,
    val capacity: Int = 0,
    val visibleReserve: Int = 0,
    val workerCount: Int = 0,
    val oldestVisibleCompletionReadyAgeUs: Long = 0,
    val oldestBackgroundCompletionReadyAgeUs: Long = 0,
    val oldestCompletionReadyAgeUs: Long = 0,
    val accepted: Long = 0,
    val rejected: Long = 0,
    val completed: Long = 0,
    val failed: Long = 0,
    val canceled: Long = 0,
    val inlineMode: Boolean = true,
    val running: Boolean = false,
)

/** Result of the visible/background selection streak (monster_compute_service.cpp:51-62). */
data class MonsterComputePrioritySelection(
    val priority: MonsterComputePriority,
    val visibleStreak: Int,
)

/** `MonsterComputeService` (src/game/scheduling/monster_compute_service.hpp:85-173). */
class MonsterComputeService(private val nanoTime: () -> Long = System::nanoTime) {
    private enum class State {
        Stopped,
        Running,
        Stopping,
    }

    private class Request(
        var token: MonsterComputeToken,
        val priority: MonsterComputePriority,
        val work: MonsterComputeWork,
        val failureCompletion: MonsterComputeCompletion?,
        val context: String,
    )

    private class CompletionRecord(
        val token: MonsterComputeToken,
        val priority: MonsterComputePriority,
        val completion: MonsterComputeCompletion?,
        val context: String,
        var readyAtNanos: Long = 0,
    )

    /** `MAX_MONSTER_COMPUTE_CAPACITY` (monster_compute_service.cpp:22). */
    private val maxCapacity = MAX_CAPACITY

    private val lock = ReentrantLock()
    private val workAvailable = lock.newCondition()
    private val lifecycleChanged = lock.newCondition()
    private val visibleRequests = ArrayDeque<Request>()
    private val backgroundRequests = ArrayDeque<Request>()
    private val visibleCompletions = ArrayDeque<CompletionRecord>()
    private val backgroundCompletions = ArrayDeque<CompletionRecord>()
    private val workers = mutableListOf<Thread>()
    private val stopping = AtomicBoolean(false)
    private var completionNotifier: (() -> Unit)? = null

    private var state = State.Stopped
    private var capacity = 0
    private var visibleReserve = 0
    private var visibleOutstanding = 0
    private var backgroundOutstanding = 0
    private var outstanding = 0
    private var activeRequests = 0
    private var completionsInFlight = 0
    private var workerCount = 0
    private var lastToken = 0L
    private var visibleStreak = 0
    private var completionVisibleStreak = 0
    private var acceptedCount = 0L
    private var rejectedCount = 0L
    private var completedCount = 0L
    private var failedCount = 0L
    private var canceledCount = 0L
    private var inlineMode = true

    fun start(config: MonsterComputeConfig) {
        lock.withLock {
            if (state == State.Running) {
                return
            }
            if (state == State.Stopping) {
                throw IllegalStateException("Cannot start monster compute service while it is stopping")
            }

            capacity = config.capacity.coerceIn(1, maxCapacity)
            visibleReserve = capacity / 4
            workerCount = resolveWorkerCount(config.configuredThreads, config.hardwareConcurrency)
            inlineMode = workerCount == 0
            visibleStreak = 0
            completionVisibleStreak = 0
            stopping.set(false)
            state = State.Running
        }

        try {
            repeat(workerCount) {
                val worker = Thread({ workerLoop() }, "canary-ai")
                workers.add(worker)
                worker.start()
            }
        } catch (t: Throwable) {
            stopping.set(true)
            lock.withLock { workAvailable.signalAll() }
            workers.forEach { it.interrupt() }
            workers.forEach { runCatching { it.join() } }
            workers.clear()
            lock.withLock {
                workerCount = 0
                state = State.Stopped
                lifecycleChanged.signalAll()
            }
            throw t
        }
    }

    fun shutdown() {
        var queuedCanceled = 0
        lock.withLock {
            if (state == State.Stopped) {
                return
            }
            if (state == State.Stopping) {
                while (state != State.Stopped) {
                    try {
                        lifecycleChanged.await()
                    } catch (e: InterruptedException) {
                        Thread.currentThread().interrupt()
                        return
                    }
                }
                return
            }

            state = State.Stopping
            stopping.set(true)
            val visibleQueuedCanceled = visibleRequests.size
            val backgroundQueuedCanceled = backgroundRequests.size
            queuedCanceled = visibleQueuedCanceled + backgroundQueuedCanceled
            visibleRequests.clear()
            backgroundRequests.clear()
            outstanding -= queuedCanceled
            visibleOutstanding -= visibleQueuedCanceled
            backgroundOutstanding -= backgroundQueuedCanceled
            canceledCount += queuedCanceled
            workAvailable.signalAll()
        }

        workers.forEach { it.interrupt() }
        workers.forEach { runCatching { it.join() } }
        workers.clear()

        lock.withLock {
            while (activeRequests != 0 || completionsInFlight != 0) {
                try {
                    lifecycleChanged.await()
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return
                }
            }

            val discardedVisibleCompletions = visibleCompletions.size
            val discardedBackgroundCompletions = backgroundCompletions.size
            val discardedCompletions = discardedVisibleCompletions + discardedBackgroundCompletions
            outstanding -= discardedCompletions
            visibleOutstanding -= discardedVisibleCompletions
            backgroundOutstanding -= discardedBackgroundCompletions
            canceledCount += discardedCompletions
            visibleCompletions.clear()
            backgroundCompletions.clear()
            workerCount = 0
            state = State.Stopped
            lifecycleChanged.signalAll()
        }
    }

    fun setCompletionNotifier(notifier: () -> Unit) {
        lock.withLock {
            completionNotifier = notifier
        }
    }

    fun submit(
        priority: MonsterComputePriority,
        work: MonsterComputeWork?,
        context: String,
        failureCompletion: MonsterComputeCompletion? = null,
    ): MonsterComputeSubmission {
        if (work == null || context.isEmpty()) {
            return MonsterComputeSubmission(MonsterComputeSubmitStatus.Invalid, 0)
        }

        val request = Request(0, priority, work, failureCompletion, context)
        var token = 0L
        var runInline = false
        lock.withLock {
            if (state != State.Running) {
                rejectedCount++
                return MonsterComputeSubmission(MonsterComputeSubmitStatus.Stopping, 0)
            }
            val backgroundCapacity = capacity - visibleReserve
            if (outstanding >= capacity || (priority == MonsterComputePriority.Background && backgroundOutstanding >= backgroundCapacity)) {
                rejectedCount++
                return MonsterComputeSubmission(MonsterComputeSubmitStatus.QueueFull, 0)
            }

            request.token = nextToken()
            token = request.token
            outstanding++
            if (priority == MonsterComputePriority.Visible) {
                visibleOutstanding++
            } else {
                backgroundOutstanding++
            }
            acceptedCount++
            runInline = inlineMode
            if (runInline) {
                activeRequests++
            } else {
                val queue = if (priority == MonsterComputePriority.Visible) visibleRequests else backgroundRequests
                queue.addLast(request)
            }
        }

        if (runInline) {
            executeRequest(request)
            return MonsterComputeSubmission(MonsterComputeSubmitStatus.RanInline, token)
        }

        lock.withLock { workAvailable.signal() }
        return MonsterComputeSubmission(MonsterComputeSubmitStatus.Accepted, token)
    }

    fun drainCompletions(
        maxCompletions: Int,
        executor: ((String, MonsterComputeCompletion?) -> Unit)? = null,
    ): Int {
        if (maxCompletions == 0) {
            return 0
        }

        val pending = mutableListOf<CompletionRecord>()
        lock.withLock {
            val completionCount = visibleCompletions.size + backgroundCompletions.size
            val count = minOf(maxCompletions, completionCount)
            repeat(count) {
                val selection = selectNextPriority(
                    visibleCompletions.isNotEmpty(),
                    backgroundCompletions.isNotEmpty(),
                    completionVisibleStreak,
                )
                requireNotNull(selection)
                completionVisibleStreak = selection.visibleStreak
                val queue = if (selection.priority == MonsterComputePriority.Visible) visibleCompletions else backgroundCompletions
                pending.add(queue.removeFirst())
            }
            completionsInFlight += count
        }

        for (record in pending) {
            try {
                if (executor != null) {
                    executor(record.context, record.completion)
                } else {
                    record.completion?.invoke()
                }
            } catch (t: Throwable) {
                logger.error(t) { "Monster compute completion '${record.context}' failed" }
                lock.withLock { failedCount++ }
            }

            lock.withLock {
                outstanding--
                if (record.priority == MonsterComputePriority.Visible) {
                    visibleOutstanding--
                } else {
                    backgroundOutstanding--
                }
                completionsInFlight--
                completedCount++
                lifecycleChanged.signalAll()
            }
        }

        return pending.size
    }

    fun getStats(): MonsterComputeStats {
        lock.withLock {
            val now = nanoTime()
            val oldestVisibleAge = oldestReadyAge(visibleCompletions.firstOrNull(), now)
            val oldestBackgroundAge = oldestReadyAge(backgroundCompletions.firstOrNull(), now)
            return MonsterComputeStats(
                visibleQueued = visibleRequests.size,
                backgroundQueued = backgroundRequests.size,
                visibleCompletionsQueued = visibleCompletions.size,
                backgroundCompletionsQueued = backgroundCompletions.size,
                completionsQueued = visibleCompletions.size + backgroundCompletions.size,
                visibleOutstanding = visibleOutstanding,
                backgroundOutstanding = backgroundOutstanding,
                outstanding = outstanding,
                active = activeRequests,
                completionsInFlight = completionsInFlight,
                capacity = capacity,
                visibleReserve = visibleReserve,
                workerCount = workerCount,
                oldestVisibleCompletionReadyAgeUs = oldestVisibleAge,
                oldestBackgroundCompletionReadyAgeUs = oldestBackgroundAge,
                oldestCompletionReadyAgeUs = maxOf(oldestVisibleAge, oldestBackgroundAge),
                accepted = acceptedCount,
                rejected = rejectedCount,
                completed = completedCount,
                failed = failedCount,
                canceled = canceledCount,
                inlineMode = inlineMode,
                running = state == State.Running,
            )
        }
    }

    fun getCompletionCount(): Int = lock.withLock { visibleCompletions.size + backgroundCompletions.size }

    private fun workerLoop() {
        while (!stopping.get()) {
            val request: Request
            lock.withLock {
                while (!stopping.get() && state == State.Running && visibleRequests.isEmpty() && backgroundRequests.isEmpty()) {
                    try {
                        workAvailable.await()
                    } catch (e: InterruptedException) {
                        Thread.currentThread().interrupt()
                        return
                    }
                }
                if (stopping.get() || state != State.Running || (visibleRequests.isEmpty() && backgroundRequests.isEmpty())) {
                    return
                }
                request = popNextRequest()
            }

            executeRequest(request)
        }
    }

    private fun executeRequest(request: Request) {
        val stopToken = MonsterComputeStopToken { stopping.get() }
        val completion = try {
            request.work(request.token, stopToken)
        } catch (t: Throwable) {
            logger.error(t) { "Monster compute work '${request.context}' failed" }
            lock.withLock { failedCount++ }
            request.failureCompletion
        }

        enqueueCompletion(CompletionRecord(request.token, request.priority, completion, request.context))
    }

    private fun popNextRequest(): Request {
        val selection = selectNextPriority(visibleRequests.isNotEmpty(), backgroundRequests.isNotEmpty(), visibleStreak)
        requireNotNull(selection)
        visibleStreak = selection.visibleStreak
        val request = if (selection.priority == MonsterComputePriority.Visible) {
            visibleRequests.removeFirst()
        } else {
            backgroundRequests.removeFirst()
        }
        activeRequests++
        return request
    }

    private fun enqueueCompletion(record: CompletionRecord) {
        var notifier: (() -> Unit)? = null
        lock.withLock {
            record.readyAtNanos = nanoTime()
            val queue = if (record.priority == MonsterComputePriority.Visible) visibleCompletions else backgroundCompletions
            queue.addLast(record)
            activeRequests--
            notifier = completionNotifier
            lifecycleChanged.signalAll()
        }
        notifier?.invoke()
    }

    private fun nextToken(): MonsterComputeToken {
        if (++lastToken == 0L) {
            lastToken = 1
        }
        return lastToken
    }

    private fun oldestReadyAge(record: CompletionRecord?, now: Long): Long {
        if (record == null || now <= record.readyAtNanos) {
            return 0
        }
        return (now - record.readyAtNanos) / 1000
    }

    companion object {
        /** `MAX_MONSTER_COMPUTE_CAPACITY` (src/game/scheduling/monster_compute_service.cpp:22). */
        const val MAX_CAPACITY: Int = 2048

        /** `MAX_MONSTER_COMPUTE_WORKERS` (src/game/scheduling/monster_compute_service.cpp:23). */
        const val MAX_WORKERS: Int = 4

        fun resolveWorkerCount(configuredThreads: Int, hardwareConcurrency: Int): Int {
            if (hardwareConcurrency <= 2) {
                return 0
            }
            if (configuredThreads != 0) {
                return configuredThreads.coerceIn(1, MAX_WORKERS)
            }

            val automatic = (hardwareConcurrency - 2) / 2
            return automatic.coerceIn(1, MAX_WORKERS)
        }

        fun selectNextPriority(
            hasVisible: Boolean,
            hasBackground: Boolean,
            visibleStreak: Int,
        ): MonsterComputePrioritySelection? {
            if (!hasVisible && !hasBackground) {
                return null
            }
            if (hasVisible && (!hasBackground || visibleStreak < 3)) {
                return MonsterComputePrioritySelection(MonsterComputePriority.Visible, minOf(3, visibleStreak + 1))
            }
            return MonsterComputePrioritySelection(MonsterComputePriority.Background, 0)
        }
    }
}
