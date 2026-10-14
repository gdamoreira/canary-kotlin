package org.opentibia.canary.creatures.monsters

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.game.scheduling.MonsterComputeConfig
import org.opentibia.canary.game.scheduling.MonsterComputeService
import org.opentibia.canary.map.Position

/**
 * Transcribes the monster spectator/visibility bookkeeping:
 * - `Monster::isPlayerVisibleForScheduling` (src/creatures/monsters/monster.cpp:2045-2052)
 * - `Monster::observeVisiblePlayerForScheduling` (src/creatures/monsters/monster.cpp:2054-2065)
 * - `Monster::addVisiblePlayerSpectator` (src/creatures/monsters/monster.cpp:2067-2080)
 * - `Monster::removeVisiblePlayerSpectator` (src/creatures/monsters/monster.cpp:2082-2089)
 * - `Monster::captureComputeRelevance` (src/creatures/monsters/monster.cpp:2009-2038)
 * - `Monster::isComputeRelevant` (src/creatures/monsters/monster.cpp:2040-2043)
 */
class MonsterVisibilityBookkeepingTest {

    private val origin = Position(100, 100, 7)
    private var now = 1_000L
    private val creatures = mutableMapOf<Long, TargetCreatureFacts>()
    private val targetState = MonsterTargetListState()
    private val host = MonsterAsyncHost().apply {
        getPosition = { origin }
        monsterFacts = { MonsterTargetFacts(origin = origin, id = 7) }
        resolveCreature = { creatures[it] }
        now = { this@MonsterVisibilityBookkeepingTest.now }
    }
    private val service = MonsterComputeService().apply {
        start(MonsterComputeConfig(configuredThreads = 0, capacity = 16, hardwareConcurrency = 1))
    }
    private val pipeline = MonsterAsyncPipelines(
        monsterId = 7,
        service = service,
        targetState = targetState,
        behaviorState = MonsterBehaviorState(),
        host = host,
    )

    private fun player(id: Long, x: Int = 100, y: Int = 100): TargetCreatureFacts =
        TargetCreatureFacts(id = id, position = Position(x, y, 7), playerId = id)

    @Test
    fun `the first spectator enters the visible window and holds it`() {
        assertThat(pipeline.addVisiblePlayerSpectator(player(2))).isTrue()
        assertThat(pipeline.visiblePlayerSpectatorIds).containsExactly(2L)
        assertThat(pipeline.isPlayerVisibleForScheduling()).isTrue()
    }

    @Test
    fun `a second spectator does not report a new visibility transition`() {
        pipeline.addVisiblePlayerSpectator(player(2))

        assertThat(pipeline.addVisiblePlayerSpectator(player(3))).isFalse()
        assertThat(pipeline.visiblePlayerSpectatorIds).containsExactly(2L, 3L)
    }

    @Test
    fun `a non player creature is never a scheduling spectator`() {
        assertThat(pipeline.addVisiblePlayerSpectator(TargetCreatureFacts(id = 9, position = origin))).isFalse()
        assertThat(pipeline.visiblePlayerSpectatorIds).isEmpty()
    }

    @Test
    fun `removing the last spectator keeps the monster visible for the hold window`() {
        pipeline.addVisiblePlayerSpectator(player(2))
        pipeline.removeVisiblePlayerSpectator(player(2))

        assertThat(pipeline.visiblePlayerSpectatorIds).isEmpty()
        assertThat(pipeline.isPlayerVisibleForScheduling()).isTrue()

        now = 1_000L + 3_000L
        assertThat(pipeline.isPlayerVisibleForScheduling()).isFalse()
    }

    @Test
    fun `observing an entering player promotes the walk lane once`() {
        var promotions = 0
        host.promoteWalkEventToPlayerVisibleLane = { promotions++ }

        pipeline.observeVisiblePlayerForScheduling(player(2))
        pipeline.observeVisiblePlayerForScheduling(player(3))

        assertThat(promotions).isEqualTo(1)
        assertThat(pipeline.visiblePlayerSpectatorIds).containsExactly(2L, 3L)
    }

    @Test
    fun `a non player creature does not trigger the scheduling promotion`() {
        var promotions = 0
        host.promoteWalkEventToPlayerVisibleLane = { promotions++ }

        pipeline.observeVisiblePlayerForScheduling(TargetCreatureFacts(id = 9, position = origin))

        assertThat(promotions).isZero()
    }

    @Test
    fun `captureComputeRelevance counts player controlled targets, friends and engagement`() {
        creatures[2] = player(2, 107, 100)
        creatures[3] = TargetCreatureFacts(id = 3, position = Position(101, 100, 7), isMonster = true)
        creatures[4] = player(4, 100, 111)
        targetState.targetList.add(MonsterTargetReference(2))
        targetState.targetList.add(MonsterTargetReference(3))
        targetState.friendList.add(4)
        host.currentAttackedId = { 2L }

        val snapshot = pipeline.captureComputeRelevance()

        assertThat(snapshot.playerSpectators).isEqualTo(2)
        assertThat(snapshot.nearestPlayerDistance).isEqualTo(7)
        assertThat(snapshot.engagedWithPlayer).isTrue()
    }

    @Test
    fun `a monster without player contact is not compute relevant`() {
        assertThat(pipeline.isComputeRelevant()).isFalse()
        assertThat(pipeline.computeRelevance.tier).isEqualTo(MonsterRelevanceTier.Background)
    }

    @Test
    fun `a player within ten tiles enters the visible compute tier and holds with hysteresis`() {
        creatures[2] = player(2, 105, 100)
        targetState.targetList.add(MonsterTargetReference(2))

        assertThat(pipeline.isComputeRelevant()).isTrue()
        assertThat(pipeline.computeRelevance.tier).isEqualTo(MonsterRelevanceTier.Visible)

        now = 2_000L
        creatures[2] = player(2, 111, 100)
        assertThat(pipeline.isComputeRelevant()).isTrue()

        now = 2_000L + 3_000L
        creatures[2] = player(2, 113, 100)
        assertThat(pipeline.isComputeRelevant()).isFalse()
    }

    @Test
    fun `a player engaged with the monster stays relevant without spectators`() {
        creatures[2] = player(2, 150, 100)
        host.currentAttackedId = { 2L }

        assertThat(pipeline.captureComputeRelevance().engagedWithPlayer).isTrue()
        assertThat(pipeline.isComputeRelevant()).isTrue()
    }
}
