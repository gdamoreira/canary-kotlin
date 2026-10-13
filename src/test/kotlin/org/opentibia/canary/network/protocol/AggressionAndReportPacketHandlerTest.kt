package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.map.Position

/**
 * Decode tests for the read-and-discard client queries
 * (src/server/network/protocol/protocolgame.cpp):
 *  - 0x8E `ProtocolGame::parseJoinAggression`   (protocolgame.cpp:3299-3301):
 *    a `uint32_t` the dispatcher reads and discards.
 *  - 0x9D `ProtocolGame::parseGetTextForReport` (protocolgame.cpp:3307-3311):
 *    a `Position`, a `uint16_t` and a byte (`getByte(true)`), all discarded.
 *
 * Canary makes no `Game` call for either opcode, so both commands exist only to
 * pin the exact consumed byte count.
 */
class AggressionAndReportPacketHandlerTest {

    @Test
    fun `parses join aggression 0x8E as a discarded uint32`() {
        val payload = Unpooled.buffer()
        payload.writeIntLE(0x01020304)

        val command = AggressionAndReportPacketHandler.parse(
            AggressionAndReportPacketHandler.OPCODE_JOIN_AGGRESSION,
            payload,
        )

        assertThat(command).isEqualTo(JoinAggressionCommand(value = 0x01020304L))
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `rejects a truncated join aggression 0x8E payload`() {
        val payload = Unpooled.buffer()
        payload.writeShortLE(1)

        assertThat(AggressionAndReportPacketHandler.parseJoinAggression(payload)).isNull()
    }

    @Test
    fun `parses get text for report 0x9D position item and byte`() {
        val payload = Unpooled.buffer()
        writePosition(payload, Position(100, 100, 7))
        payload.writeShortLE(2160)
        payload.writeByte(0x02)

        val command = AggressionAndReportPacketHandler.parse(
            AggressionAndReportPacketHandler.OPCODE_GET_TEXT_FOR_REPORT,
            payload,
        )

        assertThat(command).isEqualTo(
            GetTextForReportCommand(
                position = Position(100, 100, 7),
                itemId = 2160,
                value = 2,
            ),
        )
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `rejects a truncated get text for report 0x9D payload`() {
        val payload = Unpooled.buffer()
        writePosition(payload, Position(100, 100, 7))
        payload.writeByte(0x00) // item id needs 2 bytes

        assertThat(AggressionAndReportPacketHandler.parseGetTextForReport(payload)).isNull()
    }

    @Test
    fun `returns null for an unrelated opcode`() {
        assertThat(AggressionAndReportPacketHandler.parse(0x8F, Unpooled.buffer())).isNull()
    }

    private fun writePosition(payload: ByteBuf, position: Position) {
        payload.writeShortLE(position.x)
        payload.writeShortLE(position.y)
        payload.writeByte(position.z)
    }
}
