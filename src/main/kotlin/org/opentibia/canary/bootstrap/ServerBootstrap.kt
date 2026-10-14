package org.opentibia.canary.bootstrap

import io.github.oshai.kotlinlogging.KotlinLogging
import org.opentibia.canary.account.AccountRepository
import org.opentibia.canary.config.CliOptions
import org.opentibia.canary.config.ConfigKeys
import org.opentibia.canary.config.ConfigManager
import org.opentibia.canary.config.AppPaths
import org.opentibia.canary.config.ConfigSource
import org.opentibia.canary.config.ServerConfig
import org.opentibia.canary.config.configSourceOf
import org.opentibia.canary.creatures.monsters.LootBlock
import org.opentibia.canary.creatures.monsters.MonsterType
import org.opentibia.canary.creatures.monsters.Monsters
import org.opentibia.canary.creatures.vocations.VocationRates
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
import org.opentibia.canary.game.scheduling.SaveManager
import org.opentibia.canary.io.IOBestiaryPersistence
import org.opentibia.canary.io.appearances.Appearances
import org.opentibia.canary.io.iologindata.IOLoginData
import org.opentibia.canary.io.iologindata.PlayerSavePath
import org.opentibia.canary.io.iologindata.PlayerSaveTransaction
import org.opentibia.canary.io.pipeline.MonsterPipelineService
import org.opentibia.canary.io.pipeline.MonsterTypeDefinition
import org.opentibia.canary.items.ItemAttributeParse
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.items.Items
import org.opentibia.canary.items.functions.ItemParse
import org.opentibia.canary.items.functions.ItemParseRegistry
import org.opentibia.canary.items.functions.XmlAttribute
import org.opentibia.canary.items.functions.XmlNode
import org.opentibia.canary.lua.bindings.LootFunctions
import org.opentibia.canary.lua.bindings.library.ConfigFunctions
import org.opentibia.canary.lua.bindings.library.WorldConfigOperations
import org.opentibia.canary.lua.loader.Scripts
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.lua.runtime.WorldLootOperations
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.network.CanaryServer
import org.opentibia.canary.network.ListenerBoot
import org.opentibia.canary.monitoring.MetricsRegistry
import org.opentibia.canary.monitoring.PrometheusEndpoint
import org.opentibia.canary.network.crypto.RsaCipher
import org.opentibia.canary.systems.imbuement.Imbuements
import org.opentibia.canary.systems.prey.IOPreyPersistence
import org.opentibia.canary.systems.proficiency.WeaponProficiencyLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import javax.sql.DataSource

private val logger = KotlinLogging.logger {}

/** Resolves the effective config (`ConfigManager` precedence: CLI > file > env > defaults). */
fun interface ConfigBoot {
    fun load(environment: Map<String, String>, options: CliOptions): ServerConfig
}

/**
 * Builds the typed `g_configManager()` instance (`ConfigManager`, the port's
 * constructor-injected replacement of the C++ singleton, AGENTS.md 1.1) over
 * the same environment and `--config` source [ConfigBoot] reads.
 */
fun interface ConfigManagerBoot {
    fun load(environment: Map<String, String>, options: CliOptions): ConfigManager
}

/**
 * Production `g_configManager()`: the [ConfigKeys] surface keyed by its
 * `config.lua` identifier, with the `CANARY_*` environment and the `--config`
 * properties file applied. `reload` re-reads the same file like
 * `ConfigManager::reload` (configmanager.cpp:432).
 */
private fun defaultConfigManagerBoot(environment: Map<String, String>, options: CliOptions): ConfigManager =
    ConfigManager(
        keyMap = ConfigKeys.all.associateWith { it.luaIdentifier },
        environment = environment,
        properties = options.configPath?.let { path -> configSourceOf(path) }
            ?: AppPaths.defaultConfigFile(environment)?.let { ConfigSource.ofLua(it) }
            ?: ConfigSource.EMPTY,
    )

