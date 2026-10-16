package org.opentibia.canary.bootstrap

import com.zaxxer.hikari.HikariDataSource
import org.opentibia.canary.network.protocol.CLIENT_VERSION
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.condition.ConditionId
import org.opentibia.canary.combat.condition.ConditionSpellCooldown
import org.opentibia.canary.combat.condition.ConditionSpellGroupCooldown
import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.combat.condition.SpellGroup
import org.opentibia.canary.config.ConfigKeys
import org.opentibia.canary.config.ConfigManager
import org.opentibia.canary.config.ConfigSource
import org.opentibia.canary.config.ServerConfig
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.CyclopediaMonsterTrackerClient
import org.opentibia.canary.creatures.monsters.MonsterType
import org.opentibia.canary.creatures.monsters.Monsters
import org.opentibia.canary.creatures.players.vip.VipStatus
import org.opentibia.canary.database.DatabaseConfig
import org.opentibia.canary.database.DatabaseConnectionFactory
import org.opentibia.canary.database.JdbcVipRepository
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.game.scheduling.MonsterComputeConfig
import org.opentibia.canary.game.scheduling.MonsterComputeService
import org.opentibia.canary.io.IOBestiaryPersistence
import org.opentibia.canary.io.appearances.Appearances
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.Items
import org.opentibia.canary.kv.KVSQL
import org.opentibia.canary.kv.ValueWrapper
import org.opentibia.canary.lua.bindings.SpellBindings
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
import org.opentibia.canary.social.guild.JdbcGuildRepository
import org.opentibia.canary.spells.InstantSpell
import org.opentibia.canary.systems.imbuement.Imbuements
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.sql.DataSource

/**
 * Production composition-binding tests (wave 65, workstream B1): the wave-63/64
 * seams must be bound by the real composition root ([ServerBootstrap] +
 * [defaultListenerFactory]) instead of only by hand-wired fixtures.
 *
 *  - `Monsters` reaches `IOLoginData`, so the persisted bestiary tracker race
 *    ids resolve through `Monsters::getMonsterTypeByRaceId`
 *    (src/creatures/monsters/monsters.cpp:375-388) during a production login.
 *  - `Player.kv` is rebound to the `g_kv().scoped("player").scoped(guid)`
 *    scope (player.cpp:1398-1400) and the same `KVStore` is the save-all
 *    `KVStore::saveAll()` sink (`SaveManager::saveKV`, save_manager.cpp:104-110).
 *  - the JDBC `VipRepository` is injected into `PlayerFeatures` so the
 *    `PlayerVIP` mutators persist through `IOLoginData::addVIPEntry`
 *    (iologindata.cpp:387-396).
 *  - `Game::removeGuild` (game.cpp:11757-11762) saves through
 *    `SaveManager::saveGuild` (save_manager.cpp:182-194) to `IOGuild`.
 *  - the login load installs `Guild`'s `g_game().removeGuild(id)` seam
 *    (guild.cpp:29-31) on every guild `IOGuild::loadGuild` materializes, so an
 *    emptied guild leaves `Game::guilds` and flushes through the save manager.
 *  - `SaveManager::saveMap` (save_manager.cpp:196-206) reaches the booted
 *    `Map::save` (map.cpp:262-273) behind the `saveManager.mapSaver` binding.
 *
 * The root is booted exactly like production over an in-memory H2 database and
 * the production [defaultListenerFactory], so the assertions cover the real
 * composition rather than a hand-wired fixture.
 */
class ServerBootstrapCompositionBindingsTest {

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

    private fun configManager(properties: Map<String, String> = emptyMap()): ConfigManager =
        ConfigManager(
            ConfigKeys.all.associateWith { it.luaIdentifier },
            properties = ConfigSource.of(properties),
        )

