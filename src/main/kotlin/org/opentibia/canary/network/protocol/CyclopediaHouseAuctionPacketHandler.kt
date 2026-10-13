package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf

/**
 * Commands decoded from the cyclopedia house-auction opcode `0xAD`
 * (`ProtocolGame::parseCyclopediaHouseAuction`,
 * src/server/network/protocol/protocolgame.cpp:11958-12011).
 */
sealed interface HouseAuctionCommand {
    val opcode: Int
}

/** Action 0: `Game::playerCyclopediaHousesByTown` (game.cpp:12762). */
data class HousesByTownCommand(
    val townName: String,
) : HouseAuctionCommand {
    override val opcode: Int get() = CyclopediaHouseAuctionPacketHandler.OPCODE_CYCLOPEDIA_HOUSE_AUCTION
}

/** Action 1: `Game::playerCyclopediaHouseBid` (game.cpp:12802). */
data class HouseBidCommand(
    val houseId: Long,
    val bidValue: Long,
) : HouseAuctionCommand {
    override val opcode: Int get() = CyclopediaHouseAuctionPacketHandler.OPCODE_CYCLOPEDIA_HOUSE_AUCTION
}

/** Action 2: `Game::playerCyclopediaHouseMoveOut` (game.cpp:12867). */
data class HouseMoveOutCommand(
    val houseId: Long,
    val timestamp: Long,
) : HouseAuctionCommand {
    override val opcode: Int get() = CyclopediaHouseAuctionPacketHandler.OPCODE_CYCLOPEDIA_HOUSE_AUCTION
}

/** Action 3: `Game::playerCyclopediaHouseTransfer` (game.cpp:12925). */
data class HouseTransferCommand(
    val houseId: Long,
    val timestamp: Long,
    val newOwnerName: String,
    val bidValue: Long,
) : HouseAuctionCommand {
    override val opcode: Int get() = CyclopediaHouseAuctionPacketHandler.OPCODE_CYCLOPEDIA_HOUSE_AUCTION
}

/** Action 4: `Game::playerCyclopediaHouseCancelMoveOut` (game.cpp:12896). */
data class HouseCancelMoveOutCommand(
    val houseId: Long,
) : HouseAuctionCommand {
    override val opcode: Int get() = CyclopediaHouseAuctionPacketHandler.OPCODE_CYCLOPEDIA_HOUSE_AUCTION
}

/** Action 5: `Game::playerCyclopediaHouseCancelTransfer`. */
data class HouseCancelTransferCommand(
    val houseId: Long,
) : HouseAuctionCommand {
    override val opcode: Int get() = CyclopediaHouseAuctionPacketHandler.OPCODE_CYCLOPEDIA_HOUSE_AUCTION
}

/** Action 6: `Game::playerCyclopediaHouseAcceptTransfer`. */
data class HouseAcceptTransferCommand(
    val houseId: Long,
) : HouseAuctionCommand {
    override val opcode: Int get() = CyclopediaHouseAuctionPacketHandler.OPCODE_CYCLOPEDIA_HOUSE_AUCTION
}

/** Action 7: `Game::playerCyclopediaHouseRejectTransfer`. */
data class HouseRejectTransferCommand(
    val houseId: Long,
) : HouseAuctionCommand {
    override val opcode: Int get() = CyclopediaHouseAuctionPacketHandler.OPCODE_CYCLOPEDIA_HOUSE_AUCTION
}

/**
 * An action byte outside 0..7; the C++ `switch` (protocolgame.cpp:11964-12010)
 * has no default, so only the action byte is consumed.
 */
data class HouseUnknownActionCommand(
    val action: Int,
) : HouseAuctionCommand {
    override val opcode: Int get() = CyclopediaHouseAuctionPacketHandler.OPCODE_CYCLOPEDIA_HOUSE_AUCTION
}

/**
 * Decoder for `0xAD`. Every multi-byte field is Little-Endian, matching
 * `NetworkMessage::get<T>()`.
 */
object CyclopediaHouseAuctionPacketHandler {

    /** protocolgame.cpp:1947-1949 dispatches this opcode to `parseCyclopediaHouseAuction`. */
    const val OPCODE_CYCLOPEDIA_HOUSE_AUCTION: Int = 0xAD

    fun parse(buffer: ByteBuf): HouseAuctionCommand? {
        val action = readByte(buffer) ?: return null

        return when (action) {
            0 -> readString(buffer)?.let { HousesByTownCommand(it) }

            1 -> {
                val houseId = readInt(buffer) ?: return null
                val bidValue = readLong(buffer) ?: return null
                HouseBidCommand(houseId, bidValue)
            }

            2 -> {
                val houseId = readInt(buffer) ?: return null
                val timestamp = readInt(buffer) ?: return null
                HouseMoveOutCommand(houseId, timestamp)
            }

            3 -> {
                val houseId = readInt(buffer) ?: return null
                val timestamp = readInt(buffer) ?: return null
                val newOwnerName = readString(buffer) ?: return null
                val bidValue = readLong(buffer) ?: return null
                HouseTransferCommand(houseId, timestamp, newOwnerName, bidValue)
            }

            4 -> readInt(buffer)?.let { HouseCancelMoveOutCommand(it) }
            5 -> readInt(buffer)?.let { HouseCancelTransferCommand(it) }
            6 -> readInt(buffer)?.let { HouseAcceptTransferCommand(it) }
            7 -> readInt(buffer)?.let { HouseRejectTransferCommand(it) }
            else -> HouseUnknownActionCommand(action)
        }
    }

    private fun readByte(buffer: ByteBuf): Int? =
        if (buffer.readableBytes() < 1) null else buffer.readUnsignedByte().toInt()

    private fun readInt(buffer: ByteBuf): Long? =
        if (buffer.readableBytes() < 4) null else buffer.readUnsignedIntLE()

    private fun readLong(buffer: ByteBuf): Long? =
        if (buffer.readableBytes() < 8) null else buffer.readLongLE()

    private fun readString(buffer: ByteBuf): String? {
        if (buffer.readableBytes() < 2) return null
        val length = buffer.readUnsignedShortLE()
        if (buffer.readableBytes() < length) return null

        val bytes = ByteArray(length)
        buffer.readBytes(bytes)
        return String(bytes, Charsets.UTF_8)
    }
}
