package org.opentibia.canary.network.protocol

import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.movement.Direction
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.items.Item
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class MovePacketHandlerTest {

    @Test
    fun `parses walk opcodes and updates player position`() {
        val map = GameMap()
        val startPos = Position(100, 100, 7)
        map.getOrCreateTile(startPos)
        map.getOrCreateTile(Position(100, 99, 7)) // North
        map.getOrCreateTile(Position(101, 99, 7)) // East from North

        val player = Player(1, "Hero", 100, 100, position = startPos)

        // Opcode 0x65 = Move North
        val handledNorth = MovePacketHandler.handle(player, opcode = 0x65, payload = Unpooled.EMPTY_BUFFER, map = map)
        assertThat(handledNorth).isTrue()
        assertThat(player.position).isEqualTo(Position(100, 99, 7))

        // Opcode 0x66 = Move East
        val handledEast = MovePacketHandler.handle(player, opcode = 0x66, payload = Unpooled.EMPTY_BUFFER, map = map)
        assertThat(handledEast).isTrue()
        assertThat(player.position).isEqualTo(Position(101, 99, 7))
    }

    @Test
    fun `parses directional turn opcodes without moving tile position`() {
        val map = GameMap()
        val startPos = Position(100, 100, 7)
        map.getOrCreateTile(startPos)

        val player = Player(1, "Hero", 100, 100, position = startPos)
        player.direction = Direction.North

        // Opcode 0x70 = Turn East
        val handledTurn = MovePacketHandler.handle(player, opcode = 0x70, payload = Unpooled.EMPTY_BUFFER, map = map)
        assertThat(handledTurn).isTrue()
        assertThat(player.direction).isEqualTo(Direction.East)
        assertThat(player.position).isEqualTo(startPos) // Did not change tile
    }

    @Test
    fun `handles stop auto walk opcode 0x69 without a payload`() {
        val map = GameMap()
        val startPos = Position(100, 100, 7)
        map.getOrCreateTile(startPos)
        val player = Player(1, "Hero", 100, 100, position = startPos)

        val handled = MovePacketHandler.handle(player, opcode = 0x69, payload = Unpooled.EMPTY_BUFFER, map = map)

        assertThat(handled).isTrue()
        assertThat(player.position).isEqualTo(startPos)
    }

    @Test
    fun `rejects movement when destination tile does not exist`() {
        val map = GameMap()
        val startPos = Position(100, 100, 7)
        map.getOrCreateTile(startPos) // only start exists

        val player = Player(1, "Hero", 100, 100, position = startPos)

        val handled = MovePacketHandler.handle(player, opcode = 0x65, payload = Unpooled.EMPTY_BUFFER, map = map)
        assertThat(handled).isFalse()
        assertThat(player.position).isEqualTo(startPos)
    }

    @Test
    fun `fires registered stepin and stepout Lua move events when the player moves`() {
        val environment = LuaEnvironment().apply { initState() }
        environment.loadChunk(
            """
            local entering = MoveEvent()
            entering:type("stepin")
            entering:id(410)
            function entering.onStepIn(creature, item, position, fromPosition)
                seen_stepin_creature = creature:getName()
                seen_stepin_item = item:getId()
                seen_stepin_pos = position:toString()
                seen_stepin_from = fromPosition:toString()
            end
            entering:register()

            local leaving = MoveEvent()
            leaving:type("stepout")
            leaving:id(411)
            function leaving.onStepOut(creature, item, position, fromPosition)
                seen_stepout_creature = creature:getName()
                seen_stepout_item = item:getId()
                seen_stepout_pos = position:toString()
                seen_stepout_from = fromPosition:toString()
            end
            leaving:register()
            """.trimIndent()
        )

        val map = GameMap()
        val startPos = Position(100, 100, 7)
        map.getOrCreateTile(startPos).addItem(Item(411))
        map.getOrCreateTile(Position(100, 99, 7)).addItem(Item(410))
        val player = Player(1, "Hero", 100, 100, position = startPos)

        val dispatcher = GameDispatcher().apply { start() }
        try {
            val handled = MovePacketHandler.handle(
                player, opcode = 0x65, payload = Unpooled.EMPTY_BUFFER, map = map,
                luaEnvironment = environment, dispatcher = dispatcher,
            )
            assertThat(handled).isTrue()
            assertThat(player.position).isEqualTo(Position(100, 99, 7))

            val ran = CountDownLatch(1)
            dispatcher.dispatch { ran.countDown() }
            assertThat(ran.await(2, TimeUnit.SECONDS)).isTrue()

            assertThat(environment.getGlobal("seen_stepin_creature").tojstring()).isEqualTo("Hero")
            assertThat(environment.getGlobal("seen_stepin_item").toint()).isEqualTo(410)
            assertThat(environment.getGlobal("seen_stepin_pos").tojstring()).isEqualTo("( 100, 99, 7 )")
            assertThat(environment.getGlobal("seen_stepin_from").tojstring()).isEqualTo("( 100, 100, 7 )")

            assertThat(environment.getGlobal("seen_stepout_creature").tojstring()).isEqualTo("Hero")
            assertThat(environment.getGlobal("seen_stepout_item").toint()).isEqualTo(411)
            assertThat(environment.getGlobal("seen_stepout_pos").tojstring()).isEqualTo("( 100, 100, 7 )")
            assertThat(environment.getGlobal("seen_stepout_from").tojstring()).isEqualTo("( 100, 100, 7 )")
        } finally {
            dispatcher.shutdown()
        }
    }
}
