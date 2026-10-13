package org.opentibia.canary.network.protocol.map

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.movement.Direction
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.protocol.CreatureAppearanceFrame
import org.opentibia.canary.network.protocol.CreatureType

class TilePacketWriterTest {

    private fun ByteBuf.toByteArray(): ByteArray {
        val bytes = ByteArray(readableBytes())
        readBytes(bytes)
        return bytes
    }

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    private fun monsterFrame() = CreatureAppearanceFrame(
        id = 5000L,
        name = "M",
        health = 1,
        maxHealth = 1,
        direction = Direction.North,
        creatureType = CreatureType.MONSTER,
        known = true,
    )

    private fun monsterBody(): ByteArray = bytes(
        0x62, 0x00,
        0x88, 0x13, 0x00, 0x00,
        0x64,
        0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0xD7,
        0x00, 0x00,
        0x00,
        0x00,
        0x00,
        0x01,
        0x00,
        0xFF,
        0x00,
        0x01,
    )

    @Test
    fun `writes add tile item 0x6A with stack counts`() {
        val out = Unpooled.buffer()

        TilePacketWriter.writeAddTileItem(out, Position(100, 200, 7), 3, Item(106))
        TilePacketWriter.writeAddTileItem(
            out,
            Position(100, 200, 7),
            4,
            Item(2148, count = 100, type = ItemType(id = 2148, name = "gold coin", isStackable = true)),
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x6A,
                0x64, 0x00, 0xC8, 0x00, 0x07,
                0x03,
                0x6A, 0x00,
                0x6A,
                0x64, 0x00, 0xC8, 0x00, 0x07,
                0x04,
                0x64, 0x08,
                0x64,
            )
        )
    }

    @Test
    fun `writes update tile item 0x6B with the replacement item`() {
        val out = Unpooled.buffer()

        TilePacketWriter.writeUpdateTileItem(out, Position(100, 200, 7), 2, Item(107))

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x6B,
                0x64, 0x00, 0xC8, 0x00, 0x07,
                0x02,
                0x6B, 0x00,
            )
        )
    }

    @Test
    fun `writes update tile creature 0x6B with the creature body`() {
        val out = Unpooled.buffer()

        TilePacketWriter.writeUpdateTileCreature(out, Position(50, 60, 7), 4, monsterFrame())

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x6B,
                0x32, 0x00, 0x3C, 0x00, 0x07,
                0x04,
            ),
            *monsterBody(),
        )
    }

    @Test
    fun `writes update tile 0x69 for an existing and an empty tile`() {
        val out = Unpooled.buffer()
        val map = GameMap()
        val player = Player(1L, "Hero", 150, 150, position = Position(1000, 1000, 7))
        val tile = map.getOrCreateTile(Position(1000, 1000, 7))
        tile.ground = Item(106)

        TilePacketWriter.writeUpdateTile(out, player, tile, Position(1000, 1000, 7)) { monsterFrame() }
        TilePacketWriter.writeUpdateTile(out, player, null, Position(1000, 1000, 7)) { monsterFrame() }

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x69,
                0xE8, 0x03, 0xE8, 0x03, 0x07,
                0x6A, 0x00,
                0x00, 0xFF,
                0x69,
                0xE8, 0x03, 0xE8, 0x03, 0x07,
                0x01, 0xFF,
            )
        )
    }
}
