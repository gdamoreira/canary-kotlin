package org.opentibia.canary.creatures.monsters

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.FlagAsyncClass_t
import org.opentibia.canary.game.scheduling.MonsterComputeConfig
import org.opentibia.canary.game.scheduling.MonsterComputeService
import org.opentibia.canary.map.Position
import java.util.concurrent.TimeUnit

/**
 * Transcribes the deferred target-search compute pipeline:
 * - `Monster::requestTargetSearchCompute` (src/creatures/monsters/monster.cpp:1066-1090)
 * - `Monster::prepareTargetSearchCompute` (src/creatures/monsters/monster.cpp:1092-1233)
 * - `Monster::completeTargetSearchCompute` (src/creatures/monsters/monster.cpp:1235-1291)
 * - `Monster::retryTargetSearchCompute` (src/creatures/monsters/monster.cpp:1293-1300)
 * - `Monster::clearTargetSearchCompute` (src/creatures/monsters/monster.cpp:1302-1310)
 * - `Monster::nextTargetSearchComputeGeneration` (src/creatures/monsters/monster.cpp:1325-1330)
 */
class MonsterTargetSearchComputeTest {

    private val origin = Position(100, 100, 7)
    private lateinit var service: MonsterComputeService
    private lateinit var targetState: MonsterTargetListState
    private lateinit var behaviorState: MonsterBehaviorState
    private lateinit var host: MonsterAsyncHost
    private lateinit var pipeline: MonsterAsyncPipelines
    private val selected = mutableListOf<Long>()
    private val flagged = mutableListOf<FlagAsyncClass_t>()
    private val creatures = mutableMapOf<Long, TargetCreatureFacts>()
    private val selectable = mutableSetOf<Long>()
    private var now = 1_000L

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
        host.selectTarget = { facts -> selected.add(facts.id); selectable.isEmpty() || selectable.contains(facts.id) }
        host.random = { min, _ -> min }
        host.setAsyncTaskFlag = { flagged.add(it) }
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

    private fun addTarget(id: Long, x: Int, y: Int, health: Int = 100) {
        creatures[id] = TargetCreatureFacts(id = id, position = Position(x, y, 7), health = health)
        targetState.targetList.add(MonsterTargetReference(id))
    }

    @Test
    fun `an identical outstanding search is deduped and completed on drain`() {
        addTarget(1, 101, 100)
        addTarget(2, 105, 100)

        assertThat(pipeline.requestTargetSearchCompute(TargetSearchType.Nearest)).isTrue()
        assertThat(service.getCompletionCount()).isEqualTo(1)

        assertThat(pipeline.requestTargetSearchCompute(TargetSearchType.Nearest)).isTrue()
        assertThat(service.getStats().accepted).isEqualTo(1)

        assertThat(service.drainCompletions(4)).isEqualTo(1)
        assertThat(selected).containsExactly(1L)
        assertThat(pipeline.targetSearchComputeOutstanding).isFalse()
    }

    @Test
    fun `a single ranked candidate completes synchronously without the service`() {
        addTarget(1, 101, 100)

        assertThat(pipeline.requestTargetSearchCompute(TargetSearchType.Nearest)).isTrue()

        assertThat(service.getStats().accepted).isZero()
        assertThat(selected).containsExactly(1L)
        assertThat(pipeline.targetSearchComputeOutstanding).isFalse()
    }

    @Test
    fun `the ranking stays outstanding until the worker completion is drained`() {
        val workerService = MonsterComputeService()
        workerService.start(MonsterComputeConfig(configuredThreads = 1, capacity = 16, hardwareConcurrency = 8))
        val workerHost = MonsterAsyncHost()
        workerHost.getPosition = { origin }
        workerHost.monsterFacts = { MonsterTargetFacts(origin = origin, id = 7) }
        workerHost.resolveCreature = { creatures[it] }
        workerHost.selectTarget = { facts -> selected.add(facts.id); true }
        var workerPipeline: MonsterAsyncPipelines? = null
        workerHost.resolveMonster = { workerPipeline }
        val pipelineRef = MonsterAsyncPipelines(
            monsterId = 7,
            service = workerService,
            targetState = targetState,
            behaviorState = behaviorState,
            host = workerHost,
        )
        workerPipeline = pipelineRef

        addTarget(1, 101, 100)
        addTarget(2, 105, 100)

        assertThat(pipelineRef.requestTargetSearchCompute(TargetSearchType.Nearest)).isTrue()
        assertThat(pipelineRef.targetSearchComputeOutstanding).isTrue()
        assertThat(selected).isEmpty()

        waitForCompletion(workerService, 1)
        workerService.drainCompletions(4)

        assertThat(pipelineRef.targetSearchComputeOutstanding).isFalse()
        assertThat(selected).containsExactly(1L)
        workerService.shutdown()
    }

