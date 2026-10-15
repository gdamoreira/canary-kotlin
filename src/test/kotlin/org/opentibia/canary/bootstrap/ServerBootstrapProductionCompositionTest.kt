package org.opentibia.canary.bootstrap

import com.zaxxer.hikari.HikariDataSource
import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufAllocator
import io.netty.buffer.UnpooledByteBufAllocator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.account.InMemoryAccountRepository
import org.opentibia.canary.combat.condition.Condition
import org.opentibia.canary.combat.condition.ConditionId
import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.config.ConfigKeys
import org.opentibia.canary.config.ConfigManager
import org.opentibia.canary.config.ConfigSource
import org.opentibia.canary.config.ServerConfig
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.monsters.Monsters
import org.opentibia.canary.creatures.players.components.PlayerStorageRow
import org.opentibia.canary.creatures.vocations.Vocations
import org.opentibia.canary.database.DatabaseConfig
import org.opentibia.canary.database.DatabaseConnectionFactory
import org.opentibia.canary.database.JdbcAccountRepository
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
import org.opentibia.canary.io.IOBestiaryPersistence
import org.opentibia.canary.io.appearances.Appearances
import org.opentibia.canary.io.iologindata.IOLoginData
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.items.ItemTypeRegistry
import org.opentibia.canary.items.Items
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.CanaryServer
import org.opentibia.canary.network.ListenerBoot
import org.opentibia.canary.network.protocol.ProtocolFrameSink
import org.opentibia.canary.network.protocol.ProtocolSink
import org.opentibia.canary.systems.imbuement.Imbuements
import javax.sql.DataSource

/**
 * Production composition-root tests: `ServerBootstrap` must build the
 * `g_configManager()` instance, the shared `Creature::getCreatureAsyncTaskBuckets`
 * state (creature.cpp:34-55), the `g_monsterComputeService()`
 * (monster_compute_service.hpp:101) and the `IOLoginData::savePlayer`
 * transaction path (iologindata.cpp:195-211) once, and hand them to the
 * listener factory so `CanaryServer` can compose the `MonsterPipelinesHost`
 * over the same instances.
 */
class ServerBootstrapProductionCompositionTest {

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

    private class RecordingListener(private val started: MutableList<String>) : ListenerBoot {
        override fun start() {
            started += "listeners.start"
        }

        override fun stop() = Unit
    }

    private fun inlineService(): MonsterComputeService =
        MonsterComputeService().apply {
            start(MonsterComputeConfig(configuredThreads = 0, capacity = 16, hardwareConcurrency = 1))
        }

    private fun configManager(properties: Map<String, String> = emptyMap()): ConfigManager =
        ConfigManager(
            ConfigKeys.all.associateWith { it.luaIdentifier },
            properties = ConfigSource.of(properties),
        )

    private fun bootstrap(
        databaseBoot: TestDatabaseBoot,
        configManager: ConfigManager = configManager(),
        disposals: MutableList<String> = mutableListOf(),
        monsterComputeService: MonsterComputeService = inlineService(),
        listenerFactory: ListenerFactory = ListenerFactory { _, _, _, _, _, _ -> RecordingListener(disposals) },
    ): ServerBootstrap = ServerBootstrap(
        configBoot = ConfigBoot { _, _ -> ServerConfig(loginPort = 0, gamePort = 0) },
        configManagerBoot = ConfigManagerBoot { _, _ -> configManager },
        databaseBoot = databaseBoot,
        mapBoot = MapBoot { GameMap() },
        scriptingBoot = ScriptingBoot { LuaEnvironment() },
        appearanceBoot = AppearanceBoot { Appearances.load(ByteArray(0)) },
        itemBoot = ItemBoot { _, _ -> Items() },
        imbuementBoot = ImbuementBoot { Imbuements() },
        listenerFactory = listenerFactory,
        monsterComputeService = monsterComputeService,
    )

