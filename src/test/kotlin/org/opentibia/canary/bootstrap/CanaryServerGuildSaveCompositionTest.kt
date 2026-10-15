package org.opentibia.canary.bootstrap

import com.zaxxer.hikari.HikariDataSource
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.config.ConfigKeys
import org.opentibia.canary.config.ConfigManager
import org.opentibia.canary.config.ServerConfig
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.database.DatabaseConfig
import org.opentibia.canary.database.DatabaseConnectionFactory
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.game.scheduling.MonsterComputeConfig
import org.opentibia.canary.game.scheduling.MonsterComputeService
import org.opentibia.canary.io.appearances.Appearances
import org.opentibia.canary.items.Items
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.CanaryServer
import org.opentibia.canary.network.ListenerBoot
import org.opentibia.canary.network.codec.TibiaFrameDecoder
import org.opentibia.canary.network.codec.TibiaMessageEncoder
import org.opentibia.canary.network.crypto.Adler32Checksum
import org.opentibia.canary.network.crypto.RsaCipher
import org.opentibia.canary.network.message.writeTibiaString
import org.opentibia.canary.systems.imbuement.Imbuements
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

/**
 * Production-composition tests (wave 64, task A2) for the guild registry and
 * the session save rotation:
 *
 *  - `IOLoginDataLoad::loadPlayerGuild` (iologindata_load_player.cpp:394-442)
 *    must resolve the guild through the world `Game::getGuild` registry and
 *    register a loaded guild with `Game::addGuild` (game.cpp:11750), so two
 *    members logging in share one `Guild` instance.
 *  - the session installed by `ProtocolGame::login` (`Game::addPlayer`,
 *    game.cpp:11624) and removed on disconnect (`Game::removePlayer`,
 *    game.cpp:11631) must enter/leave the `SaveManager` rotation
 *    (`SaveManager::saveAll` iterates `g_game().getPlayers()`,
 *    save_manager.cpp:31-52).
 *
 * The root is booted exactly like production ([ServerBootstrap.run]) over an
 * in-memory H2 database and the production [defaultListenerFactory], so the
 * assertions cover the real composition rather than a hand-wired fixture.
 */
class CanaryServerGuildSaveCompositionTest {

    /** Unique in-memory H2 database booted and migrated like the production root. */
    private class TestDatabaseBoot(private val name: String) : DatabaseBoot {
        lateinit var dataSource: HikariDataSource
            private set

        override fun connect(config: DatabaseConfig?): DataSource {
            dataSource = DatabaseConnectionFactory.createInMemoryDataSource(name)
            return dataSource
        }

        override fun migrate(dataSource: DataSource): Int = DatabaseConnectionFactory.migrate(dataSource)

        override fun close(dataSource: DataSource) {
            (dataSource as? AutoCloseable)?.close()
        }
    }

    private class BootedRoot(
        val bootstrap: ServerBootstrap,
        val composition: GameComposition,
        val listener: ListenerBoot,
        val dispatcher: GameDispatcher,
    )

    private val rsaCipher = RsaCipher()
    private val xteaKey = intArrayOf(0x01020304, 0x05060708, 0x090A0B0C, 0x0D0E0F10)

    private fun inlineService(): MonsterComputeService =
        MonsterComputeService().apply {
            start(MonsterComputeConfig(configuredThreads = 0, capacity = 16, hardwareConcurrency = 1))
        }

    private fun configManager(): ConfigManager =
        ConfigManager(ConfigKeys.all.associateWith { it.luaIdentifier })

    /** Boots the production root with the default listener factory and captures its composition. */
    private fun bootRoot(databaseBoot: TestDatabaseBoot): BootedRoot {
        var composition: GameComposition? = null
        var listener: ListenerBoot? = null
        var dispatcher: GameDispatcher? = null
        val production = defaultListenerFactory(rsaCipher)
        val factory = ListenerFactory { config, accounts, map, bootedDispatcher, lua, booted ->
            composition = booted
            dispatcher = bootedDispatcher
            production.create(config, accounts, map, bootedDispatcher, lua, booted).also { listener = it }
        }
        val bootstrap = ServerBootstrap(
            configBoot = ConfigBoot { _, _ -> ServerConfig(loginPort = 0, gamePort = 0) },
            configManagerBoot = ConfigManagerBoot { _, _ -> configManager() },
            databaseBoot = databaseBoot,
            mapBoot = MapBoot { GameMap() },
            scriptingBoot = ScriptingBoot { LuaEnvironment().apply { initState() } },
            appearanceBoot = AppearanceBoot { Appearances.load(ByteArray(0)) },
            itemBoot = ItemBoot { _, _ -> Items() },
            imbuementBoot = ImbuementBoot { Imbuements() },
            listenerFactory = factory,
            monsterComputeService = inlineService(),
        )
        bootstrap.run()
        return BootedRoot(bootstrap, composition!!, listener!!, dispatcher!!)
    }

