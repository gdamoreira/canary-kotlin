package org.opentibia.canary.creatures

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.map.Position

/**
 * Transcript tests for the asynchronous follow pipeline.
 *
 * Source of truth: /Users/damoreira/.projects/canary
 *  - src/creatures/creature.hpp:412-416 (`updateCreatureWalk`,
 *    `goToFollowCreature_async`, `goToFollowCreature`)
 *  - src/creatures/creature.cpp:167 (`goToFollowCreature_async(onThink)`)
 *  - src/creatures/creature.cpp:1046-1056 (`goToFollowCreature_async`)
 *  - src/creatures/creature.cpp:2148-2184 (`executeAsyncTasks` Pathfinder branch)
 *  - src/creatures/monsters/monster.cpp:3771 (`discardFollowPathCompute` refresh)
 *
 * The dispatcher barrier is the port's `scheduleAsyncTaskBatch` /
 * `scheduleAsyncCallback` seam; deferred work is validated against the owner's
 * async generation so a removed or replaced owner can never run stale work
 * (AGENTS.md deferred-callback lifetime safety).
 */
class CreatureFollowAsyncTest {

    private class QueuedScheduler {
        val pending = ArrayDeque<() -> Unit>()
        var admissions = 0

        fun schedule(task: () -> Unit) {
            admissions++
            pending.addLast(task)
        }

        fun runNext() {
            pending.removeFirst().invoke()
        }
    }

    private class RecordingCreature(
        id: Long = 1,
        health: Int = 100,
        position: Position = Position(100, 100, 7),
    ) : Creature(id, "Async Follow Recorder", maxHealth = 100, health = health, position = position) {
        var followRuns = 0

        override fun goToFollowCreature() {
            followRuns++
        }
    }

    @Test
    fun `updateCreatureWalk queues the pathfinder batch and reruns follow once`() {
        // Creature::updateCreatureWalk -> goToFollowCreature_async (creature.hpp:412-414).
        val batch = QueuedScheduler()
        val creature = RecordingCreature()
        creature.scheduleAsyncTaskBatch = { batch.schedule(it) }

        creature.updateCreatureWalk()

        assertThat(creature.hasAsyncTaskFlag(FlagAsyncClass_t.Pathfinder)).isTrue()
        assertThat(creature.followRuns).isZero()
        assertThat(batch.pending).hasSize(1)

        batch.runNext()

        // executeAsyncTasks' Pathfinder branch (creature.cpp:2162-2164).
        assertThat(creature.followRuns).isEqualTo(1)
        assertThat(creature.hasAsyncTaskFlag(FlagAsyncClass_t.Pathfinder)).isFalse()
        assertThat(creature.hasAsyncTaskFlag(FlagAsyncClass_t.AsyncTaskRunning)).isFalse()
    }

    @Test
    fun `a dead creature refuses the async follow request`() {
        // creature.cpp:1047-1049: the isDead guard precedes every side effect.
        val batch = QueuedScheduler()
        val callbacks = QueuedScheduler()
        val creature = RecordingCreature(health = 0)
        creature.scheduleAsyncTaskBatch = { batch.schedule(it) }
        creature.scheduleAsyncCallback = { callbacks.schedule(it) }

        creature.goToFollowCreature_async { creature.followRuns += 100 }

        assertThat(creature.hasAsyncTaskFlag(FlagAsyncClass_t.Pathfinder)).isFalse()
        assertThat(batch.pending).isEmpty()
        assertThat(callbacks.pending).isEmpty()
    }

    @Test
    fun `the completion barrier is queued only while no pathfinder task is pending`() {
        // creature.cpp:1051-1053: `!hasAsyncTaskFlag(Pathfinder) && onComplete`.
        val batch = QueuedScheduler()
        val callbacks = QueuedScheduler()
        var completions = 0
        val creature = RecordingCreature()
        creature.scheduleAsyncTaskBatch = { batch.schedule(it) }
        creature.scheduleAsyncCallback = { callbacks.schedule(it) }

        creature.goToFollowCreature_async { completions++ }
        creature.goToFollowCreature_async { completions++ }

        assertThat(callbacks.pending).hasSize(1)
        callbacks.runNext()
        assertThat(completions).isEqualTo(1)
    }

    @Test
    fun `a queued completion is stale once the owner is removed`() {
        val batch = QueuedScheduler()
        val callbacks = QueuedScheduler()
        var completions = 0
        val creature = RecordingCreature()
        creature.scheduleAsyncTaskBatch = { batch.schedule(it) }
        creature.scheduleAsyncCallback = { callbacks.schedule(it) }

        creature.goToFollowCreature_async { completions++ }
        creature.setRemoved()

        callbacks.runNext()
        batch.runNext()

        assertThat(completions).isZero()
        assertThat(creature.followRuns).isZero()
    }

    @Test
    fun `a replaced owner cannot run the stale batch or completion of its predecessor`() {
        // Reused ids / re-login replacements: the predecessor's scheduled work
        // must be generation-invalidated even though a live owner now exists.
        val oldBatch = QueuedScheduler()
        val oldCallbacks = QueuedScheduler()
        val old = RecordingCreature(id = 7)
        old.scheduleAsyncTaskBatch = { oldBatch.schedule(it) }
        old.scheduleAsyncCallback = { oldCallbacks.schedule(it) }
        var oldCompletions = 0

        old.goToFollowCreature_async { oldCompletions++ }
        old.setRemoved()

        val replacement = RecordingCreature(id = 7)
        replacement.goToFollowCreature_async()

        oldCallbacks.runNext()
        oldBatch.runNext()

        assertThat(oldCompletions).isZero()
        assertThat(old.followRuns).isZero()
        assertThat(replacement.followRuns).isEqualTo(1)
    }

    @Test
    fun `an inline dispatcher keeps the synchronous walk behavior`() {
        val creature = RecordingCreature()

        creature.updateCreatureWalk()

        assertThat(creature.followRuns).isEqualTo(1)
        assertThat(creature.hasAsyncTaskFlag(FlagAsyncClass_t.Pathfinder)).isFalse()
    }
}
