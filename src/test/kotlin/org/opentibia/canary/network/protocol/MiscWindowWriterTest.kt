package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class MiscWindowWriterTest {

    private fun ByteBuf.toByteArray(): ByteArray {
        val bytes = ByteArray(readableBytes())
        readBytes(bytes)
        return bytes
    }

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun `writes the player typing frame 0x38 with creature id and typing byte`() {
        val out = Unpooled.buffer()

        MiscWindowWriter.writePlayerTyping(out, creatureId = 10L, typing = 2)

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x38,
                0x0A, 0x00, 0x00, 0x00,
                0x02,
            )
        )
    }

    @Test
    fun `writes the message dialog 0xED with the unknown type 0x14`() {
        val out = Unpooled.buffer()

        MiscWindowWriter.writeMessageDialog(out, "hello")

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xED, 0x14,
                0x05, 0x00, 0x68, 0x65, 0x6C, 0x6C, 0x6F,
            )
        )
    }

    @Test
    fun `writes the imbuement result 0xED with type 0x01`() {
        val out = Unpooled.buffer()

        MiscWindowWriter.writeImbuementResult(out, "no")

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xED, 0x01,
                0x02, 0x00, 0x6E, 0x6F,
            )
        )
    }

    @Test
    fun `writes the close imbuement window 0xEC opcode only`() {
        val out = Unpooled.buffer()

        MiscWindowWriter.writeCloseImbuementWindow(out)

        assertThat(out.toByteArray()).containsExactly(*bytes(0xEC))
    }

    @Test
    fun `writes the close forge window 0x89 opcode only`() {
        val out = Unpooled.buffer()

        MiscWindowWriter.writeCloseForgeWindow(out)

        assertThat(out.toByteArray()).containsExactly(*bytes(0x89))
    }

    @Test
    fun `writes the experience tracker 0xAF with raw and final experience`() {
        val out = Unpooled.buffer()

        MiscWindowWriter.writeExperienceTracker(out, rawExp = 1L, finalExp = 2L)

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xAF,
                0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x02, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            )
        )
    }
}
