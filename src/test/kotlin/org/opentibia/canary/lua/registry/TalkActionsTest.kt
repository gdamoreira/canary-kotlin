package org.opentibia.canary.lua.registry

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.lua.runtime.ScriptError

class TalkActionsTest {
    @Test
    fun `a Lua talkaction registers by normalized words`() {
        val env = LuaEnvironment().apply { initState() }
        env.loadChunk(
            """
            local t = TalkAction("!Online")
            function t.onSay(player, words, param) return true end
            t:register()
            """.trimIndent()
        )

        val action = env.talkActions.get("!online")
        assertThat(action).isNotNull
        assertThat(action!!.onSay.isfunction()).isTrue()
        assertThat(action.separator).isEqualTo(" ")
    }

    @Test
    fun `register without onSay fails loudly`() {
        val env = LuaEnvironment().apply { initState() }
        assertThatThrownBy { env.loadChunk("local t = TalkAction('!x') t:register()") }
            .isInstanceOf(ScriptError::class.java)
    }

    @Test
    fun `words is required`() {
        val env = LuaEnvironment().apply { initState() }
        assertThatThrownBy { env.loadChunk("local t = TalkAction()") }
            .isInstanceOf(ScriptError::class.java)
    }
}