/**
 * `MonsterComputeConfig` from the `MONSTER_COMPUTE_THREADS` /
 * `MONSTER_COMPUTE_QUEUE_CAPACITY` values (`CanaryServer::run`,
 * src/canary_server.cpp:212-215); missing keys keep the [MonsterComputeConfig]
 * defaults (`0` threads = automatic, `2048` capacity).
 */
private fun monsterComputeConfig(configManager: ConfigManager): MonsterComputeConfig {
    val defaults = MonsterComputeConfig()
    val threads = ConfigKeys.byName("MONSTER_COMPUTE_THREADS")
    val capacity = ConfigKeys.byName("MONSTER_COMPUTE_QUEUE_CAPACITY")
    return MonsterComputeConfig(
        configuredThreads = threads?.let { configManager.getNumber(it, it.defaultValue as Int) }
            ?: defaults.configuredThreads,
        capacity = capacity?.let { configManager.getNumber(it, it.defaultValue as Int) } ?: defaults.capacity,
    )
}

/** Connects and migrates the database (`CanaryServer::initializeDatabase`) and releases it on drain. */
interface DatabaseBoot {
    fun connect(config: DatabaseConfig?): DataSource
    fun migrate(dataSource: DataSource): Int
    fun close(dataSource: DataSource)
}

/** Loads the main map (`CanaryServer::loadMaps` / `Game::loadMainMap`). */
fun interface MapBoot {
    fun load(config: ServerConfig): GameMap
}

/** Boots the Lua environment (`CanaryServer::loadModules`); scripts stay Lua, never ported. */
fun interface ScriptingBoot {
    fun boot(config: ServerConfig): LuaEnvironment
}

/**
 * Loads `{CORE_DIRECTORY}/items/appearances.dat` (`Game::loadAppearanceProtobuf`,
 * src/game/game.cpp:1657). The parsed tables are the boot artifact consumed by
 * [ItemBoot] and the appearance-driven subsystems (`Items::loadFromProtobuf`,
 * src/items/items.cpp:153).
 */
fun interface AppearanceBoot {
    fun load(config: ServerConfig): Appearances
}

/**
 * Loads `Item::items` (`src/items/items.hpp:389`): the protobuf merge over the
 * [Appearances] tables followed by `{CORE_DIRECTORY}/items/items.xml`
 * (`Items::loadFromXml`, src/items/items.cpp:281).
 */
fun interface ItemBoot {
    fun load(config: ServerConfig, appearances: Appearances): Items
}

/** Loads `g_imbuements()` (`Imbuements::loadFromXml`, imbuements.cpp:47). */
fun interface ImbuementBoot {
    fun load(config: ServerConfig): Imbuements
}

/**
 * Loads `g_monsters()` (`Monsters`, src/creatures/monsters/monsters.hpp:242):
 * the monster-type registry injected into the world as the
 * `Monsters::getMonsterType` `monsterTypeProvider` seam
 * (`Game.internalPlaceCreature`, game.cpp:1495).
 *
 * Canary fills the registry from the datapack Lua scripts
 * (`CanaryServer::loadModules`, src/canary_server.cpp:599): the core
 * `data/scripts/lib` registration helper defines `MonsterType:register` and every
 * datapack Lua monster script registers its type through
 * `Game.createMonsterType` / the `MonsterType` setters. The booted environment and
 * the typed `g_configManager()` are the inputs that make that path runnable.
 */
fun interface MonsterBoot {
    fun load(
        config: ServerConfig,
        luaEnvironment: LuaEnvironment,
        configManager: ConfigManager,
        itemTypes: Items,
    ): Monsters
}

/**
 * Loads `g_vocations()` (`Vocations::loadFromXml`, src/creatures/players/vocations/vocation.cpp:28)
 * over the booted [Items] (wheel gem name resolution, vocation.cpp:405-411) and
 * the `g_configManager()` `RATE_*` values backing the vocation regen getters
 * (vocation.cpp:337-367).
 */
