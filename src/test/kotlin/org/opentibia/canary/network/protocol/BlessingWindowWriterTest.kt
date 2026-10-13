package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class BlessingWindowWriterTest {

    private fun ByteBuf.toByteArray(): ByteArray {
        val bytes = ByteArray(readableBytes())
        readBytes(bytes)
        return bytes
    }

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun `writes the blessing window 0x9B with the eight blessing rows and reductions`() {
        val out = Unpooled.buffer()

        BlessingWindowWriter.writeBlessingWindow(
            out,
            blessingCounts = List(8) { 0 },
            storeCounts = List(8) { 0 },
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x9B,
                0x08,
                0x02, 0x00, 0x00, 0x00,
                0x04, 0x00, 0x00, 0x00,
                0x08, 0x00, 0x00, 0x00,
                0x10, 0x00, 0x00, 0x00,
                0x20, 0x00, 0x00, 0x00,
                0x40, 0x00, 0x00, 0x00,
                0x80, 0x00, 0x00, 0x00,
                0x00, 0x01, 0x00, 0x00,
                0x00,
                0x1E,
                0x00,
                0x50,
                0x00,
                0x64, 0x64,
                0x00,
                0x00,
                0x00,
            )
        )
    }

    @Test
    fun `writes the promoted reduction bonuses when all seven real blessings are held`() {
        val out = Unpooled.buffer()

        BlessingWindowWriter.writeBlessingWindow(
            out,
            blessingCounts = List(8) { 1 },
            storeCounts = List(8) { 0 },
            promoted = true,
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x9B,
                0x08,
                0x02, 0x00, 0x01, 0x00,
                0x04, 0x00, 0x01, 0x00,
                0x08, 0x00, 0x01, 0x00,
                0x10, 0x00, 0x01, 0x00,
                0x20, 0x00, 0x01, 0x00,
                0x40, 0x00, 0x01, 0x00,
                0x80, 0x00, 0x01, 0x00,
                0x00, 0x01, 0x01, 0x00,
                0x01,
                0x1E,
                0x56,
                0x62,
                0x56,
                0x00, 0x00,
                0x00,
                0x00,
                0x00,
            )
        )
    }

    @Test
    fun `writes the retro blessing window without twist of fate and with the retro factor`() {
        val out = Unpooled.buffer()

        BlessingWindowWriter.writeBlessingWindow(
            out,
            retro = true,
            blessingCounts = List(8) { 1 },
            storeCounts = List(8) { 0 },
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x9B,
                0x07,
                0x04, 0x00, 0x01, 0x00,
                0x08, 0x00, 0x01, 0x00,
                0x10, 0x00, 0x01, 0x00,
                0x20, 0x00, 0x01, 0x00,
                0x40, 0x00, 0x01, 0x00,
                0x80, 0x00, 0x01, 0x00,
                0x00, 0x01, 0x01, 0x00,
                0x00,
                0x1E,
                0x2C,
                0x2C,
                0x2C,
                0x00, 0x00,
                0x00,
                0x00,
                0x00,
            )
        )
    }

    @Test
    fun `writes the glyph and green bless status 0x9C for seven blessings`() {
        val out = Unpooled.buffer()

        BlessingWindowWriter.writeBlessStatus(out, blessCount = 7, glow = true)

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x9C,
                0x01, 0x00,
                0x03,
            )
        )
    }

    @Test
    fun `writes the disabled bless status 0x9C below five blessings`() {
        val out = Unpooled.buffer()

        BlessingWindowWriter.writeBlessStatus(out, blessCount = 4)

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x9C,
                0x00, 0x00,
                0x01,
            )
        )
    }

    @Test
    fun `writes the old protocol bless status without the level byte`() {
        val out = Unpooled.buffer()

        BlessingWindowWriter.writeBlessStatus(out, blessCount = 5, oldProtocol = true)

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x9C,
                0x01, 0x00,
            )
        )
    }
}
