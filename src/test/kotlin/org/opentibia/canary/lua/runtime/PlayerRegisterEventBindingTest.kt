package org.opentibia.canary.lua.runtime

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.map.Position

/**
 * Wave 67 D4 transcript tests for the Lua `Creature:registerEvent` /
 * `Creature:unregisterEvent` bindings (creature_functions.cpp:25-26,
 * 151-176), inherited by `Player` through the `Lua::registerSharedClass`
 * class chain so `player:registerEvent(name)` attaches a named
 * `CreatureEvent` (`Creature::registerCreatureEvent`, creature.cpp:1674).
 */
class PlayerRegisterEventBindingTest {

    @Test
    fun `player registerEvent and unregisterEvent route through the creature registry`() {
        val env = LuaEnvironment().apply { initState() }
        val hero = Player(id = 7L, name = "Hero", maxHealth = 100, health = 100, position = Position(100, 100, 7))
        hero.creatureEventRegistered = { name -> name == "prepareDeath" }
        env.globals.set("player", env.userdata.wrap("Player", hero))

        env.loadChunk(
            """
            attached = player:registerEvent("prepareDeath")
            unknown = player:registerEvent("missing event")
            duplicate = player:registerEvent("prepareDeath")
            """.trimIndent(),
        )

        assertThat(env.getGlobal("attached").toboolean()).isTrue()
        assertThat(env.getGlobal("unknown").toboolean()).isFalse()
        assertThat(env.getGlobal("duplicate").toboolean()).isFalse()
        assertThat(hero.getCreatureEvents()).containsExactly("prepareDeath")

        env.loadChunk("removed = player:unregisterEvent(\"prepareDeath\")")

        assertThat(env.getGlobal("removed").toboolean()).isTrue()
        assertThat(hero.getCreatureEvents()).isEmpty()
    }

    @Test
    fun `registerEvent without a creature receiver pushes nil like Canary`() {
        val env = LuaEnvironment().apply { initState() }

        env.loadChunk("result = Creature.registerEvent(1, \"prepareDeath\")")

        assertThat(env.getGlobal("result").isnil()).isTrue()
    }
}
