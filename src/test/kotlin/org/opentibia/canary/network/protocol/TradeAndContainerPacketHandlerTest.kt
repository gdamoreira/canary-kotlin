package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.map.Position

/**
 * Decode tests for the trade (`0x7D`-`0x80`) and container
 * (`0x75`/`0x87`/`0x88`/`0x95`) client opcodes
 * (src/server/network/protocol/protocolgame.cpp):
 *  - 0x7D `ProtocolGame::parseRequestTrade`        (protocolgame.cpp:2830)
 *  - 0x7E `ProtocolGame::parseLookInTrade`         (protocolgame.cpp:2838)
 *  - 0x7F `Game::playerAcceptTrade`                (protocolgame.cpp:1816)
 *  - 0x80 `Game::playerCloseTrade`                 (protocolgame.cpp:1819)
 *  - 0x75 `ProtocolGame::parseContainerAction`     (protocolgame.cpp:3258)
 *  - 0x87 `ProtocolGame::parseCloseContainer`      (protocolgame.cpp:2584)
 *  - 0x88 `ProtocolGame::parseUpArrowContainer`    (protocolgame.cpp:2589)
 *  - 0x95 `ProtocolGame::parseOpenParentContainer` (protocolgame.cpp:11052)
 */
class TradeAndContainerPacketHandlerTest {

    @Test
    fun `parses request trade 0x7D with position item stack position and partner player id`() {
        val payload = Unpooled.buffer()
        payload.writeShortLE(100)
        payload.writeShortLE(200)
        payload.writeByte(7)
        payload.writeShortLE(2160)
        payload.writeByte(3)
        payload.writeIntLE(12345)

        val command = TradeAndContainerPacketHandler.parse(
            TradeAndContainerPacketHandler.OPCODE_REQUEST_TRADE,
            payload,
        )

        assertThat(command).isEqualTo(
            RequestTradeCommand(
                position = Position(100, 200, 7),
                itemId = 2160,
                stackPosition = 3,
                partnerPlayerId = 12345L,
            )
        )
    }

    @Test
    fun `rejects a truncated request trade 0x7D payload`() {
        val payload = Unpooled.buffer()
        payload.writeShortLE(100)
        payload.writeShortLE(200)
        payload.writeByte(7)
        payload.writeShortLE(2160)
        payload.writeByte(3) // missing the uint32_t partner player id

        assertThat(TradeAndContainerPacketHandler.parseRequestTrade(payload)).isNull()
    }

