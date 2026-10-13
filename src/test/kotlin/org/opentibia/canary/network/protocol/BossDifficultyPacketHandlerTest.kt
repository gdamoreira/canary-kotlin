package org.opentibia.canary.network.protocol

import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Decode tests for `0xC2` `ProtocolGame::parseBossDifficultySelection`
 * (src/server/network/protocol/protocolgame.cpp:3317-3323): a `uint32_t` boss
 * id, the action byte, and a `uint16_t` difficulty only for actions 0 and 2.
 */
class BossDifficultyPacketHandlerTest {

    @Test
    fun `decodes action 0 with the difficulty u16`() {
        val payload = Unpooled.buffer()
        payload.writeIntLE(0x11223344)
        payload.writeByte(0)
        payload.writeShortLE(0x5566)

        val command = BossDifficultyPacketHandler.parse(payload)

        assertThat(command).isEqualTo(
            BossDifficultySelectionCommand(bossId = 0x11223344, action = 0, difficulty = 0x5566),
        )
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `decodes action 2 with the difficulty u16`() {
        val payload = Unpooled.buffer()
        payload.writeIntLE(7)
        payload.writeByte(2)
        payload.writeShortLE(3)

        val command = BossDifficultyPacketHandler.parse(payload)

        assertThat(command).isEqualTo(
            BossDifficultySelectionCommand(bossId = 7, action = 2, difficulty = 3),
        )
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `decodes action 1 without reading a difficulty`() {
        val payload = Unpooled.buffer()
        payload.writeIntLE(9)
        payload.writeByte(1)
        payload.writeByte(0xAA)

        val command = BossDifficultyPacketHandler.parse(payload)

        assertThat(command).isEqualTo(
            BossDifficultySelectionCommand(bossId = 9, action = 1, difficulty = null),
        )
        assertThat(payload.readableBytes()).isEqualTo(1)
    }

    @Test
    fun `returns null when the boss difficulty payload is truncated`() {
        val missingAction = Unpooled.buffer()
        missingAction.writeIntLE(9)
        assertThat(BossDifficultyPacketHandler.parse(missingAction)).isNull()

        val missingDifficulty = Unpooled.buffer()
        missingDifficulty.writeIntLE(9)
        missingDifficulty.writeByte(0)
        assertThat(BossDifficultyPacketHandler.parse(missingDifficulty)).isNull()

        assertThat(BossDifficultyPacketHandler.parse(Unpooled.buffer())).isNull()
    }
}
