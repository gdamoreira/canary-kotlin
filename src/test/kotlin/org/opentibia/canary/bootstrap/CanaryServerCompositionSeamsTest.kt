package org.opentibia.canary.bootstrap

import com.zaxxer.hikari.HikariDataSource
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.account.Account
import org.opentibia.canary.chat.SpeakClasses
import org.opentibia.canary.config.ConfigKeys
import org.opentibia.canary.config.ConfigManager
import org.opentibia.canary.config.ConfigSource
import org.opentibia.canary.config.ServerConfig
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.monsters.MonsterType
import org.opentibia.canary.creatures.monsters.Monsters
import org.opentibia.canary.creatures.vocations.Vocations
import org.opentibia.canary.database.DatabaseConfig
import org.opentibia.canary.database.DatabaseConnectionFactory
import org.opentibia.canary.database.JdbcAccountRepository
import org.opentibia.canary.database.JdbcFeatureStateRepository
import org.opentibia.canary.database.JdbcPlayerDepotRepository
import org.opentibia.canary.database.JdbcPlayerItemsRepository
import org.opentibia.canary.database.JdbcPlayerKillsRepository
import org.opentibia.canary.database.JdbcPlayerRepository
import org.opentibia.canary.database.JdbcPlayerRewardsRepository
import org.opentibia.canary.database.JdbcPlayerSpellsRepository
import org.opentibia.canary.database.JdbcPlayerStashRepository
import org.opentibia.canary.database.JdbcPlayerStorageRepository
import org.opentibia.canary.database.JdbcVipRepository
import org.opentibia.canary.game.scheduling.CreatureAsyncTaskBuckets
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.game.scheduling.MonsterComputeConfig
import org.opentibia.canary.game.scheduling.MonsterComputeService
import org.opentibia.canary.io.appearances.Appearances
import org.opentibia.canary.io.iologindata.IOLoginData
import org.opentibia.canary.io.iologindata.PlayerSavePath
import org.opentibia.canary.io.iologindata.PlayerSaveTransaction
import org.opentibia.canary.items.Items
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.CanaryServer
import org.opentibia.canary.network.codec.TibiaFrameDecoder
import org.opentibia.canary.network.codec.TibiaMessageEncoder
import org.opentibia.canary.network.crypto.Adler32Checksum
import org.opentibia.canary.network.crypto.RsaCipher
import org.opentibia.canary.network.message.writeTibiaString
import org.opentibia.canary.spells.WorldType
import org.opentibia.canary.systems.imbuement.Imbuements
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

/**
 * Composition-seam tests (wave 65, workstream B1) for the listener-level bindings
 * of the production `CanaryServer`:
 *
 *  - the `Tile::queryAdd` environment (src/items/tile.cpp:691-964): the
 *    `Tile` created by the world map carries the `g_game()`/
 *    `g_configManager()`/`ExpertPvp` seams bound to the composed world and the
 *    booted config (`CanaryServer::setWorldType`, canary_server.cpp:319-333);
 *  - the player-side `registerEvent` path: a session player resolves its
 *    attached creature events through `Creature::registerCreatureEvent`'s
 *    registry lookup (src/creatures/creature.cpp:1674-1693) instead of the
 *    permissive global walk, and the `ModalWindowManager` the `Game` holds
 *    routes answered windows through the world `ScriptingDispatch`
 *    (`Game::playerAnswerModalWindow`, game.cpp:11144-11176).
 */
class CanaryServerCompositionSeamsTest {

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

    private class BootedListener(
        val server: CanaryServer,
        val dispatcher: GameDispatcher,
        val service: MonsterComputeService,
        val databaseBoot: TestDatabaseBoot,
    ) : AutoCloseable {
        override fun close() {
            dispatcher.shutdown()
            service.shutdown()
            databaseBoot.dataSource.close()
        }
    }

    private val rsaCipher = RsaCipher()
    private val xteaKey = intArrayOf(0x01020304, 0x05060708, 0x090A0B0C, 0x0D0E0F10)

    private fun inlineService(): MonsterComputeService =
        MonsterComputeService().apply {
            start(MonsterComputeConfig(configuredThreads = 0, capacity = 16, hardwareConcurrency = 1))
        }

    private fun configManager(properties: Map<String, String> = emptyMap()): ConfigManager =
        ConfigManager(
            ConfigKeys.all.associateWith { it.luaIdentifier },
            properties = ConfigSource.of(properties),
        )

