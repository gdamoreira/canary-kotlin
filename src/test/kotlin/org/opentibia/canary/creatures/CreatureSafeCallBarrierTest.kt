package org.opentibia.canary.creatures

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.game.scheduling.DispatcherLane
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.map.Position
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.CoroutineContext

/**
 * Transcript tests for the `Creature::safeCall` barrier continuation
 * (src/creatures/creature.cpp:2195-2217): inside a barrier-parallel dispatcher
 * context the action hops to `barrierContinuationLane(context lane)` through
 * `Dispatcher::addEvent` (dispatcher.cpp:1002-1027); outside it runs inline and
 * refuses once the creature was internally removed.
 */
class CreatureSafeCallBarrierTest {

    private class TestCreature : Creature(
        id = 42,
        name = "SafeCall Creature",
        maxHealth = 100,
        health = 100,
        position = Position(100, 100, 7),
    )

    private class FreshThreadDispatcher : CoroutineDispatcher() {
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            Thread(block, "safe-call-context-test").start()
        }
    }

    @Test
    fun `safeCall inside a barrier task hops to the barrier continuation lane`() = runBlocking<Unit> {
        val dispatcher = GameDispatcher(barrierWorkerCount = 2)
        dispatcher.start()
        val creature = TestCreature()
        creature.asyncTaskDispatcher = dispatcher
        try {
            val continuationLane = CompletableDeferred<DispatcherLane>()
            val accepted = dispatcher.addBarrierEvent(DispatcherLane.GenericParallel) {
                val admitted = creature.safeCall {
                    continuationLane.complete(dispatcher.context().lane)
                }
                assertThat(admitted).isTrue()
            }
            assertThat(accepted).isTrue()

            assertThat(withTimeout(2_000) { continuationLane.await() }).isEqualTo(DispatcherLane.WorldCommit)
        } finally {
            dispatcher.shutdown()
        }
    }

    @Test
    fun `a monster AI barrier task continues on the deferred lane`() = runBlocking<Unit> {
        val dispatcher = GameDispatcher(barrierWorkerCount = 2)
        dispatcher.start()
        val creature = TestCreature()
        creature.asyncTaskDispatcher = dispatcher
        try {
            val continuationLane = CompletableDeferred<DispatcherLane>()
            assertThat(
                dispatcher.addBarrierEvent(DispatcherLane.MonsterAI) {
                    creature.safeCall { continuationLane.complete(dispatcher.context().lane) }
                },
            ).isTrue()

            assertThat(withTimeout(2_000) { continuationLane.await() }).isEqualTo(DispatcherLane.Deferred)
        } finally {
            dispatcher.shutdown()
        }
    }

    @Test
    fun `safeCall after a suspension inside a barrier task still hops to the continuation lane`() = runBlocking<Unit> {
        val dispatcher = GameDispatcher(barrierDispatcher = FreshThreadDispatcher(), barrierWorkerCount = 2)
        dispatcher.start()
        val creature = TestCreature()
        creature.asyncTaskDispatcher = dispatcher
        try {
            val marker = CompletableDeferred<Unit>()
            val continuationLane = CompletableDeferred<DispatcherLane>()
            assertThat(
                dispatcher.dispatch(DispatcherLane.GenericParallel, "safe-call-marker") { marker.complete(Unit) },
            ).isTrue()
            assertThat(
                dispatcher.dispatch(DispatcherLane.GenericParallel, "safe-call-after-suspend") {
                    delay(20)
                    val admitted = creature.safeCall { continuationLane.complete(dispatcher.context().lane) }
                    assertThat(admitted).isTrue()
                },
            ).isTrue()

            withTimeout(2_000) { marker.await() }
            assertThat(withTimeout(2_000) { continuationLane.await() }).isEqualTo(DispatcherLane.WorldCommit)
        } finally {
            dispatcher.shutdown()
        }
    }

    @Test
    fun `a continuation for a removed creature is dropped`() = runBlocking<Unit> {
        val dispatcher = GameDispatcher(barrierWorkerCount = 2)
        dispatcher.start()
        val creature = TestCreature()
        creature.asyncTaskDispatcher = dispatcher
        try {
            val ran = AtomicBoolean(false)
            val observed = CompletableDeferred<Unit>()
            assertThat(
                dispatcher.addBarrierEvent(DispatcherLane.GenericParallel) {
                    creature.setRemoved()
                    creature.safeCall { ran.set(true) }
                    dispatcher.dispatch(DispatcherLane.WorldCommit) { observed.complete(Unit) }
                },
            ).isTrue()

            withTimeout(2_000) { observed.await() }
            assertThat(ran.get()).isFalse()
        } finally {
            dispatcher.shutdown()
        }
    }

    @Test
    fun `safeCall outside a barrier context runs inline and refuses a removed creature`() {
        val creature = TestCreature()
        val ran = AtomicBoolean(false)

        assertThat(creature.safeCall { ran.set(true) }).isTrue()
        assertThat(ran.get()).isTrue()

        creature.setRemoved()
        assertThat(creature.safeCall { ran.set(false) }).isFalse()
        assertThat(ran.get()).isTrue()
    }
}
