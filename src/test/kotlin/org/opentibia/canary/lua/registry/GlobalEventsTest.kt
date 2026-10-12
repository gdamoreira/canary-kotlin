package org.opentibia.canary.lua.registry

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.lua.bindings.GlobalEventBindings
import org.opentibia.canary.lua.runtime.LuaEnvironment

class GlobalEventsTest {
    private fun envWithEvents(): Pair<LuaEnvironment, GlobalEvents> {
        val env = LuaEnvironment().apply { initState() }
        val events = GlobalEventBindings.install(env)
        return env to events
    }

    @Test
    fun `a Lua global event registers by name and infers the record type`() {
        val (env, events) = envWithEvents()
        env.loadChunk(
            """
            local onlineRecord = GlobalEvent("OnlineRecord")
            function onlineRecord.onRecord(current, old) return true end
            onlineRecord:register()
            """.trimIndent()
        )

        val event = events.get("OnlineRecord")
        assertThat(event).isNotNull
        assertThat(event!!.eventType).isEqualTo("record")
        assertThat(event.callback.isfunction()).isTrue()
    }

    @Test
    fun `an interval-only think event registers with no event type`() {
        val (env, events) = envWithEvents()
        env.loadChunk(
            """
            local encounterTick = GlobalEvent("encounters.tick.onThink")
            function encounterTick.onThink(interval, lastExecution) return true end
            encounterTick:interval(1000)
            encounterTick:register()
            """.trimIndent()
        )
        val event = events.get("encounters.tick.onThink")
        assertThat(event).isNotNull
        assertThat(event!!.eventType).isEmpty()
        assertThat(event.interval).isEqualTo(1000)
        assertThat(event.callback.isfunction()).isTrue()
    }

    @Test
    fun `a global event without a callback or interval does not register`() {
        val (env, events) = envWithEvents()
        env.loadChunk(
            """
            local ev = GlobalEvent("Empty")
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
            local first = GlobalEvent("Dup")
            function first.onStartup() return true end
            first:register()

            local second = GlobalEvent("Dup")
            function second.onShutdown() return true end
            result = second:register()
            """.trimIndent()
        )
        assertThat(events.names()).containsExactly("Dup")
        assertThat(events.get("Dup")!!.eventType).isEqualTo("startup")
        assertThat(env.getGlobal("result").toboolean()).isFalse()
    }
}