fun interface VocationsBoot {
    fun load(config: ServerConfig, itemTypes: Items, configManager: ConfigManager): Vocations
}

/**
 * Booted services shared by every game connection (`g_game()`, `Item::items`,
 * `g_imbuements()`, `g_monsters()`, `g_vocations()`, `IOMarket::getInstance()`,
 * `g_configManager()`, `g_monsterComputeService()`): created by [ServerBootstrap]
 * from the connected [DataSource] and the registries loaded by
 * [ItemBoot]/[ImbuementBoot]/[MonsterBoot]/[VocationsBoot], then injected into
 * the [ListenerFactory].
 */
class GameComposition(
    /** `g_database()` source backing `IOMarket::getInstance()` (src/io/iomarket.cpp). */
    val dataSource: DataSource,
    /** `Item::items` registry (`src/items/items.hpp:389`). */
    val itemTypes: Items,
    /** `g_imbuements()` registry (`src/creatures/players/imbuements/imbuements.hpp:44`). */
    val imbuements: Imbuements,
    /**
     * `g_monsters()` registry (`src/creatures/monsters/monsters.hpp:242`), injected
     * into the world `Game` as the `Monsters::getMonsterType` provider so
     * `Game::internalPlaceCreature` (game.cpp:1495) resolves `Monster::m_monsterType`.
     */
    val monsters: Monsters,
    /**
     * `g_vocations()` registry (`src/creatures/players/vocations/vocation.hpp:122`),
     * consumed by the world `Game` (highscore queries, game.cpp:9968) and the
     * session player data.
     */
    val vocations: Vocations,
    /** `g_configManager()` typed lookup surface (configmanager.hpp). */
    val configManager: ConfigManager,
    /**
     * `Creature::getCreatureAsyncTaskBuckets` shared world state
     * (creature.cpp:34-55): one registry per root, shared by `Game`'s
     * non-monster batches and the `MonsterPipelinesHost` pipelines.
     */
    val creatureAsyncTaskBuckets: CreatureAsyncTaskBuckets,
    /**
     * `g_monsterComputeService()` (src/game/scheduling/monster_compute_service.hpp:101)
     * started by the root; the dispatcher's `WorkerCompletion` lane drains its
     * completions and the monster pipelines submit their computes to it.
     */
    val monsterComputeService: MonsterComputeService,
    /**
     * `IOLoginData::savePlayer` path (src/io/iologindata.cpp:195-211) composed
     * over one shared [PlayerSaveTransaction]; `SaveManager` flushes players
     * through it (`SaveManager::doSavePlayer`, save_manager.cpp:150-171).
     */
    val playerSavePath: PlayerSavePath,
)

/** Creates the network listener (`CanaryServer`) from the booted services. */
fun interface ListenerFactory {
    fun create(
        config: ServerConfig,
        accountRepository: AccountRepository,
        gameMap: GameMap,
        dispatcher: GameDispatcher,
        luaEnvironment: LuaEnvironment,
        composition: GameComposition
    ): ListenerBoot
}

/**
 * Production [ListenerFactory]: builds the `CanaryServer` over the booted
 * services, passing the shared config manager, async buckets, monster compute
 * service, `g_monsters()`/`g_vocations()` registries and the `IOLoginData`
 * login-load seam so the listener composes the `MonsterPipelinesHost` and the
 * game sessions over the same world instances.
 */
internal fun defaultListenerFactory(rsaCipher: RsaCipher = RsaCipher.fromEnvironment()): ListenerFactory =
    ListenerFactory { config, accountRepository, gameMap, dispatcher, luaEnvironment, composition ->
        CanaryServer(
            loginPort = config.loginPort,
            gamePort = config.gamePort,
            accountRepository = accountRepository,
            gameMap = gameMap,
            dispatcher = dispatcher,
            rsaCipher = rsaCipher,
            luaEnvironment = luaEnvironment,
            itemTypes = composition.itemTypes,
            imbuements = composition.imbuements,
            dataSource = composition.dataSource,
            configManager = composition.configManager,
            suppliedCreatureAsyncTaskBuckets = composition.creatureAsyncTaskBuckets,
            monsterComputeService = composition.monsterComputeService,
            monsters = composition.monsters,
            vocations = composition.vocations,
            ioLoginData = composition.playerSavePath.loginData,
        )
    }

