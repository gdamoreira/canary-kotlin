package org.opentibia.canary.game.scheduling

/**
 * Port of the shared creature async task buckets (src/creatures/creature.cpp:
 * 34-55 anonymous-namespace state and the static scheduling entry points):
 * - [enqueueAsyncTask] = `Creature::enqueueAsyncTask` (creature.cpp:2031-2054):
 *   coalesce one pending entry per bucket, then admit the bucket through
 *   `Dispatcher::addCreatureAsyncEvent` on the creature AI barrier lane and roll
 *   the bucket back when admission rejects.
 * - [processAsyncTaskBucket] = `Creature::processAsyncTaskBucket`
 *   (creature.cpp:2056-2130): one bounded slice per barrier event, per-slice
 *   task/runtime limits from [GameDispatcher.getCreatureAsyncSliceLimits],
 *   background -> visible promotion, and `DispatcherPolicy::requeueUnprocessed`
 *   (dispatcher_policy.hpp:236-247) for the remainder.
 * - [rollbackAsyncTaskBucket] = `Creature::rollbackAsyncTaskBucket`
 *   (creature.cpp:2132-2146): clear the schedule and each queued owner's
 *   `AsyncTaskRunning` latch so a later batch can admit again.
 *
 * AGENTS.md deferred-callback lifetime safety: an entry never re-resolves a
 * global owner; it carries the exact owner-shaped closures captured at enqueue
 * time (the port counterpart of the C++ `weak_ptr` identity), so a removed or
 * replaced owner can neither run nor be rolled back through a successor.
 */
