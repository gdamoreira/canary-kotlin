package org.opentibia.canary.network.protocol.offline

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class StartOfflineTrainingPacketTest {

    @Test
    fun `decodes opcode 0x74 single action byte into command DTO`() {
        val payload = Unpooled.buffer()
        payload.writeByte(0x03)

        val command = StartOfflineTrainingPacketParser.parse(payload)

        assertThat(command).isEqualTo(StartOfflineTrainingCommand(action = 3))
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `reads exactly one byte and leaves trailing payload untouched`() {
        val payload = Unpooled.buffer()
        payload.writeByte(0x01)
        payload.writeByte(0xAA)
        payload.writeByte(0xBB)

        val command = StartOfflineTrainingPacketParser.parse(payload)

        assertThat(command).isEqualTo(StartOfflineTrainingCommand(action = 1))
        assertThat(payload.readUnsignedByte().toInt()).isEqualTo(0xAA)
        assertThat(payload.readUnsignedByte().toInt()).isEqualTo(0xBB)
    }

    @Test
    fun `decodes bounded unsigned byte value`() {
        val payload = Unpooled.buffer()
        payload.writeByte(0xFF)

        val command = StartOfflineTrainingPacketParser.parse(payload)

        assertThat(command).isEqualTo(StartOfflineTrainingCommand(action = 255))
    }

    @Test
    fun `returns null when payload is empty`() {
        val payload = Unpooled.buffer()

        assertThat(StartOfflineTrainingPacketParser.parse(payload)).isNull()
    }

    @Test
    fun `decodes via EmbeddedChannel inbound message`() {
        val channel = EmbeddedChannel()
        try {
            channel.writeInbound(Unpooled.wrappedBuffer(byteArrayOf(0x02)))

            val payload = channel.readInbound<ByteBuf>()
            val command = StartOfflineTrainingPacketParser.parse(payload)

            assertThat(command).isEqualTo(StartOfflineTrainingCommand(action = 2))
        } finally {
            channel.finishAndReleaseAll()
        }
    }
}
