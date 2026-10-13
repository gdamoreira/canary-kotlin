package org.opentibia.canary.game.scheduling

import java.util.concurrent.atomic.AtomicInteger

/** `struct DispatcherQueueSnapshot` (src/game/scheduling/dispatcher_policy.hpp:28-32). */
data class DispatcherQueueSnapshot(
    val queued: Int = 0,
    val oldestReadyAgeUs: Long = 0,
    val oldestContext: String = "",
)

/** `enum class DispatcherAdmissionResult` (src/game/scheduling/dispatcher_policy.hpp:34-38). */
enum class DispatcherAdmissionResult {
    Accepted,
    Saturated,
    ShuttingDown,
}

/** `class DispatcherAdmissionCounter` (src/game/scheduling/dispatcher_policy.hpp:40-68). */
class DispatcherAdmissionCounter {
    private val reserved = AtomicInteger(0)

    fun tryReserve(capacity: Int): Boolean {
        var current = reserved.get()
        while (current < capacity) {
            if (reserved.compareAndSet(current, current + 1)) {
                return true
            }
            current = reserved.get()
        }
        return false
    }

    fun release(): Boolean {
        var current = reserved.get()
        while (current > 0) {
            if (reserved.compareAndSet(current, current - 1)) {
                return true
            }
            current = reserved.get()
        }
        return false
    }

    val size: Int get() = reserved.get()
}

/** `class CoalescedTaskState` (src/game/scheduling/dispatcher_policy.hpp:70-115). */
class CoalescedTaskState(private val now: () -> Long = System::currentTimeMillis) {
    private var oldestReadyAt = 0L
    private var pending = false

    fun tryEnqueue(): Boolean = tryEnqueue(now())

    fun tryEnqueue(readyAt: Long): Boolean {
        if (pending) {
            return false
        }

        pending = true
        oldestReadyAt = readyAt
        return true
    }

    val isPending: Boolean get() = pending

    fun getOldestReadyAt(): Long = oldestReadyAt

    fun consume(): Long? {
        if (!pending) {
            return null
        }

        pending = false
        val readyAt = oldestReadyAt
        oldestReadyAt = 0
        return readyAt
    }
}

/**
 * Kotlin view over a queued dispatcher task for the [DispatcherPolicy] helpers;
 * mirrors the `Task` accessors used by the C++ templates
 * (src/game/scheduling/task.hpp:51-73).
 */
interface DispatcherQueueEntry {
    val meta: TaskMeta
    val context: String
    val enqueuedAt: Long
    val readyAt: Long get() = meta.readyAt
}

/** `class DispatcherPolicy` (src/game/scheduling/dispatcher_policy.hpp:117-255). */
class DispatcherPolicy(private val nowFunction: (() -> Long)? = null) {
    fun now(): Long = nowFunction?.invoke() ?: System.currentTimeMillis()

    fun inspectQueue(tasks: Iterable<DispatcherQueueEntry>, notBefore: Long = Long.MIN_VALUE): DispatcherQueueSnapshot =
        inspectQueueAt(tasks, now(), notBefore)

    fun elapsedSince(startedAt: Long): Long = elapsed(startedAt, now())

    fun deadlineReached(deadline: Long): Boolean = now() >= deadline

