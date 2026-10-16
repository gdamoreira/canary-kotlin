package org.opentibia.canary.network.codec

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Transcript tests for `Connection::parseProxyIdentification`
 * (src/server/network/connection/connection.cpp:231-287) over the framed
 * packet decoder.
 */
class TibiaFrameDecoderTest {

    private val serverName = "Canary Kotlin Server"

    private fun frame(payload: ByteArray): ByteArray =
        byteArrayOf((payload.size and 0xFF).toByte(), ((payload.size shr 8) and 0xFF).toByte()) + payload

    private fun decode(serverName: String, vararg chunks: ByteArray): Pair<List<ByteBuf>, EmbeddedChannel> {
        val channel = EmbeddedChannel(TibiaFrameDecoder(serverName = serverName))
        for (chunk in chunks) {
            channel.writeInbound(Unpooled.wrappedBuffer(chunk))
        }

        val frames = mutableListOf<ByteBuf>()
        while (true) {
            val frame = channel.readInbound<ByteBuf>() ?: break
            frames.add(frame)
        }
        return frames to channel
    }

    @Test
    fun `modern login block-count frame emits the pending game payload`() {
        // [u16 blockCount][u32 sequence][u8 padding][0x0A payload][padding]
        val payload = byteArrayOf(0x0A, 0x02, 0x00, 0xF5.toByte(), 0x05) + ByteArray(10) { 0x42 }
        val padding = 8 - (payload.size % 8) - 1
        val message = ByteArray(4) { 0x00 } + byteArrayOf(padding.toByte()) + payload + ByteArray(padding) { 0x00 }
        val blockCount = (message.size - 4) / 8

        val framed = byteArrayOf((blockCount and 0xFF).toByte(), ((blockCount shr 8) and 0xFF).toByte()) + message
        val (frames, _) = decode(serverName, framed)

        assertThat(frames).hasSize(1)
        assertThat(frames[0].readUnsignedByte().toInt()).isEqualTo(0x0A)
        assertThat(frames[0].readableBytes()).isEqualTo(payload.size - 1)
    }

    @Test
    fun `standard packet without identification passes through`() {
        val payload = byteArrayOf(0x0A, 0x00, 0x01)

        val (frames, _) = decode(serverName, frame(payload))

        assertThat(frames).hasSize(1)
        assertThat(ByteArray(frames[0].readableBytes()).also { frames[0].readBytes(it) }).isEqualTo(payload)
    }

    @Test
    fun `proxy identification matching the server name is consumed`() {
        val payload = byteArrayOf(0x0A, 0x00, 0x01)
        val identification = "$serverName\n".toByteArray()

        val (frames, _) = decode(serverName, identification + frame(payload))

        assertThat(frames).hasSize(1)
        assertThat(ByteArray(frames[0].readableBytes()).also { frames[0].readBytes(it) }).isEqualTo(payload)
    }

    @Test
    fun `proxy identification is case-insensitive`() {
        val payload = byteArrayOf(0x0A)
        val identification = serverName.lowercase().toByteArray() + byteArrayOf(0x0A)

        val (frames, _) = decode(serverName, identification + frame(payload))

        assertThat(frames).hasSize(1)
    }

    @Test
    fun `fragmented identification and packet are reassembled`() {
        val payload = byteArrayOf(0x0A, 0x00, 0x01)
        val identification = "$serverName\n".toByteArray()

        val (frames, _) = decode(
            serverName,
            identification.copyOfRange(0, 7),
            identification.copyOfRange(7, identification.size),
            frame(payload).copyOfRange(0, 1),
            frame(payload).copyOfRange(1, frame(payload).size),
        )

        assertThat(frames).hasSize(1)
        assertThat(ByteArray(frames[0].readableBytes()).also { frames[0].readBytes(it) }).isEqualTo(payload)
    }

    @Test
    fun `a large packet without the identification prefix falls back to standard framing`() {
        val payload = ByteArray(300) { 0x01 }
        val framed = frame(payload)

        val (frames, channel) = decode(serverName, framed)

        assertThat(frames).hasSize(1)
        assertThat(frames[0].readableBytes()).isEqualTo(payload.size)
        assertThat(channel.isOpen).isTrue()
    }

    @Test
    fun `identification mismatch after a matching prefix closes the channel`() {
        val mismatch = "Canary Kotlin ServerX".toByteArray()

        val (frames, channel) = decode(serverName, mismatch + frame(byteArrayOf(0x0A)))

        assertThat(frames).isEmpty()
        assertThat(channel.isOpen).isFalse()
    }

    @Test
    fun `an empty server name disables the identification probe`() {
        val payload = ByteArray(300) { 0x01 }
        val framed = frame(payload)

        val (frames, _) = decode("", framed)

        assertThat(frames).hasSize(1)
    }
}
