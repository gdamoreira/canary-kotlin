package org.opentibia.canary.config

import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.StringReader
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

private val logger = KotlinLogging.logger {}

/** Canary `ConfigManager::loadLuaOTCFeatures` fallback (src/config/configmanager.cpp:596-599). */
private val CANARY_FORCED_ENABLED_FEATURES_OTC = listOf(101, 102, 103, 118)

/** Integer ids inside a Canary `OTCRFeatures` list (`config.lua.dist:679-688`). */
private val OTC_FEATURE_NUMBER = Regex("-?\\d+")

/** camelCase -> `CANARY_UPPER_SNAKE`, matching the `ServerCli` environment contract. */
private val CAMEL_CASE_BOUNDARY = Regex("([a-z0-9])([A-Z])")

data class ServerConfig(
    val serverName: String = "Canary",
    val ip: String = "127.0.0.1",
    val loginPort: Int = 7171,
    val gamePort: Int = 7172,
    val statusPort: Int = 7171,
    val maxPlayers: Int = 1000,
    val motd: String = "Welcome to Canary Kotlin Edition!",
    val dataDir: String = "data-canary"
)

/**
 * Re-readable raw source of Canary identifiers (the Kotlin counterpart of the `config.lua`
 * file read by `ConfigManager::load`, configmanager.cpp:26). [ConfigManager.reload] invokes
 * [read] again, so file-backed sources observe edits.
 */
fun interface ConfigSource {

    fun read(): Map<String, String>

    companion object {
        /** No source: every getter resolves to its supplied default. */
        val EMPTY: ConfigSource = ConfigSource { emptyMap() }

        /** Fixed in-memory values keyed by Canary identifier (e.g. `"luaScriptBytecodeCache"`). */
        fun of(values: Map<String, String>): ConfigSource = ConfigSource { values }

        /** `identifier = value` properties text, re-parsed on every [ConfigManager.reload]. */
        fun of(content: String): ConfigSource = ConfigSource { propertiesOf(content) }

        /** `identifier = value` properties file, re-read on every [ConfigManager.reload]. */
        fun of(path: Path): ConfigSource = ConfigSource { propertiesOf(Files.readString(path)) }
    }
}

private fun propertiesOf(content: String): Map<String, String> {
    val properties = Properties().apply {
        load(StringReader(content))
    }
    return properties.stringPropertyNames().associateWith { properties.getProperty(it) }
}

/**
 * Canary `ConfigManager` (src/config/configmanager.cpp/.hpp) typed lookup surface.
 *
 * The legacy [load]/[loadFromPropertiesString] bootstrap entry points stay source-compatible
 * in the companion. The instance API is constructed with an injected [keyMap] mapping domain
 * keys (e.g. the `ConfigKeys` enum) to Canary identifiers; values resolve with the
 * `ConfigManager` precedence: getter default < `CANARY_*` environment < properties source
 * < CLI overrides.
 */
