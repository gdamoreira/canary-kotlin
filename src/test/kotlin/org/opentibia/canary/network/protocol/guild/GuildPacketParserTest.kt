package org.opentibia.canary.network.protocol.guild

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.network.message.writeTibiaString

class GuildPacketParserTest {

    @Test
    fun `parses edit guild message 0x9C as a length prefixed string`() {
        val payload = Unpooled.buffer()
        payload.writeTibiaString("Hello guild")

        val command = GuildPacketParser.parseEditGuildMessage(payload)

        assertThat(command).isEqualTo(EditGuildMessageCommand("Hello guild"))
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `parses an empty edit guild message`() {
        val payload = Unpooled.buffer()
        payload.writeTibiaString("")

        assertThat(GuildPacketParser.parseEditGuildMessage(payload))
            .isEqualTo(EditGuildMessageCommand(""))
    }

    @Test
    fun `parses a utf8 edit guild message`() {
        val payload = Unpooled.buffer()
        payload.writeTibiaString("Guilda Ação")

        assertThat(GuildPacketParser.parseEditGuildMessage(payload))
            .isEqualTo(EditGuildMessageCommand("Guilda Ação"))
    }

    @Test
    fun `returns null when the edit guild message frame is truncated`() {
        assertThat(GuildPacketParser.parseEditGuildMessage(Unpooled.buffer())).isNull()

        val halfLength = Unpooled.buffer()
        halfLength.writeByte(0x05)
        assertThat(GuildPacketParser.parseEditGuildMessage(halfLength)).isNull()

        val missingBody = Unpooled.buffer()
        missingBody.writeShortLE(5)
        missingBody.writeByte(0x41)
        assertThat(GuildPacketParser.parseEditGuildMessage(missingBody)).isNull()
    }

    @Test
    fun `dispatches 0x9C through the opcode entry point`() {
        val payload = Unpooled.buffer()
        payload.writeTibiaString("motto")

        assertThat(GuildPacketParser.parse(GuildPacketParser.OPCODE_EDIT_GUILD_MESSAGE, payload))
            .isEqualTo(EditGuildMessageCommand("motto"))
    }

    @Test
    fun `returns null for an unhandled guild opcode`() {
        assertThat(GuildPacketParser.parse(0x00, Unpooled.buffer())).isNull()
    }

    @Test
    fun `decodes an edit guild message through an embedded channel`() {
        val channel = EmbeddedChannel()
        try {
            val frame = Unpooled.buffer()
            frame.writeTibiaString("guild")
            assertThat(channel.writeInbound(frame)).isTrue()

            val inbound = channel.readInbound<ByteBuf>()
            val command = GuildPacketParser.parse(GuildPacketParser.OPCODE_EDIT_GUILD_MESSAGE, inbound)

            assertThat(command).isEqualTo(EditGuildMessageCommand("guild"))
        } finally {
            channel.finishAndReleaseAll()
        }
    }
}
