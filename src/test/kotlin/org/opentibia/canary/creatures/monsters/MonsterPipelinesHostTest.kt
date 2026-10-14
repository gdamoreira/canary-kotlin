package org.opentibia.canary.creatures.monsters

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.FlagAsyncClass_t
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.scheduling.MonsterComputeConfig
import org.opentibia.canary.game.scheduling.MonsterComputeService
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.SpectatorMapAccess
import org.opentibia.canary.map.SpectatorSector
import org.opentibia.canary.map.Spectators

/**
 * Wires the deferred [MonsterAsyncPipelines] into the host entity:
 * - pipeline instantiation/ownership (`Monster`/`Game::checkCreatures`, game.cpp:7719-7741)
 * - `Monster::trySchedulePostThink` (monster.cpp:1633-1638)
 * - the `Game` monster post-think queues (`Game::queueMonsterPostThink`,
 *   game.cpp:7694-7717 and the anonymous-namespace queue, game.cpp:85-200)
 * - `Monster::updateTargetList` spectator scan (monster.cpp:735-770)
 * - `Monster::onCreatureAppear` (monster.cpp:348-389)
 * - `Monster::onRemoveCreature` (monster.cpp:391-432)
 * - the dispatcher lateness telemetry (`Monster::executePostThink`
 *   monster.cpp:1668 and `Monster::executeMovementAiRefresh` monster.cpp:513)
 *
 * The host is exercised through the real [Monster]/[Player] entities and the
 * real inline [MonsterComputeService]; the async task batch is captured by the
 * test so the coalesced dispatcher hop stays observable.
 */
class MonsterPipelinesHostTest {

