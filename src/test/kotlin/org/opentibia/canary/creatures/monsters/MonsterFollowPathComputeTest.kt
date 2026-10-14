package org.opentibia.canary.creatures.monsters

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.CombatType
import org.opentibia.canary.creatures.FindPathParams
import org.opentibia.canary.creatures.MonsterPathResult
import org.opentibia.canary.creatures.MonsterPathStatus
import org.opentibia.canary.game.movement.Direction
import org.opentibia.canary.game.scheduling.MonsterComputeConfig
import org.opentibia.canary.game.scheduling.MonsterComputeService
import org.opentibia.canary.map.NavCell
import org.opentibia.canary.map.NavCellFlag
import org.opentibia.canary.map.NavCellFlags
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.cache.NavRegionSnapshot
import org.opentibia.canary.map.cache.NavSectorSnapshot

/**
 * Transcribes the deferred follow-path pipeline:
 * - `Monster::requestFollowPathCompute` (src/creatures/monsters/monster.cpp:3567-3606)
 * - `Monster::supersedeFollowPathCompute` (src/creatures/monsters/monster.cpp:3608-3618)
 * - `Monster::prepareFollowPathCompute` (src/creatures/monsters/monster.cpp:3620-3678)
 * - `Monster::completeFollowPathCompute` (src/creatures/monsters/monster.cpp:3680-3730)
 * - `Monster::rejectFollowPathCompute` (src/creatures/monsters/monster.cpp:3732-3763)
 * - `Monster::discardFollowPathCompute` (src/creatures/monsters/monster.cpp:3765-3773)
 * - `Monster::capturePathTraits` (src/creatures/monsters/monster.cpp:3775-3811)
 * - `Monster::nextFollowPathComputeGeneration` (src/creatures/monsters/monster.cpp:3813-3818)
 */
class MonsterFollowPathComputeTest {

    private val start = Position(10, 10, 7)
    private val targetPos = Position(10, 11, 7)
    private lateinit var service: MonsterComputeService
    private lateinit var targetState: MonsterTargetListState
    private lateinit var host: MonsterAsyncHost
    private lateinit var pipeline: MonsterAsyncPipelines
    private val walks = mutableListOf<List<Direction>>()
    private val followCompletions = mutableListOf<Long>()
    private var refreshes = 0
    private var hasFollowPath = false

    @BeforeEach
    fun setUp() {
        service = MonsterComputeService()
        service.start(MonsterComputeConfig(configuredThreads = 0, capacity = 16, hardwareConcurrency = 1))
        targetState = MonsterTargetListState()
        host = MonsterAsyncHost()
        host.getPosition = { start }
        host.monsterFacts = { MonsterTargetFacts(origin = start, id = 7) }
        host.followCreatureId = { 2L }
        host.resolveCreature = { if (it == 2L) followCreature() else null }
        host.getNavigationSnapshot = { _, _ -> navigation() }
        host.startAutoWalk = { walks.add(it) }
        host.setHasFollowPath = { hasFollowPath = it }
        host.onFollowCreatureComplete = { followCompletions.add(it.id) }
        host.goToFollowCreatureAsync = { refreshes++ }
        host.resolveMonster = { pipeline }
        pipeline = MonsterAsyncPipelines(
            monsterId = 7,
            service = service,
            targetState = targetState,
            behaviorState = MonsterBehaviorState(),
            host = host,
        )
    }

    @AfterEach
    fun tearDown() {
        service.shutdown()
    }

    private fun followCreature(x: Int = 10, y: Int = 11): TargetCreatureFacts =
        TargetCreatureFacts(id = 2, position = Position(x, y, 7), health = 100)

    private fun params(
        maxSearchDist: Int = 2,
        minTargetDist: Int = -1,
        maxTargetDist: Int = 0,
        fullPathSearch: Boolean = true,
        clearSight: Boolean = true,
    ) = FindPathParams().apply {
        this.fullPathSearch = fullPathSearch
        this.clearSight = clearSight
        this.maxSearchDist = maxSearchDist
        this.minTargetDist = minTargetDist
        this.maxTargetDist = maxTargetDist
    }

    private fun navigation(
        blocked: Set<Position> = emptySet(),
        houseCells: Map<Position, Int> = emptyMap(),
    ): NavRegionSnapshot {
        val cells = Array(256) { NavCell().apply { flags = NavCellFlags.of(NavCellFlag.HasGround) } }
        for (position in blocked) {
            cells[index(position)].flags = NavCellFlags.NONE
        }
        for ((position, houseId) in houseCells) {
            cells[index(position)].houseId = houseId
        }
        return NavRegionSnapshot(
            epoch = 1,
            center = start,
            radius = MonsterAsyncPipelines.MAX_BACKGROUND_FOLLOW_PATH_RADIUS,
            sectors = listOf(NavSectorSnapshot(0, 7, 1, 1, cells)),
        )
    }

