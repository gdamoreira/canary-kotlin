package org.opentibia.canary.network.message

import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TibiaByteBufExtTest {

    @Test
    fun `reads and writes Tibia string correctly`() {
        val buf = Unpooled.buffer()
        val original = "CanaryKotlinServer"

        buf.writeTibiaString(original)

        val read = buf.readTibiaString()
        assertThat(read).isEqualTo(original)
        assertThat(buf.readableBytes()).isEqualTo(0)
    }

    @Test
    fun `encodes Tibia variable count properly`() {
        val buf = Unpooled.buffer()

        // 1 byte range (0..63)
        buf.writeTibiaVarCount(42)
        assertThat(buf.readUnsignedByte().toInt()).isEqualTo(42)

        // 2 byte range (64..16383)
        buf.writeTibiaVarCount(100)
        val b1 = buf.readUnsignedByte().toInt()
        val b2 = buf.readUnsignedByte().toInt()
        assertThat(b1 and 0xC0).isEqualTo(0x40) // 2-byte marker
        val decoded = ((b1 and 0x3F) shl 8) or b2
        assertThat(decoded).isEqualTo(100)
    }
}
