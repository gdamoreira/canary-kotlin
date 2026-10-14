package org.opentibia.canary.creatures.monsters

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.FlagAsyncClass_t
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.movement.Direction
import org.opentibia.canary.game.scheduling.DispatcherLane
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.game.scheduling.MonsterComputeConfig
import org.opentibia.canary.game.scheduling.MonsterComputeService
import org.opentibia.canary.map.NavCell
import org.opentibia.canary.map.NavCellFlag
import org.opentibia.canary.map.NavCellFlags
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.SpectatorMapAccess
import org.opentibia.canary.map.SpectatorSector
import org.opentibia.canary.map.Spectators
import org.opentibia.canary.map.cache.NavRegionSnapshot
import org.opentibia.canary.map.cache.NavSectorSnapshot
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Wires the remaining `Monster` seams to the [MonsterPipelinesHost] entries:
 * - `Monster::hasExtraSwing` / `Creature::checkCreatureAttack`
 *   (monster.cpp:2091-2093, creature.cpp:174-186)
 * - the `Creature::onAttacking` → `Monster::doAttacking` → `requestCombatIntention`
 *   combat path (creature.cpp:188-207, monster.cpp:1754-1767)
 * - the `Monster::onThink_async` body (monster.cpp:1708-1752)
 * - the `Monster::updateIdleStatus`/`setIdle` body (monster.cpp:1521-1556)
 * - `Monster::goToFollowCreature` over the deferred follow-path compute
 *   (creature.cpp:1058-1120, monster.cpp:3828-3855)
 * - the post-think queue lane hops (`Game::queueMonsterPostThink`,
 *   game.cpp:7694-7717 and game.cpp:150-160).
 */
class MonsterCompositionWiringTest {

    private val origin = Position(100, 100, 7)
    private val sectorIndex = (origin.x / 16) or ((origin.y / 16) shl 16)
    private lateinit var service: MonsterComputeService
    private lateinit var host: MonsterPipelinesHost
    private lateinit var monster: Monster
    private lateinit var entry: MonsterPipelinesHost.Entry
    private val creatures = mutableMapOf<Long, Creature>()
    private val sectors = mutableMapOf<Pair<Int, Int>, SpectatorSector>()
    private val batches = mutableListOf<() -> Unit>()
    private val casts = CopyOnWriteArrayList<Long>()
    private val walks = CopyOnWriteArrayList<List<Direction>>()
    private val walkEvents = CopyOnWriteArrayList<Long>()
    private val creatureChecks = CopyOnWriteArrayList<Long>()
    private val attacks = CopyOnWriteArrayList<Int>()
    private var now = 1_000L

    @BeforeEach
    fun setUp() {
        Spectators.clearCache()
        service = MonsterComputeService()
        service.start(MonsterComputeConfig(configuredThreads = 0, capacity = 16, hardwareConcurrency = 1))
        host = MonsterPipelinesHost(
            service = service,
            mapAccess = SpectatorMapAccess { x, y -> sectors[Pair(x, y)] ?: SpectatorSector() },
            resolveCreature = { creatures[it] },
            clock = { now },
        )
        host.startAutoWalk = { _, directions -> walks.add(directions) }
        host.addEventWalk = { walker -> walkEvents.add(walker.id) }
        host.addCreatureCheck = { checked -> creatureChecks.add(checked.id) }
        monster = Monster(id = 7, name = "Rat", maxHealth = 100, health = 100, position = origin)
        host.register(monster)
        entry = host.entryOf(7)!!
        entry.host.castSpell = { _, target -> casts.add(target.id) }
        monster.scheduleAsyncTaskBatch = { batch -> batches.add(batch) }
    }

    @AfterEach
    fun tearDown() {
        service.shutdown()
    }

    private fun player(id: Long, position: Position): Player {
        val player = Player(id = id, name = "Hero$id", maxHealth = 100, health = 100, position = position)
        creatures[id] = player
        val sector = sectors.getOrPut(Pair(position.x - (position.x and 15), position.y - (position.y and 15))) {
            SpectatorSector()
        }
        sector.addCreature(player)
        return player
    }

    private fun typedMonster(vararg spells: MonsterSpellBlock): MonsterType {
        val type = MonsterType("Rat")
        spells.forEach { type.info.attackSpells += SpellBlock(spell = it.spell, chance = it.chance, speed = it.speed, range = it.range, isMelee = it.isMelee) }
        host.unregister(7)
        host.register(monster, type)
        entry = host.entryOf(7)!!
        entry.host.castSpell = { _, target -> casts.add(target.id) }
        monster.scheduleAsyncTaskBatch = { batch -> batches.add(batch) }
        return type
    }

