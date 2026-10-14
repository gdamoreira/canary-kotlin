package org.opentibia.canary.network.protocol.forge

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ForgeErrorWriterTest {

    private fun ByteBuf.toByteArray(): ByteArray {
        val bytes = ByteArray(readableBytes())
        readBytes(bytes)
        return bytes
    }

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun `writes forge error as the message dialog followed by the close forge window`() {
        val out = Unpooled.buffer()

        ForgeErrorWriter.writeForgeError(out, message = "You cannot do that.")

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xED,
                0x14,
                0x13, 0x00, 0x59, 0x6F, 0x75, 0x20, 0x63, 0x61, 0x6E, 0x6E, 0x6F, 0x74, 0x20, 0x64, 0x6F,
                0x20, 0x74, 0x68, 0x61, 0x74, 0x2E,
                0x89,
            )
        )
    }
}
