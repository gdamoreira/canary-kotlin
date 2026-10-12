package org.opentibia.canary.lua.runtime

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Proves that LuaEnvironment.initState() installs the binding libraries and event
 * registries at boot, so scripts can both call engine primitives and register events
 * without any extra manual install.
 */
class LuaEnvironmentWiringTest {
    private fun env(): LuaEnvironment = LuaEnvironment().apply { initState() }

    @Test
    fun `initState installs every event constructor so scripts can register`() {
        val environment = env()

        environment.loadChunk(
            """
            local spell = Spell("instant")
            spell:name("Wired Instant")
            spell:id(1)
            spell:words("exura wired")
            function spell.onCastSpell(creature, variant) return true end
            spell_registered = spell:register()

            local login = CreatureEvent("WiredLogin")
            function login.onLogin(player) return true end
            login_registered = login:register()

            local record = GlobalEvent("WiredRecord")
            function record.onRecord(current, old) return true end
            record_registered = record:register()

            local step = MoveEvent()
            step:type("stepin")
            step:id(410)
            function step.onStepIn(creature, item, position, fromPosition) return true end
            step_registered = step:register()
            """.trimIndent()
        )

        assertThat(environment.getGlobal("spell_registered").toboolean()).isTrue()
        assertThat(environment.getGlobal("login_registered").toboolean()).isTrue()
        assertThat(environment.getGlobal("record_registered").toboolean()).isTrue()
        assertThat(environment.getGlobal("step_registered").toboolean()).isTrue()

        assertThat(environment.spells.getInstant("exura wired")).isNotNull
        assertThat(environment.creatureEvents.get("WiredLogin")).isNotNull
        assertThat(environment.globalEvents.get("WiredRecord")).isNotNull
        assertThat(environment.moveEvents.getByItemId(410)).hasSize(1)
    }

    @Test
    fun `initState installs the core binding libraries`() {
        val environment = env()
        environment.loadChunk(
            "checks = (Game ~= nil) and (Game.createItem ~= nil) and " +
                "(rawgetmetatable('Player').addItem ~= nil) and " +
                "(rawgetmetatable('Monster').getHealth ~= nil) and " +
                "(rawgetmetatable('Item').transform ~= nil) and " +
                "(rawgetmetatable('Position').sendMagicEffect ~= nil)"
        )
        assertThat(environment.getGlobal("checks").toboolean()).isTrue()
    }

    @Test
    fun `core bindings are callable after initState without throwing`() {
        val environment = env()
        environment.loadChunk("item = Game.createItem(2160, 3)")
        assertThat(environment.getGlobal("item").isnil()).isTrue()
    }
}
