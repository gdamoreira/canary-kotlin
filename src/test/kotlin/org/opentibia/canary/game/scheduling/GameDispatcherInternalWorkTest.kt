package org.opentibia.canary.game.scheduling

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Transcript tests for `Dispatcher::observeInternalWork`
 * (src/game/scheduling/dispatcher.cpp:378-388): observations are recorded only
 * while `queueLatencyLoggingEnabled` is set, and the worker loop feeds the
 * `DispatcherPass`/`DispatcherIdle` sinks (dispatcher.cpp:142-165).
 */
class GameDispatcherInternalWorkTest {

    @Test
    fun `internal work observations are recorded while telemetry is enabled`() {
        val dispatcher = GameDispatcher()
        try {
            dispatcher.setQueueLatencyLoggingEnabled(true)
            dispatcher.observeInternalWork(DispatcherInternalWork.MonsterPostThinkLateness, 1, 5_000, "Monster::postThink")

            val snapshot = dispatcher.internalWorkSnapshot(DispatcherInternalWork.MonsterPostThinkLateness)
            assertThat(snapshot.latency.samples).isEqualTo(1)
            assertThat(snapshot.workUnits).isEqualTo(1)
            assertThat(snapshot.latency.maxUs).isEqualTo(5_000)
        } finally {
            dispatcher.shutdown()
        }
    }

    @Test
    fun `internal work observations are dropped while telemetry is disabled`() {
        val dispatcher = GameDispatcher()
        try {
            dispatcher.observeInternalWork(DispatcherInternalWork.MonsterPostThinkLateness, 1, 5_000, "Monster::postThink")
            assertThat(dispatcher.internalWorkSnapshot(DispatcherInternalWork.MonsterPostThinkLateness).empty).isTrue()
        } finally {
            dispatcher.shutdown()
        }
    }

    @Test
    fun `the dispatcher pass observes internal work when telemetry is enabled`() = runBlocking<Unit> {
        val dispatcher = GameDispatcher()
        dispatcher.start()
        try {
            dispatcher.setQueueLatencyLoggingEnabled(true)
            val done = CompletableDeferred<Unit>()
            dispatcher.dispatch(DispatcherLane.Maintenance) { done.complete(Unit) }
            withTimeout(2_000) { done.await() }

            withTimeout(3_000) {
                while (dispatcher.internalWorkSnapshot(DispatcherInternalWork.DispatcherPass).workUnits == 0L) {
                    dispatcher.wake()
                    delay(5)
                }
            }
        } finally {
            dispatcher.shutdown()
        }
    }
}
