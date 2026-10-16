package org.opentibia.canary.bootstrap

import com.zaxxer.hikari.HikariDataSource
import org.opentibia.canary.network.protocol.CLIENT_VERSION
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.config.ConfigKeys
import org.opentibia.canary.config.ConfigManager
import org.opentibia.canary.config.ServerConfig
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.PlayerFlags_t
import org.opentibia.canary.database.DatabaseConfig
import org.opentibia.canary.database.DatabaseConnectionFactory
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.game.scheduling.MonsterComputeConfig
import org.opentibia.canary.game.scheduling.MonsterComputeService
import org.opentibia.canary.io.appearances.Appearances
import org.opentibia.canary.items.Item
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
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

/**
 * Production-composition tests (wave 67, task D3) for the login/boot bindings:
 *
 *  - `g_game().groups` must load `{CORE_DIRECTORY}/XML/groups.xml`
 *    (`Groups::load`, src/creatures/players/grouping/groups.cpp:78-101) and the
 *    login must resolve the persisted `players.group_id` through it
 *    (`IOLoginDataLoad::loadPlayerBasicInfo`,
 *    src/io/functions/iologindata_load_player.cpp:140-145);
 *  - the world `Game::getGuild`/`getGuildByName` offline fallback
 *    (game.cpp:11727-11748) must resolve through the composed
 *    `IOGuild::loadGuild` repository;
 *  - the `g_saveManager().savePlayer(player)` seam (game.cpp:10816-11111) must
 *    flush through the production `SaveManager`.
 *
 * The root is booted exactly like production ([ServerBootstrap.run]) over an
 * in-memory H2 database and the production [defaultListenerFactory].
 */
