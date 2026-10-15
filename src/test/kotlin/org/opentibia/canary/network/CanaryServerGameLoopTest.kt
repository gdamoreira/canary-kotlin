package org.opentibia.canary.network

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.FlagAsyncClass_t
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.game.scheduling.MonsterComputeConfig
import org.opentibia.canary.game.scheduling.MonsterComputeService
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.Spectators
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Composition tests for the wave-69 F1 game-loop driver: the [CanaryServer]
 * root binds `Game.start`'s `scheduleEvent`/`cycleEvent`/`stopEvent` seams
 * (src/game/game.cpp:862-938, 12038, 12204) to the world `GameDispatcher`'s
 * delayed execution (src/game/scheduling/dispatcher.cpp:1147-1180) and starts
 * the loop once, after the listeners are bound. The delayed execution is
 * driven through the dispatcher's injectable clock, so the cadence assertions
 * are deterministic.
 */
class CanaryServerGameLoopTest {

    private var now = 1_000_000L
    private lateinit var dispatcher: GameDispatcher
    private lateinit var service: MonsterComputeService
    private lateinit var server: CanaryServer

    @BeforeEach
    fun setUp() {
        Spectators.clearCache()
        now = 1_000_000L
        dispatcher = GameDispatcher(clock = { now })
        dispatcher.start()
        service = MonsterComputeService()
        service.start(MonsterComputeConfig(configuredThreads = 0, capacity = 16, hardwareConcurrency = 1))
        server = CanaryServer(
            loginPort = 0,
            gamePort = 0,
            dispatcher = dispatcher,
            monsterComputeService = service,
            gameMap = GameMap(),
        )
        server.game.getTimeNow = { now / 1000 }
        server.game.countPlayersOnlineValue = { 0 }
    }

    @AfterEach
    fun tearDown() {
        server.stop()
        dispatcher.shutdown()
        service.shutdown()
    }

    /** Polls [condition] until it holds; the worker runs on the dispatcher's coroutine. */
    private fun await(timeoutMs: Long = 2_000, condition: () -> Boolean) = runBlocking {
        withTimeout(timeoutMs) {
            while (!condition()) {
                delay(5)
            }
        }
    }

    /** Dispatches a checkpoint and waits until the worker has executed every earlier event. */
    private fun awaitDispatcherPass() {
        val checkpoint = CountDownLatch(1)
        dispatcher.dispatch { checkpoint.countDown() }
        assertThat(checkpoint.await(2, TimeUnit.SECONDS)).isTrue()
    }

    @Test
    fun `the composed schedule and stop seams execute through the world dispatcher`() {
        val fired = AtomicInteger(0)
        val stopped = AtomicInteger(0)

        val firstId = server.game.scheduleEvent(5_000) { fired.incrementAndGet() }
        val stoppedId = server.game.scheduleEvent(5_000) { stopped.incrementAndGet() }
        assertThat(firstId).isNotZero()
        server.game.stopEvent(stoppedId)

        now += 5_000
        dispatcher.wake()
        await { fired.get() == 1 }
        awaitDispatcherPass()

        assertThat(fired.get()).isEqualTo(1)
        assertThat(stopped.get()).isZero()
    }

    @Test
    fun `start binds the loop after the listeners and the cycle events re-arm on their interval`() {
        val gcRuns = AtomicInteger(0)
        server.game.collectLuaGarbage = { gcRuns.incrementAndGet() }

        server.start()
        try {
            assertThat(gcRuns.get()).isZero()

            now += 600_000
            dispatcher.wake()
            await { gcRuns.get() >= 1 }
            awaitDispatcherPass()
            assertThat(gcRuns.get()).isEqualTo(1)

            now += 600_000
            dispatcher.wake()
            await { gcRuns.get() >= 2 }
            awaitDispatcherPass()
            assertThat(gcRuns.get()).isEqualTo(2)
        } finally {
            server.stop()
        }
    }

    @Test
    fun `checkCreatures cadence reaches the monster host`() {
        val monster = Monster(id = 7L, name = "Rat", maxHealth = 100, health = 100, position = Position(1001, 1000, 7))
        server.game.addMonster(monster)
        monster.scheduleAsyncTaskBatch = { }
        monster.setAsyncTaskFlag(FlagAsyncClass_t.OnThink, true)
        val host = server.monsterPipelinesHost!!
        host.register(monster)
        server.game.addCreatureCheck(monster)

        server.start()
        try {
            awaitDispatcherPass()
            // `checkCreatures` rotates one of the ten buckets per fire; advance
            // the clock until the tick lands in the monster's bucket.
            await {
                now += 1_000
                dispatcher.wake()
                host.pipelineOf(monster.id)?.isPostThinkPending == true
            }

            assertThat(host.pipelineOf(monster.id)!!.isPostThinkPending).isTrue()
        } finally {
            server.stop()
        }
    }

    @Test
    fun `a repeated boot does not double-arm the cycle events`() {
        val gcRuns = AtomicInteger(0)
        server.game.collectLuaGarbage = { gcRuns.incrementAndGet() }

        server.start()
        try {
            // A second boot (`Game::start` re-entry) must not register a second
            // set of cycle events.
            server.game.start()

            now += 600_000
            dispatcher.wake()
            await { gcRuns.get() >= 1 }
            awaitDispatcherPass()

            assertThat(gcRuns.get()).isEqualTo(1)
        } finally {
            server.stop()
        }
    }
}