    private fun index(position: Position): Int = (position.x and 15) * 16 + (position.y and 15)

    @Test
    fun `a valid request plans off-thread and applies the path on drain`() {
        assertThat(pipeline.requestFollowPathCompute(followCreature(), params(), executeOnFollow = true)).isTrue()
        assertThat(pipeline.followPathComputeOutstanding).isTrue()
        assertThat(walks).isEmpty()

        assertThat(service.drainCompletions(4)).isEqualTo(1)

        assertThat(walks).containsExactly(listOf(Direction.South))
        assertThat(hasFollowPath).isTrue()
        assertThat(followCompletions).containsExactly(2L)
        assertThat(pipeline.followPathComputeOutstanding).isFalse()
        assertThat(pipeline.pendingFollowPathCompute).isNull()
    }

    @Test
    fun `invalid requests never touch the service`() {
        assertThat(
            pipeline.requestFollowPathCompute(TargetCreatureFacts(id = 0, position = targetPos), params(), true),
        ).isFalse()
        assertThat(pipeline.requestFollowPathCompute(followCreature(), params(maxSearchDist = 0), true)).isFalse()
        assertThat(
            pipeline.requestFollowPathCompute(
                followCreature(),
                params(maxSearchDist = MonsterAsyncPipelines.MAX_BACKGROUND_FOLLOW_PATH_RADIUS + 1),
                true,
            ),
        ).isFalse()

        assertThat(service.getStats().accepted).isZero()
        assertThat(pipeline.followPathComputeOutstanding).isFalse()
    }

    @Test
    fun `an identical outstanding request is deduped`() {
        pipeline.requestFollowPathCompute(followCreature(), params(), executeOnFollow = true)

        assertThat(pipeline.requestFollowPathCompute(followCreature(), params(), executeOnFollow = true)).isTrue()
        assertThat(service.getStats().accepted).isEqualTo(1)

        service.drainCompletions(4)
        assertThat(walks).hasSize(1)
    }

    @Test
    fun `superseding drops the pending path and ignores the stale completion`() {
        pipeline.requestFollowPathCompute(followCreature(), params(), executeOnFollow = true)

        pipeline.supersedeFollowPathCompute()

        service.drainCompletions(4)
        assertThat(walks).isEmpty()
        assertThat(pipeline.followPathComputeOutstanding).isFalse()
        assertThat(refreshes).isZero()
    }

    @Test
    fun `a moved follow creature discards the path and refreshes`() {
        pipeline.requestFollowPathCompute(followCreature(), params(), executeOnFollow = true)

        host.resolveCreature = { if (it == 2L) followCreature(x = 10, y = 14) else null }
        service.drainCompletions(4)

        assertThat(walks).isEmpty()
        assertThat(pipeline.followPathComputeOutstanding).isFalse()
        assertThat(refreshes).isEqualTo(1)
    }

    @Test
    fun `a stale navigation topology discards the path and refreshes`() {
        pipeline.requestFollowPathCompute(followCreature(), params(), executeOnFollow = true)
        host.isNavigationTopologyCurrent = { false }

        service.drainCompletions(4)

        assertThat(walks).isEmpty()
        assertThat(refreshes).isEqualTo(1)
    }

    @Test
    fun `a canceled worker result discards without refreshing`() {
        pipeline.requestFollowPathCompute(followCreature(), params(), executeOnFollow = true)
        val generation = pipeline.activeFollowPathComputeGeneration

        pipeline.completeFollowPathCompute(
            generation,
            navigation(),
            MonsterPathResult().apply { status = MonsterPathStatus.Cancelled },
        )

        assertThat(walks).isEmpty()
        assertThat(refreshes).isZero()
        assertThat(pipeline.followPathComputeOutstanding).isFalse()
    }

    @Test
    fun `a newer generation discards the stale completion and refreshes`() {
        pipeline.requestFollowPathCompute(followCreature(), params(), executeOnFollow = true)
        pipeline.requestFollowPathCompute(followCreature(), params(maxSearchDist = 4), executeOnFollow = true)

        service.drainCompletions(4)

        assertThat(walks).isEmpty()
        assertThat(pipeline.pendingFollowPathCompute).isNull()
        assertThat(pipeline.followPathComputeOutstanding).isFalse()
        assertThat(refreshes).isEqualTo(1)
    }

