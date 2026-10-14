package org.opentibia.canary.game.scheduling

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.coroutines.CoroutineContext

/**
 * Transcript tests for the `ThreadLocal` dispatcher context across suspending
 * tasks: `Dispatcher::context()` (dispatcher.hpp:178-182) must still describe
 * the running task after a suspension resumed it on a different thread, exactly
 * like the thread-local C++ `dispacherContext` (dispatcher.cpp:23,
 * 226-265, 295-310). The barrier fan-out runs every non-first partition on the
 * injected [CoroutineDispatcher], so a fresh-thread dispatcher makes the
 * resumption thread different from the entry thread deterministically.
 */
class GameDispatcherContextTest {

    private class FreshThreadDispatcher : CoroutineDispatcher() {
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            Thread(block, "dispatcher-context-test").start()
        }
    }

    @Test
    fun `the barrier task context survives a suspension on a fresh thread`() = runBlocking<Unit> {
        val dispatcher = GameDispatcher(barrierDispatcher = FreshThreadDispatcher(), barrierWorkerCount = 2)
        dispatcher.start()
        try {
            val marker = CompletableDeferred<Unit>()
            val observed = CompletableDeferred<DispatcherContext>()
            assertThat(
                dispatcher.dispatch(DispatcherLane.GenericParallel, "context-marker") { marker.complete(Unit) },
            ).isTrue()
            assertThat(
                dispatcher.dispatch(DispatcherLane.GenericParallel, "context-after-suspend") {
                    delay(20)
                    observed.complete(dispatcher.context())
                },
            ).isTrue()

            withTimeout(2_000) { marker.await() }
            val context = withTimeout(2_000) { observed.await() }
            assertThat(context.type).isEqualTo(DispatcherType.AsyncEvent)
            assertThat(context.lane).isEqualTo(DispatcherLane.GenericParallel)
            assertThat(context.executionMode).isEqualTo(ExecutionMode.BarrierParallel)
            assertThat(context.taskName).isEqualTo("context-after-suspend")
        } finally {
            dispatcher.shutdown()
        }
    }

    @Test
    fun `the serial task context survives a suspension`() = runBlocking<Unit> {
        val dispatcher = GameDispatcher()
        dispatcher.start()
        try {
            val observed = CompletableDeferred<DispatcherContext>()
            assertThat(
                dispatcher.dispatch(DispatcherLane.WorldCommit, "serial-after-suspend") {
                    delay(20)
                    observed.complete(dispatcher.context())
                },
            ).isTrue()

            val context = withTimeout(2_000) { observed.await() }
            assertThat(context.type).isEqualTo(DispatcherType.Event)
            assertThat(context.lane).isEqualTo(DispatcherLane.WorldCommit)
            assertThat(context.executionMode).isEqualTo(ExecutionMode.Serial)
            assertThat(context.taskName).isEqualTo("serial-after-suspend")
        } finally {
            dispatcher.shutdown()
        }
    }

    @Test
    fun `the worker completion lane context covers the drained completions`() = runBlocking<Unit> {
        val service = MonsterComputeService()
        service.start(MonsterComputeConfig(configuredThreads = 1, capacity = 16, hardwareConcurrency = 8))
        val dispatcher = GameDispatcher(monsterComputeService = service)
        dispatcher.start()
        try {
            val observed = CompletableDeferred<DispatcherContext>()
            service.submit(
                MonsterComputePriority.Background,
                { _, _ ->
                    {
                        observed.complete(dispatcher.context())
                    }
                },
                "completion-context",
            )

            val context = withTimeout(2_000) { observed.await() }
            assertThat(context.type).isEqualTo(DispatcherType.WorkerCompletion)
            assertThat(context.lane).isEqualTo(DispatcherLane.WorkerCompletion)
            assertThat(context.executionMode).isEqualTo(ExecutionMode.BackgroundCompletion)
        } finally {
            dispatcher.shutdown()
            service.shutdown()
        }
    }
}