    companion object {
        fun inspectQueueAt(
            tasks: Iterable<DispatcherQueueEntry>,
            currentTime: Long,
            notBefore: Long = Long.MIN_VALUE,
        ): DispatcherQueueSnapshot {
            var queued = 0
            var oldestReadyAge = 0L
            var oldestContext = ""
            for (task in tasks) {
                if (task.enqueuedAt < notBefore) {
                    continue
                }

                queued++
                val readyAge = elapsed(task.readyAt, currentTime)
                if (readyAge > oldestReadyAge) {
                    oldestReadyAge = readyAge
                    oldestContext = task.context
                }
            }
            return DispatcherQueueSnapshot(queued, oldestReadyAge, oldestContext)
        }

        fun inspectPlayerVisibleQueueAt(
            tasks: Iterable<DispatcherQueueEntry>,
            currentTime: Long,
            notBefore: Long = Long.MIN_VALUE,
        ): DispatcherQueueSnapshot {
            var queued = 0
            var oldestReadyAge = 0L
            var oldestContext = ""
            for (task in tasks) {
                if (task.enqueuedAt < notBefore || !isPlayerVisible(task.meta.lane)) {
                    continue
                }

                queued++
                val readyAge = elapsed(task.readyAt, currentTime)
                if (readyAge > oldestReadyAge) {
                    oldestReadyAge = readyAge
                    oldestContext = task.context
                }
            }
            return DispatcherQueueSnapshot(queued, oldestReadyAge, oldestContext)
        }

        fun elapsed(startedAt: Long, finishedAt: Long): Long {
            if (finishedAt <= startedAt) {
                return 0
            }
            return finishedAt - startedAt
        }

        fun classifyAdmission(accepted: Boolean, shuttingDown: Boolean): DispatcherAdmissionResult {
            if (accepted) {
                return DispatcherAdmissionResult.Accepted
            }
            return if (shuttingDown) DispatcherAdmissionResult.ShuttingDown else DispatcherAdmissionResult.Saturated
        }

        fun selectTaskCount(available: Int, budget: Int): Int = minOf(available, budget)

        fun selectProducerFairIndex(tasks: List<DispatcherQueueEntry>, lastProducerToken: Long): Int {
            if (tasks.isEmpty()) {
                return 0
            }

            var nextIndex = 0
            var wrappedIndex = 0
            var nextToken = Long.MAX_VALUE
            var wrappedToken = Long.MAX_VALUE
            var foundNext = false
            for (index in tasks.indices) {
                val producerToken = tasks[index].meta.producerToken
                if (producerToken > lastProducerToken && (!foundNext || producerToken < nextToken)) {
                    nextToken = producerToken
                    nextIndex = index
                    foundNext = true
                }
                if (producerToken <= lastProducerToken && producerToken < wrappedToken) {
                    wrappedToken = producerToken
                    wrappedIndex = index
                }
            }
            return if (foundNext) nextIndex else wrappedIndex
        }

        fun <T> requeueUnprocessed(queue: ArrayDeque<T>, slice: List<T>, consumed: Int): Int {
            if (consumed >= slice.size) {
                return 0
            }

            val unprocessed = slice.size - consumed
            for (index in slice.size downTo consumed + 1) {
                queue.addFirst(slice[index - 1])
            }
            return unprocessed
        }
    }
}

/** `class DispatcherBacklogWarningPolicy` (src/game/scheduling/dispatcher_policy.hpp:257-295). */
class DispatcherBacklogWarningPolicy {
    private var nextWarningAt = 0L
    private var consecutiveBacklogWindows = 0

    fun update(
        hasOnlinePlayers: Boolean,
        backlog: DispatcherQueueSnapshot,
        now: Long,
        warningThresholdUs: Long,
        repeatIntervalMs: Long = 30_000,
    ): Boolean {
        val threshold = maxOf(1L, warningThresholdUs)
        if (!hasOnlinePlayers || backlog.queued == 0 || backlog.oldestReadyAgeUs < threshold) {
            reset()
            return false
        }

        if (consecutiveBacklogWindows < BACKLOG_WINDOWS_TO_WARN) {
            consecutiveBacklogWindows++
        }
        if (consecutiveBacklogWindows < BACKLOG_WINDOWS_TO_WARN || now < nextWarningAt) {
            return false
        }

        nextWarningAt = now + maxOf(1L, repeatIntervalMs)
        return true
    }

    fun reset(): Unit {
        consecutiveBacklogWindows = 0
        nextWarningAt = 0
    }

    private companion object {
        const val BACKLOG_WINDOWS_TO_WARN = 4
    }
}
