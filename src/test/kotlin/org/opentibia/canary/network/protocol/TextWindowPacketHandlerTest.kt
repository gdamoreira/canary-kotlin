package org.opentibia.canary.network.protocol

import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.network.message.writeTibiaString

/**
 * Decode tests for the text-window client opcodes
 * (src/server/network/protocol/protocolgame.cpp):
 *  - 0x89 `ProtocolGame::parseTextWindow`  (protocolgame.cpp:2793-2797)
 *  - 0x8A `ProtocolGame::parseHouseWindow` (protocolgame.cpp:2799-2804)
 *
 * `0x89` carries a `uint32_t` window text id and the new text;
 * `0x8A` carries the list id byte, the `uint32_t` window text id and the text.
 */
class TextWindowPacketHandlerTest {

    @Test
    fun `parses text window 0x89 window text id and new text`() {
        val payload = Unpooled.buffer()
        payload.writeIntLE(0x01020304)
        payload.writeTibiaString("Signed by Hero")

        val command = TextWindowPacketHandler.parse(
            TextWindowPacketHandler.OPCODE_TEXT_WINDOW,
            payload,
        )

        assertThat(command).isEqualTo(
            WriteItemCommand(windowTextId = 0x01020304L, text = "Signed by Hero"),
        )
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `parses text window 0x89 with an empty text`() {
        val payload = Unpooled.buffer()
        payload.writeIntLE(1)
        payload.writeTibiaString("")

        val command = TextWindowPacketHandler.parseTextWindow(payload)

        assertThat(command).isEqualTo(WriteItemCommand(windowTextId = 1L, text = ""))
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `rejects a text window 0x89 string longer than the payload`() {
        val payload = Unpooled.buffer()
        payload.writeIntLE(1)
        payload.writeShortLE(10)
        payload.writeByte('A'.code)
        payload.writeByte('B'.code)

        assertThat(TextWindowPacketHandler.parseTextWindow(payload)).isNull()
    }

    @Test
    fun `rejects a truncated text window 0x89 window text id`() {
        val payload = Unpooled.buffer()
        payload.writeShortLE(1)

        assertThat(TextWindowPacketHandler.parseTextWindow(payload)).isNull()
    }

    @Test
    fun `parses house window 0x8A list id window text id and text`() {
        val payload = Unpooled.buffer()
        payload.writeByte(0)
        payload.writeIntLE(0x0000CAFE)
        payload.writeTibiaString("Guest")

        val command = TextWindowPacketHandler.parse(
            TextWindowPacketHandler.OPCODE_HOUSE_WINDOW,
            payload,
        )

        assertThat(command).isEqualTo(
            HouseWindowCommand(listId = 0, windowTextId = 0x0000CAFEL, text = "Guest"),
        )
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `rejects a truncated house window 0x8A payload`() {
        val payload = Unpooled.buffer()
        payload.writeByte(1)
        payload.writeIntLE(7)
        payload.writeShortLE(5) // text bytes missing

        assertThat(TextWindowPacketHandler.parseHouseWindow(payload)).isNull()
    }

    @Test
    fun `returns null for an unrelated opcode`() {
        assertThat(TextWindowPacketHandler.parse(0x8B, Unpooled.buffer())).isNull()
    }
}
