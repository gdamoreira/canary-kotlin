package org.opentibia.canary.lua.userdata

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.luaj.vm2.LuaValue
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.Position

/**
 * Transcribes the Canary userdata registration flow
 * (`src/lua/functions/lua_functions_loader.cpp:747-916`): `wrap` attaches the
 * registry metatable whose `__metatable` is the class table and whose `__index`
 * chain reaches the base class, `rawgetmetatable` returns that registry metatable,
 * `registerMetaMethod` installs metamethods on it and `setWeakMetatable` builds the
 * shared `_weak` copy.
 */
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
    fun `wrap resolves inherited base class methods through the class __index chain`() {
        val env = LuaEnvironment().apply { initState() }
        val getHealth = env.globals.load("return function(self) return 55 end").call()
        env.userdata.registerMethod("Creature", "getHealth", getHealth)

        val player = Player(7, "Hero", 100, 100, position = Position(1, 1, 7))
        env.globals.set("p", env.userdata.wrap("Player", player))
        env.loadChunk("health = p:getHealth()")

        assertThat(env.getGlobal("health").toint()).isEqualTo(55)
    }

    @Test
    fun `getmetatable returns the class table while rawgetmetatable returns the registry metatable`() {
        val env = LuaEnvironment().apply { initState() }
        val player = Player(1, "Hero", 100, 100, position = Position(1, 1, 7))
        env.globals.set("p", env.userdata.wrap("Player", player))
        env.loadChunk(
            """
            classTable = getmetatable(p) == Player
            registryIndex = rawgetmetatable('Player').__index == Player
            protected = rawgetmetatable('Player').__metatable == getmetatable(p)
            registryName = rawgetmetatable('Player').__name
            registryBase = rawgetmetatable('Player').baseclass
            classBaseIndex = getmetatable(Player).__index == Creature
            inherited = Player.getHealth ~= nil
            """.trimIndent(),
        )

        assertThat(env.getGlobal("classTable").toboolean()).isTrue()
        assertThat(env.getGlobal("registryIndex").toboolean()).isTrue()
        assertThat(env.getGlobal("protected").toboolean()).isTrue()
        assertThat(env.getGlobal("registryName").tojstring()).isEqualTo("Player")
        assertThat(env.getGlobal("registryBase").tojstring()).isEqualTo("Creature")
        assertThat(env.getGlobal("classBaseIndex").toboolean()).isTrue()
        assertThat(env.getGlobal("inherited").toboolean()).isTrue()
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

    @Test
    fun `__eq installed on the registry metatable compares wrapped userdata`() {
        val env = LuaEnvironment().apply { initState() }
        val alwaysEqual = env.globals.load("return function(a, b) return true end").call()
        env.classes.registerMetaMethod("Player", "__eq", alwaysEqual)

        val first = Player(7, "Hero", 100, 100, position = Position(1, 1, 7))
        val second = Player(8, "Other", 100, 100, position = Position(2, 2, 7))
        env.globals.set("a", env.userdata.wrap("Player", first))
        env.globals.set("b", env.userdata.wrap("Player", second))
        env.loadChunk("same = (a == b)")

        assertThat(env.getGlobal("same").toboolean()).isTrue()
    }

    @Test
    fun `revscriptsys __index override keeps methods reachable through getmetatable`() {
        val env = LuaEnvironment().apply { initState() }
        val player = Player(7, "Hero", 100, 100, position = Position(1, 1, 7))
        env.globals.set("p", env.userdata.wrap("Player", player))
        env.loadChunk(
            """
            rawgetmetatable('Player').__index = function(self, key)
                local methods = getmetatable(self)
                if key == 'uid' then
                    return methods.getId(self)
                end
                return methods[key]
            end
            """.trimIndent(),
        )
        env.loadChunk("uid = p.uid")
        env.loadChunk("hp = p:getHealth()")

        assertThat(env.getGlobal("uid").toint()).isEqualTo(7)
        assertThat(env.getGlobal("hp").toint()).isEqualTo(100)
    }

    @Test
    fun `creature classes register the shared __eq on their registry metatables`() {
        val env = LuaEnvironment().apply { initState() }
        env.loadChunk(
            """
            creatureEq = rawgetmetatable('Creature').__eq ~= nil
            playerEq = rawgetmetatable('Player').__eq ~= nil
            monsterEq = rawgetmetatable('Monster').__eq ~= nil
            npcEq = rawgetmetatable('Npc').__eq ~= nil
            shared = rawgetmetatable('Player').__eq == rawgetmetatable('Monster').__eq
            """.trimIndent(),
        )

        assertThat(env.getGlobal("creatureEq").toboolean()).isTrue()
        assertThat(env.getGlobal("playerEq").toboolean()).isTrue()
        assertThat(env.getGlobal("monsterEq").toboolean()).isTrue()
        assertThat(env.getGlobal("npcEq").toboolean()).isTrue()
        assertThat(env.getGlobal("shared").toboolean()).isTrue()
    }

    @Test
    fun `setWeakMetatable shares the weak metatable and copies the class fields`() {
        val env = LuaEnvironment().apply { initState() }
        val first = env.userdata.wrap("Player", Player(1, "Hero", 100, 100, position = Position(1, 1, 7)))
        val second = env.userdata.wrap("Player", Player(2, "Hero", 100, 100, position = Position(1, 1, 7)))

        env.classes.setWeakMetatable(first, "Player")
        env.classes.setWeakMetatable(second, "Player")

        val weak = first.getmetatable()
        assertThat(weak).isSameAs(second.getmetatable())
        assertThat(weak).isSameAs(env.classes.metatable("Player_weak"))
        assertThat(weak.get("__name").tojstring()).isEqualTo("Player")
        assertThat(weak.get("__gc").isnil()).isTrue()
        assertThat(weak.get("__index").istable()).isTrue()
        assertThat(weak.get("__index")).isSameAs(env.getGlobal("Player"))
        assertThat(LuaValue.userdataOf("x", weak).getmetatable()).isSameAs(weak)
    }
}
