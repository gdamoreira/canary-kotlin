package org.opentibia.canary.lua.bindings.library

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.config.ConfigKeys
import org.opentibia.canary.config.ConfigManager
import org.opentibia.canary.config.ConfigSource
import org.opentibia.canary.lua.runtime.LuaEnvironment

/**
 * Transcript tests for the `configManager`/`configKeys` tables, mirroring
 * `ConfigFunctions` (src/lua/functions/core/game/config_functions.cpp).
 */
class ConfigFunctionsLibraryTest {

    private fun environment(operations: ConfigOperations): LuaEnvironment =
        LuaEnvironment().apply { initState() }.also { ConfigFunctions.install(it, operations) }

    private fun manager(values: Map<String, String>): ConfigManager {
        val keyMap = ConfigKeys.all.associateWith { it.luaIdentifier }
        return ConfigManager(keyMap, properties = ConfigSource.of(values))
    }

    @Test
    fun `configKeys exposes the ConfigKey_t declaration indexes`() {
        val environment = environment(WorldConfigOperations(manager(emptyMap())))

        environment.loadChunk(
            """
            actionsDelay = configKeys.ACTIONS_DELAY_INTERVAL
            serverName = configKeys.SERVER_NAME
            warnUnsafe = configKeys.WARN_UNSAFE_SCRIPTS
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("actionsDelay").toint()).isEqualTo(0)
        assertThat(environment.getGlobal("serverName").toint()).isEqualTo(273)
        assertThat(environment.getGlobal("warnUnsafe").toint()).isEqualTo(340)
    }

    @Test
    fun `configManager getters resolve through the ConfigManager`() {
        val environment = environment(
            WorldConfigOperations(
                manager(
                    mapOf(
                        "serverName" to "Kotlin Server",
                        "rateExp" to "3",
                        "luaScriptBytecodeCache" to "false",
                        "houseRentRate" to "1.239",
                    ),
                ),
            ),
        )

        environment.loadChunk(
            """
            name = configManager.getString(configKeys.SERVER_NAME)
            rate = configManager.getNumber(configKeys.RATE_EXPERIENCE)
            bytecode = configManager.getBoolean(configKeys.LUA_SCRIPT_BYTECODE_CACHE)
            rounded = configManager.getFloat(configKeys.HOUSE_RENT_RATE)
            precise = configManager.getFloat(configKeys.HOUSE_RENT_RATE, false)
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("name").tojstring()).isEqualTo("Kotlin Server")
        assertThat(environment.getGlobal("rate").toint()).isEqualTo(3)
        assertThat(environment.getGlobal("bytecode").toboolean()).isFalse()
        assertThat(environment.getGlobal("rounded").todouble()).isEqualTo(1.24)
        assertThat(environment.getGlobal("precise").todouble()).isCloseTo(1.239, org.assertj.core.data.Offset.offset(1e-6))
    }

    @Test
    fun `a zero key reports the wrong-enum error and returns the argument`() {
        val errors = mutableListOf<String>()
        val operations = object : ConfigOperations by UnwiredConfigOperations {
            override fun reportError(message: String) {
                errors += message
            }
        }
        val environment = environment(operations)

        environment.loadChunk("result = configManager.getString(0)")

        assertThat(errors).containsExactly("Wrong enum")
        assertThat(environment.getGlobal("result").toint()).isEqualTo(0)
    }
}
