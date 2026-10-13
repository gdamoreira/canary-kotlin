package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class HouseWindowWriterTest {

    private fun ByteBuf.toByteArray(): ByteArray {
        val bytes = ByteArray(readableBytes())
        readBytes(bytes)
        return bytes
    }

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun `writes house window 0x97 with the window text id and text`() {
        val out = Unpooled.buffer()

        HouseWindowWriter.writeHouseWindow(out, windowTextId = 0x12345678L, text = "Rent")

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x97,
                0x00,
                0x78, 0x56, 0x34, 0x12,
                0x04, 0x00, 0x52, 0x65, 0x6E, 0x74,
            )
        )
    }

    @Test
    fun `writes house auction message 0xC3 with the success byte for a successful bid`() {
        val out = Unpooled.buffer()

        HouseWindowWriter.writeHouseAuctionMessage(
            out,
            houseId = 0x2AL,
            type = HouseAuctionType.Bid,
            index = 3,
            bidSuccess = true,
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xC3,
                0x2A, 0x00, 0x00, 0x00,
                0x01,
                0x00,
                0x03,
            )
        )
    }

    @Test
    fun `writes house auction message 0xC3 without the success byte when the bid failed`() {
        val out = Unpooled.buffer()

        HouseWindowWriter.writeHouseAuctionMessage(
            out,
            houseId = 7L,
            type = HouseAuctionType.Bid,
            index = 1,
            bidSuccess = false,
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xC3,
                0x07, 0x00, 0x00, 0x00,
                0x01,
                0x01,
            )
        )
    }

    @Test
    fun `writes non bid house auction messages without the success byte even on success`() {
        val out = Unpooled.buffer()

        HouseWindowWriter.writeHouseAuctionMessage(
            out,
            houseId = 7L,
            type = HouseAuctionType.Transfer,
            index = 1,
            bidSuccess = true,
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xC3,
                0x07, 0x00, 0x00, 0x00,
                0x03,
                0x01,
            )
        )
    }

    @Test
    fun `writes an empty cyclopedia house list 0xC7 with a zero count`() {
        val out = Unpooled.buffer()

        HouseWindowWriter.writeCyclopediaHouseList(out, emptyList(), playerName = "Alice")

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xC7,
                0x00, 0x00,
            )
        )
    }

    @Test
    fun `writes an available house where the player is the bidder including the holder limit`() {
        val out = Unpooled.buffer()

        HouseWindowWriter.writeCyclopediaHouseList(
            out,
            listOf(
                CyclopediaHouseEntry(
                    clientId = 7L,
                    state = CyclopediaHouseState.Available,
                    bidderName = "Alice",
                    bidEndDate = 0x11223344L,
                    highestBid = 100L,
                    bidHolderLimit = 200L,
                ),
            ),
            playerName = "Alice",
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xC7,
                0x01, 0x00,
                0x07, 0x00, 0x00, 0x00,
                0x01,
                0x00,
                0x05, 0x00, 0x41, 0x6C, 0x69, 0x63, 0x65,
                0x01,
                0x00,
                0x44, 0x33, 0x22, 0x11,
                0x64, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0xC8, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            )
        )
    }

    @Test
    fun `writes an available house with no bidder and the bid error byte`() {
        val out = Unpooled.buffer()

        HouseWindowWriter.writeCyclopediaHouseList(
            out,
            listOf(
                CyclopediaHouseEntry(
                    clientId = 8L,
                    state = CyclopediaHouseState.Available,
                    bidError = BidErrorMessage.Rookgaard,
                ),
            ),
            playerName = "Alice",
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xC7,
                0x01, 0x00,
                0x08, 0x00, 0x00, 0x00,
                0x01,
                0x00,
                0x00, 0x00,
                0x00,
                0x03,
            )
        )
    }

    @Test
    fun `writes an available house where another player is the bidder without the holder limit`() {
        val out = Unpooled.buffer()

        HouseWindowWriter.writeCyclopediaHouseList(
            out,
            listOf(
                CyclopediaHouseEntry(
                    clientId = 9L,
                    state = CyclopediaHouseState.Available,
                    bidderName = "Bob",
                    bidEndDate = 0x05060708L,
                    highestBid = 10L,
                    bidError = BidErrorMessage.Premium,
                ),
            ),
            playerName = "Alice",
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xC7,
                0x01, 0x00,
                0x09, 0x00, 0x00, 0x00,
                0x01,
                0x00,
                0x03, 0x00, 0x42, 0x6F, 0x62,
                0x00,
                0x05,
                0x08, 0x07, 0x06, 0x05,
                0x0A, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            )
        )
    }

    @Test
    fun `writes a rented house owned by the player with the two placeholder bytes`() {
        val out = Unpooled.buffer()

        HouseWindowWriter.writeCyclopediaHouseList(
            out,
            listOf(
                CyclopediaHouseEntry(
                    clientId = 10L,
                    state = CyclopediaHouseState.Rented,
                    ownerName = "Alice",
                    paidUntil = 0x55667788L,
                ),
            ),
            playerName = "Alice",
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xC7,
                0x01, 0x00,
                0x0A, 0x00, 0x00, 0x00,
                0x01,
                0x02,
                0x05, 0x00, 0x41, 0x6C, 0x69, 0x63, 0x65,
                0x88, 0x77, 0x66, 0x55,
                0x01,
                0x00,
                0x00,
            )
        )
    }

    @Test
    fun `writes a rented house owned by another player without placeholder bytes`() {
        val out = Unpooled.buffer()

        HouseWindowWriter.writeCyclopediaHouseList(
            out,
            listOf(
                CyclopediaHouseEntry(
                    clientId = 10L,
                    state = CyclopediaHouseState.Rented,
                    ownerName = "Bob",
                    paidUntil = 0x55667788L,
                ),
            ),
            playerName = "Alice",
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xC7,
                0x01, 0x00,
                0x0A, 0x00, 0x00, 0x00,
                0x01,
                0x02,
                0x03, 0x00, 0x42, 0x6F, 0x62,
                0x88, 0x77, 0x66, 0x55,
                0x00,
            )
        )
    }

    @Test
    fun `writes a transfer house where the player is the owner and adds the cancel error byte`() {
        val out = Unpooled.buffer()

        HouseWindowWriter.writeCyclopediaHouseList(
            out,
            listOf(
                CyclopediaHouseEntry(
                    clientId = 11L,
                    state = CyclopediaHouseState.Transfer,
                    ownerName = "Alice",
                    paidUntil = 0x01020304L,
                    bidEndDate = 0x05060708L,
                    bidderName = "Bob",
                    internalBid = 0x1122334455667788L,
                ),
            ),
            playerName = "Alice",
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xC7,
                0x01, 0x00,
                0x0B, 0x00, 0x00, 0x00,
                0x01,
                0x03,
                0x05, 0x00, 0x41, 0x6C, 0x69, 0x63, 0x65,
                0x04, 0x03, 0x02, 0x01,
                0x01,
                0x00,
                0x00,
                0x08, 0x07, 0x06, 0x05,
                0x03, 0x00, 0x42, 0x6F, 0x62,
                0x00,
                0x88, 0x77, 0x66, 0x55, 0x44, 0x33, 0x22, 0x11,
                0x00,
                0x00,
            )
        )
    }

    @Test
    fun `writes a transfer house where the player is the new owner with accept and reject error bytes`() {
        val out = Unpooled.buffer()

        HouseWindowWriter.writeCyclopediaHouseList(
            out,
            listOf(
                CyclopediaHouseEntry(
                    clientId = 12L,
                    state = CyclopediaHouseState.Transfer,
                    ownerName = "Carol",
                    paidUntil = 0x01020304L,
                    bidEndDate = 0x05060708L,
                    bidderName = "Alice",
                    internalBid = 0x64L,
                    acceptTransferError = AcceptTransferErrorMessage.AlreadyAccepted,
                ),
            ),
            playerName = "Alice",
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xC7,
                0x01, 0x00,
                0x0C, 0x00, 0x00, 0x00,
                0x01,
                0x03,
                0x05, 0x00, 0x43, 0x61, 0x72, 0x6F, 0x6C,
                0x04, 0x03, 0x02, 0x01,
                0x00,
                0x08, 0x07, 0x06, 0x05,
                0x05, 0x00, 0x41, 0x6C, 0x69, 0x63, 0x65,
                0x00,
                0x64, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x01,
                0x07,
                0x00,
            )
        )
    }

    @Test
    fun `writes a transfer house where the player is neither owner nor bidder`() {
        val out = Unpooled.buffer()

        HouseWindowWriter.writeCyclopediaHouseList(
            out,
            listOf(
                CyclopediaHouseEntry(
                    clientId = 12L,
                    state = CyclopediaHouseState.Transfer,
                    ownerName = "Carol",
                    paidUntil = 0x01020304L,
                    bidEndDate = 0x05060708L,
                    bidderName = "Dave",
                    internalBid = 0x64L,
                ),
            ),
            playerName = "Alice",
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xC7,
                0x01, 0x00,
                0x0C, 0x00, 0x00, 0x00,
                0x01,
                0x03,
                0x05, 0x00, 0x43, 0x61, 0x72, 0x6F, 0x6C,
                0x04, 0x03, 0x02, 0x01,
                0x00,
                0x08, 0x07, 0x06, 0x05,
                0x04, 0x00, 0x44, 0x61, 0x76, 0x65,
                0x00,
                0x64, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00,
            )
        )
    }

    @Test
    fun `writes a move out house owned by the player with the placeholders and bid end byte`() {
        val out = Unpooled.buffer()

        HouseWindowWriter.writeCyclopediaHouseList(
            out,
            listOf(
                CyclopediaHouseEntry(
                    clientId = 13L,
                    state = CyclopediaHouseState.MoveOut,
                    ownerName = "Alice",
                    paidUntil = 0x01020304L,
                    bidEndDate = 0x05060708L,
                ),
            ),
            playerName = "Alice",
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xC7,
                0x01, 0x00,
                0x0D, 0x00, 0x00, 0x00,
                0x01,
                0x04,
                0x05, 0x00, 0x41, 0x6C, 0x69, 0x63, 0x65,
                0x04, 0x03, 0x02, 0x01,
                0x01,
                0x00,
                0x00,
                0x08, 0x07, 0x06, 0x05,
                0x00,
            )
        )
    }

    @Test
    fun `writes a move out house owned by another player with only the bid end date`() {
        val out = Unpooled.buffer()

        HouseWindowWriter.writeCyclopediaHouseList(
            out,
            listOf(
                CyclopediaHouseEntry(
                    clientId = 13L,
                    state = CyclopediaHouseState.MoveOut,
                    ownerName = "Bob",
                    paidUntil = 0x01020304L,
                    bidEndDate = 0x05060708L,
                ),
            ),
            playerName = "Alice",
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xC7,
                0x01, 0x00,
                0x0D, 0x00, 0x00, 0x00,
                0x01,
                0x04,
                0x03, 0x00, 0x42, 0x6F, 0x62,
                0x04, 0x03, 0x02, 0x01,
                0x00,
                0x08, 0x07, 0x06, 0x05,
            )
        )
    }

    @Test
    fun `orders the cyclopedia house list by client id`() {
        val out = Unpooled.buffer()

        HouseWindowWriter.writeCyclopediaHouseList(
            out,
            listOf(
                CyclopediaHouseEntry(clientId = 9L, state = CyclopediaHouseState.Available),
                CyclopediaHouseEntry(clientId = 3L, state = CyclopediaHouseState.Available),
            ),
            playerName = "Alice",
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xC7,
                0x02, 0x00,
                0x03, 0x00, 0x00, 0x00,
                0x01,
                0x00,
                0x00, 0x00,
                0x00,
                0x00,
                0x09, 0x00, 0x00, 0x00,
                0x01,
                0x00,
                0x00, 0x00,
                0x00,
                0x00,
            )
        )
    }

    @Test
    fun `writes the cyclopedia house list frame through an embedded channel`() {
        val channel = EmbeddedChannel()
        try {
            val out = Unpooled.buffer()
            HouseWindowWriter.writeCyclopediaHouseList(
                out,
                listOf(CyclopediaHouseEntry(clientId = 1L, state = CyclopediaHouseState.Available)),
                playerName = "Alice",
            )

            assertThat(channel.writeOutbound(out)).isTrue()
            val frame = channel.readOutbound<ByteBuf>()
            assertThat(frame.readUnsignedByte().toInt()).isEqualTo(0xC7)
            assertThat(frame.readUnsignedShortLE()).isEqualTo(1)
            assertThat(frame.readIntLE()).isEqualTo(1)
            frame.release()
        } finally {
            channel.finishAndReleaseAll()
        }
    }
}
