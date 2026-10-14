package org.opentibia.canary.creatures.monsters

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.opentibia.canary.game.movement.Direction
import org.opentibia.canary.game.scheduling.MonsterComputeConfig
import org.opentibia.canary.game.scheduling.MonsterComputeService
import org.opentibia.canary.map.Position

/**
 * Transcribes the coalesced movement AI refresh:
 * - `Monster::queueMovementAiRefresh` (src/creatures/monsters/monster.cpp:479-497)
 * - `Monster::executeMovementAiRefresh` (src/creatures/monsters/monster.cpp:499-525)
 * - `Monster::processMovementAiRefresh` (src/creatures/monsters/monster.cpp:527-566)
 * - `Monster::deferTargetSelection` (src/creatures/monsters/monster.cpp:1347-1360)
 */
class MonsterMovementAiRefreshTest {

    private val origin = Position(100, 100, 7)
    private val oldPos = Position(105, 100, 7)
    private val newPos = Position(101, 100, 7)
    private lateinit var service: MonsterComputeService
    private lateinit var host: MonsterAsyncHost
    private lateinit var pipeline: MonsterAsyncPipelines
    private val batches = mutableListOf<() -> Unit>()
    private val selected = mutableListOf<Long>()
    private var targetListUpdates = 0
    private var idleUpdates = 0
    private val enters = mutableListOf<Long>()
    private val leaves = mutableListOf<Long>()
    private val creatures = mutableMapOf<Long, TargetCreatureFacts>()
    private var visibleFrom: Set<Position> = emptySet()

    @BeforeEach
    fun setUp() {
        service = MonsterComputeService()
        service.start(MonsterComputeConfig(configuredThreads = 0, capacity = 16, hardwareConcurrency = 1))
        host = MonsterAsyncHost()
        host.getPosition = { origin }
        host.monsterFacts = { MonsterTargetFacts(origin = origin, id = 7) }
        host.resolveCreature = { creatures[it] }
        host.selectTarget = { facts -> selected.add(facts.id); true }
        host.canSee = { position -> position in visibleFrom }
        host.addAsyncTask = { batches.add(it) }
        host.updateTargetList = { targetListUpdates++ }
        host.updateIdleStatus = { idleUpdates++ }
        host.onCreatureEnter = { enters.add(it.id) }
        host.onCreatureLeave = { leaves.add(it.id) }
        host.resolveMonster = { pipeline }
        pipeline = MonsterAsyncPipelines(
            monsterId = 7,
            service = service,
            targetState = MonsterTargetListState(),
            behaviorState = MonsterBehaviorState(),
            host = host,
        )
    }

    @AfterEach
    fun tearDown() {
        service.shutdown()
    }

    private fun creature(id: Long = 2, x: Int = 101, playerId: Long? = null): TargetCreatureFacts {
        val facts = TargetCreatureFacts(id = id, position = Position(x, 100, 7), playerId = playerId)
        creatures[id] = facts
        return facts
    }

    @Test
    fun `the first movement schedules one async batch and later movements force a full refresh`() {
        val first = creature(2, 101)
        val second = creature(3, 106)

        pipeline.queueMovementAiRefresh(first, oldPos, newPos)
        pipeline.queueMovementAiRefresh(second, Position(106, 100, 7), Position(107, 100, 7))

        assertThat(batches).hasSize(1)
        assertThat(targetListUpdates).isZero()

        batches.single().invoke()

        assertThat(targetListUpdates).isEqualTo(1)
        assertThat(idleUpdates).isEqualTo(1)
        assertThat(enters).isEmpty()
        assertThat(leaves).isEmpty()
    }

    @Test
    fun `a creature entering the view runs the enter path`() {
        visibleFrom = setOf(newPos)
        val entering = creature(2, 101)

        pipeline.queueMovementAiRefresh(entering, oldPos, newPos)
        batches.single().invoke()

        assertThat(enters).containsExactly(2L)
        assertThat(leaves).isEmpty()
    }

    @Test
    fun `a creature leaving the view runs the leave path`() {
        visibleFrom = setOf(oldPos)
        val leaving = creature(2, 101)

        pipeline.queueMovementAiRefresh(leaving, oldPos, newPos)
        batches.single().invoke()

        assertThat(leaves).containsExactly(2L)
        assertThat(enters).isEmpty()
    }

    @Test
    fun `an idle monster refreshes its idle status after the movement`() {
        host.isIdle = { true }
        val mover = creature(2, 101)

        pipeline.queueMovementAiRefresh(mover, oldPos, newPos)
        batches.single().invoke()

        assertThat(idleUpdates).isEqualTo(1)
    }

    @Test
    fun `a vanished moved creature forces a full refresh`() {
        val mover = creature(2, 101)

        pipeline.queueMovementAiRefresh(mover, oldPos, newPos)
        creatures.remove(2)
        batches.single().invoke()

        assertThat(targetListUpdates).isEqualTo(1)
        assertThat(idleUpdates).isEqualTo(1)
        assertThat(enters).isEmpty()
    }

    @Test
    fun `an opponent blocking the follow path defers the selection`() {
        creature(2, 102)
        creature(3, 100)
        host.followCreatureId = { 2L }
        host.changeTargetChance = { 1 }
        host.directionTo = { _, _ -> Direction.South }
        host.tileTopCreature = { 3L }
        host.isOpponent = { it.id == 3L }
        val mover = creature(4, 103)

        pipeline.queueMovementAiRefresh(mover, oldPos, newPos)
        batches.single().invoke()

        assertThat(selected).containsExactly(3L)
    }

    @Test
    fun `the follow creature itself on the next tile does not defer a selection`() {
        creature(2, 102)
        host.followCreatureId = { 2L }
        host.changeTargetChance = { 1 }
        host.directionTo = { _, _ -> Direction.South }
        host.tileTopCreature = { 2L }
        host.isOpponent = { it.id == 2L }
        val mover = creature(4, 103)

        pipeline.queueMovementAiRefresh(mover, oldPos, newPos)
        batches.single().invoke()

        assertThat(selected).isEmpty()
    }

    @Test
    fun `without a follow creature an opponent movement defers the selection`() {
        host.isOpponent = { it.id == 2L }
        val mover = creature(2, 101)

        pipeline.queueMovementAiRefresh(mover, oldPos, newPos)
        batches.single().invoke()

        assertThat(selected).containsExactly(2L)
    }

    @Test
    fun `a summon skips the follow and opponent refresh`() {
        host.isSummon = { true }
        host.isOpponent = { true }
        val mover = creature(2, 101)

        pipeline.queueMovementAiRefresh(mover, oldPos, newPos)
        batches.single().invoke()

        assertThat(selected).isEmpty()
    }

    @Test
    fun `a player entering the view promotes the visible lane while queuing`() {
        var promotions = 0
        host.promoteWalkEventToPlayerVisibleLane = { promotions++ }
        visibleFrom = setOf(newPos)
        val player = creature(2, 101, playerId = 2)

        pipeline.queueMovementAiRefresh(player, oldPos, newPos)

        assertThat(promotions).isEqualTo(1)
        assertThat(pipeline.visiblePlayerSpectatorIds).containsExactly(2L)
        assertThat(batches).hasSize(1)
    }

    @Test
    fun `deferTargetSelection ignores zero ids and removed monsters`() {
        pipeline.deferTargetSelection(0)
        host.isRemoved = { true }
        pipeline.deferTargetSelection(2)

        assertThat(selected).isEmpty()
    }
}
