package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class StashWindowWriterTest {

    private fun ByteBuf.toByteArray(): ByteArray {
        val bytes = ByteArray(readableBytes())
        readBytes(bytes)
        return bytes
    }

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun `writes the open stash 0x29 with item id and count per entry`() {
        val out = Unpooled.buffer()

        StashWindowWriter.writeOpenStash(
            out,
            linkedMapOf(100 to 500, 200 to 1),
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x29,
                0x02, 0x00,
                0x64, 0x00, 0xF4, 0x01, 0x00, 0x00,
                0xC8, 0x00, 0x01, 0x00, 0x00, 0x00,
            )
        )
    }

    @Test
    fun `writes an empty stash as the opcode and a zero count`() {
        val out = Unpooled.buffer()

        StashWindowWriter.writeOpenStash(out, emptyMap())

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x29,
                0x00, 0x00,
            )
        )
    }
}
