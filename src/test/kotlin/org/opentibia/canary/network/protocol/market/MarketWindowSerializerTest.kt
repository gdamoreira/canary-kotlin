package org.opentibia.canary.network.protocol.market

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.economy.market.MarketOfferType
import org.opentibia.canary.network.message.writeTibiaString

class MarketWindowSerializerTest {

    private fun ByteBuf.toByteArray(): ByteArray {
        val bytes = ByteArray(readableBytes())
        readBytes(bytes)
        return bytes
    }

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    private fun expectedBytes(block: ByteBuf.() -> Unit): ByteArray {
        val buffer = Unpooled.buffer()
        buffer.block()
        return buffer.toByteArray()
    }

    @Test
    fun `serializes market leave 0xF7`() {
        val out = Unpooled.buffer()

        MarketWindowSerializer.writeMarketLeave(out)

        assertThat(out.toByteArray()).containsExactly(*bytes(0xF7))
    }

    @Test
    fun `serializes market enter with the player offer count and an empty locker`() {
        val out = Unpooled.buffer()

        MarketWindowSerializer.writeMarketEnter(
            out,
            MarketEnterWindow(playerOfferCount = 3, lockerItems = emptyMap()),
        )

        assertThat(out.toByteArray()).containsExactly(*bytes(0xF6, 0x03, 0x00, 0x00))
    }

    @Test
    fun `serializes market enter without a depot locker as a zero entry count`() {
        val out = Unpooled.buffer()

        MarketWindowSerializer.writeMarketEnter(
            out,
            MarketEnterWindow(playerOfferCount = 0, lockerItems = null),
        )

        assertThat(out.toByteArray()).containsExactly(*bytes(0xF6, 0x00, 0x00, 0x00))
    }

