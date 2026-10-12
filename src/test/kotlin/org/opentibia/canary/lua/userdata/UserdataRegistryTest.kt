package org.opentibia.canary.lua.userdata

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.Position

class UserdataRegistryTest {
    @Test
    fun `wrap exposes a Canary-named method via colon call`() {
        val env = LuaEnvironment().apply { initState() }
        val getId = env.globals.load("return function(self) return 7 end").call()
        env.userdata.registerMethod("Player", "getId", getId)

        val player = Player(7, "Hero", 100, 100, position = Position(1, 1, 7))
        env.globals.set("p", env.userdata.wrap("Player", player))
        env.loadChunk("result = p:getId()")

        assertThat(env.getGlobal("result").toint()).isEqualTo(7)
    }

    @Test
    fun `rawgetmetatable returns the same metatable as getmetatable`() {
        val env = LuaEnvironment().apply { initState() }
        val player = Player(1, "Hero", 100, 100, position = Position(1, 1, 7))
        env.globals.set("p", env.userdata.wrap("Player", player))
        env.loadChunk("same = (rawgetmetatable('Player') == getmetatable(p))")

        assertThat(env.getGlobal("same").toboolean()).isTrue()
    }

    @Test
    fun `rawgetmetatable auto-vivifies an unknown type and shares mutations`() {
        val env = LuaEnvironment().apply { initState() }
        env.loadChunk("mt = rawgetmetatable('Action')")
        assertThat(env.getGlobal("mt").isnil()).isFalse()

        env.loadChunk("rawgetmetatable('Action').marker = 5")
        env.loadChunk("shared = rawgetmetatable('Action').marker")
        assertThat(env.getGlobal("shared").toint()).isEqualTo(5)
    }
}
