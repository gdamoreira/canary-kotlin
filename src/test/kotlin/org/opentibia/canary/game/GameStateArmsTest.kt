package org.opentibia.canary.game

import com.zaxxer.hikari.HikariDataSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.opentibia.canary.config.ConfigKeys
import org.opentibia.canary.config.ConfigManager
import org.opentibia.canary.config.ConfigSource
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.PlayerFlags_t
import org.opentibia.canary.database.Database
import org.opentibia.canary.database.DatabaseConnectionFactory
import org.opentibia.canary.database.SqlDialect
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position

/**
 * Transcript tests for the `Game::setGameState` shutdown arms
 * (src/game/game.cpp:995-1030) and the game-state login gate folded from
 * `ProtocolGame::onRecvFirstMessage` (protocolgame.cpp:1388-1396) and
 * `ProtocolGame::login` (protocolgame.cpp:955-966):
 *
 *  - `GAME_STATE_SHUTDOWN`: `g_globalEvents().save()/shutdown()`, the kick-all
 *    `Player::removePlayer(true)` loop, `saveMotdNum()`,
 *    `g_saveManager().saveAll()` and the dispatcher-enqueued `Game::shutdown`.
 *  - `GAME_STATE_CLOSED`: the global-event save, the kick of every player
 *    without `CanAlwaysLogin` and the save-all.
 *  - `GAME_STATE_MAINTAIN` (and the boot `TOGGLE_MAINTAIN_MODE` CLOSED mode)
 *    refusing logins with the exact Canary messages.
 */
class GameStateArmsTest {

    private lateinit var dataSource: HikariDataSource
    private lateinit var database: Database

    @BeforeEach
    fun setUp() {
        dataSource = DatabaseConnectionFactory.createInMemoryDataSource("game_state_arms_${System.nanoTime()}")
        assertThat(DatabaseConnectionFactory.migrate(dataSource)).isGreaterThanOrEqualTo(10)
        database = Database(dataSource)
    }

    @AfterEach
    fun tearDown() {
        dataSource.close()
    }

    private fun configValue(config: String): String? =
        dataSource.connection.use { connection ->
            connection.prepareStatement("SELECT \"value\" FROM server_config WHERE \"config\" = ?").use { statement ->
                statement.setString(1, config)
                statement.executeQuery().use { result ->
                    if (result.next()) result.getString(1) else null
                }
            }
        }

    private fun player(id: Long, name: String): Player = Player(
        id = id,
        name = name,
        maxHealth = 150,
        health = 150,
        position = Position(100, 100, 7),
    )

    private fun newGame(configManager: ConfigManager? = null): Game = Game(
        GameMap(),
        GameDispatcher(),
        database = database,
        sqlDialect = { SqlDialect.H2 },
        configManager = configManager,
    )

    @Test
    fun `the shutdown arm saves global events kicks every player and schedules the shutdown`() {
        val game = newGame()
        game.serverMotd = "Welcome"
        game.incrementMotdNum()
        val first = player(1L, "First")
        val second = player(2L, "Second")
        game.addPlayer(first)
        game.addPlayer(second)

        val events = mutableListOf<String>()
        val kicks = mutableListOf<Pair<Long, Boolean>>()
        game.saveGlobalEvents = { events += "globalSave" }
        game.shutdownGlobalEvents = { events += "globalShutdown" }
        game.playerRemovePlayer = { player, effect -> kicks += player.id to effect }
        game.saveAllPlayers = { events += "saveAll" }
        game.scheduleShutdown = { events += "scheduleShutdown" }

        game.setGameState(GameState_t.GAME_STATE_SHUTDOWN)

        assertThat(kicks).containsExactlyInAnyOrder(1L to true, 2L to true)
        assertThat(game.getPlayers()).isEmpty()
        assertThat(events).containsExactly("globalSave", "globalShutdown", "saveAll", "scheduleShutdown")
        assertThat(configValue("motd_num")).isEqualTo("1")
        assertThat(game.getGameState()).isEqualTo(GameState_t.GAME_STATE_SHUTDOWN)
    }

