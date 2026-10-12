package org.opentibia.canary.lua.runtime

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.items.Item
import org.opentibia.canary.map.Position
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Proves that [ScriptingDispatch] is the runtime entry point that runs the non-packet Lua
 * event families through [LuaEventDispatcher] on the single-threaded WorldCommit lane.
 */
class ScriptingDispatchTest {

    private fun env(): LuaEnvironment = LuaEnvironment().apply { initState() }

    @Test
    fun `talkActionOnSay dispatches the registered callback on the world commit lane`() {
        val environment = env()
        environment.loadChunk(
            """
            local t = TalkAction("!dispatch")
            function t.onSay(player, words, param)
                seen_dispatch_player = player:getName()
                seen_dispatch_words = words
                seen_dispatch_param = param
            end
            t:register()
            """.trimIndent()
        )

        val player = Player(1, "Hero", 100, 100, position = Position(100, 100, 7))
        val dispatcher = GameDispatcher().apply { start() }
        try {
            val scripting = ScriptingDispatch(environment, dispatcher)
            assertThat(scripting.talkActionOnSay("!dispatch", player, "hi")).isTrue()

            val ran = CountDownLatch(1)
            dispatcher.dispatch { ran.countDown() }
            assertThat(ran.await(2, TimeUnit.SECONDS)).isTrue()

            assertThat(environment.getGlobal("seen_dispatch_player").tojstring()).isEqualTo("Hero")
            assertThat(environment.getGlobal("seen_dispatch_words").tojstring()).isEqualTo("!dispatch")
            assertThat(environment.getGlobal("seen_dispatch_param").tojstring()).isEqualTo("hi")
        } finally {
            dispatcher.shutdown()
        }
    }

    @Test
    fun `talkActionOnSay reports false and enqueues nothing when the words are unregistered`() {
        val environment = env()
        val player = Player(1, "Hero", 100, 100, position = Position(100, 100, 7))
        val dispatcher = GameDispatcher().apply { start() }
        try {
            val scripting = ScriptingDispatch(environment, dispatcher)
            assertThat(scripting.talkActionOnSay("!missing", player, "")).isFalse()

            val ran = CountDownLatch(1)
            dispatcher.dispatch { ran.countDown() }
            assertThat(ran.await(2, TimeUnit.SECONDS)).isTrue()
            assertThat(environment.getGlobal("seen_dispatch_player").isnil()).isTrue()
        } finally {
            dispatcher.shutdown()
        }
    }

    @Test
    fun `spellOnCastSpell dispatches the registered instant spell callback`() {
        val environment = env()
        environment.loadChunk(
            """
            local s = Spell("instant")
            s:name("Test Dispatch Strike")
            s:id(9)
            s:words("exura dispatch")
            function s.onCastSpell(creature, variant)
                seen_spell_creature = creature:getName()
                seen_spell_variant = variant.number
            end
            s:register()
            """.trimIndent()
        )

        val player = Player(1, "Hero", 100, 100, position = Position(100, 100, 7))
        val dispatcher = GameDispatcher().apply { start() }
        try {
            val scripting = ScriptingDispatch(environment, dispatcher)
            assertThat(
                scripting.spellOnCastSpell(
                    "exura dispatch", player, LuaVariant(type = LuaVariantType.NUMBER, number = 7),
                )
            ).isTrue()

            val ran = CountDownLatch(1)
            dispatcher.dispatch { ran.countDown() }
            assertThat(ran.await(2, TimeUnit.SECONDS)).isTrue()

            assertThat(environment.getGlobal("seen_spell_creature").tojstring()).isEqualTo("Hero")
            assertThat(environment.getGlobal("seen_spell_variant").toint()).isEqualTo(7)
        } finally {
            dispatcher.shutdown()
        }
    }

    @Test
    fun `moveEventOnStepIn dispatches the registered move event callback`() {
        val environment = env()
        environment.loadChunk(
            """
            local m = MoveEvent()
            m:type("stepin")
            m:id(410)
            function m.onStepIn(creature, item, position, fromPosition)
                seen_move_creature = creature:getName()
                seen_move_item = item:getId()
            end
            m:register()
            """.trimIndent()
        )

        val player = Player(1, "Hero", 100, 100, position = Position(100, 100, 7))
        val dispatcher = GameDispatcher().apply { start() }
        try {
            val scripting = ScriptingDispatch(environment, dispatcher)
            assertThat(
                scripting.moveEventOnStepIn(
                    410, player, Item(410), Position(100, 100, 7), Position(99, 100, 7),
                )
            ).isTrue()

            val ran = CountDownLatch(1)
            dispatcher.dispatch { ran.countDown() }
            assertThat(ran.await(2, TimeUnit.SECONDS)).isTrue()

            assertThat(environment.getGlobal("seen_move_creature").tojstring()).isEqualTo("Hero")
            assertThat(environment.getGlobal("seen_move_item").toint()).isEqualTo(410)
        } finally {
            dispatcher.shutdown()
        }
    }

    @Test
    fun `moveEventOnStepIn reports false for an item id with no registered move event`() {
        val environment = env()
        val player = Player(1, "Hero", 100, 100, position = Position(100, 100, 7))
        val dispatcher = GameDispatcher().apply { start() }
        try {
            val scripting = ScriptingDispatch(environment, dispatcher)
            assertThat(
                scripting.moveEventOnStepIn(
                    9999, player, Item(9999), Position(100, 100, 7), Position(99, 100, 7),
                )
            ).isFalse()
        } finally {
            dispatcher.shutdown()
        }
    }
}
