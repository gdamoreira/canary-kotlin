package org.opentibia.canary.network.protocol

import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Decode tests for `ProtocolGame::parsePlayerTyping` (client opcode `0x38`,
 * protocolgame.cpp:3670-3673): a single `uint8_t` typing state byte handed to
 * `Game::playerSetTyping`.
 */
class PlayerTypingPacketHandlerTest {

    @Test
    fun `parses the typing state byte`() {
        val payload = Unpooled.buffer()
        payload.writeByte(1)

        assertThat(PlayerTypingPacketHandler.parse(payload)).isEqualTo(PlayerTypingCommand(typing = 1))
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `parses the stopped typing state`() {
        val payload = Unpooled.buffer()
        payload.writeByte(0)

        assertThat(PlayerTypingPacketHandler.parse(payload)).isEqualTo(PlayerTypingCommand(typing = 0))
    }

    @Test
    fun `rejects an empty typing payload`() {
        assertThat(PlayerTypingPacketHandler.parse(Unpooled.buffer())).isNull()
    }
}
