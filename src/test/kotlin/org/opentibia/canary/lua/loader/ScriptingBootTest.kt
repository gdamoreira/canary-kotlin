package org.opentibia.canary.lua.loader

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.network.CanaryServer
import java.nio.file.Path

/**
 * Proves the boot sequence wired in Main.kt: Scripts.setDirectories(datapack, core)
 * loads Canary's data/libs.lua plus the shipped data-canary scripts into the shared
 * environment that CanaryServer (and therefore GameHandler) uses.
 */
class ScriptingBootTest {
    private val dataDir = Path.of("data-canary")
    private val coreDir = Path.of("data")
    private val shippedScripts = Path.of("data-canary/scripts")

    private fun bootEnvironment(): LuaEnvironment {
        val environment = LuaEnvironment().apply { initState() }
        Scripts(environment).apply {
            setDirectories(dataDir, coreDir)
            loadLibs()
            loadScripts(shippedScripts)
        }
        return environment
    }

    @Test
    fun `boot loads the shipped Canary libs so table contains becomes available`() {
        val environment = bootEnvironment()

        environment.loadChunk(
            """
            has_table_contains = (type(table.contains) == "function")
            contains_true = table.contains({ 1, 2, 3 }, 2)
            contains_false = table.contains({ 1, 2, 3 }, 4)
            """.trimIndent()
        )

        assertThat(environment.getGlobal("has_table_contains").toboolean()).isTrue()
        assertThat(environment.getGlobal("contains_true").toboolean()).isTrue()
        assertThat(environment.getGlobal("contains_false").toboolean()).isFalse()
    }

    @Test
    fun `boot loads shipped action scripts that register with the runtime`() {
        val environment = bootEnvironment()

        assertThat(environment.actions.get(611)).isNotNull // snow_heap.lua
        assertThat(environment.actions.get(3482)).isNotNull // trap.lua
    }

    @Test
    fun `the booted environment is the instance CanaryServer shares with game handlers`() {
        val environment = bootEnvironment()

        val server = CanaryServer(luaEnvironment = environment)

        assertThat(server.scriptingEnvironment).isSameAs(environment)
    }
}