    private val origin = Position(100, 100, 7)
    private lateinit var service: MonsterComputeService
    private lateinit var host: MonsterPipelinesHost
    private lateinit var monster: Monster
    private lateinit var entry: MonsterPipelinesHost.Entry
    private val creatures = mutableMapOf<Long, Creature>()
    private val sectors = mutableMapOf<Pair<Int, Int>, SpectatorSector>()
    private val batches = mutableListOf<() -> Unit>()
    private val attackTicks = mutableListOf<Int>()
    private val conditionTicks = mutableListOf<Int>()
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
        monster = Monster(id = 7, name = "Rat", maxHealth = 100, health = 100, position = origin)
        host.register(monster)
        entry = host.entryOf(7)!!
        entry.host.onAttacking = { attackTicks.add(it) }
        entry.host.executeConditions = { conditionTicks.add(it) }
        monster.scheduleAsyncTaskBatch = { batches.add(it) }
    }

    @AfterEach
    fun tearDown() {
        service.shutdown()
    }

    private fun player(id: Long, position: Position): Player {
        val player = Player(id = id, name = "Hero$id", maxHealth = 100, health = 100, position = position)
        creatures[id] = player
        val sector = sectors.getOrPut(Pair(position.x - (position.x and 15), position.y - (position.y and 15))) { SpectatorSector() }
        sector.addCreature(player)
        return player
    }

    private fun addTarget(id: Long, x: Int = 101) {
        creatures.getOrPut(id) { Player(id = id, name = "Hero$id", maxHealth = 100, health = 100, position = Position(x, 100, 7)) }
        entry.targetState.targetList.add(MonsterTargetReference(id))
    }

    @Test
    fun `register binds the pipelines to the owning monster and resolves it by id`() {
        assertThat(host.pipelineOf(7)).isSameAs(entry.pipeline)
        assertThat(entry.pipeline.monsterId).isEqualTo(7L)
        assertThat(entry.host.getPosition()).isEqualTo(origin)
        assertThat(entry.host.isRemoved()).isFalse()
        assertThat(entry.host.resolveMonster(7)).isSameAs(entry.pipeline)
        assertThat(entry.host.resolveCreature(99L)).isNull()
    }

    @Test
    fun `the checkCreatures host seam schedules one coalesced post think`() {
        assertThat(host.trySchedulePostThink(monster)).isFalse()

        monster.setAsyncTaskFlag(FlagAsyncClass_t.OnThink, true)
        assertThat(host.trySchedulePostThink(monster)).isTrue()
        assertThat(host.trySchedulePostThink(monster)).isFalse()
        assertThat(entry.pipeline.isPostThinkPending).isTrue()
    }

    @Test
    fun `the async batch dispatch queues and executes the post think`() {
        monster.setAsyncTaskFlag(FlagAsyncClass_t.OnThink, true)
        host.trySchedulePostThink(monster)

        host.onExecuteAsyncTasks(7)

        assertThat(attackTicks).containsExactly(MonsterPipelinesHost.EVENT_CREATURE_THINK_INTERVAL)
        assertThat(conditionTicks).containsExactly(MonsterPipelinesHost.EVENT_CREATURE_THINK_INTERVAL)
        assertThat(entry.pipeline.postThinkQueued).isFalse()
        assertThat(host.queuedPostThinkCount(playerVisible = false)).isZero()
    }

    @Test
    fun `a target search completion selects the target on the owning monster`() {
        player(2, Position(101, 100, 7))
        addTarget(2)

        assertThat(entry.pipeline.requestTargetSearchCompute(TargetSearchType.Nearest)).isTrue()

        assertThat(monster.getAttackedCreature()?.id).isEqualTo(2L)
        assertThat(monster.getFollowCreature()?.id).isEqualTo(2L)
    }

    @Test
    fun `a completion drained after unregister is a no op`() {
        player(2, Position(101, 100, 7))
        player(3, Position(105, 100, 7))
        addTarget(2)
        addTarget(3, x = 105)
        entry.pipeline.requestTargetSearchCompute(TargetSearchType.Nearest)
        assertThat(entry.pipeline.targetSearchComputeOutstanding).isTrue()

        host.unregister(7)
        service.drainCompletions(4)

        assertThat(monster.getAttackedCreature()).isNull()
        assertThat(monster.getFollowCreature()).isNull()
    }

    @Test
    fun `a stale completion resolving a replaced owner advances no state`() {
        player(2, Position(101, 100, 7))
        player(3, Position(105, 100, 7))
        addTarget(2)
        addTarget(3, x = 105)
        entry.pipeline.requestTargetSearchCompute(TargetSearchType.Nearest)

        host.unregister(7)
        val replacement = Monster(id = 7, name = "Rat", maxHealth = 100, health = 100, position = origin)
        host.register(replacement)
        val replacementPipeline = host.pipelineOf(7)!!

        service.drainCompletions(4)

        assertThat(replacement.getAttackedCreature()).isNull()
        assertThat(replacementPipeline.targetSearchComputeOutstanding).isFalse()
        assertThat(replacementPipeline.pendingTargetSearchCompute).isNull()
        assertThat(entry.pipeline.targetSearchComputeOutstanding).isTrue()
    }

    @Test
    fun `a stale pipeline target list refresh does not touch the replaced owner`() {
        player(2, Position(101, 100, 7))
        host.unregister(7)
        val replacement = Monster(id = 7, name = "Rat", maxHealth = 100, health = 100, position = origin)
        host.register(replacement)
        val replacementEntry = host.entryOf(7)!!

        entry.host.updateTargetList()
        entry.host.onCreatureEnter(TargetCreatureFacts(id = 2, position = Position(101, 100, 7), playerId = 2))

        assertThat(replacementEntry.targetState.targetList).isEmpty()
        assertThat(replacementEntry.pipeline.visiblePlayerSpectatorIds).isEmpty()
        assertThat(entry.targetState.targetList).isEmpty()
    }

    @Test
    fun `updateTargetList scans spectators and records opponents and friends`() {
        player(2, Position(101, 100, 7))

        host.updateTargetList(7)

        assertThat(entry.pipeline.visiblePlayerSpectatorIds).containsExactly(2L)
        assertThat(entry.targetState.targetList.map { it.creatureId }).containsExactly(2L)
        assertThat(entry.targetState.targetStateEpoch).isGreaterThan(1L)
    }

    @Test
    fun `updateTargetList prunes dead and out of view references`() {
        val alive = Player(id = 2, name = "Hero", maxHealth = 100, health = 100, position = Position(101, 100, 7))
        val dead = Player(id = 3, name = "Dead", maxHealth = 100, health = 0, position = Position(102, 100, 7))
        val far = Player(id = 4, name = "Far", maxHealth = 100, health = 100, position = Position(200, 100, 7))
        creatures[2] = alive
        creatures[3] = dead
        creatures[4] = far
        entry.targetState.targetList.add(MonsterTargetReference(2))
        entry.targetState.targetList.add(MonsterTargetReference(3))
        entry.targetState.targetList.add(MonsterTargetReference(4))

        host.updateTargetList(7)

        assertThat(entry.targetState.targetList.map { it.creatureId }).containsExactly(2L)
    }

    @Test
    fun `updateTargetList resets the visible spectator hold window`() {
        val spectator = player(2, Position(101, 100, 7))
        host.updateTargetList(7)
        assertThat(entry.pipeline.visiblePlayerSpectatorIds).containsExactly(2L)

        sectors.values.forEach { it.removeCreature(spectator) }
        host.updateTargetList(7)

        assertThat(entry.pipeline.visiblePlayerSpectatorIds).isEmpty()
        assertThat(entry.pipeline.isPlayerVisibleForScheduling()).isTrue()

        now = 1_000L + MonsterAsyncPipelines.WALK_PLAYER_VISIBLE_HOLD_MILLIS
        assertThat(entry.pipeline.isPlayerVisibleForScheduling()).isFalse()
    }

    @Test
    fun `onCreatureAppear observes a visible player synchronously and enters asynchronously`() {
        val spectator = player(2, Position(101, 100, 7))

        host.onCreatureAppear(7, spectator, false)

        assertThat(entry.pipeline.visiblePlayerSpectatorIds).containsExactly(2L)
        assertThat(batches).hasSize(1)

        batches.single().invoke()

        assertThat(entry.targetState.targetList.map { it.creatureId }).containsExactly(2L)
    }

    @Test
    fun `onCreatureAppear of the monster itself defers the target list refresh`() {
        var idleUpdates = 0
        entry.host.updateIdleStatus = { idleUpdates++ }

        host.onCreatureAppear(7, monster, true)

        assertThat(monster.hasAsyncTaskFlag(FlagAsyncClass_t.UpdateTargetList)).isTrue()
        assertThat(idleUpdates).isEqualTo(1)
    }

    @Test
    fun `onRemoveCreature leaves asynchronously and clears the spectator and target`() {
        val spectator = player(2, Position(101, 100, 7))
        host.onCreatureAppear(7, spectator, false)
        batches.single().invoke()
        batches.clear()
        assertThat(entry.targetState.targetList).hasSize(1)

        host.onRemoveCreature(7, spectator, false)

        assertThat(batches).hasSize(1)
        batches.single().invoke()

        assertThat(entry.pipeline.visiblePlayerSpectatorIds).isEmpty()
        assertThat(entry.targetState.targetList).isEmpty()
    }

    @Test
    fun `onRemoveCreature of the monster itself idles it`() {
        entry.behaviorState.isIdle = false

        host.onRemoveCreature(7, monster, true)

        assertThat(entry.behaviorState.isIdle).isTrue()
    }

    @Test
    fun `the post think lateness reaches the dispatcher telemetry sink`() {
        monster.setAsyncTaskFlag(FlagAsyncClass_t.OnThink, true)
        host.trySchedulePostThink(monster)
        now = 1_050L

        entry.pipeline.queuePostThinkAfterAsync()

        assertThat(attackTicks).containsExactly(MonsterPipelinesHost.EVENT_CREATURE_THINK_INTERVAL)
        val snapshot = host.postThinkLateness.snapshotAndReset()
        assertThat(snapshot.latency.samples).isEqualTo(1)
        assertThat(snapshot.latency.mean()).isEqualTo(50_000L)
    }

    @Test
    fun `the movement refresh lateness reaches the dispatcher telemetry sink`() {
        val oldPos = Position(200, 100, 7)
        val newPos = Position(105, 100, 7)
        val moving = TargetCreatureFacts(id = 2, position = newPos, playerId = 2)
        creatures[2] = Player(id = 2, name = "Hero", maxHealth = 100, health = 100, position = newPos)

        entry.pipeline.queueMovementAiRefresh(moving, oldPos, newPos)
        now = 1_030L

        batches.single().invoke()

        val snapshot = host.movementRefreshLateness.snapshotAndReset()
        assertThat(snapshot.latency.samples).isEqualTo(1)
        assertThat(snapshot.latency.mean()).isEqualTo(30_000L)
    }

    @Test
    fun `registering without a monster type keeps the inert type driven seams`() {
        assertThat(entry.typeState.monsterType).isNull()
        assertThat(entry.host.attackSpells()).isEmpty()
        assertThat(entry.host.changeTargetChance()).isZero()
        assertThat(
            entry.host.canUseAttack(origin, TargetCreatureFacts(id = 2, position = Position(200, 200, 7))),
        ).isTrue()
    }

    @Test
    fun `registering with a monster type binds the type driven host seams`() {
        val type = MonsterType("Rat").apply {
            info.changeTargetChance = 20
            info.strategiesTargetNearest = 100
            info.strategiesTargetHealth = 25
            info.strategiesTargetDamage = 50
            info.attackSpells += SpellBlock(spell = BaseSpell { _, _ -> }, range = 5)
        }

        host.unregister(7)
        host.register(monster, type)
        val typed = host.entryOf(7)!!

        assertThat(typed.typeState).isSameAs(host.typeStateOf(7))
        assertThat(typed.typeState.monsterType).isSameAs(type)
        assertThat(typed.host.attackSpells()).hasSize(1)
        assertThat(typed.host.changeTargetChance()).isEqualTo(20)
        assertThat(typed.host.strategiesTargetNearest()).isEqualTo(100)
        assertThat(typed.host.strategiesTargetHealth()).isEqualTo(25)
        assertThat(typed.host.strategiesTargetDamage()).isEqualTo(50)
        assertThat(
            typed.host.canUseAttack(origin, TargetCreatureFacts(id = 2, position = Position(104, 100, 7))),
        ).isTrue()
        assertThat(
            typed.host.canUseAttack(origin, TargetCreatureFacts(id = 2, position = Position(200, 100, 7))),
        ).isFalse()
    }

    @Test
    fun `an outstanding target search defers the post think until the completion`() {
        player(2, Position(101, 100, 7))
        player(3, Position(105, 100, 7))
        addTarget(2)
        addTarget(3, x = 105)
        monster.setAsyncTaskFlag(FlagAsyncClass_t.OnThink, true)
        host.trySchedulePostThink(monster)
        entry.pipeline.requestTargetSearchCompute(TargetSearchType.Nearest)

        entry.pipeline.queuePostThinkAfterAsync()
        assertThat(entry.pipeline.postThinkWaitingForTargetDecision).isTrue()
        assertThat(attackTicks).isEmpty()

        service.drainCompletions(4)

        assertThat(entry.pipeline.postThinkWaitingForTargetDecision).isFalse()
        assertThat(attackTicks).containsExactly(MonsterPipelinesHost.EVENT_CREATURE_THINK_INTERVAL)
    }
}