/** JDBC/Hikari/Flyway implementation of [DatabaseBoot]; `null` config selects local H2. */
object JdbcDatabaseBoot : DatabaseBoot {
    override fun connect(config: DatabaseConfig?): DataSource {
        if (config == null) {
            logger.info { "No external database configured; initializing In-Memory Database (PostgreSQL mode)..." }
            return DatabaseConnectionFactory.createInMemoryDataSource("canary_local")
        }
        logger.info { "Connecting to external PostgreSQL at ${config.host}:${config.port}..." }
        return DatabaseConnectionFactory.createDataSource(config)
    }

    override fun migrate(dataSource: DataSource): Int = DatabaseConnectionFactory.migrate(dataSource)

    override fun close(dataSource: DataSource) {
        if (dataSource is AutoCloseable) {
            dataSource.close()
        } else {
            logger.warn { "DataSource ${dataSource.javaClass.name} is not closeable; skipping release" }
        }
    }
}

/**
 * Canary boot sequence, split out of `Main.kt`/`CanaryServer::run` so it is directly
 * testable: config -> database connect + Flyway -> map -> Lua scripting -> appearance
 * hook -> monster compute start -> dispatcher start -> listeners bind. [shutdown]
 * drains listeners, monster compute, dispatcher, SaveManager and database exactly
 * once and is safe to call twice (AGENTS.md).
 */