    private fun hero(id: Long, name: String): Player = Player(
        id = id,
        name = name,
        maxHealth = 150,
        health = 150,
        position = Position(1000, 1000, 7),
    )

    private fun seedGuild(dataSource: DataSource) {
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate(
                    "INSERT INTO guilds (id, name, ownerid, balance) VALUES (7, 'Wolves', 1, 0)",
                )
                statement.executeUpdate(
                    "INSERT INTO guild_ranks (id, guild_id, name, level) VALUES (11, 7, 'Member', 1)",
                )
                statement.executeUpdate(
                    "INSERT INTO guild_membership (player_id, guild_id, rank_id, nick) VALUES (1, 7, 11, 'Wolfie')",
                )
                statement.executeUpdate(
                    "INSERT INTO guild_membership (player_id, guild_id, rank_id, nick) VALUES (2, 7, 11, '')",
                )
            }
        }
    }

    @Test
    fun `two logins of the same guild share one instance registered in the game`() {
        val databaseBoot = TestDatabaseBoot("guild_save_share_${System.nanoTime()}")
        val root = bootRoot(databaseBoot)
        try {
            seedGuild(databaseBoot.dataSource)
            val loginData = root.composition.playerSavePath.loginData
            val first = hero(1L, "Knight Hero")
            val second = hero(2L, "Sorcerer Master")

            assertThat(loginData.loadPlayerById(first, 1L, disableIrrelevantInfo = false)).isTrue()
            assertThat(loginData.loadPlayerById(second, 2L, disableIrrelevantInfo = false)).isTrue()

            val shared = first.getGuild()
            assertThat(shared).isNotNull()
            assertThat(second.getGuild()).isNotNull()
            assertThat(second.getGuild()).isSameAs(shared)

            val game = (root.listener as CanaryServer).game
            assertThat(game.getGuild(7L)).isSameAs(shared)
            assertThat(game.getGuilds()).containsOnlyKeys(7L)
        } finally {
            root.bootstrap.shutdown()
        }
    }

    @Test
    fun `the production session install and removal drive the save rotation`() {
        val databaseBoot = TestDatabaseBoot("guild_save_session_${System.nanoTime()}")
        val root = bootRoot(databaseBoot)
        try {
            val saveManager = root.composition.saveManager
            assertThat(saveManager).isNotNull()
            val server = root.listener as CanaryServer

            val channel = EmbeddedChannel(TibiaFrameDecoder(), TibiaMessageEncoder(), server.createGameHandler())
            channel.writeInbound(loginPacket())
            assertThat(channel.readOutbound<ByteBuf>()).isNotNull()
            awaitLane(root)

            assertThat(server.game.getPlayerByID(1L)).isNotNull()
            assertThat(saveManager!!.onlinePlayerCount).isEqualTo(1)
            assertThat(saveManager.saveAll()).isEqualTo(1)

            channel.close()
            awaitLane(root)

            assertThat(server.game.getPlayerByID(1L)).isNull()
            assertThat(saveManager.onlinePlayerCount).isZero()
        } finally {
            root.bootstrap.shutdown()
        }
    }

    private fun awaitLane(root: BootedRoot) {
        val ran = CountDownLatch(1)
        root.dispatcher.dispatch { ran.countDown() }
        assertThat(ran.await(2, TimeUnit.SECONDS)).isTrue()
    }

    private fun loginPacket(): ByteBuf {
        val rsaPlain = ByteArray(RsaCipher.BLOCK_SIZE)
        val rsaBuf = Unpooled.wrappedBuffer(rsaPlain)
        rsaBuf.writerIndex(0)
        rsaBuf.writeByte(0) // check byte
        for (key in xteaKey) {
            rsaBuf.writeIntLE(key)
        }
        rsaBuf.writeByte(0) // GM flag
        rsaBuf.writeTibiaString("1\n1") // session key
        rsaBuf.writeTibiaString("Knight Hero") // character name

        val body = Unpooled.buffer()
        body.writeShortLE(2) // OS
        body.writeShortLE(1098) // Protocol version
        body.writeBytes(rsaCipher.encrypt(rsaPlain))

        val checksum = Adler32Checksum.compute(body, 0, body.readableBytes())

        val packet = Unpooled.buffer()
        packet.writeShortLE(4 + body.readableBytes())
        packet.writeIntLE(checksum.toInt())
        packet.writeBytes(body)
        return packet
    }
}
