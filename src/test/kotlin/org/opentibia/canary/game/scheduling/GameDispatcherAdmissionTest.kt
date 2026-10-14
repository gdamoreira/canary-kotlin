package org.opentibia.canary.game.scheduling

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Transcript tests for the serial/scheduled lane admission reservation
 * (`Dispatcher::addEvent`/`reserveDispatcherSlot`,
 * src/game/scheduling/dispatcher.cpp:750-762, 1002-1027, 1147-1177) and the
 * `stopEvent` release (dispatcher.cpp:1171-1179).
 */
class GameDispatcherAdmissionTest {

    @Test
    fun `a serial event reserves its lane admission until it executes`() = runBlocking<Unit> {
        val dispatcher = GameDispatcher()
        dispatcher.start()
        try {
            val gate = CountDownLatch(1)
            val started = CountDownLatch(1)
            assertThat(
                dispatcher.dispatch(DispatcherLane.Maintenance) {
                    started.countDown()
                    gate.await(2, TimeUnit.SECONDS)
                },
            ).isTrue()
            assertThat(started.await(2, TimeUnit.SECONDS)).isTrue()

            assertThat(dispatcher.dispatch(DispatcherLane.ProtocolInput, capacity = 1) {}).isTrue()
            assertThat(dispatcher.dispatch(DispatcherLane.ProtocolInput, capacity = 1) {}).isFalse()
            assertThat(dispatcher.laneReservationCount(DispatcherLane.ProtocolInput)).isEqualTo(1)
            assertThat(dispatcher.laneRejectionCount(DispatcherLane.ProtocolInput)).isEqualTo(1)

            gate.countDown()
            withTimeout(2_000) {
                while (dispatcher.laneReservationCount(DispatcherLane.ProtocolInput) > 0) {
                    delay(5)
                }
            }
            assertThat(dispatcher.dispatch(DispatcherLane.ProtocolInput, capacity = 1) {}).isTrue()
        } finally {
            dispatcher.shutdown()
        }
    }

    @Test
    fun `a scheduled event holds admission until execution and releases on cancel`() = runBlocking<Unit> {
        var now = 1_000L
        val dispatcher = GameDispatcher(clock = { now })
        dispatcher.start()
        try {
            val first = dispatcher.schedule(delayMs = 10_000, lane = DispatcherLane.Deferred, capacity = 1) {}
            assertThat(dispatcher.laneReservationCount(DispatcherLane.Deferred)).isEqualTo(1)

            val rejected = dispatcher.schedule(delayMs = 10_000, lane = DispatcherLane.Deferred, capacity = 1) {}
            assertThat(dispatcher.laneRejectionCount(DispatcherLane.Deferred)).isEqualTo(1)

            rejected.cancel()
            assertThat(dispatcher.laneReservationCount(DispatcherLane.Deferred)).isEqualTo(1)

            first.cancel()
            assertThat(dispatcher.laneReservationCount(DispatcherLane.Deferred)).isEqualTo(0)

            val fired = CompletableDeferred<Unit>()
            val executed = dispatcher.schedule(delayMs = 100, lane = DispatcherLane.Deferred, capacity = 1) {
                fired.complete(Unit)
            }
            assertThat(dispatcher.laneReservationCount(DispatcherLane.Deferred)).isEqualTo(1)

            now += 100
            dispatcher.wake()
            withTimeout(2_000) { fired.await() }
            withTimeout(2_000) {
                while (dispatcher.laneReservationCount(DispatcherLane.Deferred) > 0) {
                    delay(5)
                }
            }
            executed.cancel()
        } finally {
            dispatcher.shutdown()
        }
    }
}
