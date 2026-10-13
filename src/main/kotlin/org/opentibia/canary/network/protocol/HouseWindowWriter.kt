package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import org.opentibia.canary.network.message.writeTibiaString

/** `CyclopediaHouseState` (`src/enums/player_cyclopedia.hpp:64-69`). */
enum class CyclopediaHouseState(val value: Int) {
    Available(0),
    Rented(2),
    Transfer(3),
    MoveOut(4),
}

/** `HouseAuctionType` (`src/enums/player_cyclopedia.hpp:71-79`). */
enum class HouseAuctionType(val value: Int) {
    Bid(1),
    MoveOut(2),
    Transfer(3),
    CancelMoveOut(4),
    CancelTransfer(5),
    AcceptTransfer(6),
    RejectTransfer(7),
}

/** `BidErrorMessage` (`src/enums/player_cyclopedia.hpp:81-90`). */
enum class BidErrorMessage(val value: Int) {
    NoError(0),
    Rookgaard(3),
    Premium(5),
    Guildhall(6),
    OnlyOneBid(7),
    NotEnoughMoney(17),
    NotEnoughGuildMoney(21),
    Internal(24),
}

/** `AcceptTransferErrorMessage` (`src/enums/player_cyclopedia.hpp:111-120`). */
enum class AcceptTransferErrorMessage(val value: Int) {
    Success(0),
    NotNewOwner(2),
    AlreadyBid(3),
    AlreadyAccepted(7),
    Rookgaard(8),
    Premium(9),
    Frozen(15),
    Internal(19),
}

/**
 * One `HouseMap` entry (`src/map/house/house.hpp:379`) as serialized by
 * `ProtocolGame::sendCyclopediaHouseList` (protocolgame.cpp:12013-12095).
 *
 * [clientId] is the map key and the wire id. [ownerName] is Canary's
 * `IOLoginData::getNameByGuid(house->getOwner())` and [bidError] /
 * [acceptTransferError] are `Player::canBidHouse` /
 * `Player::canAcceptTransferHouse` (player.hpp:1125/1127) resolved by the
 * caller, since they require live engine state.
 */
data class CyclopediaHouseEntry(
    val clientId: Long,
    val state: CyclopediaHouseState,
    val bidderName: String = "",
    val bidEndDate: Long = 0,
    val highestBid: Long = 0,
    val bidHolderLimit: Long = 0,
    val ownerName: String = "",
    val paidUntil: Long = 0,
    val internalBid: Long = 0,
    val bidError: BidErrorMessage = BidErrorMessage.NoError,
    val acceptTransferError: AcceptTransferErrorMessage = AcceptTransferErrorMessage.Success,
)

/**
 * Outbound house-window and cyclopedia house-auction serialization, ported
 * from Canary src/server/network/protocol/protocolgame.cpp:
 *
 *  - `ProtocolGame::sendHouseWindow`          (0x97, protocolgame.cpp:9018-9025)
 *  - `ProtocolGame::sendHouseAuctionMessage`  (0xC3, protocolgame.cpp:12097-12110)
 *  - `ProtocolGame::sendCyclopediaHouseList`  (0xC7, protocolgame.cpp:12013-12095)
 *
 * `sendHousesInfo` (0xC6) lives in `SocialWindowWriter`. Canary iterates the
 * `HouseMap` (a `std::map` keyed by client id) in ascending client-id order;
 * [writeCyclopediaHouseList] sorts by [CyclopediaHouseEntry.clientId] to
 * reproduce that on the wire. Every multi-byte field is Little-Endian and the
 * `uint32_t` fields truncate the 64-bit caller values exactly like the C++
 * casts.
 */
object HouseWindowWriter {

    const val PACKET_HOUSE_WINDOW: Int = 0x97
    const val PACKET_HOUSE_AUCTION_MESSAGE: Int = 0xC3
    const val PACKET_CYCLOPEDIA_HOUSE_LIST: Int = 0xC7

