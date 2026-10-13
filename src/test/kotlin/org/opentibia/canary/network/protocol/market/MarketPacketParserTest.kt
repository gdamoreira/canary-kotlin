package org.opentibia.canary.network.protocol.market

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.economy.market.MarketOfferType

class MarketPacketParserTest {

    @Test
    fun `parses market leave 0xF4 which carries no payload`() {
        val payload = Unpooled.buffer()

        val command = MarketPacketParser.parse(MarketPacketParser.OPCODE_MARKET_LEAVE, payload)

        assertThat(command).isEqualTo(MarketLeaveCommand)
    }

    @Test
    fun `parses modern browse markers for own offers and own history`() {
        val ownOffers = Unpooled.buffer()
        ownOffers.writeByte(MarketPacketParser.MARKET_REQUEST_OWN_OFFERS)
        assertThat(MarketPacketParser.parseMarketBrowse(ownOffers))
            .isEqualTo(MarketBrowseCommand(MarketBrowseRequest.OwnOffers))

        val ownHistory = Unpooled.buffer()
        ownHistory.writeByte(MarketPacketParser.MARKET_REQUEST_OWN_HISTORY)
        assertThat(MarketPacketParser.parseMarketBrowse(ownHistory))
            .isEqualTo(MarketBrowseCommand(MarketBrowseRequest.OwnHistory))
    }

