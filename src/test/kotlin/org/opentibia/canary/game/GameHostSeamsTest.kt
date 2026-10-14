package org.opentibia.canary.game

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.FlagAsyncClass_t
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.Npc
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.monsters.MonsterPipelinesHost
import org.opentibia.canary.game.movement.Direction
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.game.scheduling.MonsterComputeConfig
import org.opentibia.canary.game.scheduling.MonsterComputeService
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.SpectatorMapAccess
import org.opentibia.canary.map.SpectatorSector
import org.opentibia.canary.map.Spectators
import org.opentibia.canary.spells.WorldType
import org.opentibia.canary.systems.rewardchest.ReturnValue

/**
 * Transcript tests for the `Game::` host seams and world accessors:
 *
 *  - `Game::checkCreatures` → `Monster::trySchedulePostThink` (game.cpp:7719-7741)
 *  - `Game::queueMonsterPostThink` (game.cpp:7694-7717)
 *  - the walk seams bound to the host `Player` methods
 *    (`Creature::startAutoWalk` creature.cpp:326, `Player::setNextWalkTask`
 *    player.cpp:2996, `Player::setNextWalkActionTask` player.cpp:2983,
 *    `Creature::updateCreatureWalk` creature.hpp:412)
 *  - `Game::getGameState` / `setGameState` (game.cpp:922, 952)
 *  - `Game::setWorldType` (game.cpp:926)
 *  - `Game::getCreatureByName` / `getNpcByName` (game.cpp:1356, 1380)
 *  - `Game::getPlayerNameByGUID` (game.cpp:1439)
 *  - `Game::addNpc` / `removeNpc` (game.cpp:11703, 11715)
 */
class GameHostSeamsTest {

    private val center = Position(1000, 1000, 7)
    private lateinit var service: MonsterComputeService
    private lateinit var host: MonsterPipelinesHost
    private lateinit var game: Game

    @BeforeEach
    fun setUp() {
        Spectators.clearCache()
        service = MonsterComputeService()
        service.start(MonsterComputeConfig(configuredThreads = 0, capacity = 16, hardwareConcurrency = 1))
        host = MonsterPipelinesHost(
            service = service,
            mapAccess = SpectatorMapAccess { _, _ -> SpectatorSector() },
        )
        game = Game(GameMap(), GameDispatcher(), monsterHost = host)
    }

    @AfterEach
    fun tearDown() {
        service.shutdown()
    }

    private fun player(id: Long, name: String): Player =
        Player(id = id, name = name, maxHealth = 150, health = 150, position = center).also { game.addPlayer(it) }

    private fun monster(id: Long): Monster =
        Monster(id = id, name = "Monster$id", maxHealth = 100, health = 100, position = Position(1001, 1000, 7))

    @Test
    fun `checkCreatures schedules the monster post think through the host`() {
        val monster = monster(7)
        game.addMonster(monster)
        monster.scheduleAsyncTaskBatch = { }
        monster.setAsyncTaskFlag(FlagAsyncClass_t.OnThink, true)
        host.register(monster)
        game.addCreatureCheck(monster)

        repeat(10) { game.checkCreatures() }

        assertThat(host.pipelineOf(monster.id)!!.isPostThinkPending).isTrue()
    }

    @Test
    fun `checkCreatures ignores creatures without a registered pipeline`() {
        val monster = monster(9)
        game.addMonster(monster)
        monster.scheduleAsyncTaskBatch = { }
        monster.setAsyncTaskFlag(FlagAsyncClass_t.OnThink, true)
        game.addCreatureCheck(monster)

        repeat(10) { game.checkCreatures() }

        assertThat(host.pipelineOf(monster.id)).isNull()
    }

    @Test
    fun `queueMonsterPostThink delegates to the host queue`() {
        assertThat(game.queueMonsterPostThink(7, true)).isTrue()
    }

    @Test
    fun `startAutoWalk is bound to the host player walk queue`() {
        val player = player(1, "Hero")

        game.startAutoWalk(player, listOf(Direction.East, Direction.North), false)

        assertThat(player.listWalkDir).containsExactly(Direction.East, Direction.North)
    }

    @Test
    fun `setNextWalkTask is bound to the host player method`() {
        val player = player(1, "Hero")

        game.setNextWalkTask(player, null)
        game.setNextWalkActionTask(player, null)
        game.updateCreatureWalk(player)
    }

