package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.chat.ChannelEvent

class ChannelWindowWriterTest {

    private fun ByteBuf.toByteArray(): ByteArray {
        val bytes = ByteArray(readableBytes())
        readBytes(bytes)
        return bytes
    }

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun `writes open private channel 0xAD with the receiver name`() {
        val out = Unpooled.buffer()

        ChannelWindowWriter.writeOpenPrivateChannel(out, "Alice")

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xAD,
                0x05, 0x00, 0x41, 0x6C, 0x69, 0x63, 0x65,
            )
        )
    }

    @Test
    fun `writes channel event 0xF3 with a little endian channel id name and event byte`() {
        val out = Unpooled.buffer()

        ChannelWindowWriter.writeChannelEvent(out, channelId = 5, playerName = "Bob", ChannelEvent.CHANNELEVENT_JOIN)
        ChannelWindowWriter.writeChannelEvent(out, channelId = 0x0201, playerName = "X", ChannelEvent.CHANNELEVENT_EXCLUDE)

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xF3,
                0x05, 0x00,
                0x03, 0x00, 0x42, 0x6F, 0x62,
                0x00,
                0xF3,
                0x01, 0x02,
                0x01, 0x00, 0x58,
                0x03,
            )
        )
    }

    @Test
    fun `writes the open private channel frame through an embedded channel`() {
        val channel = EmbeddedChannel()
        try {
            val out = Unpooled.buffer()
            ChannelWindowWriter.writeOpenPrivateChannel(out, "Bob")

            assertThat(channel.writeOutbound(out)).isTrue()
            val frame = channel.readOutbound<ByteBuf>()
            assertThat(frame.readUnsignedByte().toInt()).isEqualTo(0xAD)
            assertThat(frame.readUnsignedShortLE()).isEqualTo(3)
            frame.release()
        } finally {
            channel.finishAndReleaseAll()
        }
    }
}
