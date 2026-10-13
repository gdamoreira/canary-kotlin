package org.opentibia.canary.network.protocol.creature

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.chat.SpeakClasses
import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.movement.Direction
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.protocol.CreatureAppearanceFrame
import org.opentibia.canary.network.protocol.CreatureType

class CreaturePacketWriterTest {

    private fun ByteBuf.toByteArray(): ByteArray {
        val bytes = ByteArray(readableBytes())
        readBytes(bytes)
        return bytes
    }

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    /**
     * `ProtocolGame::AddCreature` known branch for a monster with default outfit,
     * default light, no icons and no walkthrough (protocolgame.cpp:9662-9780).
     */
    private fun monsterFrame(known: Boolean = true) = CreatureAppearanceFrame(
        id = 5000L,
        name = "M",
        health = 1,
        maxHealth = 1,
        direction = Direction.North,
        creatureType = CreatureType.MONSTER,
        known = known,
    )

    private fun monsterBody(): ByteArray = bytes(
        0x62, 0x00, // known marker
        0x88, 0x13, 0x00, 0x00, // id 5000
        0x64, // health 100%
        0x00, // north
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, // outfit: no look type, no look type ex, no mount
        0x00, 0xD7, // light level 0, color 215
        0x00, 0x00, // step speed
        0x00, // no icons
        0x00, // skull
        0x00, // party shield
        0x01, // monster creature type
        0x00, // speech bubble
        0xFF, // unmarked
        0x00, // inspection type
        0x01, // not walkthrough
    )