    @Test
    fun `the default strategy roll selects the configured ranking mode`() {
        host.strategiesTargetHealth = { 100 }
        host.random = { min, max -> if (min == 1) max else min }
        addTarget(1, 115, 100, health = 10)
        addTarget(2, 101, 100, health = 90)

        pipeline.requestTargetSearchCompute(TargetSearchType.Default)
        service.drainCompletions(4)

        assertThat(selected).containsExactly(1L)
    }

    @Test
    fun `the random search picks an eligible candidate synchronously`() {
        addTarget(1, 101, 100)
        addTarget(2, 105, 100)

        pipeline.requestTargetSearchCompute(TargetSearchType.Random)

        assertThat(service.getStats().accepted).isZero()
        assertThat(selected).containsExactly(1L)
    }

    @Test
    fun `a stale generation retries with the pending search type and flags the task`() {
        addTarget(1, 101, 100)
        addTarget(2, 105, 100)
        addTarget(3, 109, 100)

        pipeline.requestTargetSearchCompute(TargetSearchType.Nearest)
        pipeline.requestTargetSearchCompute(TargetSearchType.Hp)
        assertThat(service.getStats().accepted).isEqualTo(1)

        assertThat(service.drainCompletions(4)).isEqualTo(1)

        assertThat(selected).isEmpty()
        assertThat(pipeline.pendingTargetSearchCompute?.searchType).isEqualTo(TargetSearchType.Hp)
        assertThat(flagged).containsExactly(FlagAsyncClass_t.TargetRanking)
        assertThat(pipeline.targetSearchComputeOutstanding).isFalse()
    }

    @Test
    fun `a changed decision epoch clears the deferred search`() {
        addTarget(1, 101, 100)
        addTarget(2, 105, 100)

        pipeline.requestTargetSearchCompute(TargetSearchType.Nearest)
        MonsterTargeting.markTargetDecisionChanged(targetState)
        service.drainCompletions(4)

        assertThat(selected).isEmpty()
        assertThat(pipeline.targetSearchComputeOutstanding).isFalse()
        assertThat(pipeline.pendingTargetSearchCompute).isNull()
        assertThat(flagged).isEmpty()
    }

    @Test
    fun `a changed state epoch retries while the target list is populated`() {
        addTarget(1, 101, 100)
        addTarget(2, 105, 100)

        pipeline.requestTargetSearchCompute(TargetSearchType.Nearest)
        MonsterTargeting.markTargetStateChanged(targetState)
        service.drainCompletions(4)

        assertThat(selected).isEmpty()
        assertThat(pipeline.targetSearchComputeOutstanding).isFalse()
        assertThat(pipeline.pendingTargetSearchCompute?.searchType).isEqualTo(TargetSearchType.Nearest)
        assertThat(flagged).containsExactly(FlagAsyncClass_t.TargetRanking)
    }

    @Test
    fun `a changed state epoch with an emptied target list clears`() {
        addTarget(1, 101, 100)
        addTarget(2, 105, 100)

        pipeline.requestTargetSearchCompute(TargetSearchType.Nearest)
        MonsterTargeting.clearTargetList(targetState)
        service.drainCompletions(4)

        assertThat(selected).isEmpty()
        assertThat(pipeline.targetSearchComputeOutstanding).isFalse()
        assertThat(pipeline.pendingTargetSearchCompute).isNull()
        assertThat(flagged).isEmpty()
    }

    @Test
    fun `a canceled ranking result clears without selecting`() {
        addTarget(1, 101, 100)
        addTarget(2, 105, 100)

        pipeline.requestTargetSearchCompute(TargetSearchType.Nearest)
        val generation = pipeline.activeTargetSearchComputeGeneration

        pipeline.completeTargetSearchCompute(
            generation,
            targetState.targetStateEpoch,
            targetState.targetDecisionEpoch,
            origin,
            TargetSearchType.Nearest,
            emptyList(),
            MonsterTargetRankingResult().apply { canceled = true },
        )

        assertThat(selected).isEmpty()
        assertThat(pipeline.targetSearchComputeOutstanding).isFalse()
    }

