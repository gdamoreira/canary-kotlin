package org.opentibia.canary.game.scheduling

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class GameDispatcherTest {

    private lateinit var dispatcher: GameDispatcher

    @BeforeEach
    fun setUp() {
        dispatcher = GameDispatcher()
        dispatcher.start()
    }

    @AfterEach
    fun tearDown() {
        dispatcher.shutdown()
    }

    @Test
    fun `executes dispatched events sequentially`() = runBlocking<Unit> {
        val counter = AtomicInteger(0)
        val doneSignal = CompletableDeferred<Unit>()

        val executionOrder = mutableListOf<Int>()

        for (i in 1..5) {
            dispatcher.dispatch(DispatcherLane.WorldCommit) {
                executionOrder.add(i)
                if (counter.incrementAndGet() == 5) {
                    doneSignal.complete(Unit)
                }
            }
        }

        doneSignal.await()
        assertThat(executionOrder).containsExactly(1, 2, 3, 4, 5)
    }

    @Test
    fun `schedules delayed event execution`() = runBlocking<Unit> {
        val signal = CompletableDeferred<Long>()
        val start = System.currentTimeMillis()

        dispatcher.schedule(delayMs = 100, lane = DispatcherLane.Maintenance) {
            val elapsed = System.currentTimeMillis() - start
            signal.complete(elapsed)
        }

        val elapsed = signal.await()
        assertThat(elapsed).isGreaterThanOrEqualTo(80)
    }

    @Test
    fun `cancels scheduled task before trigger`() = runBlocking<Unit> {
        val executed = AtomicInteger(0)

        val task = dispatcher.schedule(delayMs = 200) {
            executed.incrementAndGet()
        }

        task.cancel()
        Thread.sleep(300)

        assertThat(executed.get()).isEqualTo(0)
    }

    @Test
    fun `executes tasks from every serial lane`() = runBlocking<Unit> {
        val lanes = listOf(
            DispatcherLane.ProtocolInput,
            DispatcherLane.PlayerWalk,
            DispatcherLane.PlayerAction,
            DispatcherLane.WorldCommit,
            DispatcherLane.VisibleMonster,
            DispatcherLane.BackgroundMonster,
            DispatcherLane.Deferred,
            DispatcherLane.Maintenance,
        )
        val executed = ConcurrentHashMap.newKeySet<DispatcherLane>()
        val doneSignal = CompletableDeferred<Unit>()

        for (lane in lanes) {
            dispatcher.dispatch(lane) {
                executed.add(lane)
                if (executed.size == lanes.size) {
                    doneSignal.complete(Unit)
                }
            }
        }

        withTimeout(2_000) { doneSignal.await() }
        assertThat(executed).containsExactlyInAnyOrderElementsOf(lanes)
    }

    @Test
    fun `preserves per lane fifo order`() = runBlocking<Unit> {
        val order = mutableListOf<Int>()
        val doneSignal = CompletableDeferred<Unit>()

        for (i in 1..3) {
            dispatcher.dispatch(DispatcherLane.Maintenance) {
                order.add(i)
                if (i == 3) {
                    doneSignal.complete(Unit)
                }
            }
        }

        withTimeout(2_000) { doneSignal.await() }
        assertThat(order).containsExactly(1, 2, 3)
    }

    @Test
    fun `runs every barrier lane task and records the lane barrier telemetry`() = runBlocking<Unit> {
        val executed = ConcurrentHashMap.newKeySet<Int>()
        val doneSignal = CompletableDeferred<Unit>()

        for (i in 1..3) {
            dispatcher.dispatch(DispatcherLane.VisibleMonsterAI) {
                executed.add(i)
                if (executed.size == 3) {
                    doneSignal.complete(Unit)
                }
            }
        }

        withTimeout(2_000) { doneSignal.await() }
        assertThat(executed).containsExactlyInAnyOrder(1, 2, 3)
        assertThat(awaitBarrierUnits(dispatcher, DispatcherLane.VisibleMonsterAI, expected = 3)).isEqualTo(3)
    }

    @Test
    fun `barrier lane tasks execute in parallel`() = runBlocking<Unit> {
        val localDispatcher = GameDispatcher(barrierWorkerCount = 2)
        localDispatcher.start()
        try {
            val gate = CountDownLatch(1)
            val started = CountDownLatch(1)
            localDispatcher.dispatch(DispatcherLane.Maintenance) {
                started.countDown()
                gate.await(2, TimeUnit.SECONDS)
            }
            assertThat(started.await(2, TimeUnit.SECONDS)).isTrue()

            val rendezvous = CountDownLatch(2)
            val timedOut = AtomicBoolean(false)
            val completed = AtomicInteger(0)
            val doneSignal = CompletableDeferred<Unit>()

            repeat(2) {
                assertThat(
                    localDispatcher.addBarrierEvent(DispatcherLane.VisibleMonsterAI) {
                        rendezvous.countDown()
                        if (!rendezvous.await(2, TimeUnit.SECONDS)) {
                            timedOut.set(true)
                        }
                        if (completed.incrementAndGet() == 2) {
                            doneSignal.complete(Unit)
                        }
                    },
                ).isTrue()
            }

            gate.countDown()
            withTimeout(6_000) { doneSignal.await() }
            assertThat(timedOut.get()).isFalse()
        } finally {
            localDispatcher.shutdown()
        }
    }

    private suspend fun awaitBarrierUnits(
        target: GameDispatcher,
        lane: DispatcherLane,
        expected: Long,
    ): Long {
        var total = 0L
        withTimeout(2_000) {
            while (total < expected) {
                total += target.laneTelemetry[lane.ordinal].barrierRuntime.snapshotAndReset().workUnits
                if (total >= expected) {
                    break
                }
                delay(5)
            }
        }
        return total
    }

    @Test
    fun `barrier admission rejects a saturated lane and frees the reservation on execution`() = runBlocking<Unit> {
        val localDispatcher = GameDispatcher(barrierWorkerCount = 2)
        localDispatcher.start()
        try {
            val gate = CountDownLatch(1)
            val started = CountDownLatch(1)
            localDispatcher.dispatch(DispatcherLane.Maintenance) {
                started.countDown()
                gate.await(2, TimeUnit.SECONDS)
            }
            assertThat(started.await(2, TimeUnit.SECONDS)).isTrue()

            assertThat(localDispatcher.addBarrierEvent(DispatcherLane.GenericParallel, capacity = 1) {}).isTrue()
            assertThat(localDispatcher.addBarrierEvent(DispatcherLane.GenericParallel, capacity = 1) {}).isFalse()
            assertThat(localDispatcher.laneRejectionCount(DispatcherLane.GenericParallel)).isEqualTo(1)
            assertThat(localDispatcher.laneReservationCount(DispatcherLane.GenericParallel)).isEqualTo(1)

            gate.countDown()
            withTimeout(2_000) {
                while (!localDispatcher.addBarrierEvent(DispatcherLane.GenericParallel, capacity = 1) {}) {
                    delay(10)
                }
            }
            assertThat(localDispatcher.laneRejectionCount(DispatcherLane.GenericParallel)).isEqualTo(1)
        } finally {
            localDispatcher.shutdown()
        }
    }

    @Test
    fun `creature async events are admitted only on the creature ai lanes`() = runBlocking<Unit> {
        assertThat(dispatcher.addCreatureAsyncEvent(DispatcherLane.WorldCommit) {}).isFalse()
        assertThat(dispatcher.addCreatureAsyncEvent(DispatcherLane.VisibleMonsterAI) {}).isTrue()
        assertThat(dispatcher.addCreatureAsyncEvent(DispatcherLane.MonsterAI) {}).isTrue()
    }

    @Test
    fun `creature async slice limits default to sixteen tasks and two milliseconds`() {
        val expected = CreatureAsyncSliceLimits(tasksPerBucket = 16, maxRuntimeUs = 2_000)
        assertThat(dispatcher.getCreatureAsyncSliceLimits(playerVisible = true)).isEqualTo(expected)
        assertThat(dispatcher.getCreatureAsyncSliceLimits(playerVisible = false)).isEqualTo(expected)
    }

    @Test
    fun `isolates task exceptions`() = runBlocking<Unit> {
        val doneSignal = CompletableDeferred<Unit>()

        dispatcher.dispatch {
            throw IllegalStateException("boom")
        }
        dispatcher.dispatch {
            doneSignal.complete(Unit)
        }

        withTimeout(2_000) { doneSignal.await() }
    }

    @Test
    fun `scheduled task fires when the injected clock reaches the ready time`() = runBlocking<Unit> {
        var now = 10_000L
        val localDispatcher = GameDispatcher(clock = { now })
        localDispatcher.start()
        try {
            val firedAt = CompletableDeferred<Long>()
            localDispatcher.schedule(delayMs = 5_000) { firedAt.complete(now) }

            Thread.sleep(50)
            now += 2_500
            localDispatcher.wake()
            Thread.sleep(50)
            assertThat(firedAt.isCompleted).isFalse()

            now += 2_500
            localDispatcher.wake()
            assertThat(withTimeout(2_000) { firedAt.await() }).isEqualTo(15_000)
        } finally {
            localDispatcher.shutdown()
        }
    }

    @Test
    fun `cycle event reschedules until cancelled`() = runBlocking<Unit> {
        var now = 1_000L
        val localDispatcher = GameDispatcher(clock = { now })
        localDispatcher.start()
        try {
            val fires = AtomicInteger(0)
            val first = CompletableDeferred<Unit>()
            val second = CompletableDeferred<Unit>()
            val task = localDispatcher.cycleEvent(delayMs = 100, context = "test-cycle") {
                when (fires.incrementAndGet()) {
                    1 -> first.complete(Unit)
                    2 -> second.complete(Unit)
                }
            }

            now += 100
            localDispatcher.wake()
            withTimeout(2_000) { first.await() }

            now += 100
            localDispatcher.wake()
            withTimeout(2_000) { second.await() }

            task.cancel()
            now += 100
            localDispatcher.wake()
            Thread.sleep(50)
            assertThat(fires.get()).isEqualTo(2)
        } finally {
            localDispatcher.shutdown()
        }
    }

    @Test
    fun `monster compute completions are handed back to the dispatcher thread`() = runBlocking<Unit> {
        val service = MonsterComputeService()
        service.start(MonsterComputeConfig(configuredThreads = 1, capacity = 16, hardwareConcurrency = 8))
        val localDispatcher = GameDispatcher(monsterComputeService = service)
        localDispatcher.start()
        try {
            val dispatcherThread = CompletableDeferred<Thread>()
            localDispatcher.dispatch { dispatcherThread.complete(Thread.currentThread()) }
            val expectedThread = withTimeout(2_000) { dispatcherThread.await() }

            val completionThread = CompletableDeferred<Thread>()
            val completionRan = CompletableDeferred<Unit>()
            service.submit(
                MonsterComputePriority.Background,
                { _, _ ->
                    val completion: MonsterComputeCompletion = {
                        completionThread.complete(Thread.currentThread())
                        completionRan.complete(Unit)
                    }
                    completion
                },
                "hand-back",
            )

            withTimeout(2_000) { completionRan.await() }
            assertThat(withTimeout(2_000) { completionThread.await() }).isEqualTo(expectedThread)
        } finally {
            localDispatcher.shutdown()
            service.shutdown()
        }
    }

    @Test
    fun `records per lane runtime telemetry`() = runBlocking<Unit> {
        val doneSignal = CompletableDeferred<Unit>()

        dispatcher.dispatch(DispatcherLane.Maintenance) { doneSignal.complete(Unit) }

        withTimeout(2_000) { doneSignal.await() }
        val runtime = dispatcher.laneTelemetry[DispatcherLane.Maintenance.ordinal].taskRuntime.snapshotAndReset()
        assertThat(runtime.workUnits).isGreaterThanOrEqualTo(1)
    }
}
