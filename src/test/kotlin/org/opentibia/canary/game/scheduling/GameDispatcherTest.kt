package org.opentibia.canary.game.scheduling

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
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
    fun `executes dispatched events sequentially`() = runBlocking {
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
    fun `schedules delayed event execution`() = runBlocking {
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
    fun `cancels scheduled task before trigger`() = runBlocking {
        val executed = AtomicInteger(0)

        val task = dispatcher.schedule(delayMs = 200) {
            executed.incrementAndGet()
        }

        task.cancel()
        Thread.sleep(300)

        assertThat(executed.get()).isEqualTo(0)
    }
}
