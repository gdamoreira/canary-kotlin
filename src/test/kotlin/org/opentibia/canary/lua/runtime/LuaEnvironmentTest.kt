package org.opentibia.canary.lua.runtime

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class LuaEnvironmentTest {
    @Test
    fun `loads a chunk and exposes globals`() {
        val env = LuaEnvironment()
        env.initState()
        env.loadChunk("answer = 6 * 7")
        assertThat(env.getGlobal("answer").toint()).isEqualTo(42)
    }

    @Test
    fun `unknown global is nil`() {
        val env = LuaEnvironment()
        env.initState()
        assertThat(env.getGlobal("missing").isnil()).isTrue()
    }

    @Test
    fun `syntax error surfaces as ScriptError with chunk name`() {
        val env = LuaEnvironment()
        env.initState()
        assertThatThrownBy { env.loadChunk("function (", "=(bad)") }
            .isInstanceOf(ScriptError::class.java)
            .hasMessageContaining("bad")
    }

    @Test
    fun `compat shim defines rawgetmetatable`() {
        val env = LuaEnvironment()
        env.initState()
        env.loadChunk("present = (rawgetmetatable ~= nil)")
        assertThat(env.getGlobal("present").toboolean()).isTrue()
    }
}