    @Test
    fun `production root passes the config manager buckets compute service and save path to the listener factory`() {
        val databaseBoot = TestDatabaseBoot("bootstrap_prod_wiring_${System.nanoTime()}")
        val config = configManager()
        var composition: GameComposition? = null
        val bootstrap = bootstrap(
            databaseBoot,
            configManager = config,
            listenerFactory = ListenerFactory { _, _, _, _, _, booted ->
                composition = booted
                RecordingListener(mutableListOf())
            },
        )

        try {
            bootstrap.run()
        } finally {
            bootstrap.shutdown()
        }

        val booted = composition
        assertThat(booted).isNotNull
        assertThat(booted!!.configManager).isSameAs(config)
        assertThat(booted.creatureAsyncTaskBuckets).isNotNull
        assertThat(booted.monsterComputeService).isNotNull
        assertThat(booted.playerSavePath).isNotNull
        assertThat(booted.playerSavePath.transaction).isNotNull
    }

    @Test
    fun `the root save path runs the guarded save over the shared transaction`() {
        val databaseBoot = TestDatabaseBoot("bootstrap_prod_save_${System.nanoTime()}")
        var composition: GameComposition? = null
        val bootstrap = bootstrap(
            databaseBoot,
            listenerFactory = ListenerFactory { _, _, _, _, _, booted ->
                composition = booted
                RecordingListener(mutableListOf())
            },
        )

        bootstrap.run()
        try {
            val booted = composition!!
            insertPlayer(booted.dataSource, 101L, "Root Hero")

            val player = Player(
                id = 101L,
                name = "Root Hero",
                maxHealth = 100,
                health = 90,
                capacity = 100_000.0,
                position = Position(1000, 1000, 7),
            )
            player.setLastDepotId(1)
            player.getDepotChest(1, autoCreate = true)!!.addItem(Item(id = 2400))

            booted.playerSavePath.save(player)

            assertThat(booted.playerSavePath.loginData.loadPlayerDepotItems(101L)).hasSize(1)
            assertThat(JdbcPlayerRepository(booted.dataSource).loadRecordByName("Root Hero")!!.health)
                .isEqualTo(90)
        } finally {
            bootstrap.shutdown()
        }
    }

    @Test
    fun `the production save path persists and restores conditions storage and animus`() {
        val databaseBoot = TestDatabaseBoot("bootstrap_prod_persistence_${System.nanoTime()}")
        var composition: GameComposition? = null
        val bootstrap = bootstrap(
            databaseBoot,
            listenerFactory = ListenerFactory { _, _, _, _, _, booted ->
                composition = booted
                RecordingListener(mutableListOf())
            },
        )

        bootstrap.run()
        try {
            val booted = composition!!
            insertPlayer(booted.dataSource, 101L, "Root Hero")

            val player = Player(
                id = 101L,
                name = "Root Hero",
                maxHealth = 100,
                health = 90,
                capacity = 100_000.0,
                position = Position(1000, 1000, 7),
            )
            newSessionSink(player)
            player.features.storage.add(4000, 21)
            player.features.animusMastery.add("Dragon")
            assertThat(
                player.addCondition(
                    Condition.createCondition(
                        id = ConditionId.CONDITIONID_DEFAULT,
                        type = ConditionType.CONDITION_INFIGHT,
                        ticks = 5_000,
                    )!!,
                ),
            ).isTrue()

            booted.playerSavePath.save(player)

            assertThat(readConditionsColumn(booted.dataSource, 101L)).isNotEmpty()
            assertThat(JdbcPlayerStorageRepository(booted.dataSource).load(101L))
                .containsExactly(PlayerStorageRow(key = 4000, value = 21))
            assertThat(IOBestiaryPersistence(booted.dataSource).loadAnimusMastery(101L))
                .containsExactly("dragon")

            val loaded = Player(
                id = 101L,
                name = "Root Hero",
                maxHealth = 150,
                health = 150,
                capacity = 100_000.0,
                position = Position(1000, 1000, 7),
            )
            assertThat(
                booted.playerSavePath.loginData.loadPlayerById(loaded, 101L, disableIrrelevantInfo = false),
            ).isTrue()
            assertThat(loaded.features.storage.get(4000)).isEqualTo(21)
            assertThat(loaded.storedConditionList.map { it.type }).contains(ConditionType.CONDITION_INFIGHT)
            assertThat(loaded.features.animusMastery.getAnimusMasteries()).containsExactly("dragon")
        } finally {
            bootstrap.shutdown()
        }
    }

