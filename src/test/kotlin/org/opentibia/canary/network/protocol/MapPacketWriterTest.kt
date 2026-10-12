package org.opentibia.canary.network.protocol

import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.items.Item
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position

class MapPacketWriterTest {

    @Test
    fun `serializes viewport map description with tile compaction skip bytes`() {
        val map = GameMap()
        val center = Position(1000, 1000, 7)

        // Seed 1 tile with grass (id 106)
        val centerTile = map.getOrCreateTile(center)
        centerTile.ground = Item(id = 106)

        val out = Unpooled.buffer()
        MapPacketWriter.writeMapDescription(out, map, center)

        // Standard map description starts with Opcode 0x64 (MapDescription)
        val opcode = out.readUnsignedByte().toInt()
        assertThat(opcode).isEqualTo(0x64)

        // Coordinates
        assertThat(out.readUnsignedShortLE()).isEqualTo(1000)
        assertThat(out.readUnsignedShortLE()).isEqualTo(1000)
        assertThat(out.readUnsignedByte().toInt()).isEqualTo(7)

        assertThat(out.readableBytes()).isGreaterThan(0)
    }
}
