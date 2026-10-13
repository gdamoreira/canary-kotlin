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
import org.opentibia.canary.lua.loader.Scripts
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.network.CanaryServer
import org.opentibia.canary.network.ListenerBoot
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

/** Appearance/protobuf load hook (`Game::loadAppearanceProtobuf`); supplied by the appearances wave. */
fun interface AppearanceBoot {
    fun load(config: ServerConfig)
}

/** Creates the network listener (`CanaryServer`) from the booted services. */
fun interface ListenerFactory {
    fun create(
        config: ServerConfig,
        accountRepository: AccountRepository,
        gameMap: GameMap,
        dispatcher: GameDispatcher,
        luaEnvironment: LuaEnvironment
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
    private val appearanceBoot: AppearanceBoot = AppearanceBoot { },
    private val listenerFactory: ListenerFactory = ListenerFactory { config, accountRepository, gameMap, dispatcher, luaEnvironment ->
        CanaryServer(
            loginPort = config.loginPort,
            gamePort = config.gamePort,
            accountRepository = accountRepository,
            gameMap = gameMap,
            dispatcher = dispatcher,
            luaEnvironment = luaEnvironment
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
        appearanceBoot.load(config)

        saveManager = SaveManager(JdbcPlayerRepository(source))

        dispatcher.start()

        val server = listenerFactory.create(
            config,
            JdbcAccountRepository(source),
            gameMap,
            dispatcher,
            luaEnvironment
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
