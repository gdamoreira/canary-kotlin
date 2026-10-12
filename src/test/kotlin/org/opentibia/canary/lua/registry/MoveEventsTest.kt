package org.opentibia.canary.lua.registry

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.lua.bindings.MoveEventBindings
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.Position

class MoveEventsTest {
    private fun envWithEvents(): Pair<LuaEnvironment, MoveEvents> {
        val env = LuaEnvironment().apply { initState() }
        val events = MoveEventBindings.install(env)
        return env to events
    }

    @Test
    fun `a Lua move event registers by item id and captures onStepIn`() {
        val (env, events) = envWithEvents()
        env.loadChunk(
            """
            local move = MoveEvent()
            function move.onStepIn(creature, item, position, fromPosition) return true end
            move:type("stepin")
            move:id(2145, 2148)
            move:register()
            """.trimIndent()
        )

        assertThat(events.getByItemId(2145)).hasSize(1)
        assertThat(events.getByItemId(2148)).hasSize(1)
        val event = events.getByItemId(2145).first()
        assertThat(event.eventType).isEqualTo("stepin")
        assertThat(event.callback.isfunction()).isTrue()
    }

    @Test
    fun `onEquip infers the equip type`() {
        val (env, events) = envWithEvents()
        env.loadChunk(
            """
            local move = MoveEvent()
            function move.onEquip(player, item, slot, isCheck) return true end
            move:id(1234)
            move:register()
            """.trimIndent()
        )
        assertThat(events.getByItemId(1234).first().eventType).isEqualTo("equip")
    }

    @Test
    fun `action id and unique id are keyed`() {
        val (env, events) = envWithEvents()
        env.loadChunk(
            """
            local move = MoveEvent()
            function move.onStepOut(creature, item, position, fromPosition) return true end
            move:type("stepout")
            move:aid(100)
            move:uid(200)
            move:register()
            """.trimIndent()
        )
        assertThat(events.getByActionId(100)).hasSize(1)
        assertThat(events.getByUniqueId(200)).hasSize(1)
    }

    @Test
    fun `move event constructor captures level magicLevel slot vocation and position`() {
        val (env, events) = envWithEvents()
        env.globals.set("pos", env.userdata.wrap("Position", Position(10, 20, 7)))
        env.loadChunk(
            """
            local move = MoveEvent()
            function move.onStepIn(creature, item, position, fromPosition) return true end
            move:type("stepin")
            move:id(2145)
            move:level(10)
            move:magicLevel(3)
            move:slot("head")
            move:vocation("knight")
            move:position(pos)
            move:register()
            """.trimIndent()
        )

        val event = events.getByItemId(2145).first()
        assertThat(event.requiredLevel).isEqualTo(10)
        assertThat(event.requiredMagicLevel).isEqualTo(3)
        assertThat(event.slot).isEqualTo(1)
        assertThat(event.vocations).containsExactly("knight")
        assertThat(event.positions).containsExactly(Position(10, 20, 7))
    }

    @Test
    fun `a move event without an id does not register`() {
        val (env, events) = envWithEvents()
        env.loadChunk(
            """
            local move = MoveEvent()
            function move.onStepIn(creature, item, position, fromPosition) return true end
            move:type("stepin")
            result = move:register()
            """.trimIndent()
        )
        assertThat(events.all()).isEmpty()
        assertThat(env.getGlobal("result").toboolean()).isFalse()
    }

    @Test
    fun `a move event without a callback does not register`() {
        val (env, events) = envWithEvents()
        env.loadChunk(
            """
            local move = MoveEvent()
            move:id(1)
            result = move:register()
            """.trimIndent()
        )
        assertThat(events.all()).isEmpty()
        assertThat(env.getGlobal("result").toboolean()).isFalse()
    }
}