class ServerBootstrap(
    private val environment: Map<String, String> = System.getenv(),
    private val options: CliOptions = CliOptions(),
    private val databaseConfig: DatabaseConfig? = null,
    private val configBoot: ConfigBoot = ConfigBoot(ConfigManager::load),
    private val configManagerBoot: ConfigManagerBoot = ConfigManagerBoot(::defaultConfigManagerBoot),
    private val databaseBoot: DatabaseBoot = JdbcDatabaseBoot,
    private val mapBoot: MapBoot = MapBoot { GameMap() },
    private val scriptingBoot: ScriptingBoot = ScriptingBoot(::defaultScriptingBoot),
    private val appearanceBoot: AppearanceBoot = AppearanceBoot { config ->
        Appearances.load(Path.of(config.coreDir, "items", "appearances.dat").toFile())
    },
    private val itemBoot: ItemBoot = ItemBoot { config, appearances -> defaultItemBoot(config, appearances) },
    private val imbuementBoot: ImbuementBoot = ImbuementBoot { config ->
        Imbuements().apply {
            if (!loadFromFile(Path.of(config.coreDir, "XML", "imbuements.xml"))) {
                logger.warn { "Failed to load ${config.coreDir}/XML/imbuements.xml" }
            }
        }
    },
    /** `g_monsters()` loader (defaults to the XML/model definition path). */
    private val monsterBoot: MonsterBoot = MonsterBoot(::defaultMonsterBoot),
    /** `g_vocations()` loader (defaults to `{CORE_DIRECTORY}/XML/vocations.xml`). */
    private val vocationsBoot: VocationsBoot = VocationsBoot(::defaultVocationsBoot),
    private val listenerFactory: ListenerFactory = defaultListenerFactory(),
    /**
     * `g_dispatcher()` (`Dispatcher::init`, dispatcher.cpp:114-131): the booted
     * dispatcher or one this root composes over [configManagerBoot] and the
     * monster compute service. An injected dispatcher stays caller-owned.
     */
    private val dispatcher: GameDispatcher? = null,
    /**
     * `g_monsterComputeService()` (monster_compute_service.hpp:101): the
     * instance this root starts (`CanaryServer::run`, canary_server.cpp:215) and
     * drains (`CanaryServer::shutdown`, canary_server.cpp:628). When absent the
     * root creates and owns one.
     */
    private val monsterComputeService: MonsterComputeService? = null,
) {
    private val started = AtomicBoolean(false)
    private val shutdownLatch = CountDownLatch(1)
    private val drain = ShutdownDrain(
        stopListeners = { listener?.stop() },
        closeConnections = { listener?.closeConnections() },
        shutdownMonsterCompute = { activeMonsterComputeService?.shutdown() },
        shutdownDispatcher = { activeDispatcher?.shutdown() },
        flushSaveManager = { saveManager?.shutdown() },
        closeDatabase = { dataSource?.let(databaseBoot::close) }
    )

    private var dataSource: DataSource? = null
    private var listener: ListenerBoot? = null
    private var saveManager: SaveManager? = null
    private var activeDispatcher: GameDispatcher? = null
    private var activeMonsterComputeService: MonsterComputeService? = null
    private var prometheusEndpoint: PrometheusEndpoint? = null

    val isStarted: Boolean get() = started.get()
    val isShutdown: Boolean get() = drain.isDrained

    fun run() {
        check(!drain.isDrained) { "ServerBootstrap cannot run after shutdown" }
        if (!started.compareAndSet(false, true)) return

        val config = configBoot.load(environment, options)
        val configManager = configManagerBoot.load(environment, options)

        // `metricsEnablePrometheus` HTTP exporter over the in-process registry
        // (`metrics/README.md`); best-effort like the C++ boot.
        prometheusEndpoint = PrometheusEndpoint.startIfEnabled(configManager, MetricsRegistry())

        val source = databaseBoot.connect(databaseConfig)
        dataSource = source
        databaseBoot.migrate(source)

        val gameMap = mapBoot.load(config)
        val luaEnvironment = scriptingBoot.boot(config)
        val appearances = appearanceBoot.load(config)
        val imbuements = imbuementBoot.load(config)
        val itemTypes = itemBoot.load(config, appearances)
        val monsters = monsterBoot.load(config, luaEnvironment, configManager, itemTypes)
        val vocations = vocationsBoot.load(config, itemTypes, configManager)

        // `g_monsterComputeService().start(monsterComputeConfig)` after
        // `loadMaps` (src/canary_server.cpp:212-216); the dispatcher is
        // composed over it afterwards so its `WorkerCompletion` lane drains the
        // completions (dispatcher.cpp:127, 267-293).
        val computeService = monsterComputeService ?: MonsterComputeService()
        computeService.start(monsterComputeConfig(configManager))
        activeMonsterComputeService = computeService

        val runningDispatcher = dispatcher
            ?: GameDispatcher(config = configManager, monsterComputeService = computeService)
        activeDispatcher = runningDispatcher

        // `Creature::getCreatureAsyncTaskBuckets` world state (creature.cpp:34-55).
        val buckets = CreatureAsyncTaskBuckets(dispatcher = { activeDispatcher })

        // `IOLoginData::savePlayer` (src/io/iologindata.cpp:195-211) over one
        // shared `DBTransaction::executeWithinTransaction` scope
        // (src/database/database.hpp:233-256): every repository of the guard is
        // constructed over [saveTransaction] and `SaveManager` flushes through
        // the resulting path.
        val saveTransaction = PlayerSaveTransaction(source)
        val playerSavePath = PlayerSavePath(
            transaction = saveTransaction,
            loginData = IOLoginData(
                players = JdbcPlayerRepository(saveTransaction),
                accounts = JdbcAccountRepository(saveTransaction),
                vip = JdbcVipRepository(saveTransaction),
                storageRepository = JdbcPlayerStorageRepository(saveTransaction),
                items = JdbcPlayerItemsRepository(saveTransaction),
                depots = JdbcPlayerDepotRepository(saveTransaction),
                spells = JdbcPlayerSpellsRepository(saveTransaction),
                kills = JdbcPlayerKillsRepository(saveTransaction),
                stash = JdbcPlayerStashRepository(saveTransaction),
                rewards = JdbcPlayerRewardsRepository(saveTransaction),
                features = JdbcFeatureStateRepository(saveTransaction),
                itemTypes = itemTypes,
                imbuements = imbuements,
                vocations = vocations,
                saveTransaction = saveTransaction,
                depotChests = { player -> player.getDepotChests() },
                bestiaryPersistence = IOBestiaryPersistence(saveTransaction),
                preyPersistence = IOPreyPersistence(saveTransaction),
            ),
            players = JdbcPlayerRepository(saveTransaction),
        )
        saveManager = SaveManager(playerSavePath)

        runningDispatcher.start()

        val server = listenerFactory.create(
            config,
            JdbcAccountRepository(source),
            gameMap,
            runningDispatcher,
            luaEnvironment,
            GameComposition(source, itemTypes, imbuements, monsters, vocations, configManager, buckets, computeService, playerSavePath),
        )
        listener = server
        server.start()
        logger.info { "${config.serverName} server online!" }
    }

    /** `ServiceManager::run` equivalent: blocks until a signal or crash drains the server. */
    fun awaitShutdown() {
        shutdownLatch.await()
    }

    /** Idempotent drain: listeners -> connections -> monster compute -> dispatcher -> SaveManager -> database. */
    fun shutdown() {
        prometheusEndpoint?.close()
        prometheusEndpoint = null
        drain.drain()
        shutdownLatch.countDown()
    }
}

