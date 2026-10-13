package org.opentibia.canary.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class ConfigManagerTypedTest {

    private enum class Key {
        IP,
        SERVER_NAME,
        MAX_PLAYERS,
        RATE_EXP,
        LUA_SCRIPT_BYTECODE_CACHE,
        LUA_SCRIPT_BYTECODE_CACHE_PATH
    }

    private val keyMap: Map<Key, String> = mapOf(
        Key.IP to "ip",
        Key.SERVER_NAME to "serverName",
        Key.MAX_PLAYERS to "maxPlayers",
        Key.RATE_EXP to "rateExp",
        Key.LUA_SCRIPT_BYTECODE_CACHE to "luaScriptBytecodeCache",
        Key.LUA_SCRIPT_BYTECODE_CACHE_PATH to "luaScriptBytecodeCachePath"
    )

    private fun manager(
        properties: ConfigSource = ConfigSource.EMPTY,
        environment: Map<String, String> = emptyMap(),
        cli: Map<Key, String> = emptyMap()
    ): ConfigManager = ConfigManager(keyMap, environment, properties, cli)

    private fun managerFrom(content: String): ConfigManager = manager(properties = ConfigSource.of(content))

    @Test
    fun `typed getters fall back to the supplied defaults when no source defines a key`() {
        val config = manager()

        assertThat(config.getBoolean(Key.LUA_SCRIPT_BYTECODE_CACHE, true)).isTrue()
        assertThat(config.getNumber(Key.MAX_PLAYERS, 1000)).isEqualTo(1000)
        assertThat(config.getFloat(Key.RATE_EXP, 1.5f)).isEqualTo(1.5f)
        assertThat(config.getString(Key.SERVER_NAME, "Canary")).isEqualTo("Canary")
    }

    @Test
    fun `typed getters resolve the Canary lua flags from properties`() {
        val config = managerFrom(
            """
            luaScriptBytecodeCache = false
            luaScriptBytecodeCachePath = cache/custom-bytecode
            """.trimIndent()
        )

        assertThat(config.getBoolean(Key.LUA_SCRIPT_BYTECODE_CACHE, true)).isFalse()
        assertThat(config.getString(Key.LUA_SCRIPT_BYTECODE_CACHE_PATH, "cache/lua-bytecode"))
            .isEqualTo("cache/custom-bytecode")
    }

    @Test
    fun `environment variables override defaults`() {
        val config = manager(
            environment = mapOf("CANARY_MAX_PLAYERS" to "300", "CANARY_SERVER_NAME" to "Env Server")
        )

        assertThat(config.getNumber(Key.MAX_PLAYERS, 1000)).isEqualTo(300)
        assertThat(config.getString(Key.SERVER_NAME, "Canary")).isEqualTo("Env Server")
    }

    @Test
    fun `properties override environment values`() {
        val config = manager(
            properties = ConfigSource.of(mapOf("maxPlayers" to "200")),
            environment = mapOf("CANARY_MAX_PLAYERS" to "300")
        )

        assertThat(config.getNumber(Key.MAX_PLAYERS, 1000)).isEqualTo(200)
    }

    @Test
    fun `CLI overrides properties environment and defaults`() {
        val config = manager(
            properties = ConfigSource.of(mapOf("maxPlayers" to "200")),
            environment = mapOf("CANARY_MAX_PLAYERS" to "300"),
            cli = mapOf(Key.MAX_PLAYERS to "100")
        )

        assertThat(config.getNumber(Key.MAX_PLAYERS, 1000)).isEqualTo(100)
    }

    @Test
    fun `invalid typed values fall back to the defaults`() {
        val config = managerFrom(
            """
            maxPlayers = many
            rateExp = fast
            luaScriptBytecodeCache = yes
            """.trimIndent()
        )

        assertThat(config.getNumber(Key.MAX_PLAYERS, 1000)).isEqualTo(1000)
        assertThat(config.getFloat(Key.RATE_EXP, 1.0f)).isEqualTo(1.0f)
        assertThat(config.getBoolean(Key.LUA_SCRIPT_BYTECODE_CACHE, true)).isTrue()
    }

    @Test
    fun `keys absent from the injected key map fall back to the defaults`() {
        val config = managerFrom("maxPlayers = 500")

        assertThat(config.getNumber("unknownKey", 7)).isEqualTo(7)
    }

    @Test
    fun `reload re-reads the properties source`(@TempDir dir: Path) {
        val file = dir.resolve("canary.properties")
        Files.writeString(file, "serverName = First")
        val config = manager(properties = ConfigSource.of(file))

        assertThat(config.getString(Key.SERVER_NAME, "Canary")).isEqualTo("First")

        Files.writeString(file, "serverName = Second")
        assertThat(config.reload()).isTrue()
        assertThat(config.getString(Key.SERVER_NAME, "Canary")).isEqualTo("Second")
    }

    @Test
    fun `reload reports failure and keeps previous values when the source cannot be read`() {
        var fail = false
        val source = ConfigSource {
            check(!fail) { "unreadable" }
            mapOf("serverName" to "Kept")
        }
        val config = manager(properties = source)

        assertThat(config.getString(Key.SERVER_NAME, "Canary")).isEqualTo("Kept")

        fail = true
        assertThat(config.reload()).isFalse()
        assertThat(config.getString(Key.SERVER_NAME, "Canary")).isEqualTo("Kept")
    }

    @Test
    fun `OTC feature lists are parsed from the config source`() {
        val config = managerFrom(
            "OTCRFeatures = { enableFeature = { 101, 118 }, disableFeature = { 102, 103 } }"
        )

        assertThat(config.getEnabledFeaturesOTC()).containsExactly(101, 118)
        assertThat(config.getDisabledFeaturesOTC()).containsExactly(102, 103)
    }

    @Test
    fun `reload re-reads the OTC feature table`() {
        var table = "{ enableFeature = { 101 }, disableFeature = { } }"
        val source = ConfigSource { mapOf("OTCRFeatures" to table) }
        val config = manager(properties = source)

        assertThat(config.getEnabledFeaturesOTC()).containsExactly(101)

        table = "{ enableFeature = { 102, 103 }, disableFeature = { 101 } }"
        assertThat(config.reload()).isTrue()
        assertThat(config.getEnabledFeaturesOTC()).containsExactly(102, 103)
        assertThat(config.getDisabledFeaturesOTC()).containsExactly(101)
    }

    @Test
    fun `missing OTC table keeps the Canary forced enabled features`() {
        val config = manager()

        assertThat(config.getEnabledFeaturesOTC()).containsExactly(101, 102, 103, 118)
        assertThat(config.getDisabledFeaturesOTC()).isEmpty()
    }

    @Test
    fun `declared OTC table without an enable list keeps the enabled list empty`() {
        val config = managerFrom("OTCRFeatures = { disableFeature = { 102 } }")

        assertThat(config.getEnabledFeaturesOTC()).isEmpty()
        assertThat(config.getDisabledFeaturesOTC()).containsExactly(102)
    }

    @Test
    fun `OTC feature table is also resolved from the environment`() {
        val config = manager(
            environment = mapOf(
                "CANARY_OTCR_FEATURES" to "{ enableFeature = { 118 }, disableFeature = { 101 } }"
            )
        )

        assertThat(config.getEnabledFeaturesOTC()).containsExactly(118)
        assertThat(config.getDisabledFeaturesOTC()).containsExactly(101)
    }
}
