package org.opentibia.canary.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

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
}
