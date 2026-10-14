package org.opentibia.canary.network.protocol.creature

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.condition.LightInfo
import org.opentibia.canary.combat.condition.Outfit
import org.opentibia.canary.game.movement.Direction
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.protocol.CreatureAppearanceFrame
import org.opentibia.canary.network.protocol.CreatureType
import org.opentibia.canary.network.protocol.GameWindowWriter

class CreatureUpdateWriterTest {

    private fun ByteBuf.toByteArray(): ByteArray {
        val bytes = ByteArray(readableBytes())
        readBytes(bytes)
        return bytes
    }

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    private fun emblemFrame(id: Long = 5000L) = CreatureAppearanceFrame(
        id = id,
        name = "Bob",
        health = 80,
        maxHealth = 100,
        direction = Direction.South,
        outfit = Outfit(lookType = 128, lookHead = 1, lookBody = 2, lookLegs = 3, lookFeet = 4),
        light = LightInfo(level = 7, color = 215),
        stepSpeed = 200,
        creatureType = CreatureType.PLAYER,
        known = false,
        removedKnown = id,
        vocationClientId = 4,
    )

    @Test
    fun `writes creature emblem as a remove followed by the re-add frame`() {
        val out = Unpooled.buffer()
        val expected = Unpooled.buffer()
        val appearance = emblemFrame()

        CreatureUpdateWriter.writeCreatureEmblem(out, Position(100, 200, 7), stackpos = 3, appearance = appearance)

        expected.writeByte(0x6C)
        expected.writeShortLE(100)
        expected.writeShortLE(200)
        expected.writeByte(7)
        expected.writeByte(3)
        expected.writeByte(0x6A)
        expected.writeShortLE(100)
        expected.writeShortLE(200)
        expected.writeByte(7)
        expected.writeByte(3)
        GameWindowWriter.writeCreature(expected, appearance)

        assertThat(out.toByteArray()).containsExactly(*expected.toByteArray())
    }

    @Test
    fun `creature emblem still re-adds the creature when the stack position is out of range`() {
        val out = Unpooled.buffer()
        val expected = Unpooled.buffer()
        val appearance = emblemFrame()

        CreatureUpdateWriter.writeCreatureEmblem(out, Position(100, 200, 7), stackpos = 10, appearance = appearance)

        expected.writeByte(0x6A)
        expected.writeShortLE(100)
        expected.writeShortLE(200)
        expected.writeByte(7)
        expected.writeByte(10)
        GameWindowWriter.writeCreature(expected, appearance)

        assertThat(out.toByteArray()).containsExactly(*expected.toByteArray())
    }

    @Test
    fun `writes update creature 0x6B with the tile position and the unknown creature payload`() {
        val out = Unpooled.buffer()
        val expected = Unpooled.buffer()
        val appearance = emblemFrame().copy(removedKnown = 0L)

        CreatureUpdateWriter.writeUpdateCreature(out, Position(50, 60, 7), stackpos = 4, appearance = appearance)

        expected.writeByte(0x6B)
        expected.writeShortLE(50)
        expected.writeShortLE(60)
        expected.writeByte(7)
        expected.writeByte(4)
        GameWindowWriter.writeCreature(expected, appearance)

        assertThat(out.toByteArray()).containsExactly(*expected.toByteArray())
    }
}