    private fun navigation(): NavRegionSnapshot {
        val cells = Array(256) { NavCell().apply { flags = NavCellFlags.of(NavCellFlag.HasGround) } }
        return NavRegionSnapshot(
            epoch = 1,
            center = origin,
            radius = MonsterAsyncPipelines.MAX_BACKGROUND_FOLLOW_PATH_RADIUS,
            sectors = listOf(NavSectorSnapshot(sectorIndex, 7, 1, 1, cells)),
        )
    }

    @Test
    fun `register binds the extra swing flag to the behavior state`() {
        assertThat(monster.hasExtraSwing()).isFalse()

        entry.behaviorState.extraMeleeAttack = true
        assertThat(monster.hasExtraSwing()).isTrue()

        entry.behaviorState.extraMeleeAttack = false
        assertThat(monster.hasExtraSwing()).isFalse()
    }

    @Test
    fun `a reused id does not let the stale owner drive the replacement hooks`() {
        host.unregister(7)
        val replacement = Monster(id = 7, name = "Rat", maxHealth = 100, health = 100, position = origin)
        val replacementBatches = mutableListOf<() -> Unit>()
        replacement.scheduleAsyncTaskBatch = { batch -> replacementBatches.add(batch) }
        host.register(replacement)
        val replacementEntry = host.entryOf(7)!!

        monster.onCreatureAppearHost(monster, true)
        monster.onCreatureMoveHost(monster, origin, Position(101, 100, 7))

        assertThat(replacementEntry.targetState.targetList).isEmpty()
        assertThat(replacement.hasAsyncTaskFlag(FlagAsyncClass_t.UpdateTargetList)).isFalse()
        assertThat(replacementBatches).isEmpty()
    }

    @Test
    fun `check creature attack runs the melee intention through the real service`() {
        typedMonster(
            MonsterSpellBlock(spell = BaseSpell { _, target -> casts.add(target.id) }, speed = 100, range = 1, isMelee = true),
        )
        val target = player(2, Position(101, 100, 7))
        monster.setAttackedCreature(target)
        entry.behaviorState.attackTicks = 100
        entry.behaviorState.extraMeleeAttack = true

        monster.checkCreatureAttack()

        assertThat(casts).isEmpty()
        assertThat(service.drainCompletions(4)).isEqualTo(1)
        assertThat(casts).containsExactly(2L)
        assertThat(entry.behaviorState.extraMeleeAttack).isFalse()
    }

    @Test
    fun `the host attack seam drives the combat intention through the service`() {
        typedMonster(
            MonsterSpellBlock(spell = BaseSpell { _, target -> casts.add(target.id) }, speed = 100, range = 5),
        )
        val target = player(2, Position(102, 100, 7))
        monster.setAttackedCreature(target)
        entry.behaviorState.attackTicks = 100

        entry.host.onAttacking(100)

        assertThat(casts).isEmpty()
        service.drainCompletions(4)
        assertThat(casts).containsExactly(2L)
        assertThat(entry.behaviorState.attackTicks).isZero()
    }

    @Test
    fun `the async think body drives the target search and the deferred post think`() {
        typedMonster()
        val target = player(2, Position(101, 100, 7))
        player(3, Position(105, 100, 7))
        entry.targetState.targetList.add(MonsterTargetReference(target.id))
        entry.targetState.targetList.add(MonsterTargetReference(3))
        entry.behaviorState.isIdle = false

        monster.setAsyncTaskFlag(FlagAsyncClass_t.OnThink, true)
        host.trySchedulePostThink(monster)

        host.onExecuteAsyncTasks(7)

        assertThat(walkEvents).containsExactly(7L)
        assertThat(entry.pipeline.targetSearchComputeOutstanding).isTrue()

        service.drainCompletions(4)

        assertThat(monster.getAttackedCreature()?.id).isEqualTo(2L)
        assertThat(monster.getFollowCreature()?.id).isEqualTo(2L)
        assertThat(walks).isEmpty()
    }

    @Test
    fun `the async think body queues the post think and executes it through the real queue`() {
        typedMonster()
        val target = player(2, Position(101, 100, 7))
        monster.setAttackedCreature(target)
        entry.behaviorState.isIdle = true
        entry.behaviorState.attackTicks = 0
        entry.host.onAttacking = { interval -> attacks.add(interval); entry.behaviorState.attackTicks += interval }

        monster.setAsyncTaskFlag(FlagAsyncClass_t.OnThink, true)
        host.trySchedulePostThink(monster)

        host.onExecuteAsyncTasks(7)

        assertThat(attacks).containsExactly(MonsterPipelinesHost.EVENT_CREATURE_THINK_INTERVAL)
        assertThat(entry.behaviorState.attackTicks).isEqualTo(MonsterPipelinesHost.EVENT_CREATURE_THINK_INTERVAL)
        assertThat(entry.pipeline.postThinkQueued).isFalse()
        assertThat(host.queuedPostThinkCount(playerVisible = false)).isZero()
    }