class CreatureAsyncTaskBuckets(
    /** `g_dispatcher()`; a `null` dispatcher processes every slice inline (embedded use). */
    private val dispatcher: () -> GameDispatcher? = { null },
    /** Slice clock, microseconds (`Task::Clock::now()`, creature.cpp:2057). */
    private val clockUs: () -> Long = { System.nanoTime() / 1_000 },
) {
    private class PendingCreature(
        val creatureId: Long,
        val task: () -> Unit,
        val isPlayerVisibleForScheduling: () -> Boolean,
        val clearAsyncTaskRunning: () -> Unit,
    )

    private class Bucket {
        val lock = Any()
        val creatures = ArrayDeque<PendingCreature>()
        var scheduled = false
    }

    private val visibleBuckets = Array(BUCKET_RESERVE) { Bucket() }
    private val backgroundBuckets = Array(BUCKET_RESERVE) { Bucket() }

    /** `DispatcherInternalWork::CreatureAsyncBucket` sink (creature.cpp:2122-2126). */
    val creatureAsyncBucketTelemetry = ConcurrentTimedWork()

    /** `DispatcherInternalWork::CreatureAsyncRequeue` sink (creature.cpp:2127-2129). */
    val creatureAsyncRequeueTelemetry = ConcurrentTimedWork()

    /**
     * `Creature::enqueueAsyncTask` (creature.cpp:2031-2054). The per-owner
     * closures represent the C++ `weak_ptr<Creature>`: [task] is the
     * generation-guarded `executeAsyncTasks` batch, [isPlayerVisibleForScheduling]
     * and [clearAsyncTaskRunning] act on that same owner identity.
     */
    fun enqueueAsyncTask(
        creatureId: Long,
        task: () -> Unit,
        playerVisible: Boolean,
        isPlayerVisibleForScheduling: () -> Boolean = { false },
        clearAsyncTaskRunning: () -> Unit = {},
    ) {
        val bucketIndex = getCreatureAsyncTaskBucketIndex(creatureId)
        val bucket = bucketFor(bucketIndex, playerVisible)
        var shouldSchedule = false
        synchronized(bucket.lock) {
            bucket.creatures.addLast(
                PendingCreature(creatureId, task, isPlayerVisibleForScheduling, clearAsyncTaskRunning),
            )
            if (!bucket.scheduled) {
                bucket.scheduled = true
                shouldSchedule = true
            }
        }

        if (shouldSchedule) {
            scheduleBucket(bucketIndex, playerVisible)
        }
    }

    /**
     * `g_dispatcher().addCreatureAsyncEvent(...)` admission plus the
     * `rollbackAsyncTaskBucket` fallback (creature.cpp:2045-2053).
     */
    private fun scheduleBucket(bucketIndex: Int, playerVisible: Boolean) {
        val dispatcher = dispatcher()
        if (dispatcher == null) {
            processAsyncTaskBucket(bucketIndex, playerVisible)
            return
        }

        val accepted = dispatcher.addCreatureAsyncEvent(getCreatureAsyncTaskLane(playerVisible)) {
            processAsyncTaskBucket(bucketIndex, playerVisible)
        }
        if (!accepted) {
            rollbackAsyncTaskBucket(bucketIndex, playerVisible)
        }
    }

    /** `Creature::processAsyncTaskBucket` (creature.cpp:2056-2130). */
    fun processAsyncTaskBucket(bucketIndex: Int, playerVisible: Boolean) {
        val startedAtUs = clockUs()
        val limits = dispatcher()?.getCreatureAsyncSliceLimits(playerVisible) ?: CreatureAsyncSliceLimits()
        val deadlineUs = startedAtUs + limits.maxRuntimeUs
        val bucket = bucketFor(bucketIndex, playerVisible)

        val pending = mutableListOf<PendingCreature>()
        var shouldReschedule = false
        synchronized(bucket.lock) {
            val batchSize = minOf(bucket.creatures.size, limits.tasksPerBucket)
            repeat(batchSize) {
                pending.add(bucket.creatures.removeFirst())
            }
            shouldReschedule = bucket.creatures.isNotEmpty()
            bucket.scheduled = shouldReschedule
        }

        var consumedCreatures = 0
        var processedCreatures = 0
        while (consumedCreatures < pending.size) {
            if (consumedCreatures > 0 && clockUs() >= deadlineUs) {
                break
            }

            val creature = pending[consumedCreatures++]
            if (!playerVisible && creature.isPlayerVisibleForScheduling()) {
                enqueueAsyncTask(
                    creatureId = creature.creatureId,
                    task = creature.task,
                    playerVisible = true,
                    isPlayerVisibleForScheduling = creature.isPlayerVisibleForScheduling,
                    clearAsyncTaskRunning = creature.clearAsyncTaskRunning,
                )
                continue
            }
            processedCreatures++
            creature.task()
        }

        var requeuedCreatures = 0
        if (consumedCreatures < pending.size) {
            synchronized(bucket.lock) {
                requeuedCreatures = DispatcherPolicy.requeueUnprocessed(bucket.creatures, pending, consumedCreatures)
                if (!bucket.scheduled) {
                    bucket.scheduled = true
                    shouldReschedule = true
                }
            }
        }

        if (shouldReschedule) {
            scheduleBucket(bucketIndex, playerVisible)
        }

        creatureAsyncBucketTelemetry.observe(clockUs() - startedAtUs, processedCreatures.toLong())
        if (requeuedCreatures > 0) {
            creatureAsyncRequeueTelemetry.observe(0, requeuedCreatures.toLong())
        }
    }

    /** `Creature::rollbackAsyncTaskBucket` (creature.cpp:2132-2146). */
    fun rollbackAsyncTaskBucket(bucketIndex: Int, playerVisible: Boolean) {
        val bucket = bucketFor(bucketIndex, playerVisible)
        val rejected = mutableListOf<PendingCreature>()
        synchronized(bucket.lock) {
            bucket.scheduled = false
            rejected.addAll(bucket.creatures)
            bucket.creatures.clear()
        }

        for (creature in rejected) {
            creature.clearAsyncTaskRunning()
        }
    }

    /** `bucket.scheduled` read view (telemetry/tests). */
    internal fun isBucketScheduled(bucketIndex: Int, playerVisible: Boolean): Boolean {
        val bucket = bucketFor(bucketIndex, playerVisible)
        return synchronized(bucket.lock) { bucket.scheduled }
    }

    private fun bucketFor(bucketIndex: Int, playerVisible: Boolean): Bucket =
        if (playerVisible) visibleBuckets[bucketIndex] else backgroundBuckets[bucketIndex]

    companion object {
        /** `DISPATCHER_CREATURE_ASYNC_BUCKET_RESERVE` (dispatcher.hpp:27). */
        const val BUCKET_RESERVE: Int = DISPATCHER_CREATURE_ASYNC_BUCKET_RESERVE

        /** `getCreatureAsyncTaskBucketIndex` (creature.cpp:52-54). */
        fun getCreatureAsyncTaskBucketIndex(creatureId: Long): Int =
            Math.floorMod(creatureId, BUCKET_RESERVE.toLong()).toInt()

        /** `getCreatureAsyncTaskLane` (creature.cpp:48-50). */
        fun getCreatureAsyncTaskLane(playerVisible: Boolean): DispatcherLane =
            if (playerVisible) DispatcherLane.VisibleMonsterAI else DispatcherLane.MonsterAI
    }
}