/** `CanaryServer::loadModules` Lua half: init state, wire directories, load libs + scripts. */
private fun defaultScriptingBoot(config: ServerConfig): LuaEnvironment {
    val environment = LuaEnvironment().apply { initState() }
    Scripts(environment).apply {
        setDirectories(Path.of(config.dataDir), Path.of(config.coreDir))
        loadLibs()
        loadScripts(Path.of(config.dataDir, "scripts"))
    }
    return environment
}

/**
 * `Items::loadFromProtobuf` + `Items::loadFromXml` (`src/items/items.cpp:153,281`)
 * over the booted [appearances] and `{CORE_DIRECTORY}/items/items.xml`. The
 * `ItemParse` port applies the XML attributes; the unported
 * `ItemParseAttributesMap` known-key dispatch (item_parse.hpp:19) reports every
 * key as known (PARITY GAP), and the weapon registry built by
 * `parseUnscriptedItems` is not shared with the combat host yet (PARITY GAP).
 * The known proficiency ids come from `proficiencies.json`, mirroring
 * `WeaponProficiency::loadFromJson` (weapon_proficiency.cpp:79).
 */
private fun defaultItemBoot(config: ServerConfig, appearances: Appearances): Items {
    val items = Items()
    val knownProficiencyIds = WeaponProficiencyLoader.loadFromFile(
        Path.of(config.coreDir, "items", "proficiencies.json")
    ).keys
    items.loadFromProtobuf(appearances, knownProficiencyIds = knownProficiencyIds)

    val itemParse = ItemParse(registry = ItemsParseRegistry(items))
    val xml = Files.readString(Path.of(config.coreDir, "items", "items.xml"))
    items.loadFromXml(xml, ItemAttributeParse { key, node, value, itemType ->
        itemParse.initParse(key, XmlNode(node), XmlAttribute(value), itemType)
        true
    })
    return items
}

/** `Items` view over the `ItemParseRegistry` seam (`src/items/functions/item/item_parse.hpp:267`). */
private class ItemsParseRegistry(private val items: Items) : ItemParseRegistry {
    override fun getItemType(id: Int): ItemType = items.getItemType(id)

    override fun addLadderId(newId: Int) = items.addLadderId(newId)