    @Test
    fun `the production save path flushes the online-only storage delta only for a session player`() {
        val databaseBoot = TestDatabaseBoot("bootstrap_prod_offline_${System.nanoTime()}")
        var composition: GameComposition? = null
        val bootstrap = bootstrap(
            databaseBoot,
            listenerFactory = ListenerFactory { _, _, _, _, _, booted ->
                composition = booted
                RecordingListener(mutableListOf())
            },
        )

        bootstrap.run()
        try {
            val booted = composition!!
            insertPlayer(booted.dataSource, 101L, "Root Hero")

            val offlinePlayer = offlineHero()
            offlinePlayer.features.storage.add(1, 1)
            assertThat(booted.playerSavePath.loginData.saveOnlyDataForOnlinePlayer(offlinePlayer)).isTrue()
            assertThat(JdbcPlayerStorageRepository(booted.dataSource).load(101L)).isEmpty()

            val onlinePlayer = offlineHero()
            onlinePlayer.features.storage.add(2, 2)
            newSessionSink(onlinePlayer)
            assertThat(booted.playerSavePath.loginData.saveOnlyDataForOnlinePlayer(onlinePlayer)).isTrue()
            assertThat(JdbcPlayerStorageRepository(booted.dataSource).load(101L))
                .containsExactly(PlayerStorageRow(key = 2, value = 2))
        } finally {
            bootstrap.shutdown()
        }
    }

    @Test
    fun `the listener binds the world offline predicate to the session presence`() {
        val databaseBoot = TestDatabaseBoot("bootstrap_prod_game_offline_${System.nanoTime()}")
        databaseBoot.connect(null)
        databaseBoot.migrate(databaseBoot.dataSource)
        val service = inlineService()
        val dispatcher = GameDispatcher(monsterComputeService = service)
        val composition = GameComposition(
            dataSource = databaseBoot.dataSource,
            itemTypes = Items(),
            imbuements = Imbuements(),
            monsters = Monsters(),
            vocations = Vocations(),
            configManager = configManager(),
            creatureAsyncTaskBuckets = CreatureAsyncTaskBuckets(dispatcher = { dispatcher }),
            monsterComputeService = service,
            playerSavePath = savePath(databaseBoot.dataSource),
        )

        try {
            val server = defaultListenerFactory().create(
                ServerConfig(loginPort = 0, gamePort = 0),
                InMemoryAccountRepository(),
                GameMap(),
                dispatcher,
                LuaEnvironment(),
                composition,
            ) as CanaryServer

            val player = offlineHero()
            assertThat(server.game.isOffline(player)).isTrue()

            newSessionSink(player)
            assertThat(server.game.isOffline(player)).isFalse()
        } finally {
            service.shutdown()
            databaseBoot.dataSource.close()
        }
    }

    @Test
    fun `the monster compute service starts with the configured queue capacity`() {
        val databaseBoot = TestDatabaseBoot("bootstrap_prod_compute_${System.nanoTime()}")
        var composition: GameComposition? = null
        val bootstrap = bootstrap(
            databaseBoot,
            configManager = configManager(mapOf("monsterComputeQueueCapacity" to "64")),
            monsterComputeService = MonsterComputeService(),
            listenerFactory = ListenerFactory { _, _, _, _, _, booted ->
                composition = booted
                RecordingListener(mutableListOf())
            },
        )

        bootstrap.run()
        try {
            val stats = composition!!.monsterComputeService.getStats()
            assertThat(stats.running).isTrue()
            assertThat(stats.capacity).isEqualTo(64)
        } finally {
            bootstrap.shutdown()
        }
    }