    @Test
    fun `parses look in trade 0x7E counter offer flag and index`() {
        val payload = Unpooled.buffer()
        payload.writeByte(0x01)
        payload.writeByte(4)

        val command = TradeAndContainerPacketHandler.parseLookInTrade(payload)

        assertThat(command).isEqualTo(LookInTradeCommand(counterOffer = true, index = 4))
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `parses look in trade 0x7E flag other than one as not a counter offer`() {
        val payload = Unpooled.buffer()
        payload.writeByte(0x00)
        payload.writeByte(1)

        val command = TradeAndContainerPacketHandler.parseLookInTrade(payload)

        assertThat(command).isEqualTo(LookInTradeCommand(counterOffer = false, index = 1))
    }

    @Test
    fun `rejects a truncated look in trade 0x7E payload`() {
        val payload = Unpooled.buffer()
        payload.writeByte(0x01)

        assertThat(TradeAndContainerPacketHandler.parseLookInTrade(payload)).isNull()
    }

    @Test
    fun `parses accept trade 0x7F without consuming payload`() {
        val payload = Unpooled.buffer()
        payload.writeByte(0xAA)

        val command = TradeAndContainerPacketHandler.parse(
            TradeAndContainerPacketHandler.OPCODE_ACCEPT_TRADE,
            payload,
        )

        assertThat(command).isEqualTo(AcceptTradeCommand)
        assertThat(payload.readableBytes()).isEqualTo(1)
    }

    @Test
    fun `parses close trade 0x80 without consuming payload`() {
        val payload = Unpooled.buffer()
        payload.writeByte(0xAA)

        val command = TradeAndContainerPacketHandler.parse(
            TradeAndContainerPacketHandler.OPCODE_CLOSE_TRADE,
            payload,
        )

        assertThat(command).isEqualTo(CloseTradeCommand)
        assertThat(payload.readableBytes()).isEqualTo(1)
    }

    @Test
    fun `parses container action 0x75 action zero with three operand bytes`() {
        val payload = Unpooled.buffer()
        payload.writeByte(1)
        payload.writeByte(0)
        payload.writeByte(2)
        payload.writeByte(3)
        payload.writeByte(4)

        val command = TradeAndContainerPacketHandler.parseContainerAction(payload)

        assertThat(command).isEqualTo(
            ContainerActionCommand(containerId = 1, action = 0, operands = listOf(2, 3, 4))
        )
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `parses container action 0x75 action one with one operand byte`() {
        val payload = Unpooled.buffer()
        payload.writeByte(1)
        payload.writeByte(1)
        payload.writeByte(9)

        val command = TradeAndContainerPacketHandler.parseContainerAction(payload)

        assertThat(command).isEqualTo(
            ContainerActionCommand(containerId = 1, action = 1, operands = listOf(9))
        )
    }

    @Test
    fun `parses container action 0x75 other action without operands and leaves trailing bytes`() {
        val payload = Unpooled.buffer()
        payload.writeByte(1)
        payload.writeByte(2)
        payload.writeByte(0xCC)

        val command = TradeAndContainerPacketHandler.parseContainerAction(payload)

        assertThat(command).isEqualTo(
            ContainerActionCommand(containerId = 1, action = 2, operands = emptyList())
        )
        assertThat(payload.readableBytes()).isEqualTo(1)
        assertThat(payload.readUnsignedByte().toInt()).isEqualTo(0xCC)
    }

    @Test
    fun `rejects a truncated container action 0x75 action zero payload`() {
        val payload = Unpooled.buffer()
        payload.writeByte(1)
        payload.writeByte(0)
        payload.writeByte(2) // two operand bytes missing

        assertThat(TradeAndContainerPacketHandler.parseContainerAction(payload)).isNull()
    }

    @Test
    fun `parses close container 0x87 container id`() {
        val payload = Unpooled.buffer()
        payload.writeByte(5)

        val command = TradeAndContainerPacketHandler.parseCloseContainer(payload)

        assertThat(command).isEqualTo(CloseContainerCommand(containerId = 5))
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `parses up arrow container 0x88 container id`() {
        val payload = Unpooled.buffer()
        payload.writeByte(6)

        val command = TradeAndContainerPacketHandler.parseUpArrowContainer(payload)

        assertThat(command).isEqualTo(UpArrowContainerCommand(containerId = 6))
    }

    @Test
    fun `parses open parent container 0x95 position`() {
        val payload = Unpooled.buffer()
        payload.writeShortLE(300)
        payload.writeShortLE(400)
        payload.writeByte(8)

        val command = TradeAndContainerPacketHandler.parseOpenParentContainer(payload)

        assertThat(command).isEqualTo(OpenParentContainerCommand(position = Position(300, 400, 8)))
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `returns null for an unrelated opcode`() {
        assertThat(TradeAndContainerPacketHandler.parse(0x7C, Unpooled.buffer())).isNull()
    }

    @Test
    fun `decodes request trade 0x7D via EmbeddedChannel inbound message`() {
        val channel = EmbeddedChannel()
        try {
            channel.writeInbound(requestTradePayload())

            val payload = channel.readInbound<ByteBuf>()
            val command = TradeAndContainerPacketHandler.parse(
                TradeAndContainerPacketHandler.OPCODE_REQUEST_TRADE,
                payload,
            )

            assertThat(command).isEqualTo(
                RequestTradeCommand(
                    position = Position(100, 200, 7),
                    itemId = 2160,
                    stackPosition = 3,
                    partnerPlayerId = 12345L,
                )
            )
        } finally {
            channel.finishAndReleaseAll()
        }
    }

    private fun requestTradePayload(): ByteBuf {
        val payload = Unpooled.buffer()
        payload.writeShortLE(100)
        payload.writeShortLE(200)
        payload.writeByte(7)
        payload.writeShortLE(2160)
        payload.writeByte(3)
        payload.writeIntLE(12345)
        return payload
    }
}
