package org.opentibia.canary.network.protocol

import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.movement.Direction
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position

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
    fun `rejects movement when destination tile does not exist`() {
        val map = GameMap()
        val startPos = Position(100, 100, 7)
        map.getOrCreateTile(startPos) // only start exists

        val player = Player(1, "Hero", 100, 100, position = startPos)

        val handled = MovePacketHandler.handle(player, opcode = 0x65, payload = Unpooled.EMPTY_BUFFER, map = map)
        assertThat(handled).isFalse()
        assertThat(player.position).isEqualTo(startPos)
    }
}
