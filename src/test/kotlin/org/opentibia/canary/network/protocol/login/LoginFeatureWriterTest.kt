package org.opentibia.canary.network.protocol.login

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class LoginFeatureWriterTest {

    private fun ByteBuf.toByteArray(): ByteArray {
        val bytes = ByteArray(readableBytes())
        readBytes(bytes)
        return bytes
    }

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun `writes OTCv8 features 0x43 with the extended opcode feature`() {
        val out = Unpooled.buffer()

        LoginFeatureWriter.writeFeatures(out, otclientV8 = 8)

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x43,
                0x01, 0x00, // feature count
                0x50, // GameFeature_t::ExtendedOpcode = 80
                0x01,
            )
        )
    }

    @Test
    fun `OTCv8 features are suppressed for plain clients`() {
        val out = Unpooled.buffer()

        LoginFeatureWriter.writeFeatures(out, otclientV8 = 0)

        assertThat(out.toByteArray()).isEmpty()
    }

    @Test
    fun `writes OTCR features 0x43 with enabled and disabled lists`() {
        val out = Unpooled.buffer()

        LoginFeatureWriter.writeOTCRFeatures(out, enabledFeatures = listOf(1, 2), disabledFeatures = listOf(3))

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x43,
                0x03, 0x00, // total feature count
                0x01, 0x01,
                0x02, 0x01,
                0x03, 0x00,
            )
        )
    }

    @Test
    fun `writes the OTCR login preamble with the feature and opcode frames`() {
        val out = Unpooled.buffer()

        LoginFeatureWriter.writeClientLoginPreamble(
            out,
            operatingSystem = LoginFeatureWriter.CLIENTOS_OTCLIENT_LINUX,
            otclientV8 = 0,
            enabledFeatures = listOf(1),
            disabledFeatures = emptyList(),
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x43,
                0x01, 0x00,
                0x01, 0x01,
                0x32,
                0x00,
                0x00, 0x00,
            )
        )
    }

    @Test
    fun `writes the OTCv8 preamble features without the OTCR list`() {
        val out = Unpooled.buffer()

        LoginFeatureWriter.writeClientLoginPreamble(
            out,
            operatingSystem = LoginFeatureWriter.CLIENTOS_OTCLIENT_LINUX,
            otclientV8 = 8,
            enabledFeatures = listOf(1),
            disabledFeatures = emptyList(),
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x43,
                0x01, 0x00,
                0x50, 0x01,
                0x32,
                0x00,
                0x00, 0x00,
            )
        )
    }

    @Test
    fun `the login preamble is empty for regular clients`() {
        val out = Unpooled.buffer()

        LoginFeatureWriter.writeClientLoginPreamble(
            out,
            operatingSystem = 1,
            otclientV8 = 0,
            enabledFeatures = listOf(1),
            disabledFeatures = emptyList(),
        )

        assertThat(out.toByteArray()).isEmpty()
    }
}