    @Test
    fun `the idle status body idles a targetless monster and wakes a targeted one`() {
        host.addCreatureCheck = { checked -> creatureChecks.add(checked.id) }
        entry.behaviorState.isIdle = false

        entry.host.updateIdleStatus()
        assertThat(entry.behaviorState.isIdle).isTrue()

        entry.targetState.targetList.add(MonsterTargetReference(2))
        entry.behaviorState.isIdle = true

        entry.host.updateIdleStatus()

        assertThat(entry.behaviorState.isIdle).isFalse()
        assertThat(creatureChecks).containsExactly(7L)
    }

    @Test
    fun `goToFollowCreature requests the follow path compute and applies the result`() {
        host.navigationSnapshot = { _, _ -> navigation() }
        val target = player(2, Position(100, 103, 7))
        monster.setFollowCreature(target)

        entry.host.goToFollowCreatureAsync()

        assertThat(entry.pipeline.followPathComputeOutstanding).isTrue()

        service.drainCompletions(4)

        assertThat(walks).containsExactly(listOf(Direction.South, Direction.South))
        assertThat(monster.hasFollowPath).isTrue()
        assertThat(entry.pipeline.followPathComputeOutstanding).isFalse()
    }

    @Test
    fun `goToFollowCreature skips the compute when the current position satisfies the follow`() {
        var satisfies = 0
        host.pathSatisfiesFollow = { _, _, _ -> satisfies++; true }
        val target = player(2, Position(100, 101, 7))
        monster.setFollowCreature(target)

        entry.host.goToFollowCreatureAsync()

        assertThat(satisfies).isEqualTo(1)
        assertThat(entry.pipeline.followPathComputeOutstanding).isFalse()
        assertThat(walks).containsExactly(emptyList<Direction>())
        assertThat(monster.hasFollowPath).isTrue()
    }

    @Test
    fun `a stopped dispatcher rejects the post think queue schedule and cancels it`() {
        host.dispatcher = GameDispatcher()
        monster.setAsyncTaskFlag(FlagAsyncClass_t.OnThink, true)
        host.trySchedulePostThink(monster)

        entry.pipeline.queuePostThinkAfterAsync()

        assertThat(entry.pipeline.postThinkQueued).isFalse()
        assertThat(entry.pipeline.isPostThinkPending).isFalse()
        assertThat(host.queuedPostThinkCount(playerVisible = false)).isZero()
    }

    @Test
    fun `the background post think hops through the Deferred lane`() = runBlocking<Unit> {
        val dispatcher = GameDispatcher()
        dispatcher.start()
        host.dispatcher = dispatcher
        try {
            monster.setAsyncTaskFlag(FlagAsyncClass_t.OnThink, true)
            host.trySchedulePostThink(monster)
            val done = CompletableDeferred<Unit>()
            entry.host.onAttacking = { interval -> attacks.add(interval); done.complete(Unit) }

            entry.pipeline.queuePostThinkAfterAsync()

            withTimeout(2_000) { done.await() }

            assertThat(attacks).containsExactly(MonsterPipelinesHost.EVENT_CREATURE_THINK_INTERVAL)
            val deferred = dispatcher.laneTelemetry[DispatcherLane.Deferred.ordinal].taskRuntime.snapshotAndReset()
            val worldCommit = dispatcher.laneTelemetry[DispatcherLane.WorldCommit.ordinal].taskRuntime.snapshotAndReset()
            assertThat(deferred.latency.samples).isEqualTo(1)
            assertThat(worldCommit.latency.samples).isZero()
        } finally {
            dispatcher.shutdown()
        }
    }

    @Test
    fun `the player visible post think hops through the WorldCommit lane`() = runBlocking<Unit> {
        val dispatcher = GameDispatcher()
        dispatcher.start()
        host.dispatcher = dispatcher
        try {
            entry.pipeline.addVisiblePlayerSpectator(TargetCreatureFacts(id = 2, position = origin, playerId = 2))
            monster.setAsyncTaskFlag(FlagAsyncClass_t.OnThink, true)
            host.trySchedulePostThink(monster)
            val done = CompletableDeferred<Unit>()
            entry.host.onAttacking = { interval -> attacks.add(interval); done.complete(Unit) }

            entry.pipeline.queuePostThinkAfterAsync()

            withTimeout(2_000) { done.await() }

            assertThat(attacks).containsExactly(MonsterPipelinesHost.EVENT_CREATURE_THINK_INTERVAL)
            val worldCommit = dispatcher.laneTelemetry[DispatcherLane.WorldCommit.ordinal].taskRuntime.snapshotAndReset()
            val deferred = dispatcher.laneTelemetry[DispatcherLane.Deferred.ordinal].taskRuntime.snapshotAndReset()
            assertThat(worldCommit.latency.samples).isEqualTo(1)
            assertThat(deferred.latency.samples).isZero()
        } finally {
            dispatcher.shutdown()
        }
    }
}
