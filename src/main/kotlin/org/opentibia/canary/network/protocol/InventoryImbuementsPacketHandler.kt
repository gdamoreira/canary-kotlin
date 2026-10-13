package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf

/**
 * Inventory imbuements tracker report (client opcode `0x60`), mirroring
 * `ProtocolGame::parseInventoryImbuements` (protocolgame.cpp:10727-10734): the
 * byte is stored into `Player::imbuementTrackerWindowOpen` as a `bool`
 * (nonzero is open).
 */
data class InventoryImbuementsCommand(
    val isTrackerOpen: Boolean,
)

/**
 * Decoder for `ProtocolGame::parseInventoryImbuements`
 * (protocolgame.cpp:10727).
 *
 * PARITY GAP: `Game::playerRequestInventoryImbuements` (game.cpp:11487-11517)
 * needs the unported `Player::imbuementTrackerWindowOpen` flag and the
 * `ProtocolGame::sendInventoryImbuements` sender, so the decoded command is
 * only the confinement boundary.
 */
object InventoryImbuementsPacketHandler {

    /** Client opcode for `ProtocolGame::parseInventoryImbuements`. */
    const val OPCODE_INVENTORY_IMBUEMENTS: Int = 0x60

    /**
     * 0x60: reads the tracker window byte.
     *
     * @return the decoded command, or `null` when the payload is truncated.
     */
    fun parse(buffer: ByteBuf): InventoryImbuementsCommand? {
        if (buffer.readableBytes() < 1) {
            return null
        }
        return InventoryImbuementsCommand(isTrackerOpen = buffer.readUnsignedByte().toInt() != 0)
    }
}
