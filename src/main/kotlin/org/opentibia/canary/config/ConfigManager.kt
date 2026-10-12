package org.opentibia.canary.config

import java.io.StringReader
import java.util.Properties

data class ServerConfig(
    val serverName: String = "Canary",
    val ip: String = "127.0.0.1",
    val loginPort: Int = 7171,
    val gamePort: Int = 7172,
    val statusPort: Int = 7171,
    val maxPlayers: Int = 1000,
    val motd: String = "Welcome to Canary Kotlin Edition!",
    val dataDir: String = "data"
)

object ConfigManager {

    fun load(environment: Map<String, String> = System.getenv()): ServerConfig {
        return ServerConfig(
            serverName = environment["CANARY_SERVER_NAME"] ?: "Canary",
            ip = environment["CANARY_IP"] ?: "127.0.0.1",
            loginPort = environment["CANARY_LOGIN_PORT"]?.toIntOrNull() ?: 7171,
            gamePort = environment["CANARY_GAME_PORT"]?.toIntOrNull() ?: 7172,
            statusPort = environment["CANARY_STATUS_PORT"]?.toIntOrNull() ?: 7171,
            maxPlayers = environment["CANARY_MAX_PLAYERS"]?.toIntOrNull() ?: 1000,
            motd = environment["CANARY_MOTD"] ?: "Welcome to Canary Kotlin Edition!",
            dataDir = environment["CANARY_DATA_DIR"] ?: "data"
        )
    }

    fun loadFromPropertiesString(content: String, environment: Map<String, String> = emptyMap()): ServerConfig {
        val properties = Properties().apply {
            load(StringReader(content))
        }

        val baseConfig = load(environment)

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