    /** `sendHouseWindow` (protocolgame.cpp:9018-9025). */
    fun writeHouseWindow(out: ByteBuf, windowTextId: Long, text: String) {
        out.writeByte(PACKET_HOUSE_WINDOW)
        out.writeByte(0x00)
        out.writeIntLE(windowTextId.toInt())
        out.writeTibiaString(text)
    }

    /**
     * `sendHouseAuctionMessage` (protocolgame.cpp:12097-12110). A successful
     * `Bid` (type value 1) appends an extra zero byte before the error index.
     */
    fun writeHouseAuctionMessage(
        out: ByteBuf,
        houseId: Long,
        type: HouseAuctionType,
        index: Int,
        bidSuccess: Boolean = false,
    ) {
        out.writeByte(PACKET_HOUSE_AUCTION_MESSAGE)
        out.writeIntLE(houseId.toInt())
        out.writeByte(type.value)
        if (bidSuccess && type == HouseAuctionType.Bid) {
            out.writeByte(0x00)
        }
        out.writeByte(index)
    }

    /**
     * `sendCyclopediaHouseList` (protocolgame.cpp:12013-12095). The bidder and
     * owner flags are name comparisons against [playerName], exactly like the
     * C++ `==` / `compare` checks.
     */
    fun writeCyclopediaHouseList(out: ByteBuf, houses: List<CyclopediaHouseEntry>, playerName: String) {
        out.writeByte(PACKET_CYCLOPEDIA_HOUSE_LIST)
        out.writeShortLE(houses.size)

        for (house in houses.sortedBy { it.clientId }) {
            out.writeIntLE(house.clientId.toInt())
            out.writeByte(0x01)
            out.writeByte(house.state.value)

            when (house.state) {
                CyclopediaHouseState.Available -> {
                    val bidder = house.bidderName == playerName
                    out.writeTibiaString(house.bidderName)
                    out.writeByte(if (bidder) 0x01 else 0x00)
                    out.writeByte(house.bidError.value)

                    if (house.bidderName.isNotEmpty()) {
                        out.writeIntLE(house.bidEndDate.toInt())
                        out.writeLongLE(house.highestBid)
                        if (bidder) {
                            out.writeLongLE(house.bidHolderLimit)
                        }
                    }
                }

                CyclopediaHouseState.Rented -> {
                    out.writeTibiaString(house.ownerName)
                    out.writeIntLE(house.paidUntil.toInt())

                    val rented = house.ownerName == playerName
                    out.writeByte(if (rented) 0x01 else 0x00)
                    if (rented) {
                        out.writeByte(0x00)
                        out.writeByte(0x00)
                    }
                }

                CyclopediaHouseState.Transfer -> {
                    out.writeTibiaString(house.ownerName)
                    out.writeIntLE(house.paidUntil.toInt())

                    val isOwner = house.ownerName == playerName
                    out.writeByte(if (isOwner) 0x01 else 0x00)
                    if (isOwner) {
                        out.writeByte(0x00)
                        out.writeByte(0x00)
                    }

                    out.writeIntLE(house.bidEndDate.toInt())
                    out.writeTibiaString(house.bidderName)
                    out.writeByte(0x00)
                    out.writeLongLE(house.internalBid)

                    val isNewOwner = playerName == house.bidderName
                    out.writeByte(if (isNewOwner) 0x01 else 0x00)
                    if (isNewOwner) {
                        out.writeByte(house.acceptTransferError.value)
                        out.writeByte(0x00)
                    }

                    if (isOwner) {
                        out.writeByte(0x00)
                    }
                }

                CyclopediaHouseState.MoveOut -> {
                    out.writeTibiaString(house.ownerName)
                    out.writeIntLE(house.paidUntil.toInt())

                    val isOwner = house.ownerName == playerName
                    out.writeByte(if (isOwner) 0x01 else 0x00)
                    if (isOwner) {
                        out.writeByte(0x00)
                        out.writeByte(0x00)
                        out.writeIntLE(house.bidEndDate.toInt())
                        out.writeByte(0x00)
                    } else {
                        out.writeIntLE(house.bidEndDate.toInt())
                    }
                }
            }
        }
    }
}
