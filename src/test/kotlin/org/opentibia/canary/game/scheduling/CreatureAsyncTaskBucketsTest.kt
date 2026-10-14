package org.opentibia.canary.game.scheduling

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Covers the shared creature async buckets
 * (src/creatures/creature.cpp:34-55, 2031-2146):
 * - coalesced enqueue per bucket (`Creature::enqueueAsyncTask`)
 * - per-slice budget and re-batching (`Creature::processAsyncTaskBucket`,
 *   `Dispatcher::getCreatureAsyncSliceLimits`)
 * - background -> visible promotion (creature.cpp:2091-2094)
 * - rejection rollback (`Creature::rollbackAsyncTaskBucket`)
 * - `DispatcherPolicy::requeueUnprocessed` (dispatcher_policy.hpp:236-247)
 */
class CreatureAsyncTaskBucketsTest {

    private lateinit var dispatcher: GameDispatcher
    private lateinit var buckets: CreatureAsyncTaskBuckets

    @BeforeEach
    fun setUp() {
        dispatcher = GameDispatcher(barrierWorkerCount = 2)
        buckets = CreatureAsyncTaskBuckets(dispatcher = { dispatcher })
    }

    @AfterEach
    fun tearDown() {
        dispatcher.shutdown()
    }

    /**
     * Starts the dispatcher with a blocking [DispatcherLane.Maintenance] task so
     * bucket enqueues land before the first barrier pass runs.
     */
    private fun gateDispatcher(): CountDownLatch {
        dispatcher.start()
        val gate = CountDownLatch(1)
        val started = CountDownLatch(1)
        dispatcher.dispatch(DispatcherLane.Maintenance) {
            started.countDown()
            gate.await(2, TimeUnit.SECONDS)
        }
        assertThat(started.await(2, TimeUnit.SECONDS)).isTrue()
        return gate
    }

    private suspend fun awaitBarrierUnits(lane: DispatcherLane, expected: Long): Long {
        var total = 0L
        withTimeout(2_000) {
            while (total < expected) {
                total += dispatcher.laneTelemetry[lane.ordinal].barrierRuntime.snapshotAndReset().workUnits
                if (total >= expected) {
                    break
                }
                delay(5)
            }
        }
        return total
    }

    private suspend fun awaitRequeueUnits(expected: Long): Long {
        var total = 0L
        withTimeout(2_000) {
            while (total < expected) {
                total += buckets.creatureAsyncRequeueTelemetry.snapshotAndReset().workUnits
                if (total >= expected) {
                    break
                }
                delay(5)
            }
        }
        return total
    }

    @Test
    fun `creatures sharing a bucket coalesce into one barrier event`() = runBlocking<Unit> {
        val gate = gateDispatcher()
        val executed = ConcurrentHashMap.newKeySet<Long>()
        val done = CompletableDeferred<Unit>()
        for (creatureId in longArrayOf(0, 32, 64)) {
            buckets.enqueueAsyncTask(
                creatureId = creatureId,
                task = {
                    executed.add(creatureId)
                    if (executed.size == 3) done.complete(Unit)
                },
                playerVisible = true,
            )
        }

        gate.countDown()
        withTimeout(2_000) { done.await() }

        assertThat(executed).containsExactlyInAnyOrder(0L, 32L, 64L)
        assertThat(awaitBarrierUnits(DispatcherLane.VisibleMonsterAI, expected = 1)).isEqualTo(1)
    }

    @Test
    fun `the per-bucket task budget caps the slice and requeues the remainder`() = runBlocking<Unit> {
        dispatcher.visibleCreatureAsyncSliceLimits = CreatureAsyncSliceLimits(tasksPerBucket = 2, maxRuntimeUs = 1_000_000)
        val gate = gateDispatcher()
        val executed = Collections.synchronizedList(mutableListOf<Long>())
        val done = CompletableDeferred<Unit>()
        for (creatureId in longArrayOf(0, 32, 64, 96, 128)) {
            buckets.enqueueAsyncTask(
                creatureId = creatureId,
                task = {
                    executed.add(creatureId)
                    if (executed.size == 5) done.complete(Unit)
                },
                playerVisible = true,
            )
        }

        gate.countDown()
        withTimeout(2_000) { done.await() }

        assertThat(executed).containsExactlyInAnyOrder(0L, 32L, 64L, 96L, 128L)
        assertThat(awaitBarrierUnits(DispatcherLane.VisibleMonsterAI, expected = 3)).isEqualTo(3)
        delay(50)
        assertThat(buckets.creatureAsyncRequeueTelemetry.snapshotAndReset().workUnits).isZero()
    }

    @Test
    fun `the per-bucket runtime budget re-batches the remaining creatures`() = runBlocking<Unit> {
        dispatcher.visibleCreatureAsyncSliceLimits = CreatureAsyncSliceLimits(tasksPerBucket = 16, maxRuntimeUs = 0)
        val gate = gateDispatcher()
        val executed = Collections.synchronizedList(mutableListOf<Long>())
        val done = CompletableDeferred<Unit>()
        for (creatureId in longArrayOf(0, 64, 128)) {
            buckets.enqueueAsyncTask(
                creatureId = creatureId,
                task = {
                    executed.add(creatureId)
                    if (executed.size == 3) done.complete(Unit)
                },
                playerVisible = true,
            )
        }

        gate.countDown()
        withTimeout(2_000) { done.await() }

        assertThat(executed).containsExactlyInAnyOrder(0L, 64L, 128L)
        assertThat(awaitBarrierUnits(DispatcherLane.VisibleMonsterAI, expected = 3)).isEqualTo(3)
        assertThat(awaitRequeueUnits(expected = 3)).isEqualTo(3)
    }

