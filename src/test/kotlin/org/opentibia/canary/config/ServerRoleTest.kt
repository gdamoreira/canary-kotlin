package org.opentibia.canary.config

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class ServerRoleTest {

    @Test
    fun `role defaults to ALL when neither CLI nor environment supplies one`() {
        assertThat(ServerCli.role(emptyMap(), CliOptions())).isEqualTo(ServerRole.ALL)
    }

    @Test
    fun `parses every role value from the CLI flag`() {
        assertThat(ServerCli.parse(arrayOf("--role", "all")).role).isEqualTo(ServerRole.ALL)
        assertThat(ServerCli.parse(arrayOf("--role", "login")).role).isEqualTo(ServerRole.LOGIN)
        assertThat(ServerCli.parse(arrayOf("--role", "game")).role).isEqualTo(ServerRole.GAME)
        assertThat(ServerCli.parse(arrayOf("--role=game")).role).isEqualTo(ServerRole.GAME)
    }

    @Test
    fun `role values are case-insensitive`() {
        assertThat(ServerCli.parse(arrayOf("--role", "LOGIN")).role).isEqualTo(ServerRole.LOGIN)
        assertThat(ServerCli.role(mapOf("CANARY_ROLE" to "Game"), CliOptions())).isEqualTo(ServerRole.GAME)
    }

    @Test
    fun `reads the role from the CANARY_ROLE environment variable`() {
        assertThat(ServerCli.role(mapOf("CANARY_ROLE" to "login"), CliOptions())).isEqualTo(ServerRole.LOGIN)
    }

    @Test
    fun `CLI role beats the environment`() {
        assertThat(ServerCli.role(mapOf("CANARY_ROLE" to "login"), CliOptions(role = ServerRole.GAME)))
            .isEqualTo(ServerRole.GAME)
    }

    @Test
    fun `an unknown CLI role fails fast naming the flag`() {
        assertThatThrownBy { ServerCli.parse(arrayOf("--role", "wizard")) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("--role")
    }

    @Test
    fun `an unknown environment role fails fast naming the variable`() {
        assertThatThrownBy { ServerCli.role(mapOf("CANARY_ROLE" to "wizard"), CliOptions()) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("CANARY_ROLE")
    }

    @Test
    fun `a blank environment role keeps the ALL default`() {
        assertThat(ServerCli.role(mapOf("CANARY_ROLE" to "  "), CliOptions())).isEqualTo(ServerRole.ALL)
    }

    @Test
    fun `server config resolves the role from CLI over the environment`() {
        val config = ConfigManager.load(
            environment = mapOf("CANARY_ROLE" to "login"),
            options = CliOptions(role = ServerRole.GAME),
        )

        assertThat(config.role).isEqualTo(ServerRole.GAME)
    }

    @Test
    fun `server config resolves the role from the environment`() {
        assertThat(ConfigManager.load(environment = mapOf("CANARY_ROLE" to "game")).role)
            .isEqualTo(ServerRole.GAME)
    }

    @Test
    fun `server config defaults the role to ALL`() {
        assertThat(ConfigManager.load(environment = emptyMap()).role).isEqualTo(ServerRole.ALL)
    }
}