    /** Composes the production listener over a fresh database, exactly like [defaultListenerFactory]. */
    private fun bootListener(
        databaseBoot: TestDatabaseBoot,
        config: ConfigManager = configManager(),
        luaEnvironment: LuaEnvironment = LuaEnvironment().apply { initState() },
    ): BootedListener {
        databaseBoot.connect(null)
        databaseBoot.migrate(databaseBoot.dataSource)
        val service = inlineService()
        val dispatcher = GameDispatcher(monsterComputeService = service).apply { start() }
        val composition = GameComposition(
            dataSource = databaseBoot.dataSource,
            itemTypes = Items(),
            imbuements = Imbuements(),
            monsters = Monsters(),
            vocations = Vocations(),
            configManager = config,
            creatureAsyncTaskBuckets = CreatureAsyncTaskBuckets(dispatcher = { dispatcher }),
            monsterComputeService = service,
            playerSavePath = PlayerSavePath(
                transaction = PlayerSaveTransaction(databaseBoot.dataSource),
                loginData = IOLoginData(
                    players = JdbcPlayerRepository(databaseBoot.dataSource),
                    accounts = JdbcAccountRepository(databaseBoot.dataSource),
                    vip = JdbcVipRepository(databaseBoot.dataSource),
                    storageRepository = JdbcPlayerStorageRepository(databaseBoot.dataSource),
                    spells = JdbcPlayerSpellsRepository(databaseBoot.dataSource),
                    kills = JdbcPlayerKillsRepository(databaseBoot.dataSource),
                    stash = JdbcPlayerStashRepository(databaseBoot.dataSource),
                    rewards = JdbcPlayerRewardsRepository(databaseBoot.dataSource),
                    features = JdbcFeatureStateRepository(databaseBoot.dataSource),
                    items = JdbcPlayerItemsRepository(databaseBoot.dataSource),
                    depots = JdbcPlayerDepotRepository(databaseBoot.dataSource),
                ),
                players = JdbcPlayerRepository(databaseBoot.dataSource),
            ),
        )
        val server = defaultListenerFactory(rsaCipher).create(
            ServerConfig(loginPort = 0, gamePort = 0),
            JdbcAccountRepository(databaseBoot.dataSource),
            GameMap(),
            dispatcher,
            luaEnvironment,
            composition,
        ) as CanaryServer
        return BootedListener(server, dispatcher, service, databaseBoot)
    }

