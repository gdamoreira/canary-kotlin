package org.opentibia.canary.network.protocol.map

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.movement.Direction
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.protocol.CreatureAppearanceFrame
import org.opentibia.canary.network.protocol.CreatureType

class MapDescriptionWriterTest {

    private fun ByteBuf.toByteArray(): ByteArray {
        val bytes = ByteArray(readableBytes())
        readBytes(bytes)
        return bytes
    }

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    private fun player(position: Position = Position(1000, 1000, 7)) =
        Player(1L, "Hero", 150, 150, position = position)

    private fun monsterFrame(id: Long = 5000L) = CreatureAppearanceFrame(
        id = id,
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
    fun `writes an empty viewport 0x64 map description with skip run compaction`() {
        val out = Unpooled.buffer()
        val map = GameMap()

        MapDescriptionWriter.writeMapDescription(
            out,
            map,
            player(),
            Position(1000, 1000, 7),
        ) { monsterFrame() }

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x64,
                0xE8, 0x03, 0xE8, 0x03, 0x07,
                0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
                0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
                0xDF, 0xFF,
            )
        )
    }

    @Test
    fun `writes a surface 1x1 floor range with a skip byte before the tile`() {
        val out = Unpooled.buffer()
        val map = GameMap()
        map.getOrCreateTile(Position(1000, 1000, 7)).ground = Item(106)

        MapDescriptionWriter.writeMapDescriptionRange(
            out,
            map,
            player(),
            1000,
            1000,
            7,
            1,
            1,
        ) { monsterFrame() }

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x6A, 0x00,
                0x07, 0xFF,
            )
        )
    }

    @Test
    fun `writes an underground 1x1 range with the two floor view offsets`() {
        val out = Unpooled.buffer()
        val map = GameMap()
        map.getOrCreateTile(Position(1000, 1000, 8)).ground = Item(106)

        MapDescriptionWriter.writeMapDescriptionRange(
            out,
            map,
            player(Position(1000, 1000, 8)),
            1000,
            1000,
            8,
            1,
            1,
        ) { monsterFrame() }

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x01, 0xFF,
                0x6A, 0x00,
                0x02, 0xFF,
            )
        )
    }

    @Test
    fun `writes tile ground top items creatures and the stackpos 9 player substitution`() {
        val out = Unpooled.buffer()
        val map = GameMap()
        val player = player(Position(1000, 1000, 7))
        val tile = map.getOrCreateTile(Position(1000, 1000, 7))
        tile.ground = Item(106)
        for (index in 1..8) {
            val id = 200 + index
            tile.addThing(Item(id, type = ItemType(id = id, name = "top$index", alwaysOnTopOrder = 1)))
        }
        tile.addThing(Monster(5000L, "M", 1, 1, position = Position(1000, 1000, 7)))
        tile.addThing(player)

        var resolved: Creature? = null
        MapDescriptionWriter.writeTileDescription(out, player, tile) { creature ->
            resolved = creature
            CreatureAppearanceFrame(
                id = 1L,
                name = "Hero",
                health = 10,
                maxHealth = 10,
                direction = Direction.South,
                known = true,
            )
        }

        assertThat(resolved).isSameAs(player)
        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x6A, 0x00,
                0xD0, 0x00,
                0xCF, 0x00,
                0xCE, 0x00,
                0xCD, 0x00,
                0xCC, 0x00,
                0xCB, 0x00,
                0xCA, 0x00,
                0xC9, 0x00,
                0x62, 0x00,
                0x01, 0x00, 0x00, 0x00,
                0x64,
                0x02,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0xD7,
                0x00, 0x00,
                0x00,
                0x00,
                0x00,
                0x00,
                0x00,
                0x00,
                0xFF,
                0x00,
                0x01,
            )
        )
    }

    @Test
    fun `writes tile ground followed by a visible creature`() {
        val out = Unpooled.buffer()
        val map = GameMap()
        val player = player(Position(1000, 1000, 7))
        val tile = map.getOrCreateTile(Position(1000, 1000, 7))
        tile.ground = Item(106)
        tile.addThing(Monster(5000L, "M", 1, 1, position = Position(1000, 1000, 7)))

        MapDescriptionWriter.writeTileDescription(out, player, tile) { monsterFrame() }

        assertThat(out.toByteArray()).containsExactly(
            *bytes(0x6A, 0x00),
            *monsterBody(),
        )
    }

    @Test
    fun `writes the floor change down 0xBF block with the three new floors`() {
        val out = Unpooled.buffer()
        val map = GameMap()
        val player = player(Position(100, 100, 7))

        MapDescriptionWriter.writeMoveDownCreature(
            out,
            map,
            player,
            player,
            Position(100, 100, 8),
            Position(100, 100, 7),
        ) { monsterFrame() }

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xBF,
                0xFF, 0xFF, 0xFF, 0xFF, 0xF3, 0xFF,
                0x66,
                0x45, 0xFF,
                0x67,
                0x59, 0xFF,
            )
        )
    }

    @Test
    fun `writes the floor change up 0xBE block with the six lower floors`() {
        val out = Unpooled.buffer()
        val map = GameMap()
        val player = player(Position(100, 100, 8))

        MapDescriptionWriter.writeMoveUpCreature(
            out,
            map,
            player,
            player,
            Position(100, 100, 7),
            Position(100, 100, 8),
        ) { monsterFrame() }

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xBE,
                0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
                0xE7, 0xFF,
                0x68,
                0x6F, 0xFF,
                0x65,
                0x8F, 0xFF,
            )
        )
    }

    @Test
    fun `floor change blocks are suppressed for creatures that are not the player`() {
        val out = Unpooled.buffer()
        val map = GameMap()
        val player = player(Position(100, 100, 7))
        val monster = Monster(5000L, "M", 1, 1, position = Position(100, 100, 7))

        MapDescriptionWriter.writeMoveUpCreature(
            out,
            map,
            player,
            monster,
            Position(100, 100, 7),
            Position(100, 100, 8),
        ) { monsterFrame() }
        MapDescriptionWriter.writeMoveDownCreature(
            out,
            map,
            player,
            monster,
            Position(100, 100, 8),
            Position(100, 100, 7),
        ) { monsterFrame() }

        assertThat(out.toByteArray()).isEmpty()
    }
}
