package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Decode tests for the party client opcodes `0xA3`-`0xA8`
 * (`ProtocolGame::parseInviteToParty` ... `parseEnableSharedPartyExperience`,
 * protocolgame.cpp:4373-4396). Every target id is a `uint32_t`; `0xA7`
 * (`Game::playerLeaveParty`) carries no payload and `0xA8` reads one byte that
 * is true only when it equals `0x01`.
 */
class PartyPacketHandlerTest {

    @Test
    fun `parses invite to party 0xA3 target id`() {
        val payload = Unpooled.buffer()
        payload.writeIntLE(98765)

        val command = PartyPacketHandler.parse(PartyPacketHandler.OPCODE_INVITE_TO_PARTY, payload)

        assertThat(command).isEqualTo(InviteToPartyCommand(targetId = 98765L))
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `parses join party 0xA4 target id`() {
        val payload = Unpooled.buffer()
        payload.writeIntLE(12345)

        val command = PartyPacketHandler.parse(PartyPacketHandler.OPCODE_JOIN_PARTY, payload)

        assertThat(command).isEqualTo(JoinPartyCommand(targetId = 12345L))
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `parses revoke party invite 0xA5 target id`() {
        val payload = Unpooled.buffer()
        payload.writeIntLE(22222)

        val command = PartyPacketHandler.parse(PartyPacketHandler.OPCODE_REVOKE_PARTY_INVITE, payload)

        assertThat(command).isEqualTo(RevokePartyInviteCommand(targetId = 22222L))
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `parses pass party leadership 0xA6 target id`() {
        val payload = Unpooled.buffer()
        payload.writeIntLE(33333)

        val command = PartyPacketHandler.parse(PartyPacketHandler.OPCODE_PASS_PARTY_LEADERSHIP, payload)

        assertThat(command).isEqualTo(PassPartyLeadershipCommand(targetId = 33333L))
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `decodes leave party 0xA7 with no payload`() {
        val payload = Unpooled.buffer()

        val command = PartyPacketHandler.parse(PartyPacketHandler.OPCODE_LEAVE_PARTY, payload)

        assertThat(command).isEqualTo(LeavePartyCommand)
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `parses enable shared party experience 0xA8 as true only for byte one`() {
        val enabled = Unpooled.buffer()
        enabled.writeByte(0x01)

        assertThat(PartyPacketHandler.parse(PartyPacketHandler.OPCODE_ENABLE_SHARED_PARTY_EXPERIENCE, enabled))
            .isEqualTo(EnableSharedPartyExperienceCommand(sharedExpActive = true))
        assertThat(enabled.readableBytes()).isZero()

        val disabled = Unpooled.buffer()
        disabled.writeByte(0x00)

        assertThat(PartyPacketHandler.parse(PartyPacketHandler.OPCODE_ENABLE_SHARED_PARTY_EXPERIENCE, disabled))
            .isEqualTo(EnableSharedPartyExperienceCommand(sharedExpActive = false))
        assertThat(disabled.readableBytes()).isZero()
    }

    @Test
    fun `rejects a truncated party target payload`() {
        val payload = Unpooled.buffer()
        payload.writeByte(1)
        payload.writeByte(2)
        payload.writeByte(3)

        assertThat(PartyPacketHandler.parse(PartyPacketHandler.OPCODE_INVITE_TO_PARTY, payload)).isNull()
    }

    @Test
    fun `rejects a truncated shared party experience payload`() {
        assertThat(
            PartyPacketHandler.parse(
                PartyPacketHandler.OPCODE_ENABLE_SHARED_PARTY_EXPERIENCE,
                Unpooled.buffer(),
            ),
        ).isNull()
    }

    @Test
    fun `returns null for an unrelated opcode`() {
        assertThat(PartyPacketHandler.parse(0xA2, Unpooled.buffer())).isNull()
    }

    @Test
    fun `decodes invite to party 0xA3 via EmbeddedChannel inbound message`() {
        val channel = EmbeddedChannel()
        try {
            val bytes = Unpooled.buffer()
            bytes.writeIntLE(4242)
            channel.writeInbound(bytes)

            val payload = channel.readInbound<ByteBuf>()
            val command = PartyPacketHandler.parse(PartyPacketHandler.OPCODE_INVITE_TO_PARTY, payload)

            assertThat(command).isEqualTo(InviteToPartyCommand(targetId = 4242L))
        } finally {
            channel.finishAndReleaseAll()
        }
    }
}
