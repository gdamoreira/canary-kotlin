package org.opentibia.canary.lua.runtime

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.lua.bindings.SpellBindings
import org.opentibia.canary.map.Position
import org.opentibia.canary.systems.wheel.PlayerWheel

/**
 * Wave 67 D4 transcript tests for the spell-registry consumers: the Lua
 * `player:canLearnSpell`/`player:getInstantSpells` primitives
 * (`PlayerFunctions::luaPlayerCanLearnSpell`/`luaPlayerGetInstantSpells`,
 * player_functions.cpp) and `PlayerWheel`'s `g_spells()` gate
 * (`PlayerWheel::upgradeSpell`, player_wheel.cpp:3476) must resolve against the
 * registry the datapack scripts registered into (`SpellBindings`).
 */
class SpellConsumerRegistryTest {

    /** Boots a `LuaEnvironment` and registers one datapack-shaped instant spell. */
    private fun environmentWithRegisteredSpell(): LuaEnvironment {
        val env = LuaEnvironment().apply { initState() }
        env.loadChunk(
            """
            local spell = Spell("instant")
            spell:name("Ice Strike")
            spell:id(3)
            spell:words("exori frigo")
            spell:level(1)
            spell:vocation("sorcerer;true")
            function spell.onCastSpell(creature, variant) return true end
            spell:register()
            """.trimIndent(),
        )
        assertThat(SpellBindings.recordRegisteredSpells()).isEqualTo(1)
        return env
    }

    @Test
    fun `canLearnSpell consults the registry the datapack registered into`() {
        val env = environmentWithRegisteredSpell()
        val hero = Player(id = 7L, name = "Hero", maxHealth = 100, health = 100, position = Position(100, 100, 7))
        hero.level = 8
        env.globals.set("player", env.userdata.wrap("Player", hero))

        env.loadChunk(
            """
            learnable = player:canLearnSpell("Ice Strike")
            unknown = player:canLearnSpell("Missing Spell")
            """.trimIndent(),
        )

        assertThat(env.getGlobal("learnable").toboolean()).isTrue()
        assertThat(env.getGlobal("unknown").toboolean()).isFalse()
    }

    @Test
    fun `getInstantSpells lists the registry spell for the player vocation`() {
        val env = environmentWithRegisteredSpell()
        val hero = Player(id = 7L, name = "Hero", maxHealth = 100, health = 100, position = Position(100, 100, 7))
        hero.level = 8
        env.globals.set("player", env.userdata.wrap("Player", hero))

        env.loadChunk(
            """
            count = #player:getInstantSpells()
            first = player:getInstantSpells()[1]
            """.trimIndent(),
        )

        assertThat(env.getGlobal("count").toint()).isEqualTo(1)
        assertThat(env.getGlobal("first").get("name").tojstring()).isEqualTo("Ice Strike")
        assertThat(env.getGlobal("first").get("words").tojstring()).isEqualTo("exori frigo")
        assertThat(env.getGlobal("first").get("level").toint()).isEqualTo(1)
    }

    @Test
    fun `the wheel default registry is the booted g_spells registry`() {
        LuaEnvironment().apply { initState() }

        val wheel = PlayerWheel(vocationCipId = 1, level = 100)

        assertThat(wheel.spellRegistry).isSameAs(SpellBindings.spellRegistry())
    }
}