    private fun awaitLane(listener: BootedListener) {
        val ran = CountDownLatch(1)
        listener.dispatcher.dispatch { ran.countDown() }
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

    @Test
    fun `tiles materialized by the composed world receive the queryAdd seams`() {
        val databaseBoot = TestDatabaseBoot("seams_tile_${System.nanoTime()}")
        val config = configManager(
            mapOf(
                "maxPlayersOnlinePerAccount" to "7",
                "maxPlayersOutsidePZPerAccount" to "3",
                "worldType" to "expert-pvp",
            ),
        )
        bootListener(databaseBoot, config).use { listener ->
            val tile = listener.server.game.map.getOrCreateTile(Position(1000, 1000, 7))

            assertThat(tile.maxPlayersPerAccount).isEqualTo(7)
            assertThat(tile.maxPlayersOutsidePzPerAccount).isEqualTo(3)
            // `CanaryServer::setWorldType` (canary_server.cpp:319-327): expert-pvp
            // maps onto WORLD_TYPE_PVP and enables the ExpertPvp gate.
            assertThat(tile.worldTypeProvider()).isEqualTo(WorldType.PVP)
            assertThat(tile.expertPvpEnabled).isTrue()
            assertThat(tile.playerByGuidProvider(1L)).isNull()
            assertThat(tile.playersByAccountProvider(Account(id = 1L))).isEmpty()

            val monster = Monster(
                id = 7L,
                name = "Rat",
                maxHealth = 100,
                health = 100,
                position = Position(1000, 1000, 7),
            )
            val rat = MonsterType("Rat")
            assertThat(tile.monsterTypeProvider(monster)).isNull()
            assertThat(tile.ignoreFieldDamageProvider(monster)).isFalse()
            val host = listener.server.monsterPipelinesHost
            assertThat(host).isNotNull
            host!!.register(monster, rat)
            assertThat(tile.monsterTypeProvider(monster)).isSameAs(rat)
            host.entryOf(7L)!!.behaviorState.ignoreFieldDamage = true
            assertThat(tile.ignoreFieldDamageProvider(monster)).isTrue()

            // `g_game().internalCreatureSay` reach of the account-limit message
            // (tile.cpp:827): the tile seam forwards to the composed Game.
            val says = mutableListOf<String>()
            listener.server.game.sendCreatureSayTo = { _, _, _, text -> says += text }
            tile.sendCreatureSay(
                Player(1L, "Hero", 150, 150, position = Position(1000, 1000, 7)),
                monster,
                SpeakClasses.TALKTYPE_MONSTER_SAY,
                "hi",
                tile.position,
            )
            assertThat(says).containsExactly("hi")
        }
    }

    @Test
    fun `the session registerEvent path gates the attached modal window dispatch`() {
        val luaEnvironment = LuaEnvironment().apply { initState() }
        luaEnvironment.loadChunk(
            """
            local helper = CreatureEvent("ModalWindowHelper")
            function helper.onModalWindow(player, modalWindowId, buttonId, choiceId)
                seen_modal_player = player:getName()
                seen_modal_window = modalWindowId
                seen_modal_button = buttonId
                seen_modal_choice = choiceId
            end
            helper:register()
            """.trimIndent(),
        )
        val databaseBoot = TestDatabaseBoot("seams_events_${System.nanoTime()}")
        bootListener(databaseBoot, luaEnvironment = luaEnvironment).use { listener ->
            val channel = EmbeddedChannel(
                TibiaFrameDecoder(),
                TibiaMessageEncoder(),
                listener.server.createGameHandler(),
            )
            try {
                channel.writeInbound(loginPacket())
                assertThat(channel.readOutbound<ByteBuf>()).isNotNull
                awaitLane(listener)

                val player = listener.server.game.getPlayerByID(1L)
                assertThat(player).isNotNull

                // The permissive global walk is replaced by the attached set: an
                // unattached player dispatches nothing even though the event is
                // registered (creature.cpp:1674-1693).
                assertThat(listener.server.scriptingDispatch.playerAnswerModalWindow(player!!, 5, 1, 1)).isFalse()
                assertThat(luaEnvironment.getGlobal("seen_modal_window").isnil()).isTrue()

                assertThat(player.registerCreatureEvent("ModalWindowHelper")).isTrue()
                assertThat(listener.server.scriptingDispatch.playerAnswerModalWindow(player, 5, 1, 1)).isTrue()
                awaitLane(listener)

                assertThat(luaEnvironment.getGlobal("seen_modal_player").tojstring()).isEqualTo("Knight Hero")
                assertThat(luaEnvironment.getGlobal("seen_modal_window").toint()).isEqualTo(5)
                assertThat(luaEnvironment.getGlobal("seen_modal_button").toint()).isEqualTo(1)
                assertThat(luaEnvironment.getGlobal("seen_modal_choice").toint()).isEqualTo(1)
            } finally {
                channel.close()
            }
        }
    }

    @Test
    fun `the composed dispatch keeps the wave 64 monster host attachment resolution`() {
        val luaEnvironment = LuaEnvironment().apply { initState() }
        luaEnvironment.loadChunk(
            """
            local attached = CreatureEvent("AttachedThink")
            function attached.onThink(creature, interval)
                seen_attached_think = creature:getName()
            end
            attached:register()

            local other = CreatureEvent("OtherThink")
            function other.onThink(creature, interval)
                seen_other_think = creature:getName()
            end
            other:register()
            """.trimIndent(),
        )
        val databaseBoot = TestDatabaseBoot("seams_monster_events_${System.nanoTime()}")
        bootListener(databaseBoot, luaEnvironment = luaEnvironment).use { listener ->
            val monster = Monster(
                id = 7L,
                name = "Rat",
                maxHealth = 100,
                health = 100,
                position = Position(1000, 1000, 7),
            )
            val host = listener.server.monsterPipelinesHost
            assertThat(host).isNotNull
            host!!.register(monster, MonsterType("Rat"))
            assertThat(host.typeStateOf(7L)!!.registerCreatureEvent("AttachedThink")).isTrue()

            assertThat(listener.server.scriptingDispatch.creatureOnThink(monster, 1000)).isTrue()
            awaitLane(listener)

            assertThat(luaEnvironment.getGlobal("seen_attached_think").tojstring()).isEqualTo("Rat")
            assertThat(luaEnvironment.getGlobal("seen_other_think").isnil()).isTrue()
        }
    }
}
