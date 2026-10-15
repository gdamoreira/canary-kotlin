package org.opentibia.canary.network

import com.zaxxer.hikari.HikariDataSource
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.opentibia.canary.config.ConfigKeys
import org.opentibia.canary.config.ConfigManager
import org.opentibia.canary.creatures.monsters.Monsters
import org.opentibia.canary.database.DatabaseConnectionFactory
import org.opentibia.canary.game.GameState_t
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.utils.Tools

/**
 * Production-composition tests for the `GAME_STATE_INIT` boot trigger:
 * [CanaryServer.start] calls `Game::setGameState(GAME_STATE_INIT)`
 * (src/game/game.cpp:963-993) exactly once, the wired pieces run in the C++
 * statement order (raids before `g_globalEvents().startup()`), the mounts and
 * attached effects load from `{CORE_DIRECTORY}/XML`, the persisted MOTD/players
 * record seed the world and the boot ends in `GAME_STATE_NORMAL`
 * (src/canary_server.cpp:225-249).
 */
class CanaryServerGameStateInitTest {

    @TempDir
    lateinit var tempDir: Path

    @TempDir
    lateinit var coreDir: Path

    private lateinit var dispatcher: GameDispatcher

    private val mountsXml = """
        <mounts>
            <mount id="1" clientid="1" name="Widow Queen" speed="10" premium="yes"/>
        </mounts>
    """.trimIndent()

    private val attachedEffectsXml = """
        <attachedeffects>
            <wing id="1" name="Wing of Light"/>
        </attachedeffects>
    """.trimIndent()

    @BeforeEach
    fun setUp() {
        dispatcher = GameDispatcher().apply { start() }
    }

    @AfterEach
    fun tearDown() {
        dispatcher.shutdown()
    }

    private fun writeXml(file: File, content: String) {
        file.parentFile.mkdirs()
        file.writeText(content)
    }

    private fun writeRaidFixture() {
        writeXml(
            File(tempDir.toFile(), "raids/BootRaid.xml"),
            """<raid><announce delay="1000" type="event" message="Boot!"/></raid>""",
        )
        writeXml(
            File(tempDir.toFile(), "raids/raids.xml"),
            """<raids><raid name="BootRaid" file="BootRaid.xml" interval2="1" margin="0"/></raids>""",
        )
    }

    private fun server(
        core: String? = coreDir.toString(),
        dataSource: HikariDataSource? = null,
        dataDir: String? = null,
    ): CanaryServer {
        val gameMap = GameMap()
        if (dataDir != null) {
            gameMap.monsterfile = File(tempDir.toFile(), "canary-monster.xml").apply { writeText("<monsters/>") }.path
        }
        return CanaryServer(
            loginPort = 0,
            gamePort = 0,
            gameMap = gameMap,
            dispatcher = dispatcher,
            configManager = ConfigManager(keyMap = ConfigKeys.all.associateWith { it.luaIdentifier }),
            luaEnvironment = LuaEnvironment().apply { initState() },
            monsters = Monsters(),
            dataDirectory = dataDir,
            coreDirectory = core,
            dataSource = dataSource,
        )
    }

    @Test
    fun `start triggers the init pieces once in the canary boot order`() {
        writeXml(File(coreDir.toFile(), "XML/mounts.xml"), mountsXml)
        writeXml(File(coreDir.toFile(), "XML/attachedeffects.xml"), attachedEffectsXml)
        writeRaidFixture()
        val server = server(dataDir = tempDir.toFile().path)

        var initRuns = 0
        var raidsLoadedAtGlobalEvents: Boolean? = null
        val globalEvents = server.game.initGlobalEvents
        server.game.initGlobalEvents = {
            initRuns++
            raidsLoadedAtGlobalEvents = server.raids?.isLoaded()
            globalEvents()
        }

        server.start()
        try {
            // `Game::setGameState(GAME_STATE_INIT)` runs exactly once per boot.
            assertThat(initRuns).isEqualTo(1)
            // `raids.loadFromXml(); raids.startup()` (game.cpp:979-980) precede
            // `g_globalEvents().startup()` (game.cpp:988).
            assertThat(raidsLoadedAtGlobalEvents).isTrue()

            val raids = requireNotNull(server.raids)
            assertThat(raids.isStarted()).isTrue()

            assertThat(server.mounts.getMountByName("Widow Queen")).isNotNull
            assertThat(server.game.getMountByClientID(1)).isNotNull
            assertThat(server.game.getAttachedEffects().getWingByName("Wing of Light")).isNotNull

            // `g_game().setGameState(GAME_STATE_NORMAL)` (src/canary_server.cpp:249).
            assertThat(server.game.getGameState()).isEqualTo(GameState_t.GAME_STATE_NORMAL)
        } finally {
            server.stop()
        }
    }

