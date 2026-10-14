package org.opentibia.canary.creatures.monsters

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.FlagAsyncClass_t
import org.opentibia.canary.game.scheduling.MonsterComputeConfig
import org.opentibia.canary.game.scheduling.MonsterComputeService
import org.opentibia.canary.map.Position

/**
 * Transcribes the post-think scheduler and the async task dispatch:
 * - `Monster::trySchedulePostThink` (src/creatures/monsters/monster.cpp:1633-1638)
 * - `Monster::cancelScheduledPostThink` (src/creatures/monsters/monster.cpp:1640-1648)
 * - `Monster::executePostThink` (src/creatures/monsters/monster.cpp:1650-1680)
 * - `Monster::queuePostThinkAfterAsync` (src/creatures/monsters/monster.cpp:1682-1697)
 * - `Monster::promotePostThinkToPlayerVisibleQueue` (src/creatures/monsters/monster.cpp:1699-1706)
 * - `Monster::onExecuteAsyncTasks` (src/creatures/monsters/monster.cpp:3972-3996)
 */
class MonsterPostThinkSchedulerTest {

    private val origin = Position(100, 100, 7)
    private lateinit var service: MonsterComputeService
    private lateinit var targetState: MonsterTargetListState
    private lateinit var behaviorState: MonsterBehaviorState
    private lateinit var host: MonsterAsyncHost
    private lateinit var pipeline: MonsterAsyncPipelines
    private val flags = mutableSetOf<FlagAsyncClass_t>()
    private val queuedTicks = mutableListOf<Pair<Long, Boolean>>()
    private val attackTicks = mutableListOf<Int>()
    private val conditionTicks = mutableListOf<Int>()
    private val creatures = mutableMapOf<Long, TargetCreatureFacts>()
    private val selected = mutableListOf<Long>()
    private var now = 10_000L

