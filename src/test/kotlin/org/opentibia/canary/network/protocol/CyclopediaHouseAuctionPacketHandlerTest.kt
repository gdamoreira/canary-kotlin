package org.opentibia.canary.network.protocol

import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.network.message.writeTibiaString

/**
 * Decode tests for `0xAD` `ProtocolGame::parseCyclopediaHouseAuction`
 * (src/server/network/protocol/protocolgame.cpp:11958-12011).
 */
class CyclopediaHouseAuctionPacketHandlerTest {

    @Test
    fun `decodes action 0 houses by town`() {
        val payload = Unpooled.buffer()
        payload.writeByte(0)
        payload.writeTibiaString("Thais")

        val command = CyclopediaHouseAuctionPacketHandler.parse(payload)

        assertThat(command).isEqualTo(HousesByTownCommand("Thais"))
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `decodes action 1 house bid with u32 and u64`() {
        val payload = Unpooled.buffer()
        payload.writeByte(1)
        payload.writeIntLE(0x1234)
        payload.writeLongLE(0x0102030405060708L)

        val command = CyclopediaHouseAuctionPacketHandler.parse(payload)

        assertThat(command).isEqualTo(HouseBidCommand(houseId = 0x1234, bidValue = 0x0102030405060708L))
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `decodes action 2 house move out with two u32 fields`() {
        val payload = Unpooled.buffer()
        payload.writeByte(2)
        payload.writeIntLE(7)
        payload.writeIntLE(1_700_000_000)

        val command = CyclopediaHouseAuctionPacketHandler.parse(payload)

        assertThat(command).isEqualTo(HouseMoveOutCommand(houseId = 7, timestamp = 1_700_000_000))
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `decodes action 3 house transfer with u32 u32 string u64`() {
        val payload = Unpooled.buffer()
        payload.writeByte(3)
        payload.writeIntLE(9)
        payload.writeIntLE(1_700_000_000)
        payload.writeTibiaString("Buyer")
        payload.writeLongLE(99_000L)

        val command = CyclopediaHouseAuctionPacketHandler.parse(payload)

        assertThat(command).isEqualTo(
            HouseTransferCommand(
                houseId = 9,
                timestamp = 1_700_000_000,
                newOwnerName = "Buyer",
                bidValue = 99_000L,
            ),
        )
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `decodes the u32-only transfer lifecycle actions 4 to 7`() {
        val cases = listOf(
            Triple(4, 11L, HouseCancelMoveOutCommand(11)),
            Triple(5, 12L, HouseCancelTransferCommand(12)),
            Triple(6, 13L, HouseAcceptTransferCommand(13)),
            Triple(7, 14L, HouseRejectTransferCommand(14)),
        )

        for ((action, houseId, command) in cases) {
            val payload = Unpooled.buffer()
            payload.writeByte(action)
            payload.writeIntLE(houseId.toInt())

            assertThat(CyclopediaHouseAuctionPacketHandler.parse(payload)).isEqualTo(command)
            assertThat(payload.readableBytes()).isZero()
        }
    }

    @Test
    fun `consumes only the action byte for an unknown house action`() {
        val payload = Unpooled.buffer()
        payload.writeByte(9)

        val command = CyclopediaHouseAuctionPacketHandler.parse(payload)

        assertThat(command).isEqualTo(HouseUnknownActionCommand(9))
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `returns null when the action implies fields that are truncated`() {
        val missingBid = Unpooled.buffer()
        missingBid.writeByte(1)
        missingBid.writeIntLE(1)
        assertThat(CyclopediaHouseAuctionPacketHandler.parse(missingBid)).isNull()

        val missingTown = Unpooled.buffer()
        missingTown.writeByte(0)
        missingTown.writeShortLE(5)
        assertThat(CyclopediaHouseAuctionPacketHandler.parse(missingTown)).isNull()

        assertThat(CyclopediaHouseAuctionPacketHandler.parse(Unpooled.buffer())).isNull()
    }
}
