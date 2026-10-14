package org.opentibia.canary.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * Bundled-path resolution for the installed distribution: the Gradle start
 * scripts export `APP_HOME` (the folder of the executable), and the server
 * looks for `config.lua`, `data`, `data-canary` and `data-otservbr-global`
 * there. Without `APP_HOME` the working-directory behavior is unchanged.
 */
class AppPathsTest {

    @TempDir
    lateinit var home: Path

    private val appHome: Map<String, String>
        get() = mapOf("APP_HOME" to home.toString())

    @Test
    fun `default config file is discovered next to the executable`() {
        Files.writeString(home.resolve("config.lua"), """serverName = "From App Home"""")

        assertThat(AppPaths.defaultConfigFile(appHome)).isEqualTo(home.resolve("config.lua"))
    }

    @Test
    fun `no default config file when absent`() {
        assertThat(AppPaths.defaultConfigFile(appHome)).isNull()
    }

    @Test
    fun `bundled directories resolve against the executable folder`() {
        Files.createDirectory(home.resolve("data"))
        Files.createDirectory(home.resolve("data-canary"))

        assertThat(AppPaths.resolveDirectory("data", appHome)).isEqualTo(home.resolve("data").toString())
        assertThat(AppPaths.resolveDirectory("data-canary", appHome)).isEqualTo(home.resolve("data-canary").toString())
        assertThat(AppPaths.resolveDirectory("data-otservbr-global", appHome)).isEqualTo("data-otservbr-global")
    }

    @Test
    fun `without app home the relative name is kept`() {
        assertThat(AppPaths.resolveDirectory("data-canary", emptyMap())).isEqualTo("data-canary")
        assertThat(AppPaths.defaultConfigFile(emptyMap())).isNull()
    }

    @Test
    fun `boot without --config picks up config lua from the app home`() {
        Files.writeString(
            home.resolve("config.lua"),
            """
            serverName = "App Home Server"
            loginProtocolPort = 7001
            dataPackDirectory = "data-otservbr-global"
            """.trimIndent(),
        )
        Files.createDirectory(home.resolve("data-otservbr-global"))

        val config = ConfigManager.load(environment = appHome, options = CliOptions())

        assertThat(config.serverName).isEqualTo("App Home Server")
        assertThat(config.loginPort).isEqualTo(7001)
        assertThat(config.dataDir).isEqualTo(home.resolve("data-otservbr-global").toString())
    }

    @Test
    fun `core directory honours config lua and resolves next to the executable`() {
        Files.writeString(home.resolve("config.lua"), """coreDirectory = "data"""")
        Files.createDirectory(home.resolve("data"))

        val config = ConfigManager.load(environment = appHome, options = CliOptions())

        assertThat(config.coreDir).isEqualTo(home.resolve("data").toString())
    }

    @Test
    fun `explicit config path still wins over the default discovery`() {
        Files.writeString(home.resolve("config.lua"), """serverName = "Default"""")
        val explicit = home.resolve("other.lua")
        Files.writeString(explicit, """serverName = "Explicit"""")

        val config = ConfigManager.load(
            environment = appHome,
            options = CliOptions(configPath = explicit.toString()),
        )

        assertThat(config.serverName).isEqualTo("Explicit")
    }

    @Test
    fun `defaults stay untouched without an app home`() {
        val config = ConfigManager.load(environment = emptyMap(), options = CliOptions())

        assertThat(config.dataDir).isEqualTo("data-canary")
        assertThat(config.coreDir).isEqualTo("data")
    }
}