    /** Boots the production root with the default listener factory and captures its composition. */
    private fun bootRoot(
        databaseBoot: TestDatabaseBoot,
        config: ConfigManager = configManager(),
        monsterBoot: MonsterBoot = MonsterBoot { _, _, _, _ -> Monsters() },
        luaEnvironment: LuaEnvironment = LuaEnvironment().apply { initState() },
        // `ProtocolGame::login` places the session player (protocolgame.cpp:1056):
        // the seeded `Knight Hero` login position needs a tile for the login
        // sessions these tests open.
        mapBoot: MapBoot = MapBoot { _, _, _, _ ->
            GameMap().apply {
                getOrCreateTile(Position(1000, 1000, 7)).ground = Item(id = 106)
                towns.getOrCreateTown(1).apply {
                    setName("Thais")
                    setTemplePos(Position(1000, 1000, 7))
                }
            }
        },
    ): BootedRoot {
        var composition: GameComposition? = null
        var listener: ListenerBoot? = null
        var dispatcher: GameDispatcher? = null
        val production = defaultListenerFactory(rsaCipher)
        val factory = ListenerFactory { bootConfig, accounts, map, bootedDispatcher, lua, booted ->
            composition = booted
            dispatcher = bootedDispatcher
            production.create(bootConfig, accounts, map, bootedDispatcher, lua, booted).also { listener = it }
        }
        val bootstrap = ServerBootstrap(
            configBoot = ConfigBoot { _, _ -> ServerConfig(loginPort = 0, gamePort = 0) },
            configManagerBoot = ConfigManagerBoot { _, _ -> config },
            databaseBoot = databaseBoot,
            mapBoot = mapBoot,
            scriptingBoot = ScriptingBoot { _, _, _, _ -> luaEnvironment },
            appearanceBoot = AppearanceBoot { Appearances.load(ByteArray(0)) },
            itemBoot = ItemBoot { _, _ -> Items() },
            imbuementBoot = ImbuementBoot { Imbuements() },
            monsterBoot = monsterBoot,
            listenerFactory = factory,
            monsterComputeService = inlineService(),
        )
        bootstrap.run()
        return BootedRoot(bootstrap, composition!!, listener!!, dispatcher!!)
    }

    /**
     * Logs a session in through the production game handler; the returned channel
     * stays open for the whole test so the session keeps its world registration.
     */
    private fun loginSession(root: BootedRoot): Pair<Player, EmbeddedChannel> {
        val channel = EmbeddedChannel(
            TibiaFrameDecoder(),
            TibiaMessageEncoder(),
            root.server().createGameHandler(),
        )
        channel.writeInbound(loginPacket())
        assertThat(channel.readOutbound<ByteBuf>()).isNotNull
        awaitLane(root)
        val player = root.server().game.getPlayerByID(1L)
        assertThat(player).isNotNull
        return player!! to channel
    }

    private fun BootedRoot.server(): CanaryServer = listener as CanaryServer

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

