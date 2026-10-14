package org.opentibia.canary.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * `ConfigManager::load` executes `config.lua` through Lua and reads the globals by
 * identifier (configmanager.cpp:380-417) plus the `OTCRFeatures` table
 * (`loadLuaOTCFeatures`, configmanager.cpp:592-599).
 *
 * Canary source of truth: `../.projects/canary/src/config/configmanager.cpp`
 */
class LuaConfigSourceTest {

    @TempDir
    lateinit var tempDir: Path

    private fun writeConfig(content: String): Path {
        val file = tempDir.resolve("config.lua")
        Files.writeString(file, content)
        return file
    }

    private fun managerFor(file: Path, environment: Map<String, String> = emptyMap()): ConfigManager =
        ConfigManager(
            keyMap = ConfigKeys.all.associateWith { it.luaIdentifier },
            environment = environment,
            properties = ConfigSource.ofLua(file),
        )

    private fun key(name: String) = ConfigKeys.all.first { it.name == name }

    @Test
    fun `lua globals resolve through the typed getters`() {
        val file = writeConfig(
            """
            -- Canary-style config.lua
            serverName = "Lua Server"
            loginProtocolPort = 7777
            gameProtocolPort = 7778
            statusProtocolPort = 7779
            maxPlayers = 500
            serverMotd = "Welcome from Lua"
            dataPackDirectory = "data-otservbr-global"
            rateExp = 2.5
            pvpMode = true
            """.trimIndent()
        )

        val manager = managerFor(file)

        assertThat(manager.getString(key("SERVER_NAME"))).isEqualTo("Lua Server")
        assertThat(manager.getNumber(key("LOGIN_PORT"))).isEqualTo(7777)
        assertThat(manager.getNumber(key("GAME_PORT"))).isEqualTo(7778)
        assertThat(manager.getNumber(key("STATUS_PORT"))).isEqualTo(7779)
        assertThat(manager.getNumber(key("MAX_PLAYERS"))).isEqualTo(500)
        assertThat(manager.getString(key("SERVER_MOTD"))).isEqualTo("Welcome from Lua")
        assertThat(manager.getString(key("DATA_DIRECTORY"))).isEqualTo("data-otservbr-global")
    }

    @Test
    fun `absent globals keep the getter defaults`() {
        val file = writeConfig("""serverName = "Only Name"""")

        val manager = managerFor(file)

        assertThat(manager.getString(key("SERVER_NAME"))).isEqualTo("Only Name")
        assertThat(manager.getNumber(key("LOGIN_PORT"), 7171)).isEqualTo(7171)
        assertThat(manager.getBoolean(key("ALLOW_RELOAD"), false)).isFalse()
    }

    @Test
    fun `config file value takes precedence over the environment`() {
        val file = writeConfig("""serverName = "From File"""")

        val manager = managerFor(file, environment = mapOf("CANARY_SERVER_NAME" to "From Env"))

        assertThat(manager.getString(key("SERVER_NAME"))).isEqualTo("From File")
    }

    @Test
    fun `OTCRFeatures table feeds the feature lists`() {
        val file = writeConfig(
            """
            OTCRFeatures = {
                enableFeature = { 101, 102, 200 },
                disableFeature = { 50 }
            }
            """.trimIndent()
        )

        val manager = managerFor(file)

        assertThat(manager.getEnabledFeaturesOTC()).containsExactly(101, 102, 200)
        assertThat(manager.getDisabledFeaturesOTC()).containsExactly(50)
    }

    @Test
    fun `reload re-reads the lua file`() {
        val file = writeConfig("""serverName = "First"""")
        val manager = managerFor(file)
        assertThat(manager.getString(key("SERVER_NAME"))).isEqualTo("First")

        Files.writeString(file, """serverName = "Second"""")
        assertThat(manager.reload()).isTrue()

        assertThat(manager.getString(key("SERVER_NAME"))).isEqualTo("Second")
    }

    @Test
    fun `ServerConfig load resolves canary globals from a lua file`() {
        val file = writeConfig(
            """
            serverName = "Lua Server"
            ip = "10.0.0.5"
            loginProtocolPort = 8888
            gameProtocolPort = 8889
            statusProtocolPort = 8890
            maxPlayers = 250
            serverMotd = "Hello"
            dataPackDirectory = "data-otservbr-global"
            """.trimIndent()
        )

        val config = ConfigManager.load(environment = emptyMap(), options = CliOptions(configPath = file.toString()))

        assertThat(config.serverName).isEqualTo("Lua Server")
        assertThat(config.ip).isEqualTo("10.0.0.5")
        assertThat(config.loginPort).isEqualTo(8888)
        assertThat(config.gamePort).isEqualTo(8889)
        assertThat(config.statusPort).isEqualTo(8890)
        assertThat(config.maxPlayers).isEqualTo(250)
        assertThat(config.motd).isEqualTo("Hello")
        assertThat(config.dataDir).isEqualTo("data-otservbr-global")
    }

    @Test
    fun `ServerConfig CLI data-dir still wins over the lua file`() {
        val file = writeConfig("""dataPackDirectory = "data-otservbr-global"""")

        val config = ConfigManager.load(
            environment = emptyMap(),
            options = CliOptions(configPath = file.toString(), dataDir = "custom-pack"),
        )

        assertThat(config.dataDir).isEqualTo("custom-pack")
    }

    @Test
    fun `properties config files keep their existing behavior`() {
        val file = tempDir.resolve("server.properties")
        Files.writeString(
            file,
            """
            server.name = Properties Server
            server.loginPort = 9999
            """.trimIndent(),
        )

        val config = ConfigManager.load(environment = emptyMap(), options = CliOptions(configPath = file.toString()))

        assertThat(config.serverName).isEqualTo("Properties Server")
        assertThat(config.loginPort).isEqualTo(9999)
    }

    @Test
    fun `the shipped config lua dist template parses`() {
        val template = Path.of("config.lua.dist")

        val manager = managerFor(template)

        assertThat(manager.getString(key("SERVER_NAME"))).isNotEmpty()
        assertThat(manager.getNumber(key("LOGIN_PORT"))).isEqualTo(7171)
        assertThat(manager.getNumber(key("GAME_PORT"))).isEqualTo(7172)
        assertThat(manager.getString(key("DATA_DIRECTORY"))).isEqualTo("data-otservbr-global")
        assertThat(manager.getEnabledFeaturesOTC()).isNotEmpty()
    }
}
