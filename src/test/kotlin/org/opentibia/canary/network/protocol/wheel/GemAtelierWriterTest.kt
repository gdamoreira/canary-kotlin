package org.opentibia.canary.network.protocol.wheel

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class GemAtelierWriterTest {

    private fun ByteBuf.toByteArray(): ByteArray {
        val bytes = ByteArray(readableBytes())
        readBytes(bytes)
        return bytes
    }

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun `writes gem atelier gem revealed 0xC5 with the gem index`() {
        val out = Unpooled.buffer()

        GemAtelierWriter.writeGemAtelierGemRevealed(out, gemIndex = 5)

        assertThat(out.toByteArray()).containsExactly(*bytes(0xC5, 0x05, 0x00))
    }
}
