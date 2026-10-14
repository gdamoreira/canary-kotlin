package org.opentibia.canary.creatures.monsters

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.scheduling.DispatcherLane
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.game.scheduling.MonsterComputeConfig
import org.opentibia.canary.game.scheduling.MonsterComputeService
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.SpectatorMapAccess
import org.opentibia.canary.map.SpectatorSector
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Transcript tests for the `MonsterAsyncPipelines` `safeCall` continuation
 * (`Creature::safeCall`, src/creatures/creature.cpp:2195-2217): inside a
 * barrier-parallel dispatcher context the action hops to
 * `barrierContinuationLane(context lane)` through `Dispatcher::addEvent`
 * (dispatcher.cpp:1002-1027); outside it runs inline, and the deferred callback
 * drops the action once the owner was removed (AGENTS.md deferred-callback
 * lifetime safety).
 */
class MonsterAsyncContinuationLaneTest {

    private val origin = Position(100, 100, 7)
    private lateinit var dispatcher: GameDispatcher
    private lateinit var service: MonsterComputeService
    private lateinit var host: MonsterPipelinesHost
    private lateinit var monster: Monster
    private lateinit var pipeline: MonsterAsyncPipelines
    private val creatures = mutableMapOf<Long, Creature>()

    @BeforeEach
    fun setUp() {
        dispatcher = GameDispatcher(barrierWorkerCount = 2)
        dispatcher.start()
        service = MonsterComputeService()
        service.start(MonsterComputeConfig(configuredThreads = 0, capacity = 16, hardwareConcurrency = 1))
        host = MonsterPipelinesHost(
            service = service,
            mapAccess = SpectatorMapAccess { _, _ -> SpectatorSector() },
            resolveCreature = { creatures[it] },
            clock = { 1_000L },
        )
        host.dispatcher = dispatcher
        monster = Monster(id = 7, name = "Rat", maxHealth = 100, health = 100, position = origin)
        host.register(monster)
        pipeline = host.pipelineOf(7)!!
    }

    @AfterEach
    fun tearDown() {
        dispatcher.shutdown()
        service.shutdown()
    }

    private fun addTarget() {
        creatures[2] = Player(id = 2, name = "Hero", maxHealth = 100, health = 100, position = Position(101, 100, 7))
    }

    @Test
    fun `a monster AI barrier task resumes safeCall on the deferred continuation lane`() = runBlocking<Unit> {
        addTarget()
        val continuationLane = CompletableDeferred<DispatcherLane>()
        host.entryOf(7)!!.host.selectTarget = { facts ->
            continuationLane.complete(dispatcher.context().lane)
            facts.id == 2L
        }

        assertThat(
            dispatcher.addBarrierEvent(DispatcherLane.MonsterAI) {
                pipeline.deferTargetSelection(2)
            },
        ).isTrue()

        assertThat(withTimeout(2_000) { continuationLane.await() }).isEqualTo(DispatcherLane.Deferred)
    }

    @Test
    fun `a generic parallel barrier task resumes safeCall on the world commit lane`() = runBlocking<Unit> {
        addTarget()
        val continuationLane = CompletableDeferred<DispatcherLane>()
        host.entryOf(7)!!.host.selectTarget = { facts ->
            continuationLane.complete(dispatcher.context().lane)
            facts.id == 2L
        }

        assertThat(
            dispatcher.addBarrierEvent(DispatcherLane.GenericParallel) {
                pipeline.deferTargetSelection(2)
            },
        ).isTrue()

        assertThat(withTimeout(2_000) { continuationLane.await() }).isEqualTo(DispatcherLane.WorldCommit)
    }

    @Test
    fun `a continuation for a removed monster is dropped`() = runBlocking<Unit> {
        addTarget()
        val ran = AtomicBoolean(false)
        host.entryOf(7)!!.host.selectTarget = { ran.set(true); true }
        val observed = CompletableDeferred<Unit>()

        assertThat(
            dispatcher.addBarrierEvent(DispatcherLane.MonsterAI) {
                monster.setRemoved()
                pipeline.deferTargetSelection(2)
                dispatcher.dispatch(DispatcherLane.Deferred) { observed.complete(Unit) }
            },
        ).isTrue()

        withTimeout(2_000) { observed.await() }
        assertThat(ran.get()).isFalse()
    }

    @Test
    fun `safeCall outside a barrier context runs inline with a dispatcher attached`() {
        addTarget()
        val ran = AtomicBoolean(false)
        host.entryOf(7)!!.host.selectTarget = { ran.set(true); true }

        pipeline.deferTargetSelection(2)

        assertThat(ran.get()).isTrue()
    }
}
