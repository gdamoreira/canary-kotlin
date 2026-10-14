package org.opentibia.canary.creatures.monsters

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.FlagAsyncClass_t
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.scheduling.MonsterComputeConfig
import org.opentibia.canary.game.scheduling.MonsterComputeService
import org.opentibia.canary.map.Position

/**
 * Wires the previously injected rule seams to the real
 * [MonsterAsyncPipelines] state machine:
 * - `MonsterTargeting.searchTarget` -> `requestTargetSearchCompute`
 * - `MonsterBehavior.doAttacking` -> `requestCombatIntention`
 * - `MonsterThink.onThinkAsync` -> `queuePostThinkAfterAsync`
 * - `MonsterTargeting.onThinkTarget` -> `requestTargetSearchCompute`
 */
class MonsterAsyncPipelineWiringTest {

    private val origin = Position(100, 100, 7)
    private lateinit var service: MonsterComputeService
    private lateinit var targetState: MonsterTargetListState
    private lateinit var behaviorState: MonsterBehaviorState
    private lateinit var host: MonsterAsyncHost
    private lateinit var pipeline: MonsterAsyncPipelines
    private val selected = mutableListOf<Long>()
    private val creatures = mutableMapOf<Long, TargetCreatureFacts>()
    private val flags = mutableSetOf<FlagAsyncClass_t>()
    private val queuedTicks = mutableListOf<Pair<Long, Boolean>>()
    private val casts = mutableListOf<Long>()

    @BeforeEach
    fun setUp() {
        service = MonsterComputeService()
        service.start(MonsterComputeConfig(configuredThreads = 0, capacity = 16, hardwareConcurrency = 1))
        targetState = MonsterTargetListState()
        behaviorState = MonsterBehaviorState().apply { attackTicks = 100 }
        host = MonsterAsyncHost()
        host.getPosition = { origin }
        host.monsterFacts = { MonsterTargetFacts(origin = origin, id = 7) }
        host.resolveCreature = { creatures[it] }
        host.selectTarget = { facts -> selected.add(facts.id); true }
        host.setAsyncTaskFlag = { flags.add(it) }
        host.hasAsyncTaskFlag = { flags.contains(it) }
        host.queueMonsterPostThink = { id, playerVisible -> queuedTicks.add(id to playerVisible); true }
        host.castSpell = { _, target -> casts.add(target.id) }
        host.resolveMonster = { pipeline }
        pipeline = MonsterAsyncPipelines(
            monsterId = 7,
            service = service,
            targetState = targetState,
            behaviorState = behaviorState,
            host = host,
        )
    }

    @AfterEach
    fun tearDown() {
        service.shutdown()
    }

    private fun addTarget(id: Long, x: Int) {
        creatures[id] = TargetCreatureFacts(id = id, position = Position(x, 100, 7), health = 100)
        targetState.targetList.add(MonsterTargetReference(id))
    }

    @Test
    fun `searchTarget defers into the real target search pipeline`() {
        addTarget(1, 101)
        addTarget(2, 105)

        val result = MonsterTargeting.searchTarget(
            searchType = TargetSearchType.Nearest,
            isBarrierParallel = true,
            requestTargetSearchCompute = pipeline::requestTargetSearchCompute,
            searchTargetImmediate = { false },
        )

        assertThat(result).isTrue()
        assertThat(pipeline.targetSearchComputeOutstanding).isTrue()
        service.drainCompletions(4)
        assertThat(selected).containsExactly(1L)
    }

    @Test
    fun `doAttacking starts the real combat intention pipeline`() {
        host.currentAttackedId = { 2L }
        host.attackSpells = { listOf(MonsterSpellBlock(spell = BaseSpell { _, _ -> }, speed = 100, range = 0)) }
        val attacked = Player(2, "Hero", 100, 100, position = Position(102, 100, 7))
        creatures[2] = TargetCreatureFacts(id = 2, position = Position(102, 100, 7), health = 100)

        MonsterBehavior.doAttacking(
            MonsterAttackingRequest(
                state = behaviorState,
                monsterId = 7,
                isSummon = false,
                attackedCreature = attacked,
                interval = 100,
                requestCombatIntention = { interval, target ->
                    pipeline.requestCombatIntention(interval, creatures.getValue(target.id))
                },
            ),
        )

        assertThat(behaviorState.attackTicks).isEqualTo(200)
        service.drainCompletions(4)
        assertThat(casts).containsExactly(2L)
        assertThat(behaviorState.attackTicks).isZero()
    }

    @Test
    fun `onThinkAsync queues the post-think through the real scheduler`() {
        flags.add(FlagAsyncClass_t.OnThink)
        pipeline.trySchedulePostThink()

        val alive = MonsterThink.onThinkAsync(
            MonsterThinkAsyncRequest(
                state = behaviorState,
                position = origin,
                isIdle = false,
                isSummon = false,
                targetListEmpty = true,
                queuePostThinkAfterAsync = pipeline::queuePostThinkAfterAsync,
            ),
        )

        assertThat(alive).isTrue()
        assertThat(pipeline.postThinkQueued).isTrue()
        assertThat(queuedTicks).containsExactly(7L to false)
    }

    @Test
    fun `onThinkTarget requests the real target search when the change window elapses`() {
        addTarget(1, 101)
        addTarget(2, 105)
        host.targetDistance = { 2 }
        behaviorState.targetChangeTicks = 900
        behaviorState.targetChangeCooldown = 0

        MonsterTargeting.onThinkTarget(
            MonsterTargetThinkRequest(
                state = behaviorState,
                interval = 100,
                isSummon = false,
                changeTargetSpeed = 1000,
                changeTargetChance = 100,
                targetDistance = 2,
                monsterPerfTestFriendlyFire = false,
                runAwayHealth = 0,
                rollTargetChange = { 1 },
                requestTargetSearchCompute = pipeline::requestTargetSearchCompute,
            ),
        )

        assertThat(pipeline.targetSearchComputeOutstanding).isTrue()
        service.drainCompletions(4)
        assertThat(selected).containsExactly(1L)
    }
}