    private fun insertPlayer(dataSource: DataSource, id: Long, name: String) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "INSERT INTO players (id, name, account_id) VALUES (?, ?, 1)",
            ).use { stmt ->
                stmt.setLong(1, id)
                stmt.setString(2, name)
                stmt.executeUpdate()
            }
        }
    }

    @Test
    fun `production login resolves a persisted bestiary race id through the booted monsters registry`() {
        val databaseBoot = TestDatabaseBoot("bindings_bestiary_${System.nanoTime()}")
        val rat = MonsterType("Rat").apply { info.raceid = 42 }
        val monsters = Monsters(bestiaryList = { mapOf(42 to "Rat") }).apply {
            tryAddMonsterType("Rat", rat)
        }
        val root = bootRoot(
            databaseBoot,
            monsterBoot = MonsterBoot { _, _, _, _ -> monsters },
        )
        try {
            assertThat(root.composition.monsters).isSameAs(monsters)
            insertPlayer(databaseBoot.dataSource, 101L, "Bindings Hero")
            IOBestiaryPersistence(databaseBoot.dataSource).saveBestiaryTracker(101L, listOf(42, 99))

            val player = Player(
                id = 101L,
                name = "Bindings Hero",
                maxHealth = 1,
                health = 1,
                position = Position(1, 1, 7),
            )
            // `Player::addMonsterToCyclopediaTrackerList` gates on
            // `Player::client != nullptr` (player.cpp:946-948).
            player.cyclopediaMonsterTrackerClient = RecordingTrackerClient()
            assertThat(
                root.composition.playerSavePath.loginData.loadPlayerById(player, 101L, disableIrrelevantInfo = false),
            ).isTrue()

            // `IOBestiaryPersistence` drops the tracker ids the injected `Monsters`
            // registry cannot resolve (monsters.cpp:375-388): 42 resolves to Rat,
            // 99 stays unresolved.
            assertThat(player.getCyclopediaMonsterTrackerSet(false)).containsExactly(rat)
        } finally {
            root.bootstrap.shutdown()
        }
    }

    @Test
    fun `the session player kv is rebound to the scoped production kv store and persists on save-all`() {
        val databaseBoot = TestDatabaseBoot("bindings_kv_${System.nanoTime()}")
        val root = bootRoot(databaseBoot)
        var channel: EmbeddedChannel? = null
        try {
            val (player, session) = loginSession(root).also { channel = it.second }

            player.kv.scoped("bindings-probe").set("value", ValueWrapper(7))

            val store = root.composition.keyValueStore
            assertThat(store).isNotNull
            assertThat(
                store!!.scoped("player").scoped("1").scoped("bindings-probe").get("value")?.getNumber(),
            ).isEqualTo(7.0)

            assertThat(root.composition.saveManager!!.saveAll()).isEqualTo(1)

            val persisted = KVSQL(databaseBoot.dataSource)
                .scoped("player").scoped("1").scoped("bindings-probe")
                .get("value", forceLoad = true)
            assertThat(persisted?.getNumber()).isEqualTo(7.0)
            assertThat(session.isOpen).isTrue()
        } finally {
            channel?.close()
            root.bootstrap.shutdown()
        }
    }

    @Test
    fun `the session vip writes persist through the injected jdbc repository`() {
        val databaseBoot = TestDatabaseBoot("bindings_vip_${System.nanoTime()}")
        val root = bootRoot(databaseBoot)
        var channel: EmbeddedChannel? = null
        try {
            val player = loginSession(root).also { channel = it.second }.first

            assertThat(player.features.vip.add(2L, "Sorcerer Master", VipStatus.Offline)).isTrue()

            val entries = JdbcVipRepository(databaseBoot.dataSource).getVipEntries(1L)
            assertThat(entries.map { it.guid }).containsExactly(2L)
            assertThat(entries.single().name).isEqualTo("Sorcerer Master")
        } finally {
            channel?.close()
            root.bootstrap.shutdown()
        }
    }

    @Test
    fun `removeGuild flushes the guild through the save manager into IOGuild`() {
        val databaseBoot = TestDatabaseBoot("bindings_saveguild_${System.nanoTime()}")
        val root = bootRoot(databaseBoot)
        try {
            seedGuild(databaseBoot.dataSource)
            val loginData = root.composition.playerSavePath.loginData
            val member = Player(
                id = 1L,
                name = "Knight Hero",
                maxHealth = 150,
                health = 150,
                position = Position(1000, 1000, 7),
            )
            assertThat(loginData.loadPlayerById(member, 1L, disableIrrelevantInfo = false)).isTrue()

            val game = root.server().game
            val guild = game.getGuild(7L)
            assertThat(guild).isNotNull
            guild!!.setBankBalance(4321L)

            game.removeGuild(7L)

            assertThat(game.getGuild(7L)).isNull()
            val reloaded = JdbcGuildRepository(databaseBoot.dataSource).loadGuild(7L)
            assertThat(reloaded).isNotNull
            assertThat(reloaded!!.getBankBalance()).isEqualTo(4321L)
        } finally {
            root.bootstrap.shutdown()
        }
    }

    @Test
    fun `the last member leaving a loaded guild erases it from the world and flushes it`() {
        val databaseBoot = TestDatabaseBoot("bindings_guildremoval_${System.nanoTime()}")
        val root = bootRoot(databaseBoot)
        var channel: EmbeddedChannel? = null
        try {
            seedGuild(databaseBoot.dataSource)
            val (player, session) = loginSession(root).also { channel = it.second }

            val game = root.server().game
            val guild = game.getGuild(7L)
            assertThat(guild).isNotNull
            guild!!.setBankBalance(4321L)

            guild.removeMember(player)

            assertThat(game.getGuilds()).doesNotContainKey(7L)
            assertThat(JdbcGuildRepository(databaseBoot.dataSource).loadGuild(7L)!!.getBankBalance())
                .isEqualTo(4321L)
            assertThat(session.isOpen).isTrue()
        } finally {
            channel?.close()
            root.bootstrap.shutdown()
        }
    }

    @Test
    fun `save all reaches the booted map saver`() {
        val databaseBoot = TestDatabaseBoot("bindings_mapsaver_${System.nanoTime()}")
        val houseInfoSaves = AtomicInteger(0)
        val houseItemSaves = AtomicInteger(0)
        val root = bootRoot(
            databaseBoot,
            mapBoot = MapBoot { _, _, _, _ ->
                GameMap().apply {
                    towns.getOrCreateTown(1).apply {
                        setName("Thais")
                        setTemplePos(Position(1000, 1000, 7))
                    }
                    saveHouseInfo = {
                        houseInfoSaves.incrementAndGet()
                        true
                    }
                    saveHouseItems = {
                        houseItemSaves.incrementAndGet()
                        true
                    }
                }
            },
        )
        try {
            assertThat(root.composition.saveManager!!.saveAll()).isZero()

            assertThat(houseInfoSaves.get()).isEqualTo(1)
            assertThat(houseItemSaves.get()).isEqualTo(1)
        } finally {
            root.bootstrap.shutdown()
        }
    }

    /**
     * Wave 69 F4: the world `Game`'s `reduceAllSpellsCooldownTimer` seam must be
     * bound by the production root to the owner's wheel
     * (`PlayerWheel::reduceAllSpellsCooldownTimer`, player_wheel.cpp:3423-3450)
     * so the Gift of Life path
     * (`Game::checkGiftOfLife` -> `reduceAllSpellsCooldownTimer(player, 60000)`,
     * player_wheel.cpp:3155-3170) reduces every stored spell cooldown.
     */
    @Test
    fun `the booted root binds the gift of life cooldown reduction to the owner wheel`() {
        val databaseBoot = TestDatabaseBoot("bindings_cooldowns_${System.nanoTime()}")
        val root = bootRoot(databaseBoot)
        var channel: EmbeddedChannel? = null
        try {
            val (player, session) = loginSession(root).also { channel = it.second }

            SpellBindings.spellRegistry().registerInstantLuaEvent(
                InstantSpell().apply {
                    spellId = 1300
                    name = "Bindings Beam"
                    words = "exevo bindings beam"
                    secondaryGroup = SpellGroup.SPELLGROUP_GREAT_BEAMS
                },
            )

            val clock = 2_000_000L
            player.now = { clock }
            player.addCondition(
                ConditionSpellCooldown(
                    ConditionId.CONDITIONID_DEFAULT,
                    ConditionType.CONDITION_SPELLCOOLDOWN,
                    90_000,
                    subId = 1300,
                    now = { clock },
                ),
            )
            player.addCondition(
                ConditionSpellGroupCooldown(
                    ConditionId.CONDITIONID_DEFAULT,
                    ConditionType.CONDITION_SPELLGROUPCOOLDOWN,
                    90_000,
                    subId = SpellGroup.SPELLGROUP_GREAT_BEAMS.value,
                    now = { clock },
                ),
            )

            // `PlayerWheel::checkGiftOfLife`'s `reductionTimer = 60000`
            // (player_wheel.cpp:3164-3165).
            root.server().game.reduceAllSpellsCooldownTimer(player, 60_000)

            assertThat(
                player.getCondition(
                    ConditionType.CONDITION_SPELLCOOLDOWN,
                    ConditionId.CONDITIONID_DEFAULT,
                    1300,
                )?.ticks,
            ).isEqualTo(30_000)
            assertThat(
                player.getCondition(
                    ConditionType.CONDITION_SPELLGROUPCOOLDOWN,
                    ConditionId.CONDITIONID_DEFAULT,
                    SpellGroup.SPELLGROUP_GREAT_BEAMS.value,
                )?.ticks,
            ).isEqualTo(30_000)
            assertThat(session.isOpen).isTrue()
        } finally {
            channel?.close()
            root.bootstrap.shutdown()
        }
    }

    /**
     * Wave 69 F4: the guild helper broadcast must resolve the member count from
     * `Player::getHelpers()` (player.cpp:8335-8358) and reach the world
     * `Game::updatePlayerHelpers` (game.cpp:9690-9699) through the seam the
     * production root installs on every guild `IOGuild::loadGuild` materializes
     * (guild.cpp:14-32, ServerBootstrap guild repository binding).
     */
    @Test
    fun `guild membership changes broadcast the helper count through the world binding`() {
        val databaseBoot = TestDatabaseBoot("bindings_guildhelpers_${System.nanoTime()}")
        val root = bootRoot(databaseBoot)
        var channel: EmbeddedChannel? = null
        try {
            seedGuild(databaseBoot.dataSource)
            val (player, session) = loginSession(root).also { channel = it.second }
            val game = root.server().game
            val frames = mutableListOf<Pair<Long, Int>>()
            game.sendCreatureHelpers = { _, creatureId, helpers -> frames.add(creatureId to helpers) }

            val guild = game.getGuild(7L)
            assertThat(guild).isNotNull
            assertThat(game.getPlayerHelpers(player)).isEqualTo(1)

            val mate = Player(
                id = 2L,
                name = "Mate",
                maxHealth = 1,
                health = 1,
                position = Position(1000, 1000, 7),
            )
            mate.setGuild(guild)

            // `Guild::addMember` refreshes every online member through the world
            // seam; only the placed session player spectates the login tile.
            assertThat(frames).containsExactly(1L to 2, 2L to 2)
            assertThat(game.getPlayerHelpers(player)).isEqualTo(2)

            mate.setGuild(null)

            // `Player::setGuild` calls `Guild::removeMember` before clearing
            // `player->guild` (player.cpp:9316-9326), so the departing member's
            // refresh still resolves the guild's remaining online count.
            assertThat(frames).containsExactly(1L to 2, 2L to 2, 1L to 1, 2L to 1)
            assertThat(session.isOpen).isTrue()
        } finally {
            channel?.close()
            root.bootstrap.shutdown()
        }
    }

    /** Silent `CyclopediaMonsterTrackerClient` stand-in for the tracker-list gate. */
    private class RecordingTrackerClient : CyclopediaMonsterTrackerClient {
        override fun parseSendBosstiary() = Unit

        override fun sendBestiaryEntryChanged(raceId: Int) = Unit

        override fun refreshCyclopediaMonsterTracker(tracker: Set<MonsterType>, isBoss: Boolean) = Unit
    }

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
            }
        }
    }
}