    @Test
    fun `a background creature that becomes player visible is promoted to the visible lane`() = runBlocking<Unit> {
        val gate = gateDispatcher()
        val executed = CompletableDeferred<Unit>()
        buckets.enqueueAsyncTask(
            creatureId = 1,
            task = { executed.complete(Unit) },
            playerVisible = false,
            isPlayerVisibleForScheduling = { true },
        )

        gate.countDown()
        withTimeout(2_000) { executed.await() }

        assertThat(awaitBarrierUnits(DispatcherLane.MonsterAI, expected = 1)).isEqualTo(1)
        assertThat(awaitBarrierUnits(DispatcherLane.VisibleMonsterAI, expected = 1)).isEqualTo(1)
    }

    @Test
    fun `bucket barrier events execute in parallel`() = runBlocking<Unit> {
        val gate = gateDispatcher()
        val rendezvous = CountDownLatch(2)
        val timedOut = AtomicBoolean(false)
        val completed = AtomicInteger(0)
        val done = CompletableDeferred<Unit>()
        val task = {
            rendezvous.countDown()
            if (!rendezvous.await(2, TimeUnit.SECONDS)) {
                timedOut.set(true)
            }
            if (completed.incrementAndGet() == 2) {
                done.complete(Unit)
            }
        }
        buckets.enqueueAsyncTask(creatureId = 0, task = task, playerVisible = true)
        buckets.enqueueAsyncTask(creatureId = 1, task = task, playerVisible = true)

        gate.countDown()
        withTimeout(6_000) { done.await() }

        assertThat(timedOut.get()).isFalse()
    }

    @Test
    fun `rejected bucket admission rolls back the schedule and clears the latch`() = runBlocking<Unit> {
        val cleared = mutableListOf<Long>()
        val ran = AtomicBoolean(false)

        buckets.enqueueAsyncTask(
            creatureId = 5,
            task = { ran.set(true) },
            playerVisible = true,
            clearAsyncTaskRunning = { cleared.add(5L) },
        )

        assertThat(cleared).containsExactly(5L)
        assertThat(ran.get()).isFalse()
        assertThat(buckets.isBucketScheduled(CreatureAsyncTaskBuckets.getCreatureAsyncTaskBucketIndex(5), true)).isFalse()

        dispatcher.start()
        val reran = CompletableDeferred<Unit>()
        buckets.enqueueAsyncTask(
            creatureId = 5,
            task = { reran.complete(Unit) },
            playerVisible = true,
            clearAsyncTaskRunning = { cleared.add(5L) },
        )
        withTimeout(2_000) { reran.await() }

        assertThat(cleared).containsExactly(5L)
    }

    @Test
    fun `bucket and requeue work feed the dispatcher internal telemetry when reachable`() = runBlocking<Unit> {
        dispatcher.setQueueLatencyLoggingEnabled(true)
        val gate = gateDispatcher()
        dispatcher.visibleCreatureAsyncSliceLimits = CreatureAsyncSliceLimits(tasksPerBucket = 16, maxRuntimeUs = 0)
        val executed = Collections.synchronizedList(mutableListOf<Long>())
        val done = CompletableDeferred<Unit>()
        for (creatureId in longArrayOf(0, 64, 128)) {
            buckets.enqueueAsyncTask(
                creatureId = creatureId,
                task = {
                    executed.add(creatureId)
                    if (executed.size == 3) done.complete(Unit)
                },
                playerVisible = true,
            )
        }

        gate.countDown()
        withTimeout(2_000) { done.await() }
        delay(50)

        assertThat(dispatcher.internalWorkSnapshot(DispatcherInternalWork.CreatureAsyncBucket).workUnits).isEqualTo(3)
        assertThat(dispatcher.internalWorkSnapshot(DispatcherInternalWork.CreatureAsyncRequeue).workUnits).isEqualTo(3)
    }

    @Test
    fun `bucket work is dropped by disabled dispatcher telemetry while the local sink records`() = runBlocking<Unit> {
        val gate = gateDispatcher()
        val done = CompletableDeferred<Unit>()
        buckets.enqueueAsyncTask(
            creatureId = 0,
            task = { done.complete(Unit) },
            playerVisible = true,
        )

        gate.countDown()
        withTimeout(2_000) { done.await() }
        delay(50)

        assertThat(dispatcher.internalWorkSnapshot(DispatcherInternalWork.CreatureAsyncBucket).empty).isTrue()
        assertThat(buckets.creatureAsyncBucketTelemetry.snapshotAndReset().workUnits).isEqualTo(1)
    }

    @Test
    fun `bucket index and lane selection follow the creature ai reservation`() {
        assertThat(CreatureAsyncTaskBuckets.getCreatureAsyncTaskBucketIndex(0)).isZero()
        assertThat(CreatureAsyncTaskBuckets.getCreatureAsyncTaskBucketIndex(31)).isEqualTo(31)
        assertThat(CreatureAsyncTaskBuckets.getCreatureAsyncTaskBucketIndex(32)).isZero()
        assertThat(CreatureAsyncTaskBuckets.getCreatureAsyncTaskLane(playerVisible = true))
            .isEqualTo(DispatcherLane.VisibleMonsterAI)
        assertThat(CreatureAsyncTaskBuckets.getCreatureAsyncTaskLane(playerVisible = false))
            .isEqualTo(DispatcherLane.MonsterAI)
    }
}