    @Test
    fun `serializes the old protocol market enter bank balance before the offer count`() {
        val out = Unpooled.buffer()

        MarketWindowSerializer.writeMarketEnter(
            out,
            MarketEnterWindow(bankBalance = 0x0102030405060708L, playerOfferCount = 1, lockerItems = emptyMap()),
            oldProtocol = true,
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xF6,
                0x08, 0x07, 0x06, 0x05, 0x04, 0x03, 0x02, 0x01,
                0x01,
                0x00, 0x00,
            )
        )
    }

    @Test
    fun `serializes locker entries ordered by item then tier with tier bytes only for classified items`() {
        val out = Unpooled.buffer()

        MarketWindowSerializer.writeMarketEnter(
            out,
            MarketEnterWindow(
                playerOfferCount = 0,
                lockerItems = mapOf(
                    2000 to mapOf(0 to 1),
                    1000 to mapOf(2 to 3, 1 to 5),
                ),
            ),
        ) { if (it == 1000) 5 else 0 }

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xF6,
                0x00,
                0x03, 0x00, // three entries
                0xE8, 0x03, 0x01, 0x05, 0x00, // itemId 1000, tier 1, count 5
                0xE8, 0x03, 0x02, 0x03, 0x00, // itemId 1000, tier 2, count 3
                0xD0, 0x07, 0x01, 0x00, // itemId 2000, no tier byte, count 1
            )
        )
    }

    @Test
    fun `serializes a modern item browse frame with buy and sell offers`() {
        val out = Unpooled.buffer()

        MarketWindowSerializer.writeMarketBrowseItem(
            out,
            itemId = 3000,
            buyOffers = listOf(
                MarketOffer(
                    timestamp = 0x01020304L,
                    counter = 0x0A0B,
                    amount = 12,
                    itemId = 3000,
                    tier = 2,
                    price = 0x0000000100000002L,
                    playerName = "Buyer",
                ),
            ),
            sellOffers = listOf(
                MarketOffer(
                    timestamp = 0x05060708L,
                    counter = 0x0C0D,
                    amount = 14,
                    price = 16,
                    playerName = "Seller",
                ),
            ),
            tier = 2,
        ) { if (it == 3000) 5 else 0 }

        val expected = expectedBytes {
            writeByte(0xF9)
            writeByte(MarketPacketParser.MARKET_REQUEST_ITEM_BROWSE)
            writeShortLE(3000)
            writeByte(2)
            writeIntLE(1)
            writeIntLE(0x01020304)
            writeShortLE(0x0A0B)
            writeShortLE(12)
            writeLongLE(0x0000000100000002L)
            writeTibiaString("Buyer")
            writeIntLE(1)
            writeIntLE(0x05060708)
            writeShortLE(0x0C0D)
            writeShortLE(14)
            writeLongLE(16)
            writeTibiaString("Seller")
        }

        assertThat(out.toByteArray()).containsExactly(*expected)
    }

    @Test
    fun `serializes an old protocol item browse without request byte or tier byte and truncates prices to 32 bits`() {
        val out = Unpooled.buffer()

        MarketWindowSerializer.writeMarketBrowseItem(
            out,
            itemId = 3000,
            buyOffers = listOf(
                MarketOffer(
                    timestamp = 0x01020304L,
                    counter = 0x0A0B,
                    amount = 12,
                    price = 0x0000000100000002L,
                    playerName = "Buyer",
                ),
            ),
            sellOffers = emptyList(),
            tier = 2,
            oldProtocol = true,
        ) { 5 }

        val expected = expectedBytes {
            writeByte(0xF9)
            writeShortLE(3000)
            writeIntLE(1)
            writeIntLE(0x01020304)
            writeShortLE(0x0A0B)
            writeShortLE(12)
            writeIntLE(0x00000002)
            writeTibiaString("Buyer")
            writeIntLE(0)
        }

        assertThat(out.toByteArray()).containsExactly(*expected)
    }

    @Test
    fun `serializes own offers with item ids and price widths per profile`() {
        val out = Unpooled.buffer()

        MarketWindowSerializer.writeMarketBrowseOwnOffers(
            out,
            buyOffers = listOf(
                MarketOffer(
                    timestamp = 1,
                    counter = 2,
                    amount = 4,
                    itemId = 1000,
                    tier = 3,
                    price = 5,
                ),
            ),
            sellOffers = listOf(
                MarketOffer(
                    timestamp = 6,
                    counter = 7,
                    amount = 8,
                    itemId = 2000,
                    tier = 0,
                    price = 9,
                ),
            ),
        ) { if (it == 1000) 5 else 0 }

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xF9,
                MarketPacketParser.MARKET_REQUEST_OWN_OFFERS,
                0x01, 0x00, 0x00, 0x00,
                0x01, 0x00, 0x00, 0x00, // timestamp 1
                0x02, 0x00, // counter 2
                0xE8, 0x03, // itemId 1000
                0x03, // tier 3
                0x04, 0x00, // amount 4
                0x05, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, // price 5
                0x01, 0x00, 0x00, 0x00,
                0x06, 0x00, 0x00, 0x00,
                0x07, 0x00,
                0xD0, 0x07,
                0x08, 0x00,
                0x09, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            )
        )
    }

    @Test
    fun `serializes own history with a per timestamp counter sequence`() {
        val out = Unpooled.buffer()

        MarketWindowSerializer.writeMarketBrowseOwnHistory(
            out,
            buyOffers = listOf(
                HistoryMarketOffer(100, 1000, 1, 2, 3, MarketOfferState.Accepted),
                HistoryMarketOffer(100, 1001, 0, 4, 5, MarketOfferState.Expired),
                HistoryMarketOffer(200, 1002, 0, 6, 7, MarketOfferState.Cancelled),
            ),
            sellOffers = listOf(
                HistoryMarketOffer(300, 1003, 0, 8, 9, MarketOfferState.Active),
            ),
        ) { if (it == 1000) 5 else 0 }

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xF9,
                MarketPacketParser.MARKET_REQUEST_OWN_HISTORY,
                0x03, 0x00, 0x00, 0x00,
                0x64, 0x00, 0x00, 0x00, 0x00, 0x00, // timestamp 100, counter 0
                0xE8, 0x03, 0x01, 0x02, 0x00,
                0x03, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x03,
                0x64, 0x00, 0x00, 0x00, 0x01, 0x00, // same timestamp, counter 1
                0xE9, 0x03, 0x04, 0x00,
                0x05, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x02,
                0xC8, 0x00, 0x00, 0x00, 0x00, 0x00,
                0xEA, 0x03, 0x06, 0x00,
                0x07, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x01,
                0x01, 0x00, 0x00, 0x00,
                0x2C, 0x01, 0x00, 0x00, 0x00, 0x00,
                0xEB, 0x03, 0x08, 0x00,
                0x09, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00,
            )
        )
    }

    @Test
    fun `caps each history side at 810 when the other side is full`() {
        val buys = (1..900).map {
            HistoryMarketOffer(it.toLong(), 1000, 0, 1, 1, MarketOfferState.Active)
        }
        val sells = (1..900).map {
            HistoryMarketOffer(it.toLong(), 2000, 0, 1, 1, MarketOfferState.Active)
        }
        val out = Unpooled.buffer()

        MarketWindowSerializer.writeMarketBrowseOwnHistory(out, buys, sells) { 0 }

        // header (2) + buy count (4) + 810 buy entries (19 each) + sell count (4) + 810 sell entries (19 each)
        assertThat(out.writerIndex()).isEqualTo(30_790)
        assertThat(out.getUnsignedIntLE(2)).isEqualTo(810)
        assertThat(out.getUnsignedIntLE(6 + 810 * 19)).isEqualTo(810)
    }

    @Test
    fun `old protocol own history keeps tier bytes and 64 bit sell prices`() {
        val out = Unpooled.buffer()

        MarketWindowSerializer.writeMarketBrowseOwnHistory(
            out,
            buyOffers = emptyList(),
            sellOffers = listOf(
                HistoryMarketOffer(1, 1000, 3, 2, 0x0000000100000003L, MarketOfferState.Active),
            ),
            oldProtocol = true,
        ) { if (it == 1000) 5 else 0 }

        val expected = expectedBytes {
            writeByte(0xF9)
            writeShortLE(MarketPacketParser.MARKET_REQUEST_OWN_HISTORY_OLD)
            writeIntLE(0)
            writeIntLE(1)
            writeIntLE(1)
            writeShortLE(0)
            writeShortLE(1000)
            writeByte(3)
            writeShortLE(2)
            writeLongLE(0x0000000100000003L)
            writeByte(0)
        }

        assertThat(out.toByteArray()).containsExactly(*expected)
    }

    @Test
    fun `serializes an accepted buy offer in the item browse frame`() {
        val out = Unpooled.buffer()

        MarketWindowSerializer.writeMarketAcceptOffer(
            out,
            MarketOfferEx(
                type = MarketOfferType.Buy,
                itemId = 3000,
                tier = 2,
                timestamp = 0x01020304L,
                counter = 0x0A0B,
                amount = 12,
                price = 0x0000000100000002L,
                playerName = "Seller",
            ),
        ) { if (it == 3000) 5 else 0 }

        val expected = expectedBytes {
            writeByte(0xF9)
            writeByte(MarketPacketParser.MARKET_REQUEST_ITEM_BROWSE)
            writeShortLE(3000)
            writeByte(2)
            writeIntLE(1)
            writeIntLE(0x01020304)
            writeShortLE(0x0A0B)
            writeShortLE(12)
            writeLongLE(0x0000000100000002L)
            writeTibiaString("Seller")
            writeIntLE(0)
        }

        assertThat(out.toByteArray()).containsExactly(*expected)
    }

    @Test
    fun `serializes an accepted sell offer as an empty buy list followed by one sell`() {
        val out = Unpooled.buffer()

        MarketWindowSerializer.writeMarketAcceptOffer(
            out,
            MarketOfferEx(
                type = MarketOfferType.Sell,
                itemId = 3000,
                tier = 2,
                timestamp = 7,
                counter = 8,
                amount = 9,
                price = 10,
                playerName = "Buyer",
            ),
        ) { 5 }

        val expected = expectedBytes {
            writeByte(0xF9)
            writeByte(MarketPacketParser.MARKET_REQUEST_ITEM_BROWSE)
            writeShortLE(3000)
            writeByte(2)
            writeIntLE(0)
            writeIntLE(1)
            writeIntLE(7)
            writeShortLE(8)
            writeShortLE(9)
            writeLongLE(10)
            writeTibiaString("Buyer")
        }

        assertThat(out.toByteArray()).containsExactly(*expected)
    }

    @Test
    fun `serializes a cancelled buy offer with a trailing empty sell list`() {
        val out = Unpooled.buffer()

        MarketWindowSerializer.writeMarketCancelOffer(
            out,
            MarketOfferEx(
                type = MarketOfferType.Buy,
                itemId = 1000,
                tier = 3,
                timestamp = 4,
                counter = 5,
                amount = 6,
                price = 7,
            ),
        ) { 5 }

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xF9, MarketPacketParser.MARKET_REQUEST_OWN_OFFERS,
                0x01, 0x00, 0x00, 0x00,
                0x04, 0x00, 0x00, 0x00,
                0x05, 0x00,
                0xE8, 0x03,
                0x03,
                0x06, 0x00,
                0x07, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00,
            )
        )
    }

    @Test
    fun `serializes a cancelled sell offer with no trailing sell list count`() {
        val out = Unpooled.buffer()

        MarketWindowSerializer.writeMarketCancelOffer(
            out,
            MarketOfferEx(
                type = MarketOfferType.Sell,
                itemId = 2000,
                tier = 0,
                timestamp = 4,
                counter = 5,
                amount = 6,
                price = 7,
            ),
        ) { 0 }

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xF9, MarketPacketParser.MARKET_REQUEST_OWN_OFFERS,
                0x00, 0x00, 0x00, 0x00,
                0x01, 0x00, 0x00, 0x00,
                0x04, 0x00, 0x00, 0x00,
                0x05, 0x00,
                0xD0, 0x07,
                0x06, 0x00,
                0x07, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            )
        )
    }

    @Test
    fun `serializes the old protocol own offers marker for cancel`() {
        val out = Unpooled.buffer()

        MarketWindowSerializer.writeMarketCancelOffer(
            out,
            MarketOfferEx(
                type = MarketOfferType.Sell,
                itemId = 2000,
                tier = 0,
                timestamp = 4,
                counter = 5,
                amount = 6,
                price = 7,
            ),
            oldProtocol = true,
        ) { 0 }

        val expected = expectedBytes {
            writeByte(0xF9)
            writeShortLE(MarketPacketParser.MARKET_REQUEST_OWN_OFFERS_OLD)
            writeIntLE(0)
            writeIntLE(1)
            writeIntLE(4)
            writeShortLE(5)
            writeShortLE(2000)
            writeShortLE(6)
            writeIntLE(7)
        }

        assertThat(out.toByteArray()).containsExactly(*expected)
    }

    @Test
    fun `serializes a minimal modern market detail with empty optional fields`() {
        val out = Unpooled.buffer()

        MarketWindowSerializer.writeMarketDetail(
            out,
            MarketDetail(itemId = 3000),
            clientVersion = 1510,
        )

        val expected = expectedBytes {
            writeByte(0xF8)
            writeShortLE(3000)
            repeat(26) { writeShortLE(0) }
            writeByte(0x00) // no purchase statistics
            writeByte(0x00) // no sale statistics
        }

        assertThat(out.toByteArray()).containsExactly(*expected)
    }

    @Test
    fun `serializes a modern market detail with tier fields and purchase statistics`() {
        val out = Unpooled.buffer()

        MarketWindowSerializer.writeMarketDetail(
            out,
            MarketDetail(
                itemId = 3000,
                tier = 2,
                upgradeClassification = 5,
                armor = "15",
                attack = "physical",
                description = "A test item.",
                weight = "10.0 oz",
                tierDescription = "2 (0.45% Onslaught)",
                purchaseStatistics = MarketStatistics(
                    numTransactions = 4,
                    totalPrice = 1000,
                    highestPrice = 400,
                    lowestPrice = 100,
                ),
            ),
            clientVersion = 1510,
        )

        val expected = expectedBytes {
            writeByte(0xF8)
            writeShortLE(3000)
            writeByte(2) // tier
            writeTibiaString("15")
            writeTibiaString("physical")
            writeShortLE(0) // container capacity
            writeShortLE(0) // defense
            writeTibiaString("A test item")
            writeShortLE(0) // decay
            writeShortLE(0) // absorb
            writeShortLE(0) // min req level
            writeShortLE(0) // min req magic level
            writeShortLE(0) // vocation
            writeShortLE(0) // rune spell name
            writeShortLE(0) // skills
            writeShortLE(0) // charges
            writeShortLE(0) // weapon name
            writeTibiaString("10.0 oz")
            writeShortLE(0) // augments
            writeShortLE(0) // imbuement slot
            writeShortLE(0) // magic shield capacity
            writeShortLE(0) // cleave percent
            writeShortLE(0) // damage reflection
            writeShortLE(0) // perfect shot
            writeShortLE(0) // upgrade classification
            writeShortLE(0) // elemental bond
            writeShortLE(0) // mantra
            writeShortLE(0) // imbuement effect
            writeTibiaString("2 (0.45% Onslaught)")
            writeByte(0x01)
            writeIntLE(4)
            writeLongLE(1000)
            writeLongLE(400)
            writeLongLE(100)
            writeByte(0x00) // no sale statistics
        }

        assertThat(out.toByteArray()).containsExactly(*expected)
    }

    @Test
    fun `serializes an old protocol market detail without the tier block but with imbuement slot`() {
        val out = Unpooled.buffer()

        MarketWindowSerializer.writeMarketDetail(
            out,
            MarketDetail(
                itemId = 3000,
                tier = 2,
                upgradeClassification = 5,
                armor = "15",
                imbuementSlot = "3",
                purchaseStatistics = MarketStatistics(
                    numTransactions = 1,
                    totalPrice = 0x0000000100000001L,
                    highestPrice = 2,
                    lowestPrice = 3,
                ),
            ),
            oldProtocol = true,
            clientVersion = 1510,
        )

        val expected = expectedBytes {
            writeByte(0xF8)
            writeShortLE(3000)
            writeTibiaString("15")
            writeShortLE(0) // attack
            writeShortLE(0) // container capacity
            writeShortLE(0) // defense
            writeShortLE(0) // description
            writeShortLE(0) // decay
            writeShortLE(0) // absorb
            writeShortLE(0) // min req level
            writeShortLE(0) // min req magic level
            writeShortLE(0) // vocation
            writeShortLE(0) // rune spell name
            writeShortLE(0) // skills
            writeShortLE(0) // charges
            writeShortLE(0) // weapon name
            writeShortLE(0) // weight
            writeTibiaString("3")
            writeByte(0x01)
            writeIntLE(1)
            writeIntLE(0xFFFFFFFF.toInt()) // total price clamped to uint32
            writeIntLE(2)
            writeIntLE(3)
            writeByte(0x00)
        }

        assertThat(out.toByteArray()).containsExactly(*expected)
    }

    @Test
    fun `clamps only the modern sale total price to the uint32 limit`() {
        val out = Unpooled.buffer()

        MarketWindowSerializer.writeMarketDetail(
            out,
            MarketDetail(
                itemId = 3000,
                saleStatistics = MarketStatistics(
                    numTransactions = 1,
                    totalPrice = 0x0000000100000001L,
                    highestPrice = 0x0000000100000002L,
                    lowestPrice = 0x0000000100000003L,
                ),
            ),
            clientVersion = 1510,
        )

        val expected = expectedBytes {
            writeByte(0xF8)
            writeShortLE(3000)
            repeat(26) { writeShortLE(0) }
            writeByte(0x00) // no purchase statistics
            writeByte(0x01)
            writeIntLE(1)
            writeLongLE(0xFFFFFFFFL) // clamped sale total
            writeLongLE(0x0000000100000002L)
            writeLongLE(0x0000000100000003L)
        }

        assertThat(out.toByteArray()).containsExactly(*expected)
    }

    @Test
    fun `writes the market leave frame through an embedded channel`() {
        val channel = EmbeddedChannel()
        try {
            val out = Unpooled.buffer()
            MarketWindowSerializer.writeMarketLeave(out)

            assertThat(channel.writeOutbound(out)).isTrue()
            val frame = channel.readOutbound<ByteBuf>()
            assertThat(frame.readUnsignedByte().toInt()).isEqualTo(0xF7)
        } finally {
            channel.finishAndReleaseAll()
        }
    }
}
