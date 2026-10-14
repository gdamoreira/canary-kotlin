package org.opentibia.canary.network.protocol.login

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class LoginChallengeWriterTest {

    private fun ByteBuf.toByteArray(): ByteArray {
        val bytes = ByteArray(readableBytes())
        readBytes(bytes)
        return bytes
    }

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun `writes the current login challenge 0x1F with the adler checksum over the eight body bytes`() {
        val out = Unpooled.buffer()

        LoginChallengeWriter.writeCurrentLoginChallenge(out, timestamp = 0x11223344L, random = 0xAB)

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xE7, 0x01, 0x02, 0x06, // adler32 of the eight bytes at offset 4
                0x01,
                0x1F,
                0x44, 0x33, 0x22, 0x11, // timestamp
                0xAB, // random
                0x71,
            )
        )
    }

    @Test
    fun `writes the legacy login challenge with the inner length and checksum headers`() {
        val out = Unpooled.buffer()

        LoginChallengeWriter.writeLegacyLoginChallenge(out, timestamp = 0x11223344L, random = 0xAB)

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x7B, 0x01, 0x45, 0x04, // adler32 of length + body
                0x06, 0x00, // inner length
                0x1F,
                0x44, 0x33, 0x22, 0x11,
                0xAB,
            )
        )
    }
}