    @BeforeEach
    fun setUp() {
        service = MonsterComputeService()
        service.start(MonsterComputeConfig(configuredThreads = 0, capacity = 16, hardwareConcurrency = 1))
        targetState = MonsterTargetListState()
        behaviorState = MonsterBehaviorState()
        host = MonsterAsyncHost()
        host.getPosition = { origin }
        host.monsterFacts = { MonsterTargetFacts(origin = origin, id = 7) }
        host.resolveCreature = { creatures[it] }
        host.selectTarget = { facts -> selected.add(facts.id); true }
        host.setAsyncTaskFlag = { flags.add(it) }
        host.hasAsyncTaskFlag = { flags.contains(it) }
        host.queueMonsterPostThink = { id, playerVisible -> queuedTicks.add(id to playerVisible); true }
        host.onAttacking = { attackTicks.add(it) }
        host.executeConditions = { conditionTicks.add(it) }
        host.now = { now }
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
    fun `trySchedulePostThink requires the OnThink flag and coalesces once`() {
        assertThat(pipeline.trySchedulePostThink()).isFalse()

        flags.add(FlagAsyncClass_t.OnThink)
        assertThat(pipeline.trySchedulePostThink()).isTrue()
        assertThat(pipeline.trySchedulePostThink()).isFalse()
        assertThat(pipeline.isPostThinkPending).isTrue()
    }

    @Test
    fun `queuePostThinkAfterAsync schedules a player invisible tick that attacks on execute`() {
        flags.add(FlagAsyncClass_t.OnThink)
        pipeline.trySchedulePostThink()

        pipeline.queuePostThinkAfterAsync()

        assertThat(pipeline.postThinkQueued).isTrue()
        assertThat(pipeline.postThinkPlayerVisible).isFalse()
        assertThat(queuedTicks).containsExactly(7L to false)

        pipeline.executePostThink(100, false)

        assertThat(attackTicks).containsExactly(100)
        assertThat(conditionTicks).containsExactly(100)
        assertThat(pipeline.postThinkQueued).isFalse()
        assertThat(pipeline.isPostThinkPending).isFalse()
    }

    @Test
    fun `executePostThink ignores a mismatched player visible lane`() {
        flags.add(FlagAsyncClass_t.OnThink)
        pipeline.trySchedulePostThink()
        pipeline.queuePostThinkAfterAsync()

        pipeline.executePostThink(100, true)

        assertThat(attackTicks).isEmpty()
        assertThat(pipeline.postThinkQueued).isTrue()
    }

    @Test
    fun `queuePostThinkAfterAsync defers behind an outstanding target search and releases on clear`() {
        addTarget(1, 101)
        addTarget(2, 105)
        flags.add(FlagAsyncClass_t.OnThink)
        pipeline.trySchedulePostThink()
        pipeline.requestTargetSearchCompute(TargetSearchType.Nearest)

        pipeline.queuePostThinkAfterAsync()
        assertThat(pipeline.postThinkWaitingForTargetDecision).isTrue()
        assertThat(pipeline.postThinkQueued).isFalse()
        assertThat(queuedTicks).isEmpty()

        service.drainCompletions(4)

        assertThat(selected).containsExactly(1L)
        assertThat(pipeline.postThinkWaitingForTargetDecision).isFalse()
        assertThat(pipeline.postThinkQueued).isTrue()
        assertThat(queuedTicks).containsExactly(7L to false)
    }

    @Test
    fun `executePostThink defers while the target search is outstanding`() {
        addTarget(1, 101)
        addTarget(2, 105)
        flags.add(FlagAsyncClass_t.OnThink)
        pipeline.trySchedulePostThink()
        pipeline.queuePostThinkAfterAsync()
        pipeline.requestTargetSearchCompute(TargetSearchType.Nearest)

        pipeline.executePostThink(100, false)

        assertThat(attackTicks).isEmpty()
        assertThat(pipeline.postThinkQueued).isFalse()
        assertThat(pipeline.postThinkWaitingForTargetDecision).isTrue()
    }

    @Test
    fun `a rejected post-think queue cancels the scheduled tick`() {
        host.queueMonsterPostThink = { _, _ -> false }
        flags.add(FlagAsyncClass_t.OnThink)
        pipeline.trySchedulePostThink()

        pipeline.queuePostThinkAfterAsync()

        assertThat(pipeline.postThinkQueued).isFalse()
        assertThat(pipeline.isPostThinkPending).isFalse()

        pipeline.executePostThink(100, false)
        assertThat(attackTicks).isEmpty()
    }

    @Test
    fun `promotePostThinkToPlayerVisibleQueue re-queues a scheduled tick on the visible lane`() {
        flags.add(FlagAsyncClass_t.OnThink)
        pipeline.trySchedulePostThink()
        pipeline.queuePostThinkAfterAsync()
        pipeline.addVisiblePlayerSpectator(TargetCreatureFacts(id = 2, position = origin, playerId = 2))

        pipeline.promotePostThinkToPlayerVisibleQueue()

        assertThat(queuedTicks).containsExactly(7L to false, 7L to true)
        assertThat(pipeline.postThinkQueued).isTrue()
        assertThat(pipeline.postThinkPlayerVisible).isTrue()
    }

    @Test
    fun `a removed monster drops the post-think tick before attacking`() {
        flags.add(FlagAsyncClass_t.OnThink)
        pipeline.trySchedulePostThink()
        pipeline.queuePostThinkAfterAsync()
        host.isRemoved = { true }

        pipeline.executePostThink(100, false)

        assertThat(attackTicks).isEmpty()
        assertThat(pipeline.isPostThinkPending).isFalse()
    }

    @Test
    fun `onExecuteAsyncTasks dispatches the target list and idle flags`() {
        var targetListUpdates = 0
        var idleUpdates = 0
        host.updateTargetList = { targetListUpdates++ }
        host.updateIdleStatus = { idleUpdates++ }
        flags.add(FlagAsyncClass_t.UpdateTargetList)
        flags.add(FlagAsyncClass_t.UpdateIdleStatus)

        pipeline.onExecuteAsyncTasks()

        assertThat(targetListUpdates).isEqualTo(1)
        assertThat(idleUpdates).isEqualTo(1)
    }

    @Test
    fun `onExecuteAsyncTasks re-requests the parked target search`() {
        addTarget(1, 101)
        addTarget(2, 105)
        pipeline.retryTargetSearchCompute(TargetSearchType.Hp)

        pipeline.onExecuteAsyncTasks()

        assertThat(service.getStats().accepted).isEqualTo(1)
        service.drainCompletions(4)
        assertThat(selected).containsExactly(1L)
    }

    @Test
    fun `onExecuteAsyncTasks restarts the deferred combat intention`() {
        host.attackSpells = { listOf(MonsterSpellBlock(spell = BaseSpell { _, _ -> }, speed = 100, range = 0)) }
        behaviorState.attackTicks = 100
        val target = TargetCreatureFacts(id = 2, position = Position(102, 100, 7), health = 100)
        host.resolveCreature = { if (it == 2L) target else null }
        host.currentAttackedId = { 2L }

        pipeline.requestCombatIntention(interval = 100, target = target)
        pipeline.requestCombatIntention(interval = 50, target = target)
        service.drainCompletions(4)
        assertThat(flags).contains(FlagAsyncClass_t.CombatIntention)
        val casts = mutableListOf<Long>()
        host.castSpell = { _, victim -> casts.add(victim.id) }

        pipeline.onExecuteAsyncTasks()
        service.drainCompletions(4)

        assertThat(casts).containsExactly(2L)
    }

    @Test
    fun `onExecuteAsyncTasks queues the post-think when the async think declines`() {
        flags.add(FlagAsyncClass_t.OnThink)
        pipeline.trySchedulePostThink()
        var thinkAsyncCalls = 0
        host.onThinkAsync = { thinkAsyncCalls++; false }

        pipeline.onExecuteAsyncTasks()

        assertThat(thinkAsyncCalls).isEqualTo(1)
        assertThat(queuedTicks).containsExactly(7L to false)
    }

    @Test
    fun `onExecuteAsyncTasks skips the post-think queue when the async think claims the tick`() {
        flags.add(FlagAsyncClass_t.OnThink)
        pipeline.trySchedulePostThink()
        host.onThinkAsync = { true }

        pipeline.onExecuteAsyncTasks()

        assertThat(queuedTicks).isEmpty()
    }
}
