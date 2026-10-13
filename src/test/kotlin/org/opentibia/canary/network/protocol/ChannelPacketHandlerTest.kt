package org.opentibia.canary.network.protocol

import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.network.message.writeTibiaString

/**
 * Decode tests for `0xAA` `Game::playerCreatePrivateChannel`,
 * `0xAB` `ProtocolGame::parseChannelInvite` and `0xAC`
 * `ProtocolGame::parseChannelExclude` (src/server/network/protocol/protocolgame.cpp:1938-1946).
 */
class ChannelPacketHandlerTest {

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
