package org.opentibia.canary.lua.loader

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.opentibia.canary.lua.runtime.LuaEnvironment
import java.nio.file.Files
import java.nio.file.Path

class ScriptsTest {
    @Test
    fun `loads every lua file in a directory`(@TempDir dir: Path) {
        Files.writeString(dir.resolve("a.lua"), "loaded_a = true")
        Files.writeString(dir.resolve("b.lua"), "loaded_b = true")

        val env = LuaEnvironment().apply { initState() }
        Scripts(env).loadScripts(dir)

        assertThat(env.getGlobal("loaded_a").toboolean()).isTrue()
        assertThat(env.getGlobal("loaded_b").toboolean()).isTrue()
    }

    @Test
    fun `a broken file logs and does not stop loading siblings`(@TempDir dir: Path) {
        Files.writeString(dir.resolve("broken.lua"), "function (")
        Files.writeString(dir.resolve("ok.lua"), "loaded_ok = true")

        val env = LuaEnvironment().apply { initState() }
        Scripts(env).loadScripts(dir)

        assertThat(env.getGlobal("loaded_ok").toboolean()).isTrue()
    }
}
