package org.opentibia.canary.lua.registry

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.lua.runtime.ScriptError

class ActionsTest {
    @Test
    fun `a Lua action registers by id and captures onUse`() {
        val env = LuaEnvironment().apply { initState() }
        env.loadChunk(
            """
            local a = Action()
            a:id(3483)
            function a.onUse(player, item, fromPosition, target, toPosition, isHotkey)
                return true
            end
            a:register()
            """.trimIndent()
        )

        val action = env.actions.get(3483)
        assertThat(action).isNotNull
        assertThat(action!!.onUse.isfunction()).isTrue()
    }

    @Test
    fun `register without id fails loudly`() {
        val env = LuaEnvironment().apply { initState() }
        assertThatThrownBy {
            env.loadChunk(
                """
                local a = Action()
                function a.onUse(p) return true end
                a:register()
                """.trimIndent()
            )
        }.isInstanceOf(ScriptError::class.java)
    }

    @Test
    fun `register without onUse fails loudly`() {
        val env = LuaEnvironment().apply { initState() }
        assertThatThrownBy { env.loadChunk("local a = Action() a:id(7) a:register()") }
            .isInstanceOf(ScriptError::class.java)
    }

    @Test
    fun `id with a non-number argument fails loudly`() {
        val env = LuaEnvironment().apply { initState() }
        assertThatThrownBy { env.loadChunk("local a = Action() a:id('x')") }
            .isInstanceOf(ScriptError::class.java)
    }

    @Test
    fun `id with no argument fails loudly`() {
        val env = LuaEnvironment().apply { initState() }
        assertThatThrownBy { env.loadChunk("local a = Action() a:id()") }
            .isInstanceOf(ScriptError::class.java)
    }

    @Test
    fun `duplicate id re-registration replaces the previous action`() {
        val env = LuaEnvironment().apply { initState() }
        env.loadChunk("local a = Action() a:id(100) function a.onUse(p) return true end a:register()")
        env.loadChunk("local b = Action() b:id(100) function b.onUse(p) return false end b:register()")

        assertThat(env.actions.ids()).containsExactly(100)
        assertThat(env.actions.get(100)!!.onUse.isfunction()).isTrue()
    }
}
