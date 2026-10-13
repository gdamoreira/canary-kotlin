package org.opentibia.canary.creatures

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.map.Position

/**
 * Transcript tests for the `Creature` async-task queue basics.
 *
 * Source of truth: /Users/damoreira/.projects/canary
 *  - src/creatures/creature.hpp:819-827 (`FlagAsyncClass_t`)
 *  - src/creatures/creature.hpp:977-1076 (`addAsyncTask`, flags, safeCall)
 *  - src/creatures/creature.cpp:2148-2184 (`executeAsyncTasks`)
 *  - src/creatures/creature.cpp:2186-2193 (`sendAsyncTasks`)
 *  - src/creatures/creature.cpp:2195-2217 (`safeCall` serial path)
 */
class CreatureAsyncTaskTest {

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
    ) : Creature(id, "Async Recorder", maxHealth = 100, health = health, position = position) {
        var executeHookCalls = 0
        var pathfinderFlagDuringHook = false

        override fun onExecuteAsyncTasks() {
            executeHookCalls++
            pathfinderFlagDuringHook = hasAsyncTaskFlag(FlagAsyncClass_t.Pathfinder)
        }
    }

    @Test
    fun `addAsyncTask runs a batch immediately when no dispatcher hop is wired`() {
        val ran = mutableListOf<Int>()
        val creature = RecordingCreature()

        creature.addAsyncTask { ran.add(1) }

        assertThat(ran).containsExactly(1)
        assertThat(creature.executeHookCalls).isEqualTo(1)
        assertThat(creature.hasAsyncTaskFlag(FlagAsyncClass_t.AsyncTaskRunning)).isFalse()
    }

    @Test
    fun `queued tasks run once in submission order when the batch is pumped`() {
        val scheduler = QueuedScheduler()
        val ran = mutableListOf<Int>()
        val creature = RecordingCreature()
        creature.scheduleAsyncTaskBatch = { scheduler.schedule(it) }

        creature.addAsyncTask { ran.add(1) }
        creature.addAsyncTask { ran.add(2) }

        assertThat(ran).isEmpty()
        assertThat(scheduler.admissions).isEqualTo(1)
        assertThat(creature.hasAsyncTaskFlag(FlagAsyncClass_t.AsyncTaskRunning)).isTrue()

        scheduler.runNext()

        assertThat(ran).containsExactly(1, 2)
        assertThat(creature.hasAsyncTaskFlag(FlagAsyncClass_t.AsyncTaskRunning)).isFalse()
    }

    @Test
    fun `a task queued during execution runs in the follow-up batch`() {
        val scheduler = QueuedScheduler()
        val ran = mutableListOf<Int>()
        val creature = RecordingCreature()
        creature.scheduleAsyncTaskBatch = { scheduler.schedule(it) }

        creature.addAsyncTask {
            ran.add(1)
            creature.addAsyncTask { ran.add(2) }
        }

        scheduler.runNext()
        assertThat(ran).containsExactly(1)
        assertThat(scheduler.pending).hasSize(1)

        scheduler.runNext()
        assertThat(ran).containsExactly(1, 2)
        assertThat(creature.hasAsyncTaskFlag(FlagAsyncClass_t.AsyncTaskRunning)).isFalse()
    }

    @Test
    fun `a removed creature drops its pending tasks without running them`() {
        val scheduler = QueuedScheduler()
        val ran = mutableListOf<Int>()
        val creature = RecordingCreature()
        creature.scheduleAsyncTaskBatch = { scheduler.schedule(it) }

        creature.addAsyncTask { ran.add(1) }
        creature.setRemoved()
        scheduler.runNext()

        assertThat(ran).isEmpty()
        assertThat(creature.hasAsyncTaskFlag(FlagAsyncClass_t.AsyncTaskRunning)).isFalse()
        assertThat(creature.executeHookCalls).isZero()
    }

    @Test
    fun `a lifeless creature drops its pending tasks without running them`() {
        val scheduler = QueuedScheduler()
        val ran = mutableListOf<Int>()
        val creature = RecordingCreature(health = 0)
        creature.scheduleAsyncTaskBatch = { scheduler.schedule(it) }

        creature.addAsyncTask { ran.add(1) }
        scheduler.runNext()

        assertThat(ran).isEmpty()
        assertThat(creature.executeHookCalls).isZero()
    }

    @Test
    fun `setting a flag during execution defers it to the follow-up batch`() {
        val scheduler = QueuedScheduler()
        val creature = RecordingCreature()
        creature.scheduleAsyncTaskBatch = { scheduler.schedule(it) }

        creature.addAsyncTask { creature.setAsyncTaskFlag(FlagAsyncClass_t.Pathfinder, true) }

        scheduler.runNext()
        assertThat(creature.pathfinderFlagDuringHook).isFalse()
        assertThat(creature.hasAsyncTaskFlag(FlagAsyncClass_t.Pathfinder)).isTrue()
        assertThat(scheduler.pending).hasSize(1)

        scheduler.runNext()
        assertThat(creature.pathfinderFlagDuringHook).isTrue()
        assertThat(creature.hasAsyncTaskFlag(FlagAsyncClass_t.Pathfinder)).isFalse()
    }

    @Test
    fun `setting a flag outside execution schedules its own batch`() {
        val scheduler = QueuedScheduler()
        val creature = RecordingCreature()
        creature.scheduleAsyncTaskBatch = { scheduler.schedule(it) }

        creature.setAsyncTaskFlag(FlagAsyncClass_t.OnThink, true)

        assertThat(scheduler.admissions).isEqualTo(1)
        assertThat(creature.hasAsyncTaskFlag(FlagAsyncClass_t.AsyncTaskRunning)).isTrue()
        scheduler.runNext()
    }

    @Test
    fun `clearing a flag during execution also drops it from the deferred set`() {
        val scheduler = QueuedScheduler()
        val creature = RecordingCreature()
        creature.scheduleAsyncTaskBatch = { scheduler.schedule(it) }

        creature.addAsyncTask {
            creature.setAsyncTaskFlag(FlagAsyncClass_t.Pathfinder, true)
            creature.setAsyncTaskFlag(FlagAsyncClass_t.Pathfinder, false)
        }

        scheduler.runNext()

        assertThat(creature.hasAsyncTaskFlag(FlagAsyncClass_t.Pathfinder)).isFalse()
        assertThat(scheduler.pending).isEmpty()
    }

    @Test
    fun `safeCall runs immediately and refuses once the creature is removed`() {
        val ran = mutableListOf<Int>()
        val creature = RecordingCreature()

        assertThat(creature.safeCall { ran.add(1) }).isTrue()
        assertThat(ran).containsExactly(1)

        creature.setRemoved()
        assertThat(creature.safeCall { ran.add(2) }).isFalse()
        assertThat(ran).containsExactly(1)
    }

    @Test
    fun `isAlive mirrors the lifeless health guard`() {
        assertThat(RecordingCreature(health = 100).isAlive()).isTrue()
        assertThat(RecordingCreature(health = 0).isAlive()).isFalse()
    }
}
