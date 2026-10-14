package org.opentibia.canary.creatures.monsters

import org.opentibia.canary.game.scheduling.DispatcherAdmissionCounter

/**
 * Port of the anonymous-namespace `MonsterPostThinkQueue`
 * (src/game/game.cpp:85-200): the per-priority admission queue, batch drain and
 * cancellation of the deferred monster post-think ids.
 *
 * Canary guards the queue with a mutex because `Game::queueMonsterPostThink`
 * can be reached from an async worker; the Kotlin world lane is single
 * threaded (AGENTS.md 1.2), so the state is world-confined and lock-free.
 */
class MonsterPostThinkQueue(
    /** `VISIBLE_MONSTER_POST_THINK_BATCH_SIZE` / `BACKGROUND_...` (game.cpp:81-82). */
    val batchSize: Int,
) {
    /** `MonsterPostThinkQueue::monsterIds` (game.cpp:87). */
    private val monsterIds = ArrayDeque<Long>()

    /** `MonsterPostThinkQueue::admission` (game.cpp:88). */
    val admission = DispatcherAdmissionCounter()

    /** `MonsterPostThinkQueue::scheduled` (game.cpp:90). */
    var scheduled: Boolean = false
        private set

    /** `queue.monsterIds.size()` read view for telemetry/tests. */
    val size: Int get() = monsterIds.size

    /**
     * `queue.monsterIds.emplace_back(monsterId)` plus the `queue.scheduled`
     * write (game.cpp:7701-7708); returns the C++ `shouldSchedule`.
     */
    fun add(monsterId: Long): Boolean {
        monsterIds.addLast(monsterId)
        if (scheduled) {
            return false
        }
        scheduled = true
        return true
    }

    /**
     * `processMonsterPostThinkQueue` slice (game.cpp:163-190): releases the
     * admission of the drained ids and reports `shouldReschedule`.
     */
    fun drainBatch(execute: (Long) -> Unit): Boolean {
        val batch = minOf(monsterIds.size, batchSize)
        repeat(batch) {
            val monsterId = monsterIds.removeFirst()
            admission.release()
            execute(monsterId)
        }
        scheduled = monsterIds.isNotEmpty()
        return scheduled
    }

    /** `cancelMonsterPostThinkQueue` (game.cpp:129-147). */
    fun cancelAll(cancel: (Long) -> Unit) {
        while (monsterIds.isNotEmpty()) {
            val monsterId = monsterIds.removeFirst()
            admission.release()
            cancel(monsterId)
        }
        scheduled = false
    }

    companion object {
        /** `MONSTER_POST_THINK_QUEUE_CAPACITY` (game.cpp:83) = `DISPATCHER_LANE_QUEUE_CAPACITY`. */
        const val CAPACITY: Int = 16_384

        /** `VISIBLE_MONSTER_POST_THINK_BATCH_SIZE` (game.cpp:81). */
        const val VISIBLE_BATCH_SIZE: Int = 8

        /** `BACKGROUND_MONSTER_POST_THINK_BATCH_SIZE` (game.cpp:82). */
        const val BACKGROUND_BATCH_SIZE: Int = 64
    }
}
