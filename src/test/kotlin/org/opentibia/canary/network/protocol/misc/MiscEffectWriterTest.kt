package org.opentibia.canary.network.protocol.misc

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import java.nio.charset.StandardCharsets
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.protocol.SourceEffect

class MiscEffectWriterTest {

    private fun ByteBuf.toByteArray(): ByteArray {
        val bytes = ByteArray(readableBytes())
        readBytes(bytes)
        return bytes
    }

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    private fun tibiaString(value: String): ByteArray {
        val payload = value.toByteArray(StandardCharsets.UTF_8)
        return bytes(payload.size and 0xFF, (payload.size shr 8) and 0xFF) + payload
    }

    @Test
    fun `writes the add marker 0xDD with the minimap marker type and position`() {
        val out = Unpooled.buffer()

        MiscEffectWriter.writeAddMarker(out, Position(100, 200, 7), markType = 3, desc = "hi")

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xDD,
                0x00,
                0x64, 0x00,
                0xC8, 0x00,
                0x07,
                0x03,
            ),
            *tibiaString("hi"),
        )
    }

    @Test
    fun `writes the single sound effect 0x83 loop with source and id`() {
        val out = Unpooled.buffer()

        MiscEffectWriter.writeSingleSoundEffect(
            out,
            Position(1, 2, 3),
            id = 0x1234,
            source = SourceEffect.CREATURES,
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x83,
                0x01, 0x00,
                0x02, 0x00,
                0x03,
                0x06,
                0x03,
                0x34, 0x12,
                0x00,
            )
        )
    }

    @Test
    fun `writes the double sound effect 0x83 loop with both sources and ids`() {
        val out = Unpooled.buffer()

        MiscEffectWriter.writeDoubleSoundEffect(
            out,
            Position(1, 2, 3),
            mainSoundId = 0x0010,
            mainSource = SourceEffect.OWN,
            secondarySoundId = 0x0020,
            secondarySource = SourceEffect.OTHERS,
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x83,
                0x01, 0x00,
                0x02, 0x00,
                0x03,
                0x06,
                0x01,
                0x10, 0x00,
                0x07,
                0x01,
                0x02,
                0x20, 0x00,
                0x00,
            )
        )
    }

    @Test
    fun `writes the ambient sound effect 0x85 with the ambient sub-opcode`() {
        val out = Unpooled.buffer()

        MiscEffectWriter.writeAmbientSoundEffect(out, id = 5)

        assertThat(out.toByteArray()).containsExactly(*bytes(0x85, 0x00, 0x05, 0x00))
    }

    @Test
    fun `writes the music sound effect 0x85 with the music sub-opcode`() {
        val out = Unpooled.buffer()

        MiscEffectWriter.writeMusicSoundEffect(out, id = 7)

        assertThat(out.toByteArray()).containsExactly(*bytes(0x85, 0x01, 0x07, 0x00))
    }

    @Test
    fun `writes the attached effect 0x34 with the creature id and effect id`() {
        val out = Unpooled.buffer()

        MiscEffectWriter.writeAttachedEffect(out, creatureId = 0x01020304L, effectId = 9)

        assertThat(out.toByteArray()).containsExactly(
            *bytes(0x34, 0x04, 0x03, 0x02, 0x01, 0x09, 0x00)
        )
    }

    @Test
    fun `writes the detach effect 0x35 with the creature id and effect id`() {
        val out = Unpooled.buffer()

        MiscEffectWriter.writeDetachEffect(out, creatureId = 0x01020304L, effectId = 9)

        assertThat(out.toByteArray()).containsExactly(
            *bytes(0x35, 0x04, 0x03, 0x02, 0x01, 0x09, 0x00)
        )
    }

    @Test
    fun `writes the creature shader 0x36 with the shader name`() {
        val out = Unpooled.buffer()

        MiscEffectWriter.writeShader(out, creatureId = 5L, shaderName = "fire")

        assertThat(out.toByteArray()).containsExactly(
            *bytes(0x36, 0x05, 0x00, 0x00, 0x00),
            *tibiaString("fire"),
        )
    }

    @Test
    fun `writes the map shader 0x37 with the shader name`() {
        val out = Unpooled.buffer()

        MiscEffectWriter.writeMapShader(out, shaderName = "water")

        assertThat(out.toByteArray()).containsExactly(
            *bytes(0x37),
            *tibiaString("water"),
        )
    }
}