    override fun addDummyId(newId: Int, rate: Int) = items.addDummyId(newId, rate)
}

/**
 * Production [MonsterBoot]: reproduces Canary's module load order for monster types
 * (`CanaryServer::loadModules`, src/canary_server.cpp:568-601):
 *
 * 1. the core `data/scripts/lib` registration helper runs first
 *    (`register_monster_type.lua` defines `MonsterType:register`; the same order is
 *    replayed by `GameReload::reloadMonsters`, game_reload.cpp:216-217);
 * 2. every `*.lua` under `{DATA_DIRECTORY}/monster` runs through the booted Lua
 *    bridge, registering its type with `Game.createMonsterType` and applying the mask
 *    onto the `MonsterType` the registry holds;
 * 3. XML definitions under `{DATA_DIRECTORY}/monster` remain a fallback for datapacks
 *    that ship them (`MonsterPipelineService` -> `Monsters::tryAddMonsterType`,
 *    monsters.cpp:390-399).
 *
 * The monster scripts need the typed `g_configManager()` (`elements`/`reflects` read
 * `MIN_ELEMENTAL_RESISTANCE`, `MAX_ELEMENTAL_RESISTANCE`, `MAX_DAMAGE_REFLECTION` and
 * `SORT_LOOT_BY_CHANCE`), so this boot re-installs `configManager` over
 * [WorldConfigOperations] before running them, and re-installs the `Loot` seam over the
 * booted [Items] `nameToItems` catalog so named loot resolves like
 * `Item::items.getItemIdByName`. When no Lua state was booted (a test composition root
 * injecting a bare [LuaEnvironment]) the Lua half is skipped and the XML fallback alone
 * runs.
 */
internal fun defaultMonsterBoot(
    config: ServerConfig,
    luaEnvironment: LuaEnvironment,
    configManager: ConfigManager,
    itemTypes: Items,
): Monsters {
    val registry = Monsters()
    loadMonsterTypesFromLua(config, luaEnvironment, configManager, itemTypes, registry)
    loadMonsterTypesFromXml(config, registry)
    return registry
}

/**
 * Loads `g_monsters()` from the datapack Lua scripts
 * (`CanaryServer::loadModules`, src/canary_server.cpp:590-601): core `scripts/lib`
 * then `{DATA_DIRECTORY}/monster`.
 */
private fun loadMonsterTypesFromLua(
    config: ServerConfig,
    luaEnvironment: LuaEnvironment,
    configManager: ConfigManager,
    itemTypes: Items,
    registry: Monsters,
) {
    val world = luaEnvironment.worldOperations
    if (luaEnvironment.getLuaState() == null || world == null) {
        logger.info { "Lua state not booted; skipping the datapack monster scripts" }
        return
    }

    world.attachMonsters(registry)
    ConfigFunctions.install(luaEnvironment, WorldConfigOperations(configManager))
    // `MonsterType:createLoot` resolves named loot through `Item::items.getItemIdByName`
    // (`Loot:setIdFromName`); the item catalog is loaded before the monster scripts run,
    // so re-install the loot seam over the booted `Item::items` (`nameToItems`) view.
    val itemIdsByName = itemTypes.nameToItems.mapValues { it.value.first() }
    LootFunctions.install(luaEnvironment, WorldLootOperations(itemIdsByName))

    val scripts = Scripts(luaEnvironment)
    scripts.setDirectories(Path.of(config.dataDir), Path.of(config.coreDir))

    val coreLib = Path.of(config.coreDir, "scripts", "lib")
    if (!Files.isDirectory(coreLib)) {
        logger.warn { "[defaultMonsterBoot] - Core monster registration libs missing under $coreLib" }
    } else {
        scripts.loadScripts(coreLib)
    }

    val directory = Path.of(config.dataDir, "monster")
    if (!Files.isDirectory(directory)) {
        logger.info { "No Lua monster definitions under $directory" }
        return
    }

    val before = registry.monsters.size
    scripts.loadScripts(directory)
    logger.info {
        "Registered ${registry.monsters.size - before} monster types from Lua scripts under $directory"
    }
}

