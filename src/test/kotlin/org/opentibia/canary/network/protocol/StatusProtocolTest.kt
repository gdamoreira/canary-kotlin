package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.config.ServerConfig
import org.opentibia.canary.network.codec.TibiaFrameDecoder
import org.opentibia.canary.network.codec.TibiaMessageEncoder
import org.opentibia.canary.network.crypto.Adler32Checksum
import java.nio.charset.StandardCharsets

class StatusProtocolTest {

    @Test
    fun `responds to status ping with XML status document`() {
        val config = ServerConfig(serverName = "Canary Test Server", maxPlayers = 500)
        val channel = EmbeddedChannel(
            TibiaFrameDecoder(),
            TibiaMessageEncoder(),
            StatusHandler(config)
        )

        val unchecksummed = Unpooled.buffer()
        unchecksummed.writeByte(0xFF) // PROTOCOL_STATUS
        unchecksummed.writeByte(0x01) // info request

        val checksum = Adler32Checksum.compute(unchecksummed, 0, unchecksummed.readableBytes())

        val packet = Unpooled.buffer()
        packet.writeShortLE(4 + unchecksummed.readableBytes())
        packet.writeIntLE(checksum.toInt())
        packet.writeBytes(unchecksummed)

        channel.writeInbound(packet)

        val responseBytes: ByteBuf? = channel.readOutbound()
        assertThat(responseBytes).isNotNull

        val length = responseBytes!!.readUnsignedShortLE()
        val xmlBytes = ByteArray(length)
        responseBytes.readBytes(xmlBytes)
        val xml = String(xmlBytes, StandardCharsets.UTF_8)

        assertThat(xml).contains("<tsqp version=\"1.0\">")
        assertThat(xml).contains("server=\"Canary Test Server\"")
        assertThat(xml).contains("max=\"500\"")
    }
}