    @Test
    fun `default listener factory composes config buckets and monster host into the server`() {
        val databaseBoot = TestDatabaseBoot("bootstrap_prod_factory_${System.nanoTime()}")
        databaseBoot.connect(null)
        databaseBoot.migrate(databaseBoot.dataSource)
        val config = configManager(
            mapOf(
                "maxMessageBuffer" to "7",
                "monsterPerfTestFriendlyFire" to "true",
            ),
        )
        val service = inlineService()
        val dispatcher = GameDispatcher(monsterComputeService = service)
        val buckets = CreatureAsyncTaskBuckets(dispatcher = { dispatcher })
        val composition = GameComposition(
            dataSource = databaseBoot.dataSource,
            itemTypes = Items(),
            imbuements = Imbuements(),
            monsters = Monsters(),
            vocations = Vocations(),
            configManager = config,
            creatureAsyncTaskBuckets = buckets,
            monsterComputeService = service,
            playerSavePath = savePath(databaseBoot.dataSource),
        )

        try {
            val server = defaultListenerFactory().create(
                ServerConfig(loginPort = 0, gamePort = 0),
                InMemoryAccountRepository(),
                GameMap(),
                dispatcher,
                LuaEnvironment(),
                composition,
            ) as CanaryServer

            assertThat(server.creatureAsyncTaskBuckets).isSameAs(buckets)
            val host = server.monsterPipelinesHost
            assertThat(host).isNotNull
            assertThat(host!!.creatureAsyncTaskBuckets).isSameAs(buckets)
            assertThat(host.monsterPerfTestFriendlyFire()).isTrue()

            val monster = Monster(id = 7L, name = "Rat", maxHealth = 100, health = 100, position = Position(1000, 1000, 7))
            host.register(monster)
            host.typeStateOf(7L)!!.forgeStack = 5
            assertThat(server.game.getForgeStack(monster)).isEqualTo(5)

            val player = Player(id = 1L, name = "Hero", maxHealth = 150, health = 150, position = Position(1000, 1000, 7))
            server.game.addPlayer(player)
            assertThat(player.maxMessageBuffer).isEqualTo(7)
        } finally {
            service.shutdown()
            databaseBoot.dataSource.close()
        }
    }

    private fun savePath(dataSource: DataSource): org.opentibia.canary.io.iologindata.PlayerSavePath {
        val transaction = org.opentibia.canary.io.iologindata.PlayerSaveTransaction(dataSource)
        val itemTypes = ItemTypeRegistry().apply { register(ItemType(2400, "magic sword", isMovable = true)) }
        val loginData = IOLoginData(
            players = JdbcPlayerRepository(transaction),
            accounts = JdbcAccountRepository(transaction),
            vip = JdbcVipRepository(transaction),
            storageRepository = JdbcPlayerStorageRepository(transaction),
            spells = JdbcPlayerSpellsRepository(transaction),
            kills = JdbcPlayerKillsRepository(transaction),
            stash = JdbcPlayerStashRepository(transaction),
            rewards = JdbcPlayerRewardsRepository(transaction),
            items = JdbcPlayerItemsRepository(transaction),
            depots = JdbcPlayerDepotRepository(transaction),
            itemTypes = itemTypes,
        )
        return org.opentibia.canary.io.iologindata.PlayerSavePath(
            transaction = transaction,
            loginData = loginData,
            players = JdbcPlayerRepository(transaction),
        )
    }

    private fun insertPlayer(dataSource: DataSource, id: Long, name: String) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """
                INSERT INTO players (
                    id, name, account_id, level, vocation, health, healthmax, mana, manamax,
                    experience, posx, posy, posz, cap, sex, pronoun, town_id
                ) VALUES (?, ?, 1, 10, 4, 100, 100, 50, 50, 1, 1000, 1000, 7, 350, 1, 3, 1)
                """.trimIndent(),
            ).use { stmt ->
                stmt.setLong(1, id)
                stmt.setString(2, name)
                stmt.executeUpdate()
            }
        }
    }

    private fun offlineHero(): Player = Player(
        id = 101L,
        name = "Root Hero",
        maxHealth = 150,
        health = 150,
        capacity = 100_000.0,
        position = Position(1000, 1000, 7),
    )

    /**
     * A live session sink attached through `Player.protocolSink`
     * (`Player::client`, GameHandler.kt:302): the port's session presence that
     * `Player::isOffline` (player.hpp:481) is wired against.
     */
    private fun newSessionSink(player: Player): ProtocolSink =
        object : ProtocolFrameSink(player, GameMap()) {
            override fun frameAllocator(): ByteBufAllocator = UnpooledByteBufAllocator.DEFAULT

            override fun writeFrame(frame: ByteBuf) {
                frame.release()
            }
        }.also { player.protocolSink = it }

    private fun readConditionsColumn(dataSource: DataSource, playerId: Long): ByteArray? =
        dataSource.connection.use { conn ->
            conn.prepareStatement("SELECT conditions FROM players WHERE id = ?").use { stmt ->
                stmt.setLong(1, playerId)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) rs.getBytes("conditions") else null
                }
            }
        }
}
