package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import org.opentibia.canary.map.Position

/**
 * Self-contained commands produced by the imbuement client opcodes, mirroring
 * the Canary `ProtocolGame` handlers (src/server/network/protocol/protocolgame.cpp).
 */
sealed interface ImbuementCommand {
    val opcode: Int
}

/**
 * Imbuement window action (client opcode `0xB2`), mirroring
 * `ProtocolGame::parseImbuementAction` (protocolgame.cpp:3065-3128): the action
 * byte is Canary's `ImbuementAction` (`0` open, `1` pick item, `2` scroll).
 *
 * On the current protocol payload only `PickItem` carries operands — a
 * `Position`, the `uint16_t` item id and the stack position byte; every other
 * action decodes with all three fields `null`.
 */
data class ImbuementActionCommand(
    val action: Int,
    val position: Position?,
    val itemId: Int?,
    val stackPosition: Int?,
) : ImbuementCommand {
    override val opcode: Int get() = ImbuementPacketHandler.OPCODE_IMBUEMENT_ACTION
}

/**
 * Apply an imbuement to an item slot (client opcode `0xD5`), mirroring
 * `ProtocolGame::parseApplyImbuement` (protocolgame.cpp:2538-2547): the slot
 * byte and, on the current protocol payload, a `uint32_t` imbuement id.
 *
 * Canary drops ids above `uint16_t` max before calling
 * `Game::playerApplyImbuement`; the decoded value is preserved here so the
 * caller can apply that gate.
 */
data class ApplyImbuementCommand(
    val slot: Int,
    val imbuementId: Long,
) : ImbuementCommand {
    override val opcode: Int get() = ImbuementPacketHandler.OPCODE_APPLY_IMBUEMENT
}

/**
 * Clear the imbuement of an item slot (client opcode `0xD6`), mirroring
 * `ProtocolGame::parseClearImbuement` (protocolgame.cpp:2549-2552): the slot byte.
 */
data class ClearImbuementCommand(
    val slot: Int,
) : ImbuementCommand {
    override val opcode: Int get() = ImbuementPacketHandler.OPCODE_CLEAR_IMBUEMENT
}

/**
 * Close the imbuement window (client opcode `0xD7`). Canary reads no payload
 * (`ProtocolGame::parseCloseImbuementWindow`, protocolgame.cpp:2554-2556).
 */
object CloseImbuementWindowCommand : ImbuementCommand {
    override val opcode: Int get() = ImbuementPacketHandler.OPCODE_CLOSE_IMBUEMENT_WINDOW
}

/**
 * Decoder for the imbuement client opcodes on the current protocol payload
 * (`ProtocolFeature::CurrentPayload`, the profile this port targets). The
 * legacy pre-11 payload variants are not modeled (PARITY GAP).
 *
 *  - `0xB2` `ProtocolGame::parseImbuementAction`      (protocolgame.cpp:3065)
 *  - `0xD5` `ProtocolGame::parseApplyImbuement`       (protocolgame.cpp:2538)
 *  - `0xD6` `ProtocolGame::parseClearImbuement`       (protocolgame.cpp:2549)
 *  - `0xD7` `ProtocolGame::parseCloseImbuementWindow` (protocolgame.cpp:2554)
 */
object ImbuementPacketHandler {

    const val OPCODE_IMBUEMENT_ACTION: Int = 0xB2
    const val OPCODE_APPLY_IMBUEMENT: Int = 0xD5
    const val OPCODE_CLEAR_IMBUEMENT: Int = 0xD6
    const val OPCODE_CLOSE_IMBUEMENT_WINDOW: Int = 0xD7

    /** `ImbuementAction::Open` (src/enums/imbuement.hpp:16). */
    const val IMBUEMENT_ACTION_OPEN: Int = 0

    /** `ImbuementAction::PickItem` (src/enums/imbuement.hpp:16). */
    const val IMBUEMENT_ACTION_PICK_ITEM: Int = 1

    /** `ImbuementAction::Scroll` (src/enums/imbuement.hpp:16). */
    const val IMBUEMENT_ACTION_SCROLL: Int = 2

    fun parse(opcode: Int, buffer: ByteBuf): ImbuementCommand? = when (opcode) {
        OPCODE_IMBUEMENT_ACTION -> parseImbuementAction(buffer)
        OPCODE_APPLY_IMBUEMENT -> parseApplyImbuement(buffer)
        OPCODE_CLEAR_IMBUEMENT -> parseClearImbuement(buffer)
        OPCODE_CLOSE_IMBUEMENT_WINDOW -> CloseImbuementWindowCommand
        else -> null
    }

    /**
     * 0xB2: reads the action byte and, for `PickItem`, the `Position`, item id
     * and stack position. Any other action consumes nothing further.
     *
     * @return the decoded command, or `null` when the payload is truncated.
     */
    fun parseImbuementAction(buffer: ByteBuf): ImbuementActionCommand? {
        val action = readByte(buffer) ?: return null
        if (action != IMBUEMENT_ACTION_PICK_ITEM) {
            return ImbuementActionCommand(action, position = null, itemId = null, stackPosition = null)
        }

        val position = readPosition(buffer) ?: return null
        val itemId = readShort(buffer) ?: return null
        val stackPosition = readByte(buffer) ?: return null
        return ImbuementActionCommand(
            action = action,
            position = position,
            itemId = itemId,
            stackPosition = stackPosition,
        )
    }

    /**
     * 0xD5: reads the `uint8_t` slot and the current-payload `uint32_t`
     * imbuement id.
     *
     * @return the decoded command, or `null` when the payload is truncated.
     */
    fun parseApplyImbuement(buffer: ByteBuf): ApplyImbuementCommand? {
        val slot = readByte(buffer) ?: return null
        val imbuementId = readInt(buffer) ?: return null
        return ApplyImbuementCommand(slot = slot, imbuementId = imbuementId)
    }

    /**
     * 0xD6: reads the `uint8_t` slot.
     *
     * @return the decoded command, or `null` when the payload is truncated.
     */
    fun parseClearImbuement(buffer: ByteBuf): ClearImbuementCommand? {
        val slot = readByte(buffer) ?: return null
        return ClearImbuementCommand(slot = slot)
    }

    private fun readByte(buffer: ByteBuf): Int? =
        if (buffer.readableBytes() < 1) null else buffer.readUnsignedByte().toInt()

    private fun readShort(buffer: ByteBuf): Int? =
        if (buffer.readableBytes() < 2) null else buffer.readUnsignedShortLE()

    private fun readInt(buffer: ByteBuf): Long? =
        if (buffer.readableBytes() < 4) null else buffer.readUnsignedIntLE()

    private fun readPosition(buffer: ByteBuf): Position? {
        if (buffer.readableBytes() < 5) return null
        val x = buffer.readUnsignedShortLE()
        val y = buffer.readUnsignedShortLE()
        val z = buffer.readUnsignedByte().toInt()
        return Position(x, y, z)
    }
}
