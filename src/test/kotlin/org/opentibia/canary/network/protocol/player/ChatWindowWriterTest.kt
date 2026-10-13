package org.opentibia.canary.network.protocol.player

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.chat.SpeakClasses
import org.opentibia.canary.map.Position

class ChatWindowWriterTest {

    private fun ByteBuf.toByteArray(): ByteArray {
        val bytes = ByteArray(readableBytes())
        readBytes(bytes)
        return bytes
    }

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun `writes creature say 0xAA with the speaker name level position and text`() {
        val out = Unpooled.buffer()

        ChatWindowWriter.writeCreatureSay(
            out,
            statementId = 1L,
            speakerName = "Bob",
            speakerLevel = 42,
            type = SpeakClasses.TALKTYPE_SAY,
            position = Position(100, 200, 7),
            text = "hi",
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xAA,
                0x01, 0x00, 0x00, 0x00,
                0x03, 0x00, 0x42, 0x6F, 0x62,
                0x00,
                0x2A, 0x00,
                0x01,
                0x64, 0x00, 0xC8, 0x00, 0x07,
                0x02, 0x00, 0x68, 0x69,
            )
        )
    }

    @Test
    fun `writes creature say 0xAA with a zero level for non-player speakers`() {
        val out = Unpooled.buffer()

        ChatWindowWriter.writeCreatureSay(
            out,
            statementId = 2L,
            speakerName = "Rat",
            speakerLevel = 0,
            type = SpeakClasses.TALKTYPE_MONSTER_SAY,
            position = Position(1, 2, 3),
            text = "x",
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xAA,
                0x02, 0x00, 0x00, 0x00,
                0x03, 0x00, 0x52, 0x61, 0x74,
                0x00,
                0x00, 0x00,
                0x24,
                0x01, 0x00, 0x02, 0x00, 0x03,
                0x01, 0x00, 0x78,
            )
        )
    }

    @Test
    fun `writes to channel 0xAA with the speaker level and channel id`() {
        val out = Unpooled.buffer()

        ChatWindowWriter.writeToChannel(
            out,
            statementId = 2L,
            speakerName = "Ann",
            speakerLevel = 10,
            type = SpeakClasses.TALKTYPE_CHANNEL_Y,
            text = "yo",
            channelId = 3,
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xAA,
                0x02, 0x00, 0x00, 0x00,
                0x03, 0x00, 0x41, 0x6E, 0x6E,
                0x00,
                0x0A, 0x00,
                0x07,
                0x03, 0x00,
                0x02, 0x00, 0x79, 0x6F,
            )
        )
    }

    @Test
    fun `writes to channel 0xAA downgrading R2 to R1 with a null speaker id`() {
        val out = Unpooled.buffer()

        ChatWindowWriter.writeToChannel(
            out,
            statementId = 3L,
            speakerName = "Ann",
            speakerLevel = 10,
            type = SpeakClasses.TALKTYPE_CHANNEL_R2,
            text = "x",
            channelId = 5,
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xAA,
                0x03, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00,
                0x00,
                0x0E,
                0x05, 0x00,
                0x01, 0x00, 0x78,
            )
        )
    }

    @Test
    fun `writes to channel 0xAA with the null speaker branch`() {
        val out = Unpooled.buffer()

        ChatWindowWriter.writeToChannel(
            out,
            statementId = 4L,
            speakerName = null,
            speakerLevel = 0,
            type = SpeakClasses.TALKTYPE_SAY,
            text = "x",
            channelId = 0,
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xAA,
                0x04, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00,
                0x00,
                0x01,
                0x00, 0x00,
                0x01, 0x00, 0x78,
            )
        )
    }

    @Test
    fun `writes to channel 0xAA without the traded byte when the statement id is zero`() {
        val out = Unpooled.buffer()

        ChatWindowWriter.writeToChannel(
            out,
            statementId = 0L,
            speakerName = "Ann",
            speakerLevel = 10,
            type = SpeakClasses.TALKTYPE_CHANNEL_Y,
            text = "yo",
            channelId = 3,
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xAA,
                0x00, 0x00, 0x00, 0x00,
                0x03, 0x00, 0x41, 0x6E, 0x6E,
                0x0A, 0x00,
                0x07,
                0x03, 0x00,
                0x02, 0x00, 0x79, 0x6F,
            )
        )
    }

    @Test
    fun `writes private message 0xAA with the speaker name and level`() {
        val out = Unpooled.buffer()

        ChatWindowWriter.writePrivateMessage(
            out,
            statementId = 5L,
            speakerName = "Cid",
            speakerLevel = 8,
            type = SpeakClasses.TALKTYPE_PRIVATE_FROM,
            text = "hey",
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xAA,
                0x05, 0x00, 0x00, 0x00,
                0x03, 0x00, 0x43, 0x69, 0x64,
                0x00,
                0x08, 0x00,
                0x04,
                0x03, 0x00, 0x68, 0x65, 0x79,
            )
        )
    }

    @Test
    fun `writes private message 0xAA with the null speaker branch`() {
        val out = Unpooled.buffer()

        ChatWindowWriter.writePrivateMessage(
            out,
            statementId = 6L,
            speakerName = null,
            speakerLevel = 0,
            type = SpeakClasses.TALKTYPE_PRIVATE_TO,
            text = "no",
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xAA,
                0x06, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00,
                0x00,
                0x05,
                0x02, 0x00, 0x6E, 0x6F,
            )
        )
    }

    @Test
    fun `writes channel message 0xAA with the author and channel id`() {
        val out = Unpooled.buffer()

        ChatWindowWriter.writeChannelMessage(
            out,
            author = "Guild",
            type = SpeakClasses.TALKTYPE_CHANNEL_Y,
            text = "msg",
            channelId = 3,
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xAA,
                0x00, 0x00, 0x00, 0x00,
                0x05, 0x00, 0x47, 0x75, 0x69, 0x6C, 0x64,
                0x00, 0x00,
                0x07,
                0x03, 0x00,
                0x03, 0x00, 0x6D, 0x73, 0x67,
            )
        )
    }

    @Test
    fun `writes the creature say frame through an embedded channel`() {
        val channel = EmbeddedChannel()
        try {
            val out = Unpooled.buffer()
            ChatWindowWriter.writeCreatureSay(
                out,
                statementId = 1L,
                speakerName = "Bob",
                speakerLevel = 42,
                type = SpeakClasses.TALKTYPE_SAY,
                position = Position(100, 200, 7),
                text = "hi",
            )

            assertThat(channel.writeOutbound(out)).isTrue()
            val frame = channel.readOutbound<ByteBuf>()
            assertThat(frame.readUnsignedByte().toInt()).isEqualTo(0xAA)
            assertThat(frame.readIntLE()).isEqualTo(1)
            assertThat(frame.readUnsignedShortLE()).isEqualTo(3)
            frame.release()
        } finally {
            channel.finishAndReleaseAll()
        }
    }
}
