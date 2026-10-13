package org.opentibia.canary.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class ConfigManagerTest {

    @Test
    fun `loads default configuration values when no overrides provided`() {
        val config = ConfigManager.load(environment = emptyMap())

        assertThat(config.serverName).isEqualTo("Canary")
        assertThat(config.ip).isEqualTo("127.0.0.1")
        assertThat(config.loginPort).isEqualTo(7171)
        assertThat(config.gamePort).isEqualTo(7172)
        assertThat(config.statusPort).isEqualTo(7171)
        assertThat(config.maxPlayers).isEqualTo(1000)
        assertThat(config.motd).isEqualTo("Welcome to Canary Kotlin Edition!")
        assertThat(config.dataDir).isEqualTo("data-canary")
    }

    @Test
    fun `overrides configuration with CANARY environment variables`() {
        val env = mapOf(
            "CANARY_SERVER_NAME" to "Kotlin OT",
            "CANARY_IP" to "192.168.1.100",
            "CANARY_LOGIN_PORT" to "7175",
            "CANARY_GAME_PORT" to "7176",
            "CANARY_MAX_PLAYERS" to "500",
            "CANARY_MOTD" to "Custom MOTD Message"
        )

        val config = ConfigManager.load(environment = env)

        assertThat(config.serverName).isEqualTo("Kotlin OT")
        assertThat(config.ip).isEqualTo("192.168.1.100")
        assertThat(config.loginPort).isEqualTo(7175)
        assertThat(config.gamePort).isEqualTo(7176)
        assertThat(config.maxPlayers).isEqualTo(500)
        assertThat(config.motd).isEqualTo("Custom MOTD Message")
    }

    @Test
    fun `parses key value properties string`() {
        val propertiesText = """
            server.name = Custom Properties Server
            server.loginPort = 7180
            server.maxPlayers = 250
        """.trimIndent()

        val config = ConfigManager.loadFromPropertiesString(propertiesText)

        assertThat(config.serverName).isEqualTo("Custom Properties Server")
        assertThat(config.loginPort).isEqualTo(7180)
        assertThat(config.maxPlayers).isEqualTo(250)
        // Default remains for unspecified
        assertThat(config.gamePort).isEqualTo(7172)
    }

    @Test
    fun `CLI options override environment and defaults`() {
        val env = mapOf("CANARY_DATA_DIR" to "env-data")

        val config = ConfigManager.load(environment = env, options = CliOptions(dataDir = "cli-data"))

        assertThat(config.dataDir).isEqualTo("cli-data")
    }

    @Test
    fun `CLI data dir overrides the default when no environment is set`() {
        val config = ConfigManager.load(environment = emptyMap(), options = CliOptions(dataDir = "cli-data"))

        assertThat(config.dataDir).isEqualTo("cli-data")
    }

    @Test
    fun `config file supplies values with CLI taking precedence`(@TempDir dir: Path) {
        val file = dir.resolve("canary.properties")
        Files.writeString(
            file,
            """
            server.name = File Server
            server.loginPort = 7200
            server.dataDir = file-data
            """.trimIndent()
        )

        val config = ConfigManager.load(
            environment = emptyMap(),
            options = CliOptions(configPath = file.toString(), dataDir = "cli-data")
        )

        assertThat(config.serverName).isEqualTo("File Server")
        assertThat(config.loginPort).isEqualTo(7200)
        assertThat(config.dataDir).isEqualTo("cli-data")
    }

    @Test
    fun `config file values override environment values`(@TempDir dir: Path) {
        val file = dir.resolve("canary.properties")
        Files.writeString(file, "server.name = File Server")

        val config = ConfigManager.load(
            environment = mapOf("CANARY_SERVER_NAME" to "Env Server"),
            options = CliOptions(configPath = file.toString())
        )

        assertThat(config.serverName).isEqualTo("File Server")
    }
}
