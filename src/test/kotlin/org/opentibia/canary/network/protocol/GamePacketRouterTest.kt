package org.opentibia.canary.network.protocol

import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.movement.Direction
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position

class GamePacketRouterTest {

    @Test
    fun `unbundles and routes multiple chained client packets within single frame`() {
        val map = GameMap()
        val startPos = Position(100, 100, 7)
        map.getOrCreateTile(startPos)
        map.getOrCreateTile(Position(100, 99, 7)) // North
        map.getOrCreateTile(Position(101, 99, 7)) // East

        val player = Player(1, "Hero", 100, 100, position = startPos)

        // Create buffer with 3 bundled packets:
        // 1. Turn North (0x6F)
        // 2. Move North (0x65)
        // 3. Move East  (0x66)
        val frame = Unpooled.buffer()
        frame.writeByte(0x6F) // Turn North
        frame.writeByte(0x65) // Move North
        frame.writeByte(0x66) // Move East

        val context = GamePacketContext(map = map)
        val handledCount = GamePacketRouter.route(player, frame, context)

        assertThat(handledCount).isEqualTo(3)
        assertThat(player.direction).isEqualTo(Direction.East)
        assertThat(player.position).isEqualTo(Position(101, 99, 7))
    }
}
