package org.opentibia.canary.bootstrap

import com.zaxxer.hikari.HikariDataSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.account.InMemoryAccountRepository
import org.opentibia.canary.config.ConfigKeys
import org.opentibia.canary.config.ConfigManager
import org.opentibia.canary.config.ServerConfig
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.monsters.MonsterType
import org.opentibia.canary.creatures.monsters.Monsters
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
import org.opentibia.canary.io.appearances.Appearances
import org.opentibia.canary.io.iologindata.IOLoginData
import org.opentibia.canary.io.iologindata.PlayerSavePath
import org.opentibia.canary.io.iologindata.PlayerSaveTransaction
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.Items
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.CanaryServer
import org.opentibia.canary.network.ListenerBoot
import org.opentibia.canary.systems.imbuement.Imbuements
import java.nio.file.Files
import javax.sql.DataSource

/**
 * Production registry-injection tests (wave 56, task A1): `ServerBootstrap`
 * must compose the `g_monsters()` registry (`Monsters`,
 * src/creatures/monsters/monsters.hpp:242) over the existing XML/model
 * definition path, the `g_vocations()` registry (`Vocations::loadFromXml`,
 * vocation.cpp:28) over `{CORE_DIRECTORY}/XML/vocations.xml`, and the
 * `IOLoginData` login-load seam (iologindata.cpp:81-86), then hand all three
 * to `CanaryServer`/`GameHandler` through the `GameComposition`.
 */
class ServerBootstrapRegistryInjectionTest {

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

    private class RecordingListener : ListenerBoot {
        override fun start() = Unit
        override fun stop() = Unit
    }

    private fun inlineService(): MonsterComputeService =
        MonsterComputeService().apply {
            start(MonsterComputeConfig(configuredThreads = 0, capacity = 16, hardwareConcurrency = 1))
        }

    private fun configManager(): ConfigManager =
        ConfigManager(ConfigKeys.all.associateWith { it.luaIdentifier })

    private fun bootstrap(
        databaseBoot: TestDatabaseBoot,
        config: ServerConfig = ServerConfig(loginPort = 0, gamePort = 0),
        configManager: ConfigManager = configManager(),
        monsterComputeService: MonsterComputeService = inlineService(),
        listenerFactory: ListenerFactory = ListenerFactory { _, _, _, _, _, _ -> RecordingListener() },
    ): ServerBootstrap = ServerBootstrap(
        configBoot = ConfigBoot { _, _ -> config },
        configManagerBoot = ConfigManagerBoot { _, _ -> configManager },
        databaseBoot = databaseBoot,
        mapBoot = MapBoot { _, _, _, _ -> GameMap() },
        scriptingBoot = ScriptingBoot { LuaEnvironment() },
        appearanceBoot = AppearanceBoot { Appearances.load(ByteArray(0)) },
        itemBoot = ItemBoot { _, _ -> Items() },
        imbuementBoot = ImbuementBoot { Imbuements() },
        listenerFactory = listenerFactory,
        monsterComputeService = monsterComputeService,
    )