    @Test
    fun `removal clears the deferred search and drops the selection`() {
        addTarget(1, 101, 100)
        addTarget(2, 105, 100)

        pipeline.requestTargetSearchCompute(TargetSearchType.Nearest)
        host.isRemoved = { true }
        service.drainCompletions(4)

        assertThat(selected).isEmpty()
        assertThat(pipeline.targetSearchComputeOutstanding).isFalse()
    }

    @Test
    fun `a request on a removed monster is rejected by the safe hand-back`() {
        addTarget(1, 101, 100)
        addTarget(2, 105, 100)
        host.isRemoved = { true }

        assertThat(pipeline.requestTargetSearchCompute(TargetSearchType.Nearest)).isFalse()
        assertThat(pipeline.targetSearchComputeOutstanding).isFalse()
        assertThat(service.getStats().accepted).isZero()
    }

    @Test
    fun `a replaced owner ignores the stale completion`() {
        val replaced = MonsterAsyncPipelines(
            monsterId = 7,
            service = service,
            targetState = MonsterTargetListState(),
            behaviorState = MonsterBehaviorState(),
            host = MonsterAsyncHost(),
        )
        addTarget(1, 101, 100)
        addTarget(2, 105, 100)
        host.resolveMonster = { replaced }

        pipeline.requestTargetSearchCompute(TargetSearchType.Nearest)
        service.drainCompletions(4)

        assertThat(selected).isEmpty()
        assertThat(replaced.targetSearchComputeOutstanding).isFalse()
    }

    @Test
    fun `shutdown discards queued completions and rejects new requests`() {
        addTarget(1, 101, 100)
        addTarget(2, 105, 100)

        pipeline.requestTargetSearchCompute(TargetSearchType.Nearest)
        service.shutdown()

        assertThat(service.drainCompletions(4)).isZero()
        assertThat(selected).isEmpty()

        val stopped = MonsterAsyncPipelines(
            monsterId = 7,
            service = service,
            targetState = targetState,
            behaviorState = behaviorState,
            host = host,
        )
        assertThat(stopped.requestTargetSearchCompute(TargetSearchType.Nearest)).isTrue()
        assertThat(stopped.targetSearchComputeOutstanding).isFalse()
        assertThat(stopped.pendingTargetSearchCompute).isNull()
        assertThat(service.getCompletionCount()).isZero()
    }

    @Test
    fun `the fallback list selects an eligible target when the suggestion is rejected`() {
        addTarget(1, 105, 100)
        addTarget(2, 101, 100)
        selectable.add(1L)

        pipeline.requestTargetSearchCompute(TargetSearchType.Nearest)
        service.drainCompletions(4)

        assertThat(selected).containsExactly(2L, 1L)
    }

    @Test
    fun `skipCurrentUnreachable drops the attributed target from ranking and fallback`() {
        addTarget(1, 101, 100)
        addTarget(2, 105, 100)
        host.currentAttackedId = { 1L }
        host.targetDistance = { 1 }
        host.hasFollowPath = { false }
        selectable.clear()
        host.selectTarget = { facts -> selected.add(facts.id); false }

        pipeline.requestTargetSearchCompute(TargetSearchType.Nearest)
        service.drainCompletions(4)

        assertThat(selected).containsExactly(2L)
    }

    @Test
    fun `canUseAttack filters ranking candidates but keeps the fallback list`() {
        addTarget(1, 105, 100)
        addTarget(2, 101, 100)
        host.targetDistance = { 3 }
        host.canUseAttack = { _, facts -> facts.id != 1L }
        selectable.add(1L)

        pipeline.requestTargetSearchCompute(TargetSearchType.Nearest)
        service.drainCompletions(4)

        assertThat(selected).containsExactly(2L, 1L)
    }

    @Test
    fun `zero eligible candidates clears immediately`() {
        targetState.targetList.add(MonsterTargetReference(1))

        pipeline.requestTargetSearchCompute(TargetSearchType.Nearest)

        assertThat(service.getStats().accepted).isZero()
        assertThat(pipeline.targetSearchComputeOutstanding).isFalse()
        assertThat(pipeline.pendingTargetSearchCompute).isNull()
    }

    @Test
    fun `retry refuses to re-arm a removed monster`() {
        host.isRemoved = { true }

        pipeline.retryTargetSearchCompute(TargetSearchType.Hp)

        assertThat(pipeline.pendingTargetSearchCompute).isNull()
        assertThat(flagged).isEmpty()
    }

    private fun waitForCompletion(service: MonsterComputeService, count: Int) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (service.getCompletionCount() < count && System.nanoTime() < deadline) {
            Thread.sleep(1)
        }
        assertThat(service.getCompletionCount()).isEqualTo(count)
    }
}
