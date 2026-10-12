package org.opentibia.canary.network.crypto

import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets

class Adler32ChecksumTest {

    @Test
    fun `computes Adler32 for known ASCII string`() {
        val testBytes = "Wikipedia".toByteArray(StandardCharsets.UTF_8)
        val buf = Unpooled.wrappedBuffer(testBytes)

        // Adler32 of "Wikipedia" is 0x11E60398 = 300286872
        val checksum = Adler32Checksum.compute(buf, 0, testBytes.size)
        assertThat(checksum).isEqualTo(0x11E60398L)
    }

    @Test
    fun `computes Adler32 for single byte`() {
        val buf = Unpooled.wrappedBuffer(byteArrayOf(0x01))
        // a = (1 + 1) = 2, b = (0 + 2) = 2 -> (2 shl 16) | 2 = 0x00020002
        val checksum = Adler32Checksum.compute(buf, 0, 1)
        assertThat(checksum).isEqualTo(0x00020002L)
    }
}
