package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Decode tests for the quest-log client opcodes `0xF0`/`0xF1`
 * (`Game::playerShowQuestLog`, protocolgame.cpp:2083-2085 and
 * `ProtocolGame::parseQuestLine`, protocolgame.cpp:4398-4401). `0xF0` carries
 * no payload; `0xF1` reads a single `uint16_t` quest id.
 */
class QuestLogPacketHandlerTest {

    @Test
    fun `decodes show quest log 0xF0 with no payload`() {
        val payload = Unpooled.buffer()

        val command = QuestLogPacketHandler.parse(QuestLogPacketHandler.OPCODE_SHOW_QUEST_LOG, payload)

        assertThat(command).isEqualTo(ShowQuestLogCommand)
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `parses quest line 0xF1 quest id`() {
        val payload = Unpooled.buffer()
        payload.writeShortLE(123)

        val command = QuestLogPacketHandler.parse(QuestLogPacketHandler.OPCODE_QUEST_LINE, payload)

        assertThat(command).isEqualTo(ShowQuestLineCommand(questId = 123))
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `rejects a truncated quest line payload`() {
        val payload = Unpooled.buffer()
        payload.writeByte(0x01)

        assertThat(QuestLogPacketHandler.parse(QuestLogPacketHandler.OPCODE_QUEST_LINE, payload)).isNull()
    }

    @Test
    fun `returns null for an unrelated opcode`() {
        assertThat(QuestLogPacketHandler.parse(0xF2, Unpooled.buffer())).isNull()
    }

    @Test
    fun `decodes quest line 0xF1 via EmbeddedChannel inbound message`() {
        val channel = EmbeddedChannel()
        try {
            val bytes = Unpooled.buffer()
            bytes.writeShortLE(77)
            channel.writeInbound(bytes)

            val payload = channel.readInbound<ByteBuf>()
            val command = QuestLogPacketHandler.parse(QuestLogPacketHandler.OPCODE_QUEST_LINE, payload)

            assertThat(command).isEqualTo(ShowQuestLineCommand(questId = 77))
        } finally {
            channel.finishAndReleaseAll()
        }
    }
}