    @Test
    fun `getGameState defaults to normal and setGameState assigns`() {
        assertThat(game.getGameState()).isEqualTo(GameState_t.GAME_STATE_NORMAL)

        game.setGameState(GameState_t.GAME_STATE_CLOSING)
        assertThat(game.getGameState()).isEqualTo(GameState_t.GAME_STATE_CLOSING)

        game.setGameState(GameState_t.GAME_STATE_SHUTDOWN)
        game.setGameState(GameState_t.GAME_STATE_NORMAL)
        assertThat(game.getGameState()).isEqualTo(GameState_t.GAME_STATE_SHUTDOWN)
    }

    @Test
    fun `setWorldType assigns the world type`() {
        assertThat(game.getWorldType()).isEqualTo(WorldType.PVP)

        game.setWorldType(WorldType.PVP_ENFORCED)
        assertThat(game.getWorldType()).isEqualTo(WorldType.PVP_ENFORCED)
    }

    @Test
    fun `getCreatureByName resolves players case-insensitively`() {
        val player = player(1, "Hero")

        assertThat(game.getCreatureByName("hero")).isSameAs(player)
        assertThat(game.getCreatureByName("")).isNull()
        assertThat(game.getCreatureByName("missing")).isNull()
    }

    @Test
    fun `getCreatureByName resolves npcs and monsters`() {
        val npc = Npc(id = 100, name = "Al Dee", position = center)
        game.addNpc(npc)
        val monster = monster(7)
        game.addMonster(monster)

        assertThat(game.getCreatureByName("al dee")).isSameAs(npc)
        assertThat(game.getCreatureByName("MONSTER7")).isSameAs(monster)
        assertThat(game.getNpcByName("al dee")).isSameAs(npc)
        assertThat(game.getNpcByName("missing")).isNull()
    }

    @Test
    fun `removeNpc drops the npc from the lookup indexes`() {
        val npc = Npc(id = 100, name = "Al Dee", position = center)
        game.addNpc(npc)

        game.removeNpc(npc)

        assertThat(game.getNpcByName("al dee")).isNull()
        assertThat(game.getNpcByID(100)).isNull()
    }

    @Test
    fun `getPlayerNameByGUID resolves online players and caches the name`() {
        val player = player(42, "Hero")

        assertThat(game.getPlayerNameByGUID(42)).isEqualTo("Hero")
        assertThat(game.getPlayerNameByGUID(42)).isEqualTo("Hero")
        assertThat(game.getPlayerNameByGUID(0)).isEmpty()
    }

    @Test
    fun `getPlayerNameByGUID resolves offline players through the login seam`() {
        val offline = Player(id = 77, name = "Offline", maxHealth = 150, health = 150, position = center)
        val game = Game(
            GameMap(),
            GameDispatcher(),
            loadPlayerById = { guid -> if (guid == 77L) offline else null },
        )

        assertThat(game.getPlayerNameByGUID(77)).isEqualTo("Offline")
    }

    @Test
    fun `getPlayerByNameWildcard resolves the unique suffix through the tree`() {
        val player = player(1, "Hero")

        val (value, resolved) = game.getPlayerByNameWildcard("hero~")
        assertThat(value).isEqualTo(ReturnValue.NOERROR)
        assertThat(resolved).isSameAs(player)

        val (exactValue, exact) = game.getPlayerByNameWildcard("Hero")
        assertThat(exactValue).isEqualTo(ReturnValue.NOERROR)
        assertThat(exact).isSameAs(player)
    }

    @Test
    fun `getPlayerByNameWildcard reports unknown and ambiguous suffixes`() {
        player(1, "Hero")
        player(2, "Heroic")

        assertThat(game.getPlayerByNameWildcard("nobody~").first)
            .isEqualTo(ReturnValue.PLAYERWITHTHISNAMEISNOTONLINE)
        assertThat(game.getPlayerByNameWildcard("hero~").first)
            .isEqualTo(ReturnValue.NAMEISTOOAMBIGUOUS)
        assertThat(game.getPlayerByNameWildcard("").first)
            .isEqualTo(ReturnValue.PLAYERWITHTHISNAMEISNOTONLINE)
    }

    @Test
    fun `removePlayer drops the wildcard entry`() {
        val player = player(1, "Hero")
        game.removePlayer(player)

        assertThat(game.getPlayerByNameWildcard("hero~").first)
            .isEqualTo(ReturnValue.PLAYERWITHTHISNAMEISNOTONLINE)
    }

    @Test
    fun `dead player registry stores and drops players by name`() {
        val player = player(1, "Hero")

        game.addDeadPlayer(player)
        assertThat(game.getDeadPlayer("Hero")).isSameAs(player)

        game.removeDeadPlayer("Hero")
        assertThat(game.getDeadPlayer("Hero")).isNull()
    }
}
