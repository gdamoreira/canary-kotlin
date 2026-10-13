package org.opentibia.canary.network.protocol

import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.network.message.writeTibiaString

/**
 * Decode tests for the channel client opcodes
 * (src/server/network/protocol/protocolgame.cpp):
 *  - 0x97 `Game::playerRequestChannels`         (protocolgame.cpp:1887-1889)
 *  - 0x98 `ProtocolGame::parseOpenChannel`      (protocolgame.cpp:2434-2437)
 *  - 0x99 `ProtocolGame::parseCloseChannel`     (protocolgame.cpp:2439-2442)
 *  - 0x9A `ProtocolGame::parseOpenPrivateChannel` (protocolgame.cpp:2444-2447)
 *  - 0x9E `Game::playerCloseNpcChannel`         (protocolgame.cpp:1905-1907)
 *
 * `0x97` and `0x9E` read no payload at all, so their commands are objects and
 * the decoder must leave every following byte untouched.
 */
class ChannelPacketHandlerTest {

    @Test
    fun `parses request channels 0x97 without consuming payload`() {
        val payload = Unpooled.buffer()
        payload.writeByte(0xAB)

        val command = ChannelPacketHandler.parse(
            ChannelPacketHandler.OPCODE_REQUEST_CHANNELS,
            payload,
        )

        assertThat(command).isSameAs(RequestChannelsCommand)
        assertThat(payload.readableBytes()).isEqualTo(1)
    }

    @Test
    fun `parses open channel 0x98 uint16 channel id`() {
        val payload = Unpooled.buffer()
        payload.writeShortLE(0x0008)

        val command = ChannelPacketHandler.parse(
            ChannelPacketHandler.OPCODE_OPEN_CHANNEL,
            payload,
        )

        assertThat(command).isEqualTo(OpenChannelCommand(channelId = 8))
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `rejects a truncated open channel 0x98 channel id`() {
        val payload = Unpooled.buffer()
        payload.writeByte(0x00)

        assertThat(ChannelPacketHandler.parseOpenChannel(payload)).isNull()
    }

    @Test
    fun `parses close channel 0x99 uint16 channel id`() {
        val payload = Unpooled.buffer()
        payload.writeShortLE(0x0008)

        val command = ChannelPacketHandler.parse(
            ChannelPacketHandler.OPCODE_CLOSE_CHANNEL,
            payload,
        )

        assertThat(command).isEqualTo(CloseChannelCommand(channelId = 8))
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `parses open private channel 0x9A receiver name`() {
        val payload = Unpooled.buffer()
        payload.writeTibiaString("Bubble")

        val command = ChannelPacketHandler.parse(
            ChannelPacketHandler.OPCODE_OPEN_PRIVATE_CHANNEL,
            payload,
        )

        assertThat(command).isEqualTo(OpenPrivateChannelCommand(receiver = "Bubble"))
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `rejects a truncated open private channel 0x9A string`() {
        val payload = Unpooled.buffer()
        payload.writeShortLE(9)
        payload.writeByte('B'.code)

        assertThat(ChannelPacketHandler.parseOpenPrivateChannel(payload)).isNull()
    }

    @Test
    fun `parses close npc channel 0x9E without consuming payload`() {
        val payload = Unpooled.buffer()
        payload.writeByte(0xCD)

        val command = ChannelPacketHandler.parse(
            ChannelPacketHandler.OPCODE_CLOSE_NPC_CHANNEL,
            payload,
        )

        assertThat(command).isSameAs(CloseNpcChannelCommand)
        assertThat(payload.readableBytes()).isEqualTo(1)
    }

    @Test
    fun `returns null for an unrelated opcode`() {
        assertThat(ChannelPacketHandler.parse(0x9B, Unpooled.buffer())).isNull()
    }

    @Test
    fun `decodes create private channel 0xAA without consuming bytes`() {
        val payload = Unpooled.buffer()

        val command = ChannelPacketHandler.parse(
            ChannelPacketHandler.OPCODE_CREATE_PRIVATE_CHANNEL,
            payload,
        )

        assertThat(command).isEqualTo(CreatePrivateChannelCommand)
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `decodes channel invite 0xAB with the current payload trailing u16`() {
        val payload = Unpooled.buffer()
        payload.writeTibiaString("Guildmate")
        payload.writeShortLE(0x1234)

        val command = ChannelPacketHandler.parse(ChannelPacketHandler.OPCODE_CHANNEL_INVITE, payload)

        assertThat(command).isEqualTo(ChannelInviteCommand("Guildmate"))
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `decodes channel exclude 0xAC with the current payload trailing u16`() {
        val payload = Unpooled.buffer()
        payload.writeTibiaString("Rival")
        payload.writeShortLE(0x4321)

        val command = ChannelPacketHandler.parse(ChannelPacketHandler.OPCODE_CHANNEL_EXCLUDE, payload)

        assertThat(command).isEqualTo(ChannelExcludeCommand("Rival"))
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `leaves a single trailing byte untouched because the payload check needs two`() {
        val payload = Unpooled.buffer()
        payload.writeTibiaString("Rival")
        payload.writeByte(0x7F)

        val command = ChannelPacketHandler.parse(ChannelPacketHandler.OPCODE_CHANNEL_EXCLUDE, payload)

        assertThat(command).isEqualTo(ChannelExcludeCommand("Rival"))
        assertThat(payload.readableBytes()).isEqualTo(1)
    }

    @Test
    fun `decodes a channel invite name without a trailing u16`() {
        val payload = Unpooled.buffer()
        payload.writeTibiaString("Guildmate")

        val command = ChannelPacketHandler.parse(ChannelPacketHandler.OPCODE_CHANNEL_INVITE, payload)

        assertThat(command).isEqualTo(ChannelInviteCommand("Guildmate"))
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `returns null for an unknown channel opcode`() {
        val payload = Unpooled.buffer()
        payload.writeTibiaString("Guildmate")

        assertThat(ChannelPacketHandler.parse(0x1D, payload)).isNull()
    }

    @Test
    fun `returns null when the invite name is truncated`() {
        val payload = Unpooled.buffer()
        payload.writeShortLE(5)
        payload.writeByte('A'.code)
        payload.writeByte('B'.code)

        assertThat(ChannelPacketHandler.parse(ChannelPacketHandler.OPCODE_CHANNEL_INVITE, payload)).isNull()
    }

    @Test
    fun `returns null when the invite frame is empty`() {
        assertThat(ChannelPacketHandler.parse(ChannelPacketHandler.OPCODE_CHANNEL_INVITE, Unpooled.buffer()))
            .isNull()
    }
}
