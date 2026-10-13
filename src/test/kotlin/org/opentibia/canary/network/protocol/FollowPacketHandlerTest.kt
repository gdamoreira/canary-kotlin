package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Decode tests for the follow client opcode `0xA2`
 * (`ProtocolGame::parseFollow`, protocolgame.cpp:2787-2791). Canary reads a
 * single `uint32_t` creature id and calls `Game::playerFollowCreature`
 * (game.cpp:7033), which stores the follow target and drops the attack target.
 */
class FollowPacketHandlerTest {

    @Test
    fun `parses follow 0xA2 creature id`() {
        val payload = Unpooled.buffer()
        payload.writeIntLE(98765)

        val command = FollowPacketHandler.parse(
            FollowPacketHandler.OPCODE_FOLLOW,
            payload,
        )

        assertThat(command).isEqualTo(FollowCommand(creatureId = 98765L))
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `rejects a truncated follow 0xA2 payload`() {
        val payload = Unpooled.buffer()
        payload.writeByte(1)
        payload.writeByte(2)
        payload.writeByte(3)

        assertThat(FollowPacketHandler.parseFollow(payload)).isNull()
    }

    @Test
    fun `returns null for an unrelated opcode`() {
        assertThat(FollowPacketHandler.parse(0xA1, Unpooled.buffer())).isNull()
    }

    @Test
    fun `decodes follow 0xA2 via EmbeddedChannel inbound message`() {
        val channel = EmbeddedChannel()
        try {
            val bytes = Unpooled.buffer()
            bytes.writeIntLE(4242)
            channel.writeInbound(bytes)

            val payload = channel.readInbound<ByteBuf>()
            val command = FollowPacketHandler.parse(
                FollowPacketHandler.OPCODE_FOLLOW,
                payload,
            )

            assertThat(command).isEqualTo(FollowCommand(creatureId = 4242L))
        } finally {
            channel.finishAndReleaseAll()
        }
    }
}