class ConfigManager(
    keyMap: Map<out Any, String>,
    private val environment: Map<String, String> = emptyMap(),
    private val properties: ConfigSource = ConfigSource.EMPTY,
    cliOverrides: Map<out Any, String> = emptyMap()
) {
    private val keys: Map<Any, String> = keyMap.toMap()
    private val overrides: Map<Any, String> = cliOverrides.toMap()
    private val environmentNames: Map<String, String> = keys.values
        .associateWith { environmentVariable(it) }

    private var resolved: Map<String, String> = properties.read()

    /**
     * Canary `ConfigManager::reload` (configmanager.cpp:432) clears its typed caches and
     * re-runs `load()`; here the re-read source is [properties]. Returns `false` and keeps
     * the previously resolved values when the source cannot be read, matching Canary's
     * failure return without leaving the server without a config.
     */
    fun reload(): Boolean {
        return try {
            resolved = properties.read()
            true
        } catch (exception: Exception) {
            logger.warn { "Failed to reload configuration; keeping previous values: ${exception.message}" }
            false
        }
    }

    /** Canary `ConfigManager::getBoolean` (configmanager.cpp:554). */
    fun getBoolean(key: Any, default: Boolean = false): Boolean =
        raw(key)?.lowercase()?.toBooleanStrictOrNull() ?: default

    /** Canary `ConfigManager::getNumber` (configmanager.cpp:535). */
    fun getNumber(key: Any, default: Int = 0): Int =
        raw(key)?.toIntOrNull() ?: default

    /** Canary `ConfigManager::getFloat` (configmanager.cpp:573). */
    fun getFloat(key: Any, default: Float = 0.0f): Float =
        raw(key)?.toFloatOrNull() ?: default

    /** Canary `ConfigManager::getString` (configmanager.cpp:516). */
    fun getString(key: Any, default: String = ""): String =
        raw(key) ?: default

    /** Canary `ConfigManager::getEnabledFeaturesOTC` (configmanager.cpp:630). */
    fun getEnabledFeaturesOTC(): List<Int> {
        val table = otcTable() ?: return CANARY_FORCED_ENABLED_FEATURES_OTC
        return parseOtcFeatureList(table, "enableFeature")
    }

    /** Canary `ConfigManager::getDisabledFeaturesOTC` (configmanager.cpp:634). */
    fun getDisabledFeaturesOTC(): List<Int> {
        val table = otcTable() ?: return emptyList()
        return parseOtcFeatureList(table, "disableFeature")
    }

    private fun raw(key: Any): String? {
        val identifier = keys[key] ?: return null
        overrides[key]?.let { return it }
        resolved[identifier]?.let { return it }
        return environment[environmentNames.getValue(identifier)]
    }

    private fun otcTable(): String? =
        resolved["OTCRFeatures"] ?: environment["CANARY_OTCR_FEATURES"]

    private fun parseOtcFeatureList(table: String, field: String): List<Int> {
        val fieldStart = table.indexOf(field)
        if (fieldStart < 0) {
            return emptyList()
        }
        val open = table.indexOf('{', fieldStart)
        if (open < 0) {
            return emptyList()
        }
        val close = table.indexOf('}', open)
        if (close < 0) {
            return emptyList()
        }
        return OTC_FEATURE_NUMBER.findAll(table.substring(open + 1, close)).map { it.value.toInt() }.toList()
    }

    companion object {

        /**
         * Resolves the effective server config with Canary boot precedence:
         * defaults < environment (`CANARY_*`) < `--config` properties file < CLI options.
         */
        fun load(
            environment: Map<String, String> = System.getenv(),
            options: CliOptions = CliOptions()
        ): ServerConfig {
            val baseConfig = fromEnvironment(environment)
            val fileConfig = options.configPath?.let { path ->
                applyProperties(baseConfig, Files.readString(Path.of(path)))
            } ?: baseConfig
            return fileConfig.copy(dataDir = options.dataDir ?: fileConfig.dataDir)
        }

        fun loadFromPropertiesString(content: String, environment: Map<String, String> = emptyMap()): ServerConfig {
            return applyProperties(fromEnvironment(environment), content)
        }

        private fun fromEnvironment(environment: Map<String, String>): ServerConfig {
            return ServerConfig(
                serverName = environment["CANARY_SERVER_NAME"] ?: "Canary",
                ip = environment["CANARY_IP"] ?: "127.0.0.1",
                loginPort = environment["CANARY_LOGIN_PORT"]?.toIntOrNull() ?: 7171,
                gamePort = environment["CANARY_GAME_PORT"]?.toIntOrNull() ?: 7172,
                statusPort = environment["CANARY_STATUS_PORT"]?.toIntOrNull() ?: 7171,
                maxPlayers = environment["CANARY_MAX_PLAYERS"]?.toIntOrNull() ?: 1000,
                motd = environment["CANARY_MOTD"] ?: "Welcome to Canary Kotlin Edition!",
                dataDir = environment["CANARY_DATA_DIR"] ?: "data-canary"
            )
        }

        private fun applyProperties(baseConfig: ServerConfig, content: String): ServerConfig {
            val properties = propertiesOf(content)

            return baseConfig.copy(
                serverName = properties["server.name"] ?: baseConfig.serverName,
                ip = properties["server.ip"] ?: baseConfig.ip,
                loginPort = properties["server.loginPort"]?.toIntOrNull() ?: baseConfig.loginPort,
                gamePort = properties["server.gamePort"]?.toIntOrNull() ?: baseConfig.gamePort,
                statusPort = properties["server.statusPort"]?.toIntOrNull() ?: baseConfig.statusPort,
                maxPlayers = properties["server.maxPlayers"]?.toIntOrNull() ?: baseConfig.maxPlayers,
                motd = properties["server.motd"] ?: baseConfig.motd,
                dataDir = properties["server.dataDir"] ?: baseConfig.dataDir
            )
        }
    }
}

private fun environmentVariable(identifier: String): String =
    "CANARY_" + identifier.replace(CAMEL_CASE_BOUNDARY, "$1_$2").uppercase()