class ServerBootstrapLoginBindingsTest {

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
    ) {
        val server: CanaryServer get() = listener as CanaryServer
    }

    private val rsaCipher = RsaCipher()
    private val xteaKey = intArrayOf(0x01020304, 0x05060708, 0x090A0B0C, 0x0D0E0F10)

    private fun inlineService(): MonsterComputeService =
        MonsterComputeService().apply {
            start(MonsterComputeConfig(configuredThreads = 0, capacity = 16, hardwareConcurrency = 1))
        }

    private fun configManager(): ConfigManager =
        ConfigManager(ConfigKeys.all.associateWith { it.luaIdentifier })

    /** Boots the production root with the default listener factory and captures its composition. */
    private fun bootRoot(databaseBoot: TestDatabaseBoot, coreDir: Path? = null): BootedRoot {
        var composition: GameComposition? = null
        var listener: ListenerBoot? = null
        var dispatcher: GameDispatcher? = null
        val production = defaultListenerFactory(rsaCipher)
        val factory = ListenerFactory { config, accounts, map, bootedDispatcher, lua, booted ->
            composition = booted
            dispatcher = bootedDispatcher
            production.create(config, accounts, map, bootedDispatcher, lua, booted).also { listener = it }
        }
        val config = ServerConfig(loginPort = 0, gamePort = 0, coreDir = coreDir?.toString() ?: "data")
        val bootstrap = ServerBootstrap(
            configBoot = ConfigBoot { _, _ -> config },
            configManagerBoot = ConfigManagerBoot { _, _ -> configManager() },
            databaseBoot = databaseBoot,
            // The seeded `Knight Hero` login position (V2 migration: 1000,1000,7).
            mapBoot = MapBoot { _, _, _, _ ->
                GameMap().apply {
                    getOrCreateTile(Position(1000, 1000, 7)).ground = Item(id = 106)
                    towns.getOrCreateTown(1).apply {
                        setName("Thais")
                        setTemplePos(Position(1000, 1000, 7))
                    }
                }
            },
            scriptingBoot = ScriptingBoot { _, _, _ -> LuaEnvironment().apply { initState() } },
            appearanceBoot = AppearanceBoot { Appearances.load(ByteArray(0)) },
            itemBoot = ItemBoot { _, _ -> Items() },
            imbuementBoot = ImbuementBoot { Imbuements() },
            listenerFactory = factory,
            monsterComputeService = inlineService(),
        )
        bootstrap.run()
        return BootedRoot(bootstrap, composition!!, listener!!, dispatcher!!)
    }

    private fun awaitLane(root: BootedRoot) {
        val ran = CountDownLatch(1)
        root.dispatcher.dispatch { ran.countDown() }
        assertThat(ran.await(2, TimeUnit.SECONDS)).isTrue()
    }

    /** Logs the seeded `Knight Hero` in through the production game handler. */
    private fun loginSession(root: BootedRoot): Pair<Player, EmbeddedChannel> {
        val channel = EmbeddedChannel(
            TibiaFrameDecoder(),
            TibiaMessageEncoder(),
            root.server.createGameHandler(),
        )
        channel.writeInbound(loginPacket())
        assertThat(channel.readOutbound<ByteBuf>()).isNotNull
        awaitLane(root)
        val player = root.server.game.getPlayerByID(1L)
        assertThat(player).isNotNull
        return player!! to channel
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
        body.writeShortLE(CLIENT_VERSION)
        body.writeIntLE(CLIENT_VERSION)
        body.writeTibiaString("")
        body.writeTibiaString("")
        body.writeByte(0)
        body.writeBytes(rsaCipher.encrypt(rsaPlain))

        val checksum = Adler32Checksum.compute(body, 0, body.readableBytes())

        val packet = Unpooled.buffer()
        packet.writeShortLE(4 + body.readableBytes())
        packet.writeIntLE(checksum.toInt())
        packet.writeBytes(body)
        return packet
    }

    private fun groupsXml(): String = """
        <groups>
            <group id="1" name="player" access="0" maxdepotitems="0" maxvipentries="0" />
            <group id="2" name="tutor" access="0" maxdepotitems="0" maxvipentries="200">
                <flags>
                    <flag talkorangehelpchannel="1" />
                    <flag cannotbemuted="1" />
                </flags>
            </group>
            <group id="4" name="gamemaster" access="1" maxdepotitems="0" maxvipentries="200" />
        </groups>
    """.trimIndent()

    @Test
    fun `the production root loads groups xml and the login resolves the persisted group`() {
        val coreDir = Files.createTempDirectory("canary_groups_boot")
        Files.createDirectories(coreDir.resolve("XML"))
        Files.writeString(coreDir.resolve("XML").resolve("groups.xml"), groupsXml())

        val databaseBoot = TestDatabaseBoot("bootstrap_login_groups_${System.nanoTime()}")
        val root = bootRoot(databaseBoot, coreDir)
        try {
            databaseBoot.dataSource.connection.use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeUpdate("UPDATE players SET group_id = 2 WHERE id = 1")
                }
            }

            val (player, channel) = loginSession(root)
            try {
                assertThat(player.group).isNotNull
                assertThat(player.group!!.name).isEqualTo("tutor")
                assertThat(player.group!!.maxVipEntries).isEqualTo(200)
                assertThat(player.group!!.flags).containsExactlyInAnyOrder(
                    PlayerFlags_t.TalkOrangeHelpChannel,
                    PlayerFlags_t.CannotBeMuted,
                )
            } finally {
                channel.close()
            }
        } finally {
            root.bootstrap.shutdown()
        }
    }

    @Test
    fun `the production world resolves an offline guild through the composed repository`() {
        val databaseBoot = TestDatabaseBoot("bootstrap_login_guild_${System.nanoTime()}")
        val root = bootRoot(databaseBoot)
        try {
            databaseBoot.dataSource.connection.use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeUpdate(
                        "INSERT INTO guilds (id, name, ownerid, balance) VALUES (7, 'Wolves', 1, 0)",
                    )
                }
            }

            val game = root.server.game
            assertThat(game.getGuild(7L)).isNull()
            val guild = game.getGuild(7L, allowOffline = true)
            assertThat(guild).isNotNull
            assertThat(guild!!.name).isEqualTo("Wolves")
            // `Game::getGuildByName` resolves the id through
            // `IOGuild::getGuildIdByName`, then loads like `getGuild`
            // (game.cpp:11738-11748).
            assertThat(game.getGuildByName("Wolves", allowOffline = true)!!.id).isEqualTo(guild.id)
        } finally {
            root.bootstrap.shutdown()
        }
    }

    @Test
    fun `the production world savePlayer seam flushes through the save manager`() {
        val databaseBoot = TestDatabaseBoot("bootstrap_login_save_${System.nanoTime()}")
        val root = bootRoot(databaseBoot)
        try {
            val (player, channel) = loginSession(root)
            try {
                player.health = 1234
                root.server.game.savePlayer(player)

                databaseBoot.dataSource.connection.use { connection ->
                    connection.prepareStatement("SELECT health FROM players WHERE id = 1").use { statement ->
                        statement.executeQuery().use { result ->
                            assertThat(result.next()).isTrue()
                            assertThat(result.getInt(1)).isEqualTo(1234)
                        }
                    }
                }
            } finally {
                channel.close()
            }
        } finally {
            root.bootstrap.shutdown()
        }
    }
}
