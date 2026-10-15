package org.opentibia.canary.game.scheduling

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Transcript tests for `Dispatcher::stopEvent` (dispatcher.cpp:1171-1180) and
 * the per-event cycle telemetry context names (`Dispatcher::executeScheduledTask`,
 * dispatcher.cpp:906-943; `Task::getContext`).
 *
 * The C++ `scheduledTasksRef` keeps a scheduled task registered while it is
 * promoted to a lane and (for a cycle) while its callback runs, so `stopEvent`
 * reaches a currently-executing cycle and cancels it before it re-registers
 * (dispatcher.cpp:918, 935-939). The Kotlin port must not drop that registration
 * at promotion time.
 */
class GameDispatcherCycleStopTest {

    /** Runs a checkpoint through the lane so the caller observes a completed worker pass. */
    private fun awaitDispatcherPass(dispatcher: GameDispatcher) {
        val ran = CountDownLatch(1)
        assertThat(dispatcher.dispatch(DispatcherLane.WorldCommit) { ran.countDown() }).isTrue()
        assertThat(ran.await(2, TimeUnit.SECONDS)).isTrue()
    }

    @Test
    fun `stopEvent cancels the currently-executing cycle`() = runBlocking<Unit> {
        val dispatcher = GameDispatcher()
        dispatcher.start()
        try {
            val fires = AtomicInteger(0)
            lateinit var task: ScheduledTask
            task = dispatcher.cycleEvent(delayMs = 50, context = "self-cancel-cycle") {
                fires.incrementAndGet()
                dispatcher.stopEvent(task.eventId)
            }

            withTimeout(2_000) {
                while (fires.get() < 1) {
                    delay(5)
                }
            }

            // The cycle cancelled itself from inside its own callback: the
            // reschedule must observe the cancellation and stop the chain.
            delay(250)
            assertThat(fires.get()).isEqualTo(1)
        } finally {
            dispatcher.shutdown()
        }
    }

    @Test
    fun `stopEvent cancels a promoted cycle waiting behind a blocked lane`() = runBlocking<Unit> {
        var now = 1_000L
        val dispatcher = GameDispatcher(clock = { now })
        dispatcher.start()
        try {
            val firstGate = CountDownLatch(1)
            val firstStarted = CountDownLatch(1)
            dispatcher.dispatch(DispatcherLane.WorldCommit) {
                firstStarted.countDown()
                firstGate.await(2, TimeUnit.SECONDS)
            }
            assertThat(firstStarted.await(2, TimeUnit.SECONDS)).isTrue()

            val fires = AtomicInteger(0)
            val task = dispatcher.cycleEvent(delayMs = 0, context = "promoted-cancel-cycle") {
                fires.incrementAndGet()
            }

            // Queue a second blocker while the worker is held on the first; the
            // next worker pass drains it and promotes the cycle behind it, so
            // the cycle is queued but not executing when stopEvent runs.
            now += 10
            dispatcher.wake()
            val secondGate = CountDownLatch(1)
            val secondStarted = CountDownLatch(1)
            dispatcher.dispatch(DispatcherLane.WorldCommit) {
                secondStarted.countDown()
                secondGate.await(2, TimeUnit.SECONDS)
            }
            firstGate.countDown()
            assertThat(secondStarted.await(2, TimeUnit.SECONDS)).isTrue()

            dispatcher.stopEvent(task.eventId)
            secondGate.countDown()
            now += 1_000
            dispatcher.wake()
            delay(100)

            assertThat(fires.get()).isEqualTo(0)
        } finally {
            dispatcher.shutdown()
        }
    }

    @Test
    fun `cycle execution carries the per-event context name in telemetry and the dispatcher context`() =
        runBlocking<Unit> {
            var now = 1_000L
            // The periodic log drain is gated on the game state; a non-normal
            // state keeps the sinks observable without the logger consuming them.
            val dispatcher = GameDispatcher(clock = { now }, isGameStateNormal = { false })
            dispatcher.start()
            try {
                dispatcher.setQueueLatencyLoggingEnabled(true)

                val observedContext = CompletableDeferred<DispatcherContext>()
                val fired = CompletableDeferred<Unit>()
                dispatcher.cycleEvent(delayMs = 100, context = "cycle-telemetry-name") {
                    observedContext.complete(dispatcher.context())
                    fired.complete(Unit)
                    now += 10_000
                }

                // Advance past the ready time so the queue wait (and therefore
                // the longest-context record) is positive, like the C++
                // `ConcurrentTimedWork::observe` requires.
                now += 250
                dispatcher.wake()
                val context = withTimeout(2_000) { observedContext.await() }
                withTimeout(2_000) { fired.await() }

                assertThat(context.type).isEqualTo(DispatcherType.CycleEvent)
                assertThat(context.taskName).isEqualTo("cycle-telemetry-name")

                val taskRuntime = dispatcher.laneTelemetry[DispatcherLane.WorldCommit.ordinal]
                    .taskRuntime.snapshotAndReset()
                assertThat(taskRuntime.longestContext).isEqualTo("cycle-telemetry-name")

                val scheduledLateness = dispatcher.scheduledLatenessTelemetry.snapshotAndReset()
                assertThat(scheduledLateness.longestContext).isEqualTo("cycle-telemetry-name")
            } finally {
                dispatcher.shutdown()
            }
        }

    @Test
    fun `each cycle records its own telemetry context name`() = runBlocking<Unit> {
        val now = AtomicLong(1_000)
        val dispatcher = GameDispatcher(clock = { now.get() }, isGameStateNormal = { false })
        dispatcher.start()
        try {
            dispatcher.setQueueLatencyLoggingEnabled(true)

            val alphaFired = CompletableDeferred<Unit>()
            val alpha = dispatcher.cycleEvent(delayMs = 100, context = "cycle-alpha") {
                alphaFired.complete(Unit)
                delay(20)
            }

            now.addAndGet(100)
            dispatcher.wake()
            withTimeout(2_000) { alphaFired.await() }
            awaitDispatcherPass(dispatcher)
            val alphaSnapshot = dispatcher.laneTelemetry[DispatcherLane.WorldCommit.ordinal]
                .taskRuntime.snapshotAndReset()
            assertThat(alphaSnapshot.longestContext).isEqualTo("cycle-alpha")

            // Park alpha so the next firing recorded by telemetry is beta.
            alpha.cancel()
            awaitDispatcherPass(dispatcher)
            val betaFired = CompletableDeferred<Unit>()
            dispatcher.cycleEvent(delayMs = 100, context = "cycle-beta") {
                betaFired.complete(Unit)
                delay(30)
            }
            now.addAndGet(100)
            dispatcher.wake()
            withTimeout(2_000) { betaFired.await() }
            awaitDispatcherPass(dispatcher)
            val betaSnapshot = dispatcher.laneTelemetry[DispatcherLane.WorldCommit.ordinal]
                .taskRuntime.snapshotAndReset()
            assertThat(betaSnapshot.longestContext).isEqualTo("cycle-beta")
        } finally {
            dispatcher.shutdown()
        }
    }
}