    @Test
    fun `writes creature turn 0x6B with the update marker and walkthrough byte`() {
        val out = Unpooled.buffer()

        CreaturePacketWriter.writeCreatureTurn(
            out,
            Position(1000, 1000, 7),
            3,
            5000L,
            Direction.South,
            walkthrough = false,
        )
        CreaturePacketWriter.writeCreatureTurn(
            out,
            Position(50, 60, 7),
            4,
            0x11223344L,
            Direction.North,
            walkthrough = true,
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x6B,
                0xE8, 0x03, 0xE8, 0x03, 0x07,
                0x03,
                0x63, 0x00,
                0x88, 0x13, 0x00, 0x00,
                0x02,
                0x01,
                0x6B,
                0x32, 0x00, 0x3C, 0x00, 0x07,
                0x04,
                0x63, 0x00,
                0x44, 0x33, 0x22, 0x11,
                0x00,
                0x00,
            )
        )
    }

    @Test
    fun `writes creature say 0xAA with the current profile traded byte and level`() {
        val out = Unpooled.buffer()

        CreaturePacketWriter.writeCreatureSay(
            out,
            statementId = 1,
            speakerName = "Bob",
            speakerLevel = 42,
            type = SpeakClasses.TALKTYPE_SAY,
            pos = Position(1000, 1000, 7),
            text = "hi",
        )
        CreaturePacketWriter.writeCreatureSay(
            out,
            statementId = 2,
            speakerName = "Demon",
            speakerLevel = 0,
            type = SpeakClasses.TALKTYPE_MONSTER_SAY,
            pos = Position(50, 60, 7),
            text = "GRRR",
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xAA,
                0x01, 0x00, 0x00, 0x00,
                0x03, 0x00, 0x42, 0x6F, 0x62,
                0x00,
                0x2A, 0x00,
                0x01,
                0xE8, 0x03, 0xE8, 0x03, 0x07,
                0x02, 0x00, 0x68, 0x69,
                0xAA,
                0x02, 0x00, 0x00, 0x00,
                0x05, 0x00, 0x44, 0x65, 0x6D, 0x6F, 0x6E,
                0x00,
                0x00, 0x00,
                0x24,
                0x32, 0x00, 0x3C, 0x00, 0x07,
                0x04, 0x00, 0x47, 0x52, 0x52, 0x52,
            )
        )
    }

    @Test
    fun `writes creature helpers 0x94 with the helper bitmask`() {
        val out = Unpooled.buffer()

        CreaturePacketWriter.writeCreatureHelpers(out, 5000L, 7)

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x94,
                0x88, 0x13, 0x00, 0x00,
                0x07, 0x00,
            )
        )
    }

    @Test
    fun `writes a same floor other creature move as a single 0x6D frame`() {
        val out = Unpooled.buffer()
        val map = GameMap()
        val player = Player(1L, "Hero", 150, 150, position = Position(100, 100, 7))
        val monster = Monster(5000L, "M", 1, 1, position = Position(101, 100, 7))

        CreaturePacketWriter.writeMoveCreature(
            out,
            map,
            player,
            monster,
            Position(102, 100, 7),
            1,
            Position(101, 100, 7),
            2,
            teleport = false,
        ) { monsterFrame() }

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x6D,
                0x65, 0x00, 0x64, 0x00, 0x07,
                0x02,
                0x66, 0x00, 0x64, 0x00, 0x07,
            )
        )
    }

    @Test
    fun `writes a teleporting other creature move as remove plus add`() {
        val out = Unpooled.buffer()
        val map = GameMap()
        val player = Player(1L, "Hero", 150, 150, position = Position(100, 100, 7))
        val monster = Monster(5000L, "M", 1, 1, position = Position(101, 100, 7))

        CreaturePacketWriter.writeMoveCreature(
            out,
            map,
            player,
            monster,
            Position(102, 100, 7),
            1,
            Position(101, 100, 7),
            2,
            teleport = true,
        ) { monsterFrame() }

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x6C,
                0x65, 0x00, 0x64, 0x00, 0x07,
                0x02,
                0x6A,
                0x66, 0x00, 0x64, 0x00, 0x07,
                0x01,
            ),
            *monsterBody(),
        )
    }

    @Test
    fun `writes only the removal when the new position is out of view`() {
        val out = Unpooled.buffer()
        val map = GameMap()
        val player = Player(1L, "Hero", 150, 150, position = Position(100, 100, 7))
        val monster = Monster(5000L, "M", 1, 1, position = Position(101, 100, 7))

        CreaturePacketWriter.writeMoveCreature(
            out,
            map,
            player,
            monster,
            Position(120, 100, 7),
            1,
            Position(101, 100, 7),
            2,
            teleport = false,
        ) { monsterFrame() }

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x6C,
                0x65, 0x00, 0x64, 0x00, 0x07,
                0x02,
            )
        )
    }

    @Test
    fun `writes only the add when the old position is out of view`() {
        val out = Unpooled.buffer()
        val map = GameMap()
        val player = Player(1L, "Hero", 150, 150, position = Position(100, 100, 7))
        val monster = Monster(5000L, "M", 1, 1, position = Position(120, 100, 7))

        CreaturePacketWriter.writeMoveCreature(
            out,
            map,
            player,
            monster,
            Position(101, 100, 7),
            1,
            Position(120, 100, 7),
            2,
            teleport = false,
        ) { monsterFrame() }

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x6A,
                0x65, 0x00, 0x64, 0x00, 0x07,
                0x01,
            ),
            *monsterBody(),
        )
    }

    @Test
    fun `writes a self walk east with the 0x66 east strip refresh`() {
        val out = Unpooled.buffer()
        val map = GameMap()
        val player = Player(1L, "Hero", 150, 150, position = Position(100, 100, 7))

        CreaturePacketWriter.writeMoveCreature(
            out,
            map,
            player,
            player,
            Position(101, 100, 7),
            1,
            Position(100, 100, 7),
            2,
            teleport = false,
        ) { monsterFrame() }

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x6D,
                0x64, 0x00, 0x64, 0x00, 0x07,
                0x02,
                0x65, 0x00, 0x64, 0x00, 0x07,
                0x66,
                0x6F, 0xFF,
            )
        )
    }

    @Test
    fun `writes a self walk north with the 0x65 north strip refresh`() {
        val out = Unpooled.buffer()
        val map = GameMap()
        val player = Player(1L, "Hero", 150, 150, position = Position(100, 100, 7))

        CreaturePacketWriter.writeMoveCreature(
            out,
            map,
            player,
            player,
            Position(100, 99, 7),
            1,
            Position(100, 100, 7),
            2,
            teleport = false,
        ) { monsterFrame() }

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x6D,
                0x64, 0x00, 0x64, 0x00, 0x07,
                0x02,
                0x64, 0x00, 0x63, 0x00, 0x07,
                0x65,
                0x8F, 0xFF,
            )
        )
    }

    @Test
    fun `writes a full 0x64 map description when the self stack position is out of range`() {
        val out = Unpooled.buffer()
        val map = GameMap()
        val player = Player(1L, "Hero", 150, 150, position = Position(1000, 1000, 7))

        CreaturePacketWriter.writeMoveCreature(
            out,
            map,
            player,
            player,
            Position(1001, 1000, 7),
            1,
            Position(1000, 1000, 7),
            10,
            teleport = false,
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
    fun `writes a self floor change down with the 0xBF block and out of sync strips`() {
        val out = Unpooled.buffer()
        val map = GameMap()
        val player = Player(1L, "Hero", 150, 150, position = Position(100, 100, 7))

        CreaturePacketWriter.writeMoveCreature(
            out,
            map,
            player,
            player,
            Position(100, 100, 8),
            1,
            Position(100, 100, 7),
            0,
            teleport = false,
        ) { monsterFrame() }

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x6C,
                0x64, 0x00, 0x64, 0x00, 0x07,
                0x00,
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
    fun `writes a self floor change up with the 0xBE block and out of sync strips`() {
        val out = Unpooled.buffer()
        val map = GameMap()
        val player = Player(1L, "Hero", 150, 150, position = Position(100, 100, 8))

        CreaturePacketWriter.writeMoveCreature(
            out,
            map,
            player,
            player,
            Position(100, 100, 7),
            1,
            Position(100, 100, 8),
            0,
            teleport = false,
        ) { monsterFrame() }

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x6D,
                0x64, 0x00, 0x64, 0x00, 0x08,
                0x00,
                0x64, 0x00, 0x64, 0x00, 0x07,
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
    fun `resolves the appearance once through the session known set seam`() {
        val out = Unpooled.buffer()
        val map = GameMap()
        val player = Player(1L, "Hero", 150, 150, position = Position(100, 100, 7))
        val monster = Monster(5000L, "M", 1, 1, position = Position(101, 100, 7))
        val resolved = mutableListOf<Creature>()

        CreaturePacketWriter.writeMoveCreature(
            out,
            map,
            player,
            monster,
            Position(102, 100, 7),
            1,
            Position(101, 100, 7),
            2,
            teleport = true,
        ) { creature ->
            resolved.add(creature)
            monsterFrame()
        }

        assertThat(resolved).containsExactly(monster)
    }
}