    private fun savePath(dataSource: DataSource): PlayerSavePath {
        val transaction = PlayerSaveTransaction(dataSource)
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
            itemTypes = Items(),
        )
        return PlayerSavePath(
            transaction = transaction,
            loginData = loginData,
            players = JdbcPlayerRepository(transaction),
        )
    }

    private fun composition(databaseBoot: TestDatabaseBoot, service: MonsterComputeService): GameComposition {
        val dispatcher = GameDispatcher(monsterComputeService = service)
        val monsters = Monsters().apply {
            tryAddMonsterType("Rat", MonsterType("Rat").apply { info.experience = 5L })
        }
        val vocations = Vocations().apply {
            loadFromXml(
                """
                <vocations>
                    <vocation id="1" name="Sorcerer" fromvoc="1" />
                    <vocation id="5" name="Master Sorcerer" fromvoc="1" />
                </vocations>
                """.trimIndent(),
            )
        }
        return GameComposition(
            dataSource = databaseBoot.dataSource,
            itemTypes = Items(),
            imbuements = Imbuements(),
            monsters = monsters,
            vocations = vocations,
            configManager = configManager(),
            creatureAsyncTaskBuckets = CreatureAsyncTaskBuckets(dispatcher = { dispatcher }),
            monsterComputeService = service,
            playerSavePath = savePath(databaseBoot.dataSource),
        )
    }

    private fun GameComposition.server(): CanaryServer {
        val dispatcher = GameDispatcher(monsterComputeService = monsterComputeService)
        return defaultListenerFactory().create(
            ServerConfig(loginPort = 0, gamePort = 0),
            InMemoryAccountRepository(),
            GameMap(),
            dispatcher,
            LuaEnvironment(),
            this,
        ) as CanaryServer
    }

    @Test
    fun `production root loads the vocations registry from the core XML path`() {
        val databaseBoot = TestDatabaseBoot("bootstrap_registry_vocations_${System.nanoTime()}")
        var composition: GameComposition? = null
        val bootstrap = bootstrap(
            databaseBoot,
            listenerFactory = ListenerFactory { _, _, _, _, _, booted ->
                composition = booted
                RecordingListener()
            },
        )

        try {
            bootstrap.run()
        } finally {
            bootstrap.shutdown()
        }

        val vocations = composition!!.vocations
        assertThat(vocations.getVocations()).isNotEmpty()
        assertThat(vocations.getVocation(4)!!.getVocName()).isEqualTo("Knight")
        assertThat(vocations.getVocationId("Knight")).isEqualTo(4)
    }

    @Test
    fun `production root loads monster types from the datapack XML path`() {
        val dataDir = Files.createTempDirectory("canary_registry_monsters")
        val nested = Files.createDirectories(dataDir.resolve("monster").resolve("dragons"))
        Files.writeString(
            nested.resolve("dragon.xml"),
            """
            <monster name="Dragon" experience="700" speed="200" race="blood">
                <health now="1000" max="1000"/>
                <loot>
                    <item id="2148" countmax="100" chance="100000"/>
                </loot>
            </monster>
            """.trimIndent(),
        )

        val databaseBoot = TestDatabaseBoot("bootstrap_registry_monsters_${System.nanoTime()}")
        var composition: GameComposition? = null
        val bootstrap = bootstrap(
            databaseBoot,
            config = ServerConfig(loginPort = 0, gamePort = 0, dataDir = dataDir.toString()),
            listenerFactory = ListenerFactory { _, _, _, _, _, booted ->
                composition = booted
                RecordingListener()
            },
        )

        try {
            bootstrap.run()
        } finally {
            bootstrap.shutdown()
        }

        val dragon = composition!!.monsters.getMonsterType("dragon")
        assertThat(dragon).isNotNull
        assertThat(dragon!!.name).isEqualTo("Dragon")
        assertThat(dragon.info.experience).isEqualTo(700L)
        assertThat(dragon.info.baseSpeed).isEqualTo(200)
        assertThat(dragon.info.health).isEqualTo(1000)
        assertThat(dragon.info.healthMax).isEqualTo(1000)
        assertThat(dragon.info.lootItems).hasSize(1)
        assertThat(dragon.info.lootItems.first().id).isEqualTo(2148)
        assertThat(dragon.info.lootItems.first().countMax).isEqualTo(100)
        assertThat(dragon.info.lootItems.first().chance).isEqualTo(100000)
    }

    @Test
    fun `default listener factory hands the registries and login data to the server`() {
        val databaseBoot = TestDatabaseBoot("bootstrap_registry_factory_${System.nanoTime()}")
        databaseBoot.connect(null)
        databaseBoot.migrate(databaseBoot.dataSource)
        val service = inlineService()

        try {
            val booted = composition(databaseBoot, service)
            val server = booted.server()

            assertThat(server.monsters).isSameAs(booted.monsters)
            assertThat(server.vocations).isSameAs(booted.vocations)
            assertThat(server.ioLoginData).isSameAs(booted.playerSavePath.loginData)
        } finally {
            service.shutdown()
            databaseBoot.dataSource.close()
        }
    }

    @Test
    fun `the composed world resolves the injected monster type and vocation registry`() {
        val databaseBoot = TestDatabaseBoot("bootstrap_registry_world_${System.nanoTime()}")
        databaseBoot.connect(null)
        databaseBoot.migrate(databaseBoot.dataSource)
        val service = inlineService()
        val dispatcher = GameDispatcher(monsterComputeService = service)
        val position = Position(1000, 1000, 7)
        val map = GameMap()
        map.getOrCreateTile(position).also { it.ground = Item(id = 100) }
        val rat = MonsterType("Rat").apply { info.experience = 5L }
        val booted = GameComposition(
            dataSource = databaseBoot.dataSource,
            itemTypes = Items(),
            imbuements = Imbuements(),
            monsters = Monsters().apply { tryAddMonsterType("Rat", rat) },
            vocations = Vocations().apply {
                loadFromXml(
                    """
                    <vocations>
                        <vocation id="1" name="Sorcerer" fromvoc="1" />
                        <vocation id="5" name="Master Sorcerer" fromvoc="1" />
                    </vocations>
                    """.trimIndent(),
                )
            },
            configManager = configManager(),
            creatureAsyncTaskBuckets = CreatureAsyncTaskBuckets(dispatcher = { dispatcher }),
            monsterComputeService = service,
            playerSavePath = savePath(databaseBoot.dataSource),
        )

        try {
            val server = defaultListenerFactory().create(
                ServerConfig(loginPort = 0, gamePort = 0),
                InMemoryAccountRepository(),
                map,
                dispatcher,
                LuaEnvironment(),
                booted,
            ) as CanaryServer

            val monster = Monster(id = 7L, name = "Rat", maxHealth = 100, health = 100, position = position)
            assertThat(server.game.internalPlaceCreature(monster, position)).isTrue()
            assertThat(server.monsterPipelinesHost!!.typeStateOf(7L)!!.monsterType).isSameAs(rat)

            assertThat(server.game.generateVocationConditionHighscore(1L))
                .isEqualTo("(`vocation` = 1 OR `vocation` = 5)")
        } finally {
            service.shutdown()
            databaseBoot.dataSource.close()
        }
    }
}
