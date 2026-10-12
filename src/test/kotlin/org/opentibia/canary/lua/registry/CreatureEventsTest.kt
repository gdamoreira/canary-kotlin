package org.opentibia.canary.lua.registry

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.lua.bindings.CreatureEventBindings
import org.opentibia.canary.lua.runtime.LuaEnvironment

class CreatureEventsTest {
    private fun envWithEvents(): Pair<LuaEnvironment, CreatureEvents> {
        val env = LuaEnvironment().apply { initState() }
        val events = CreatureEventBindings.install(env)
        return env to events
    }

    @Test
    fun `a Lua creature event registers by name and infers the login type`() {
        val (env, events) = envWithEvents()
        env.loadChunk(
            """
            local playerLogin = CreatureEvent("NamelockLogin")
            function playerLogin.onLogin(player) return true end
            playerLogin:register()
            """.trimIndent()
        )

        val event = events.get("NamelockLogin")
        assertThat(event).isNotNull
        assertThat(event!!.eventType).isEqualTo("login")
        assertThat(event.callback.isfunction()).isTrue()
    }

    @Test
    fun `onDeath infers the death type`() {
        val (env, events) = envWithEvents()
        env.loadChunk(
            """
            local ev = CreatureEvent("PlayerDeath")
            function ev.onDeath(creature, corpse, lastHitKiller, mostDamageKiller, lastHitUnjustified, mostDamageUnjustified) return true end
            ev:register()
            """.trimIndent()
        )
        assertThat(events.get("PlayerDeath")!!.eventType).isEqualTo("death")
    }

    @Test
    fun `an explicit type overrides inference`() {
        val (env, events) = envWithEvents()
        env.loadChunk(
            """
            local ev = CreatureEvent("Thinker")
            function ev.onThink(creature, interval) return true end
            ev:type("think")
            ev:register()
            """.trimIndent()
        )
        assertThat(events.get("Thinker")!!.eventType).isEqualTo("think")
    }

    @Test
    fun `a creature event without a callback does not register`() {
        val (env, events) = envWithEvents()
        env.loadChunk(
            """
            local ev = CreatureEvent("NoCallback")
            result = ev:register()
            """.trimIndent()
        )
        assertThat(events.names()).isEmpty()
        assertThat(env.getGlobal("result").toboolean()).isFalse()
    }

    @Test
    fun `duplicate names are rejected`() {
        val (env, events) = envWithEvents()
        env.loadChunk(
            """
            local first = CreatureEvent("Dup")
            function first.onLogin(player) return true end
            first:register()

            local second = CreatureEvent("Dup")
            function second.onLogout(player) return true end
            result = second:register()
            """.trimIndent()
        )
        assertThat(events.names()).containsExactly("Dup")
        assertThat(events.get("Dup")!!.eventType).isEqualTo("login")
        assertThat(env.getGlobal("result").toboolean()).isFalse()
    }
}
