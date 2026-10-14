package org.opentibia.canary.game

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.FlagAsyncClass_t
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.monsters.MonsterPipelinesHost
import org.opentibia.canary.creatures.monsters.MonsterType
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.game.scheduling.MonsterComputeConfig
import org.opentibia.canary.game.scheduling.MonsterComputeService
import org.opentibia.canary.items.Item
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.SpectatorMapAccess
import org.opentibia.canary.map.SpectatorSector
import org.opentibia.canary.map.Spectators
import org.opentibia.canary.map.Tile

/**
 * Transcript tests for the monster world-lifecycle registration wiring:
 * `Game::internalPlaceCreature` (game.cpp:1495) registers the placed monster
 * with the injected [MonsterPipelinesHost] and `Game::removeCreature`
 * (game.cpp:1551) unregisters it, so the deferred monster state activates and
 * deactivates with placement. The type is resolved through
 * `g_monsters().getMonsterType` (`Monsters::getMonsterType`,
 * monsters.cpp:363-373) for `Monster::m_monsterType` (monster.hpp:363).
 *
 * A monster whose id was unregistered or replaced must stay inert: the host's
 * owner identity checks refuse the stale hooks and its generation guards drop
 * the coalesced completions (AGENTS.md deferred-callback lifetime safety).
 */
class GameMonsterRegistrationTest {

    private val center = Position(1000, 1000, 7)
    private lateinit var service: MonsterComputeService

    @BeforeEach
    fun setUp() {
        Spectators.clearCache()
        service = MonsterComputeService()
        service.start(MonsterComputeConfig(configuredThreads = 0, capacity = 16, hardwareConcurrency = 1))
    }

    @AfterEach
    fun tearDown() {
        service.shutdown()
    }

    private fun newHost(): MonsterPipelinesHost = MonsterPipelinesHost(
        service = service,
        mapAccess = SpectatorMapAccess { _, _ -> SpectatorSector() },
        resolveCreature = { null },
        clock = { 0L },
    )

    private fun newGame(
        host: MonsterPipelinesHost? = null,
        monsterTypeProvider: (String) -> MonsterType? = { null },
    ): Game {
        Spectators.clearCache()
        return Game(
            GameMap(),
            GameDispatcher(),
            monsterHost = host,
            monsterTypeProvider = monsterTypeProvider,
        )
    }

    private fun Game.groundTile(position: Position = center): Tile =
        map.getOrCreateTile(position).also { it.ground = Item(id = 100) }

    private fun monster(id: Long = 7, position: Position = center): Monster =
        Monster(id = id, name = "Rat", maxHealth = 100, health = 100, position = position)

    @Test
    fun `placing a monster registers its pipeline with the provider type`() {
        val host = newHost()
        val type = MonsterType("Rat")
        val game = newGame(host) { name -> if (name.equals("Rat", ignoreCase = true)) type else null }
        game.groundTile()
        val monster = monster()

        assertThat(game.internalPlaceCreature(monster, center)).isTrue()

        val entry = host.entryOf(7)
        assertThat(entry).isNotNull
        assertThat(entry!!.monster).isSameAs(monster)
        assertThat(entry.typeState.monsterType).isSameAs(type)

        entry.behaviorState.extraMeleeAttack = true
        assertThat(monster.hasExtraSwing()).isTrue()
    }

    @Test
    fun `placing a monster without a provider still registers the pipeline`() {
        val host = newHost()
        val game = newGame(host)
        game.groundTile()

        assertThat(game.internalPlaceCreature(monster(), center)).isTrue()

        assertThat(host.pipelineOf(7)).isNotNull
        assertThat(host.typeStateOf(7)!!.monsterType).isNull()
    }

    @Test
    fun `placing a non monster does not register a pipeline`() {
        val host = newHost()
        val game = newGame(host)
        game.groundTile()
        val player = Player(id = 1, name = "Hero", maxHealth = 150, health = 150, position = center)

        assertThat(game.internalPlaceCreature(player, center)).isTrue()

        assertThat(host.entryOf(1)).isNull()
    }

    @Test
    fun `a failed placement leaves the monster unregistered`() {
        val host = newHost()
        val game = newGame(host)
        game.groundTile()
        val occupant = Player(id = 1, name = "Hero", maxHealth = 150, health = 150, position = center)
        assertThat(game.internalPlaceCreature(occupant, center)).isTrue()

        assertThat(game.internalPlaceCreature(monster(), center)).isFalse()

        assertThat(host.entryOf(7)).isNull()
        assertThat(host.pipelineOf(7)).isNull()
    }

    @Test
    fun `removal unregisters the monster pipeline`() {
        val host = newHost()
        val game = newGame(host)
        game.groundTile()
        val monster = monster()
        assertThat(game.internalPlaceCreature(monster, center)).isTrue()
        assertThat(host.pipelineOf(7)).isNotNull

        assertThat(game.removeCreature(monster)).isTrue()

        assertThat(host.entryOf(7)).isNull()
        assertThat(host.pipelineOf(7)).isNull()
        assertThat(host.trySchedulePostThink(monster)).isFalse()
    }

    @Test
    fun `a replaced owner under a reused id drives only the replacement pipeline`() {
        val host = newHost()
        val game = newGame(host)
        game.groundTile()
        val first = monster()
        assertThat(game.internalPlaceCreature(first, center)).isTrue()
        val firstPipeline = host.pipelineOf(7)!!
        assertThat(game.removeCreature(first)).isTrue()

        val replacement = monster()
        assertThat(game.internalPlaceCreature(replacement, center)).isTrue()
        val replacementEntry = host.entryOf(7)!!
        assertThat(host.pipelineOf(7)).isNotSameAs(firstPipeline)

        first.onCreatureAppearHost(first, true)
        first.onCreatureMoveHost(first, center, Position(1001, 1000, 7))
        first.onExecuteAsyncTasksHost()
        first.setAsyncTaskFlag(FlagAsyncClass_t.OnThink, true)

        assertThat(replacementEntry.monster).isSameAs(replacement)
        assertThat(replacementEntry.targetState.targetList).isEmpty()
        assertThat(replacement.hasAsyncTaskFlag(FlagAsyncClass_t.UpdateTargetList)).isFalse()
        assertThat(replacement.getAttackedCreature()).isNull()
    }
}
