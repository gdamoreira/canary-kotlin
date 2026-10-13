package org.opentibia.canary.config

import java.io.StringReader
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

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

object ConfigManager {

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
        val properties = Properties().apply {
            load(StringReader(content))
        }

        return baseConfig.copy(
            serverName = properties.getProperty("server.name", baseConfig.serverName),
            ip = properties.getProperty("server.ip", baseConfig.ip),
            loginPort = properties.getProperty("server.loginPort")?.toIntOrNull() ?: baseConfig.loginPort,
            gamePort = properties.getProperty("server.gamePort")?.toIntOrNull() ?: baseConfig.gamePort,
            statusPort = properties.getProperty("server.statusPort")?.toIntOrNull() ?: baseConfig.statusPort,
            maxPlayers = properties.getProperty("server.maxPlayers")?.toIntOrNull() ?: baseConfig.maxPlayers,
            motd = properties.getProperty("server.motd", baseConfig.motd),
            dataDir = properties.getProperty("server.dataDir", baseConfig.dataDir)
        )
    }
}
