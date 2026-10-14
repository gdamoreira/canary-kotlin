package org.opentibia.canary.lua.runtime

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Transcribes the script-environment reload lifecycle: `LuaEnvironment::reInitState` /
 * `closeState` (`src/lua/scripts/lua_environment.cpp:49-87`) and the shared-state
 * handling of `LuaScriptInterface` (`src/lua/scripts/luascript.cpp:731-760`).
 */
class LuaScriptReloadTest {

    @Test
    fun `reInitState drops script globals and reinstalls the bindings`() {
        val environment = LuaEnvironment().apply { initState() }
        environment.loadChunk("reload_probe = 41")
        assertThat(environment.getGlobal("reload_probe").toint()).isEqualTo(41)

        assertThat(environment.reInitState()).isTrue()

        assertThat(environment.getGlobal("reload_probe").isnil()).isTrue()
        environment.loadChunk(
            "bindings_alive = (Action ~= nil) and (Game ~= nil) and " +
                "(rawgetmetatable('Player').__metatable.addItem ~= nil)"
        )
        assertThat(environment.getGlobal("bindings_alive").toboolean()).isTrue()
    }

    @Test
    fun `reInitState replaces the lua state`() {
        val environment = LuaEnvironment().apply { initState() }
        val originalState = environment.getLuaState()

        environment.reInitState()

        assertThat(environment.getLuaState()).isNotSameAs(originalState)
    }

    @Test
    fun `closeState closes the environment until initState runs again`() {
        val environment = LuaEnvironment().apply { initState() }
        assertThat(environment.getLuaState()).isNotNull()

        assertThat(environment.closeState()).isTrue()

        assertThat(environment.getLuaState()).isNull()
        assertThat(environment.closeState()).isFalse()

        environment.initState()
        assertThat(environment.getLuaState()).isNotNull()
        environment.loadChunk("after_reopen = true")
        assertThat(environment.getGlobal("after_reopen").toboolean()).isTrue()
    }
}