    @Test
    fun `parses modern item browse with tier when the item has an upgrade classification`() {
        val payload = Unpooled.buffer()
        payload.writeByte(MarketPacketParser.MARKET_REQUEST_ITEM_BROWSE)
        payload.writeShortLE(3000)
        payload.writeByte(7)

        val command = MarketPacketParser.parseMarketBrowse(payload) { if (it == 3000) 5 else 0 }

        assertThat(command).isEqualTo(MarketBrowseCommand(MarketBrowseRequest.Item(3000, 7)))
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `parses modern item browse without tier for a plain item`() {
        val payload = Unpooled.buffer()
        payload.writeByte(MarketPacketParser.MARKET_REQUEST_ITEM_BROWSE)
        payload.writeShortLE(100)

        val command = MarketPacketParser.parseMarketBrowse(payload) { 0 }

        assertThat(command).isEqualTo(MarketBrowseCommand(MarketBrowseRequest.Item(100, 0)))
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `parses old protocol browse markers and raw item ids`() {
        val ownHistory = Unpooled.buffer()
        ownHistory.writeShortLE(MarketPacketParser.MARKET_REQUEST_OWN_HISTORY_OLD)
        assertThat(MarketPacketParser.parseMarketBrowse(ownHistory, oldProtocol = true))
            .isEqualTo(MarketBrowseCommand(MarketBrowseRequest.OwnHistory))

        val ownOffers = Unpooled.buffer()
        ownOffers.writeShortLE(MarketPacketParser.MARKET_REQUEST_OWN_OFFERS_OLD)
        assertThat(MarketPacketParser.parseMarketBrowse(ownOffers, oldProtocol = true))
            .isEqualTo(MarketBrowseCommand(MarketBrowseRequest.OwnOffers))

        val item = Unpooled.buffer()
        item.writeShortLE(3000)
        assertThat(MarketPacketParser.parseMarketBrowse(item, oldProtocol = true) { 5 })
            .isEqualTo(MarketBrowseCommand(MarketBrowseRequest.Item(3000, 0)))
    }

    @Test
    fun `parses modern create offer with a 64 bit price tier and anonymous flag`() {
        val payload = Unpooled.buffer()
        payload.writeByte(MarketOfferType.Buy.ordinal)
        payload.writeShortLE(3000)
        payload.writeByte(7)
        payload.writeShortLE(5)
        payload.writeLongLE(123456789L)
        payload.writeByte(1)

        val command = MarketPacketParser.parseMarketCreateOffer(payload) { if (it == 3000) 5 else 0 }

        assertThat(command).isEqualTo(
            MarketCreateOfferCommand(
                action = MarketOfferType.Buy,
                itemId = 3000,
                tier = 7,
                amount = 5,
                price = 123456789L,
                anonymous = true,
            )
        )
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `parses old protocol create offer with a 32 bit price and no tier`() {
        val payload = Unpooled.buffer()
        payload.writeByte(MarketOfferType.Sell.ordinal)
        payload.writeShortLE(0x0ABC)
        payload.writeShortLE(3)
        payload.writeIntLE(0xFFFFFFFE.toInt())
        payload.writeByte(0)

        val command = MarketPacketParser.parseMarketCreateOffer(payload, oldProtocol = true) { 9 }

        assertThat(command).isEqualTo(
            MarketCreateOfferCommand(
                action = MarketOfferType.Sell,
                itemId = 0x0ABC,
                tier = 0,
                amount = 3,
                price = 0xFFFFFFFEL,
                anonymous = false,
            )
        )
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `parses cancel offer timestamp and counter`() {
        val payload = Unpooled.buffer()
        payload.writeIntLE(0x12345678)
        payload.writeShortLE(0x0102)

        val command = MarketPacketParser.parseMarketCancelOffer(payload)

        assertThat(command).isEqualTo(MarketCancelOfferCommand(timestamp = 0x12345678L, counter = 0x0102))
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `parses accept offer timestamp counter and amount`() {
        val payload = Unpooled.buffer()
        payload.writeIntLE(0x11223344)
        payload.writeShortLE(0x00FF)
        payload.writeShortLE(0x000A)

        val command = MarketPacketParser.parseMarketAcceptOffer(payload)

        assertThat(command).isEqualTo(
            MarketAcceptOfferCommand(timestamp = 0x11223344L, counter = 0x00FF, amount = 0x000A)
        )
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `dispatches market browse create cancel and accept through the opcode entry point`() {
        val browse = Unpooled.buffer()
        browse.writeByte(MarketPacketParser.MARKET_REQUEST_OWN_OFFERS)
        assertThat(MarketPacketParser.parse(MarketPacketParser.OPCODE_MARKET_BROWSE, browse))
            .isEqualTo(MarketBrowseCommand(MarketBrowseRequest.OwnOffers))

        val create = Unpooled.buffer()
        create.writeByte(MarketOfferType.Buy.ordinal)
        create.writeShortLE(0x0BB8)
        create.writeShortLE(2)
        create.writeLongLE(50L)
        create.writeByte(0)
        assertThat(MarketPacketParser.parse(MarketPacketParser.OPCODE_MARKET_CREATE_OFFER, create) { 0 })
            .isEqualTo(MarketCreateOfferCommand(MarketOfferType.Buy, 0x0BB8, 0, 2, 50L, false))

        val cancel = Unpooled.buffer()
        cancel.writeIntLE(7)
        cancel.writeShortLE(8)
        assertThat(MarketPacketParser.parse(MarketPacketParser.OPCODE_MARKET_CANCEL_OFFER, cancel))
            .isEqualTo(MarketCancelOfferCommand(7L, 8))

        val accept = Unpooled.buffer()
        accept.writeIntLE(9)
        accept.writeShortLE(10)
        accept.writeShortLE(11)
        assertThat(MarketPacketParser.parse(MarketPacketParser.OPCODE_MARKET_ACCEPT_OFFER, accept))
            .isEqualTo(MarketAcceptOfferCommand(9L, 10, 11))
    }

    @Test
    fun `returns null for an unhandled market opcode`() {
        assertThat(MarketPacketParser.parse(0x00, Unpooled.buffer())).isNull()
    }

    @Test
    fun `rejects an unknown create offer action byte`() {
        val payload = Unpooled.buffer()
        payload.writeByte(2)
        payload.writeShortLE(100)
        payload.writeShortLE(1)
        payload.writeLongLE(10L)
        payload.writeByte(0)

        assertThat(MarketPacketParser.parseMarketCreateOffer(payload)).isNull()
    }

    @Test
    fun `returns null when a browse frame is truncated`() {
        assertThat(MarketPacketParser.parseMarketBrowse(Unpooled.buffer())).isNull()

        val missingItem = Unpooled.buffer()
        missingItem.writeByte(MarketPacketParser.MARKET_REQUEST_ITEM_BROWSE)
        assertThat(MarketPacketParser.parseMarketBrowse(missingItem)).isNull()

        val missingTier = Unpooled.buffer()
        missingTier.writeByte(MarketPacketParser.MARKET_REQUEST_ITEM_BROWSE)
        missingTier.writeShortLE(3000)
        assertThat(MarketPacketParser.parseMarketBrowse(missingTier) { 5 }).isNull()

        val truncatedOld = Unpooled.buffer()
        truncatedOld.writeByte(0x01)
        assertThat(MarketPacketParser.parseMarketBrowse(truncatedOld, oldProtocol = true)).isNull()
    }

    @Test
    fun `returns null for every truncated prefix of a modern create offer`() {
        val full = Unpooled.buffer()
        full.writeByte(MarketOfferType.Buy.ordinal)
        full.writeShortLE(3000)
        full.writeByte(7)
        full.writeShortLE(5)
        full.writeLongLE(123456789L)
        full.writeByte(1)

        val bytes = ByteArray(full.readableBytes())
        full.readBytes(bytes)

        for (cut in 0 until bytes.size) {
            val partial = Unpooled.wrappedBuffer(bytes.copyOf(cut))
            assertThat(MarketPacketParser.parseMarketCreateOffer(partial) { 5 })
                .describedAs("prefix of %d of %d bytes must not decode", cut, bytes.size)
                .isNull()
        }
    }

    @Test
    fun `returns null when cancel or accept frames are truncated`() {
        assertThat(MarketPacketParser.parseMarketCancelOffer(Unpooled.buffer())).isNull()

        val cancelShort = Unpooled.buffer()
        cancelShort.writeIntLE(1)
        assertThat(MarketPacketParser.parseMarketCancelOffer(cancelShort)).isNull()

        assertThat(MarketPacketParser.parseMarketAcceptOffer(Unpooled.buffer())).isNull()

        val acceptShort = Unpooled.buffer()
        acceptShort.writeIntLE(1)
        acceptShort.writeShortLE(2)
        assertThat(MarketPacketParser.parseMarketAcceptOffer(acceptShort)).isNull()
    }

    @Test
    fun `decodes a market create offer frame through an embedded channel`() {
        val channel = EmbeddedChannel()
        try {
            val frame = Unpooled.buffer()
            frame.writeByte(MarketOfferType.Sell.ordinal)
            frame.writeShortLE(100)
            frame.writeShortLE(2)
            frame.writeLongLE(50L)
            frame.writeByte(0)
            assertThat(channel.writeInbound(frame)).isTrue()

            val inbound = channel.readInbound<ByteBuf>()
            val command = MarketPacketParser.parse(MarketPacketParser.OPCODE_MARKET_CREATE_OFFER, inbound) { 0 }

            assertThat(command).isEqualTo(
                MarketCreateOfferCommand(MarketOfferType.Sell, 100, 0, 2, 50L, false)
            )
        } finally {
            channel.finishAndReleaseAll()
        }
    }
}