    @Test
    fun `an endpoint that no longer matches discards and refreshes`() {
        pipeline.requestFollowPathCompute(followCreature(), params(), executeOnFollow = true)
        val generation = pipeline.activeFollowPathComputeGeneration

        val result = MonsterPathResult().apply {
            status = MonsterPathStatus.Found
            endpoint = Position(10, 12, 7)
            directions.add(Direction.South)
        }
        pipeline.completeFollowPathCompute(generation, navigation(), result)

        assertThat(walks).isEmpty()
        assertThat(refreshes).isEqualTo(1)
    }

    @Test
    fun `a path that does not reach the endpoint discards and refreshes`() {
        // The endpoint passes the frozen condition (distance 1) but the directions
        // walk from the start to (10,12) instead.
        pipeline.requestFollowPathCompute(
            followCreature(x = 10, y = 12),
            params(maxTargetDist = 1),
            executeOnFollow = true,
        )
        val generation = pipeline.activeFollowPathComputeGeneration

        val result = MonsterPathResult().apply {
            status = MonsterPathStatus.Found
            endpoint = targetPos
            directions.add(Direction.North)
        }
        pipeline.completeFollowPathCompute(generation, navigation(), result)

        assertThat(walks).isEmpty()
        assertThat(refreshes).isEqualTo(1)
    }

    @Test
    fun `a rejected worker result clears the path and closes the follow`() {
        pipeline.requestFollowPathCompute(followCreature(), params(), executeOnFollow = true)
        val generation = pipeline.activeFollowPathComputeGeneration

        pipeline.rejectFollowPathCompute(generation)

        assertThat(walks).containsExactly(emptyList<Direction>())
        assertThat(hasFollowPath).isFalse()
        assertThat(followCompletions).containsExactly(2L)
        assertThat(pipeline.followPathComputeOutstanding).isFalse()
    }

    @Test
    fun `a not found result clears the walk path`() {
        host.getNavigationSnapshot = { _, _ -> navigation(blocked = setOf(targetPos)) }

        pipeline.requestFollowPathCompute(followCreature(), params(), executeOnFollow = true)
        service.drainCompletions(4)

        assertThat(walks).containsExactly(emptyList<Direction>())
        assertThat(hasFollowPath).isFalse()
        assertThat(followCompletions).containsExactly(2L)
    }

    @Test
    fun `a submission rejected at shutdown clears the path and closes the follow`() {
        service.shutdown()

        assertThat(pipeline.requestFollowPathCompute(followCreature(), params(), executeOnFollow = true)).isTrue()

        assertThat(walks).containsExactly(emptyList<Direction>())
        assertThat(hasFollowPath).isFalse()
        assertThat(followCompletions).containsExactly(2L)
        assertThat(pipeline.followPathComputeOutstanding).isFalse()
    }

    @Test
    fun `capturePathTraits reflects the monster flags and invited houses`() {
        host.canPushItems = { true }
        host.canPushCreatures = { true }
        host.isSummon = { true }
        host.canSeeInvisibility = { true }
        host.isMoveLocked = { true }
        host.isFamiliar = { true }
        host.masterHasAttackedCreature = { false }
        host.masterPlayerId = { 9L }
        host.isHouseInvited = { houseId, playerId -> playerId == 9L && houseId == 5 }
        host.isImmuneCombat = { combatType -> combatType == CombatType.FIREDAMAGE }
        host.canWalkOnField = { combatType -> combatType == CombatType.EARTHDAMAGE }
        host.hasFieldCondition = { combatType -> combatType == CombatType.ENERGYDAMAGE }

        val traits = pipeline.capturePathTraits(navigation(houseCells = mapOf(Position(10, 10, 7) to 5)))

        assertThat(traits.canPushItems).isTrue()
        assertThat(traits.canPushCreatures).isTrue()
        assertThat(traits.isSummon).isTrue()
        assertThat(traits.canSeeInvisibility).isTrue()
        assertThat(traits.moveLocked).isTrue()
        assertThat(traits.canEnterProtectionZone).isTrue()
        assertThat(traits.summonInvitedHouseIds).containsExactly(5)
        assertThat(traits.fieldAllowed[CombatType.FIREDAMAGE.value]).isTrue()
        assertThat(traits.fieldAllowed[CombatType.EARTHDAMAGE.value]).isTrue()
        assertThat(traits.fieldPenalty[CombatType.ENERGYDAMAGE.value]).isFalse()
        assertThat(traits.fieldPenalty[CombatType.ICEDAMAGE.value]).isTrue()
    }
}