    @Test
    fun `the closed arm kicks only players without CanAlwaysLogin and saves`() {
        val game = newGame()
        val staff = player(1L, "Staff")
        staff.setFlag(PlayerFlags_t.CanAlwaysLogin)
        val regular = player(2L, "Regular")
        game.addPlayer(staff)
        game.addPlayer(regular)

        val events = mutableListOf<String>()
        val kicks = mutableListOf<Pair<Long, Boolean>>()
        game.saveGlobalEvents = { events += "globalSave" }
        game.playerRemovePlayer = { player, effect -> kicks += player.id to effect }
        game.saveAllPlayers = { events += "saveAll" }

        game.setGameState(GameState_t.GAME_STATE_CLOSED)

        assertThat(kicks).containsExactly(2L to true)
        assertThat(game.getPlayerByID(1L)).isSameAs(staff)
        assertThat(game.getPlayerByID(2L)).isNull()
        assertThat(events).containsExactly("globalSave", "saveAll")
    }

    @Test
    fun `maintain mode refuses logins with the canary message`() {
        val game = newGame()
        game.setGameState(GameState_t.GAME_STATE_MAINTAIN)

        assertThat(game.getGameState()).isEqualTo(GameState_t.GAME_STATE_MAINTAIN)
        assertThat(game.loginRefusal(player(1L, "Visitor")))
            .isEqualTo("Gameworld is under maintenance. Please re-connect in a while.")
    }

    @Test
    fun `the closed boot mode refuses logins unless the player can always login`() {
        val config = ConfigManager(
            keyMap = mapOf(
                ConfigKeys.byName("TOGGLE_MAINTAIN_MODE")!! to "TOGGLE_MAINTAIN_MODE",
                ConfigKeys.byName("MAINTAIN_MODE_MESSAGE")!! to "MAINTAIN_MODE_MESSAGE",
            ),
            properties = ConfigSource.of(mapOf("MAINTAIN_MODE_MESSAGE" to "Down for maintenance.")),
        )
        val configured = newGame(config)
        configured.setGameState(GameState_t.GAME_STATE_CLOSED)

        assertThat(configured.loginRefusal(player(2L, "Regular"))).isEqualTo("Down for maintenance.")
        val staff = player(3L, "Staff")
        staff.setFlag(PlayerFlags_t.CanAlwaysLogin)
        assertThat(configured.loginRefusal(staff)).isNull()

        val plain = newGame()
        plain.setGameState(GameState_t.GAME_STATE_CLOSED)
        assertThat(plain.loginRefusal(player(4L, "Other")))
            .isEqualTo("Server is currently closed.\nPlease try again later.")
    }

    @Test
    fun `the startup and shutdown states fold the raw disconnect messages`() {
        val starting = newGame()
        starting.setGameState(GameState_t.GAME_STATE_STARTUP)
        assertThat(starting.loginRefusal(player(1L, "Visitor")))
            .isEqualTo("Gameworld is starting up. Please wait.")

        val shutting = newGame()
        shutting.setGameState(GameState_t.GAME_STATE_SHUTDOWN)
        assertThat(shutting.loginRefusal(null)).isEmpty()
    }

    @Test
    fun `the closing gate spares CanAlwaysLogin players and normal admits everyone`() {
        val closing = newGame()
        closing.setGameState(GameState_t.GAME_STATE_CLOSING)
        assertThat(closing.loginRefusal(player(1L, "Regular")))
            .isEqualTo("The game is just going down.\nPlease try again later.")
        val staff = player(2L, "Staff")
        staff.setFlag(PlayerFlags_t.CanAlwaysLogin)
        assertThat(closing.loginRefusal(staff)).isNull()

        val open = newGame()
        assertThat(open.loginRefusal(player(3L, "Visitor"))).isNull()
    }
}