/** XML fallback for datapacks that ship XML monster definitions under the datapack. */
private fun loadMonsterTypesFromXml(config: ServerConfig, registry: Monsters) {
    val directory = Path.of(config.dataDir, "monster")
    if (!Files.isDirectory(directory)) {
        logger.info { "No XML monster definitions under $directory" }
        return
    }

    val pipeline = MonsterPipelineService()
    var loaded = 0
    Files.walk(directory).use { files ->
        files
            .filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".xml") }
            .sorted()
            .forEach { file ->
                val definition = try {
                    pipeline.parse(Files.readString(file))
                } catch (exception: Exception) {
                    logger.warn(exception) { "[defaultMonsterBoot] - Failed to parse $file" }
                    null
                }
                if (definition == null || definition.name.isEmpty()) {
                    return@forEach
                }
                if (registry.tryAddMonsterType(definition.name, monsterTypeOf(definition))) {
                    loaded++
                } else {
                    logger.warn { "[defaultMonsterBoot] - Monster type '${definition.name}' already registered; skipping $file" }
                }
            }
    }
    if (loaded > 0) {
        logger.info { "Loaded $loaded XML monster types from $directory" }
    }
}

/** Maps a `MonsterPipelineService` XML definition onto the [MonsterType] model. */
private fun monsterTypeOf(definition: MonsterTypeDefinition): MonsterType =
    MonsterType(definition.name).apply {
        info.experience = definition.experience
        info.baseSpeed = definition.speed
        info.health = definition.maxHealth
        info.healthMax = definition.maxHealth
        info.lootItems = definition.lootTable.map { entry ->
            LootBlock(
                id = entry.itemId,
                countMin = 1,
                countMax = entry.countMax,
                chance = entry.chanceBasisPoints,
            )
        }.toMutableList()
    }

/**
 * Production [VocationsBoot]: `Vocations::loadFromXml` (vocation.cpp:28) over
 * `{CORE_DIRECTORY}/XML/vocations.xml`, with the `g_configManager()` `RATE_*`
 * values behind the `Vocation` regen getters (vocation.cpp:337-367) and
 * `Item::items.getItemIdByName` for the wheel gem ids (vocation.cpp:405-411).
 */
internal fun defaultVocationsBoot(config: ServerConfig, itemTypes: Items, configManager: ConfigManager): Vocations {
    val registry = Vocations(rates = vocationRates(configManager), itemIdByName = itemTypes::getItemIdByName)
    val path = Path.of(config.coreDir, "XML", "vocations.xml")
    val xml = try {
        Files.readString(path)
    } catch (exception: Exception) {
        logger.warn(exception) { "[defaultVocationsBoot] - Failed to read $path; g_vocations() stays empty" }
        return registry
    }
    if (!registry.loadFromXml(xml)) {
        logger.warn { "[defaultVocationsBoot] - Failed to load $path" }
    }
    return registry
}

/** `g_configManager()` `RATE_*` reads backing the [VocationRates] seam. */
private fun vocationRates(configManager: ConfigManager): VocationRates {
    fun rate(name: String): Float {
        val key = ConfigKeys.byName(name) ?: return 1.0f
        return configManager.getFloat(key, key.defaultValue as Float)
    }

    return VocationRates(
        rateManaRegenSpeed = rate("RATE_MANA_REGEN_SPEED"),
        rateManaRegen = rate("RATE_MANA_REGEN"),
        rateHealthRegenSpeed = rate("RATE_HEALTH_REGEN_SPEED"),
        rateHealthRegen = rate("RATE_HEALTH_REGEN"),
        rateSoulRegenSpeed = rate("RATE_SOUL_REGEN_SPEED"),
        rateAttackSpeed = rate("RATE_ATTACK_SPEED"),
    )
}
