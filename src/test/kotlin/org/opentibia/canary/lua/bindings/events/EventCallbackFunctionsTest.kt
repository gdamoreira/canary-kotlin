package org.opentibia.canary.lua.bindings.events

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.opentibia.canary.lua.loader.Scripts
import org.opentibia.canary.lua.registry.EventCallbackType
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.lua.runtime.ScriptError
import java.nio.file.Path

/**
 * Covers `src/lua/functions/events/event_callback_functions.cpp`: the `EventCallback`
 * constructor, `type`, `register`, and the `luaEventCallbackLoad` loader exposed through
 * `Game.getEventCallbacks()` (registered by `game_functions.cpp`).
 */
class EventCallbackFunctionsTest {
    private fun env(): LuaEnvironment = LuaEnvironment().apply { initState() }

    @Test
    fun `event callback loads through Game getEventCallbacks and registers by type`() {
        val environment = env()
        environment.loadChunk(
            """
            local callback = EventCallback("PlayerLookWatcher")
            local loaders = Game.getEventCallbacks()
            loaded = loaders.playerOnLook(callback, function(player, ...) return true end)
            typed = callback:type("playerOnLook")
            registered = callback:register()
            """.trimIndent()
        )

        assertThat(environment.getGlobal("loaded").toboolean()).isTrue()
        assertThat(environment.getGlobal("typed").toboolean()).isTrue()
        assertThat(environment.getGlobal("registered").toboolean()).isTrue()
        val entry = environment.eventCallbacks.get(EventCallbackType.playerOnLook).single()
        assertThat(entry.name).isEqualTo("PlayerLookWatcher")
        assertThat(entry.callback.isfunction()).isTrue()
    }

    @Test
    fun `type matches enum names case-insensitively and rejects unknown names`() {
        val environment = env()
        environment.loadChunk(
            """
            local callback = EventCallback("CaseWatcher")
            ok = callback:type("PLAYERonlook")
            bad = callback:type("notAnEvent")
            """.trimIndent()
        )

        assertThat(environment.getGlobal("ok").toboolean()).isTrue()
        assertThat(environment.getGlobal("bad").toboolean()).isFalse()
    }

    @Test
    fun `register without a loaded callback returns no value`() {
        val environment = env()
        environment.loadChunk(
            """
            local callback = EventCallback("NoLoader")
            typed = callback:type("playerOnLook")
            registered = callback:register()
            """.trimIndent()
        )

        assertThat(environment.getGlobal("typed").toboolean()).isTrue()
        assertThat(environment.getGlobal("registered").isnil()).isTrue()
        assertThat(environment.eventCallbacks.names()).isEmpty()
    }

    @Test
    fun `register without a type returns no value`() {
        val environment = env()
        environment.loadChunk(
            """
            local callback = EventCallback("NoType")
            Game.getEventCallbacks().playerOnLook(callback, function(...) return true end)
            registered = callback:register()
            """.trimIndent()
        )

        assertThat(environment.getGlobal("registered").isnil()).isTrue()
        assertThat(environment.eventCallbacks.names()).isEmpty()
    }

    @Test
    fun `duplicate names of the same type are rejected unless skipDuplicationCheck is set`() {
        val environment = env()
        environment.loadChunk(
            """
            local loaders = Game.getEventCallbacks()

            local first = EventCallback("Dup")
            loaders.playerOnLook(first, function(...) return true end)
            first:type("playerOnLook")
            first_result = first:register()

            local duplicate = EventCallback("Dup")
            loaders.playerOnLook(duplicate, function(...) return true end)
            duplicate:type("playerOnLook")
            duplicate_result = duplicate:register()

            local skipped = EventCallback("Dup", true)
            loaders.playerOnLook(skipped, function(...) return true end)
            skipped:type("playerOnLook")
            skipped_result = skipped:register()
            """.trimIndent()
        )

        assertThat(environment.getGlobal("first_result").toboolean()).isTrue()
        assertThat(environment.getGlobal("duplicate_result").isnil()).isTrue()
        assertThat(environment.getGlobal("skipped_result").toboolean()).isTrue()
        assertThat(environment.eventCallbacks.get(EventCallbackType.playerOnLook)).hasSize(2)
    }

    @Test
    fun `the same name on different callback types is allowed`() {
        val environment = env()
        environment.loadChunk(
            """
            local loaders = Game.getEventCallbacks()

            local look = EventCallback("Shared")
            loaders.playerOnLook(look, function(...) return true end)
            look:type("playerOnLook")
            look:register()

            local turn = EventCallback("Shared")
            loaders.playerOnTurn(turn, function(...) return true end)
            turn:type("playerOnTurn")
            turn_result = turn:register()
            """.trimIndent()
        )

        assertThat(environment.getGlobal("turn_result").toboolean()).isTrue()
        assertThat(environment.eventCallbacks.get(EventCallbackType.playerOnLook)).hasSize(1)
        assertThat(environment.eventCallbacks.get(EventCallbackType.playerOnTurn)).hasSize(1)
    }

    @Test
    fun `a raw callback field assigned without the libs loader still registers`() {
        val environment = env()
        environment.loadChunk(
            """
            local callback = EventCallback("RawField")
            function callback.playerOnTurn(player) return true end
            callback:type("playerOnTurn")
            registered = callback:register()
            """.trimIndent()
        )

        assertThat(environment.getGlobal("registered").toboolean()).isTrue()
        assertThat(environment.eventCallbacks.get(EventCallbackType.playerOnTurn).single().name)
            .isEqualTo("RawField")
    }

    @Test
    fun `revscriptsys field assignment drives the loader through the metatable`() {
        val environment = env()
        Scripts(environment).apply {
            setDirectories(Path.of("data-canary"), Path.of("data"))
            loadLibs()
        }
        environment.loadChunk(
            """
            local callback = EventCallback("ZoneWatcher", true)
            function callback.zoneBeforeCreatureEnter(zone, creature) return true end
            registered = callback:register()
            """.trimIndent()
        )

        assertThat(environment.getGlobal("registered").toboolean()).isTrue()
        assertThat(environment.eventCallbacks.get(EventCallbackType.zoneBeforeCreatureEnter).single().name)
            .isEqualTo("ZoneWatcher")
    }

    @Test
    fun `an empty callback name fails loudly`() {
        val environment = env()
        assertThatThrownBy { environment.loadChunk("EventCallback('')") }
            .isInstanceOf(ScriptError::class.java)
    }

    @Test
    fun `Game getEventCallbacks exposes every enum name except none`() {
        val environment = env()
        environment.loadChunk(
            """
            local loaders = Game.getEventCallbacks()
            has_look = (type(loaders.playerOnLook) == "function")
            has_last = (type(loaders.last) == "function")
            has_none = (loaders.none ~= nil)
            """.trimIndent()
        )

        assertThat(environment.getGlobal("has_look").toboolean()).isTrue()
        assertThat(environment.getGlobal("has_last").toboolean()).isTrue()
        assertThat(environment.getGlobal("has_none").toboolean()).isFalse()
    }
}