    @Test
    fun `the boot loads the persisted motd counter and players record`() {
        val dataSource = DatabaseConnectionFactory.createInMemoryDataSource("canary_init_motd_${System.nanoTime()}")
        assertThat(DatabaseConnectionFactory.migrate(dataSource)).isGreaterThanOrEqualTo(10)
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate("UPDATE server_config SET \"value\" = '7' WHERE config = 'motd_num'")
                statement.executeUpdate(
                    "UPDATE server_config SET \"value\" = '${Tools.transformToSHA1("")}' WHERE config = 'motd_hash'",
                )
                statement.executeUpdate("UPDATE server_config SET \"value\" = '9' WHERE config = 'players_record'")
            }
        }

        val server = server(core = null, dataSource = dataSource)
        try {
            server.start()

            assertThat(server.game.getMotdNum()).isEqualTo(7)
            assertThat(server.game.getMotdHash()).isEqualTo(Tools.transformToSHA1(""))
            assertThat(server.game.playersRecord).isEqualTo(9)
            // The login-path record tracker is seeded from the loaded value: a
            // single online cannot beat the stored record of 9.
            assertThat(server.onPlayerAppear()).isFalse()
            server.onPlayerDisappear()
        } finally {
            server.stop()
            dataSource.close()
        }
    }

    @Test
    fun `a new online peak persists the players record through the world seam`() {
        val dataSource = DatabaseConnectionFactory.createInMemoryDataSource("canary_init_record_${System.nanoTime()}")
        assertThat(DatabaseConnectionFactory.migrate(dataSource)).isGreaterThanOrEqualTo(10)
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate("UPDATE server_config SET \"value\" = '2' WHERE config = 'players_record'")
            }
        }

        val server = server(core = null, dataSource = dataSource)
        try {
            server.start()
            assertThat(server.game.playersRecord).isEqualTo(2)

            // Two logins do not beat the stored record of two; the third does.
            // `Game::checkPlayersRecord` advances the peak and
            // `Game::updatePlayersRecord` persists it (game.cpp:9625-9644).
            repeat(2) { server.onPlayerAppear() }
            assertThat(server.game.playersRecord).isEqualTo(2)
            assertThat(readPlayersRecord(dataSource)).isEqualTo("2")

            server.onPlayerAppear()
            assertThat(server.game.playersRecord).isEqualTo(3)
            assertThat(readPlayersRecord(dataSource)).isEqualTo("3")
        } finally {
            server.stop()
            dataSource.close()
        }
    }

    @Test
    fun `a boot without a core directory keeps the mount and attached-effect loads inert`() {
        val server = server(core = null)

        server.start()
        try {
            assertThat(server.mounts.getMounts()).isEmpty()
            assertThat(server.game.getAttachedEffects().getAuras()).isEmpty()
            assertThat(server.game.getAttachedEffects().getWings()).isEmpty()
            assertThat(server.game.getMountByClientID(1)).isNull()
            assertThat(server.game.getGameState()).isEqualTo(GameState_t.GAME_STATE_NORMAL)
        } finally {
            server.stop()
        }
    }

    private fun readPlayersRecord(dataSource: HikariDataSource): String =
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT \"value\" FROM server_config WHERE config = 'players_record'")
                    .use { result ->
                        assertThat(result.next()).isTrue
                        result.getString("value")
                    }
            }
        }

    @Test
    fun `the loaded core xml is read from the configured core directory`() {
        writeXml(File(coreDir.toFile(), "XML/mounts.xml"), mountsXml)
        val server = server()

        server.start()
        try {
            assertThat(Files.exists(coreDir.resolve("XML/mounts.xml"))).isTrue()
            assertThat(server.mounts.getMountByID(1)?.clientId).isEqualTo(1)
        } finally {
            server.stop()
        }
    }
}
