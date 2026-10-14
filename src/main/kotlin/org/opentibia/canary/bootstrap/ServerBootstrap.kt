package org.opentibia.canary.bootstrap

import io.github.oshai.kotlinlogging.KotlinLogging
import org.opentibia.canary.account.AccountRepository
import org.opentibia.canary.config.CliOptions
import org.opentibia.canary.config.ConfigManager
import org.opentibia.canary.config.ServerConfig
import org.opentibia.canary.database.DatabaseConfig
import org.opentibia.canary.database.DatabaseConnectionFactory
import org.opentibia.canary.database.JdbcAccountRepository
import org.opentibia.canary.database.JdbcPlayerRepository
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.game.scheduling.SaveManager
import org.opentibia.canary.io.appearances.Appearances
import org.opentibia.canary.items.ItemAttributeParse
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.items.Items
import org.opentibia.canary.items.functions.ItemParse
import org.opentibia.canary.items.functions.ItemParseRegistry
import org.opentibia.canary.items.functions.XmlAttribute
import org.opentibia.canary.items.functions.XmlNode
import org.opentibia.canary.lua.loader.Scripts
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.network.CanaryServer
import org.opentibia.canary.network.ListenerBoot
import org.opentibia.canary.systems.imbuement.Imbuements
import org.opentibia.canary.systems.proficiency.WeaponProficiencyLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import javax.sql.DataSource

private val logger = KotlinLogging.logger {}

/** Canary `CORE_DIRECTORY` default (config.lua.dist): engine Lua core lives under `data/`. */
private const val DEFAULT_CORE_DIRECTORY = "data"

/** Resolves the effective config (`ConfigManager` precedence: CLI > file > env > defaults). */
fun interface ConfigBoot {
    fun load(environment: Map<String, String>, options: CliOptions): ServerConfig
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
 * Booted services shared by every game connection (`g_game()`, `Item::items`,
 * `g_imbuements()`, `IOMarket::getInstance()`): created by [ServerBootstrap] from
 * the connected [DataSource] and the registries loaded by [ItemBoot]/[ImbuementBoot],
 * then injected into the [ListenerFactory].
 */
class GameComposition(
    /** `g_database()` source backing `IOMarket::getInstance()` (src/io/iomarket.cpp). */
    val dataSource: DataSource,
    /** `Item::items` registry (`src/items/items.hpp:389`). */
    val itemTypes: Items,
    /** `g_imbuements()` registry (`src/creatures/players/imbuements/imbuements.hpp:44`). */
    val imbuements: Imbuements,
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
 * hook -> dispatcher start -> listeners bind. [shutdown] drains listeners, dispatcher,
 * SaveManager and database exactly once and is safe to call twice (AGENTS.md).
 */
class ServerBootstrap(
    private val environment: Map<String, String> = System.getenv(),
    private val options: CliOptions = CliOptions(),
    private val databaseConfig: DatabaseConfig? = null,
    private val configBoot: ConfigBoot = ConfigBoot(ConfigManager::load),
    private val databaseBoot: DatabaseBoot = JdbcDatabaseBoot,
    private val mapBoot: MapBoot = MapBoot { GameMap() },
    private val scriptingBoot: ScriptingBoot = ScriptingBoot(::defaultScriptingBoot),
    private val appearanceBoot: AppearanceBoot = AppearanceBoot {
        Appearances.load(Path.of(DEFAULT_CORE_DIRECTORY, "items", "appearances.dat").toFile())
    },
    private val itemBoot: ItemBoot = ItemBoot { _, appearances -> defaultItemBoot(appearances) },
    private val imbuementBoot: ImbuementBoot = ImbuementBoot {
        Imbuements().apply {
            if (!loadFromFile(Path.of(DEFAULT_CORE_DIRECTORY, "XML", "imbuements.xml"))) {
                logger.warn { "Failed to load $DEFAULT_CORE_DIRECTORY/XML/imbuements.xml" }
            }
        }
    },
    private val listenerFactory: ListenerFactory = ListenerFactory { config, accountRepository, gameMap, dispatcher, luaEnvironment, composition ->
        CanaryServer(
            loginPort = config.loginPort,
            gamePort = config.gamePort,
            accountRepository = accountRepository,
            gameMap = gameMap,
            dispatcher = dispatcher,
            luaEnvironment = luaEnvironment,
            itemTypes = composition.itemTypes,
            imbuements = composition.imbuements,
            dataSource = composition.dataSource,
        )
    },
    private val dispatcher: GameDispatcher = GameDispatcher()
) {
    private val started = AtomicBoolean(false)
    private val shutdownLatch = CountDownLatch(1)
    private val drain = ShutdownDrain(
        stopListeners = { listener?.stop() },
        closeConnections = { listener?.closeConnections() },
        shutdownDispatcher = { dispatcher.shutdown() },
        flushSaveManager = { saveManager?.shutdown() },
        closeDatabase = { dataSource?.let(databaseBoot::close) }
    )

    private var dataSource: DataSource? = null
    private var listener: ListenerBoot? = null
    private var saveManager: SaveManager? = null

    val isStarted: Boolean get() = started.get()
    val isShutdown: Boolean get() = drain.isDrained

    fun run() {
        check(!drain.isDrained) { "ServerBootstrap cannot run after shutdown" }
        if (!started.compareAndSet(false, true)) return

        val config = configBoot.load(environment, options)

        val source = databaseBoot.connect(databaseConfig)
        dataSource = source
        databaseBoot.migrate(source)

        val gameMap = mapBoot.load(config)
        val luaEnvironment = scriptingBoot.boot(config)
        val appearances = appearanceBoot.load(config)
        val imbuements = imbuementBoot.load(config)
        val itemTypes = itemBoot.load(config, appearances)

        saveManager = SaveManager(JdbcPlayerRepository(source))

        dispatcher.start()

        val server = listenerFactory.create(
            config,
            JdbcAccountRepository(source),
            gameMap,
            dispatcher,
            luaEnvironment,
            GameComposition(source, itemTypes, imbuements),
        )
        listener = server
        server.start()
        logger.info { "${config.serverName} server online!" }
    }

    /** `ServiceManager::run` equivalent: blocks until a signal or crash drains the server. */
    fun awaitShutdown() {
        shutdownLatch.await()
    }

    /** Idempotent drain: listeners -> dispatcher -> SaveManager flush -> database close. */
    fun shutdown() {
        drain.drain()
        shutdownLatch.countDown()
    }
}

/** `CanaryServer::loadModules` Lua half: init state, wire directories, load libs + scripts. */
private fun defaultScriptingBoot(config: ServerConfig): LuaEnvironment {
    val environment = LuaEnvironment().apply { initState() }
    Scripts(environment).apply {
        setDirectories(Path.of(config.dataDir), Path.of(DEFAULT_CORE_DIRECTORY))
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
private fun defaultItemBoot(appearances: Appearances): Items {
    val items = Items()
    val knownProficiencyIds = WeaponProficiencyLoader.loadFromFile().keys
    items.loadFromProtobuf(appearances, knownProficiencyIds = knownProficiencyIds)

    val itemParse = ItemParse(registry = ItemsParseRegistry(items))
    val xml = Files.readString(Path.of(DEFAULT_CORE_DIRECTORY, "items", "items.xml"))
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
