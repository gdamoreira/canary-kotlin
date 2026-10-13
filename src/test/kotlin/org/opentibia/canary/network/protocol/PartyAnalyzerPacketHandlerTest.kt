package org.opentibia.canary.network.protocol

import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Decode tests for `ProtocolGame::parsePartyAnalyzerAction` (client opcode
 * `0x2B`, protocolgame.cpp:3900-3926). The action byte is Canary's
 * `PartyAnalyzerAction_t` (`RESET` 0, `PRICETYPE` 1, `PRICEVALUE` 2,
 * src/utils/utils_definitions.hpp:502-504) and only `PRICEVALUE` carries the
 * `uint16_t` entry count followed by `(uint16_t itemId, uint64_t price)` pairs.
 */
class PartyAnalyzerPacketHandlerTest {

    @Test
    fun `parses the reset action byte`() {
        val payload = Unpooled.buffer()
        payload.writeByte(0)

        val command = PartyAnalyzerPacketHandler.parse(payload)

        assertThat(command).isEqualTo(PartyAnalyzerActionCommand(action = 0, prices = emptyList()))
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `parses the price type action byte`() {
        val payload = Unpooled.buffer()
        payload.writeByte(1)

        val command = PartyAnalyzerPacketHandler.parse(payload)

        assertThat(command).isEqualTo(PartyAnalyzerActionCommand(action = 1, prices = emptyList()))
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `parses the price value action with its item price pairs`() {
        val payload = Unpooled.buffer()
        payload.writeByte(2)
        payload.writeShortLE(2)
        payload.writeShortLE(2160)
        payload.writeLongLE(100_000L)
        payload.writeShortLE(2400)
        payload.writeLongLE(7L)

        val command = PartyAnalyzerPacketHandler.parse(payload)

        assertThat(command).isEqualTo(
            PartyAnalyzerActionCommand(
                action = 2,
                prices = listOf(
                    PartyAnalyzerItemPrice(itemId = 2160, price = 100_000L),
                    PartyAnalyzerItemPrice(itemId = 2400, price = 7L),
                ),
            ),
        )
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `parses an empty price value list`() {
        val payload = Unpooled.buffer()
        payload.writeByte(2)
        payload.writeShortLE(0)

        val command = PartyAnalyzerPacketHandler.parse(payload)

        assertThat(command).isEqualTo(PartyAnalyzerActionCommand(action = 2, prices = emptyList()))
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `keeps every other action byte with no operands`() {
        val payload = Unpooled.buffer()
        payload.writeByte(9)

        val command = PartyAnalyzerPacketHandler.parse(payload)

        assertThat(command).isEqualTo(PartyAnalyzerActionCommand(action = 9, prices = emptyList()))
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `rejects a truncated price value payload`() {
        val payload = Unpooled.buffer()
        payload.writeByte(2)
        payload.writeShortLE(2)
        payload.writeShortLE(2160)
        payload.writeLongLE(100_000L)
        payload.writeShortLE(2400)

        assertThat(PartyAnalyzerPacketHandler.parse(payload)).isNull()
    }

    @Test
    fun `rejects an empty action payload`() {
        assertThat(PartyAnalyzerPacketHandler.parse(Unpooled.buffer())).isNull()
    }
}
