package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import org.opentibia.canary.map.Position

/**
 * Self-contained commands produced by the inspection client opcodes, mirroring
 * `ProtocolGame::parseInspectionObject` / `parseInspectPlayer`
 * (src/server/network/protocol/protocolgame.cpp).
 */
sealed interface InspectionCommand {
    val opcode: Int
}

/**
 * Object inspection request (client opcode `0xCD`), mirroring
 * `ProtocolGame::parseInspectionObject` (protocolgame.cpp:2913-2931). The
 * `InspectObjectTypes` values are `src/server/server_definitions.hpp:84-90`.
 *
 * Canary returns immediately for the old protocol; this decoder targets the
 * current profile only, like the rest of the port's inbound handlers.
 */
sealed interface InspectObjectCommand : InspectionCommand {
    override val opcode: Int get() = InspectionPacketHandler.OPCODE_INSPECTION_OBJECT

    /** `INSPECT_NORMALOBJECT`: a `Position` inspected through `Game::playerInspectItem(player, pos)`. */
    data class NormalObject(val position: Position) : InspectObjectCommand

    /** `INSPECT_NPCTRADE`: a `uint16_t` item id plus count byte (`Game::playerInspectItem(player, itemId, count, false)`). */
    data class NpcTrade(val itemId: Int, val itemCount: Int) : InspectObjectCommand

    /** `INSPECT_CYCLOPEDIA`: a `uint16_t` item id plus count byte (`Game::playerInspectItem(player, itemId, count, true)`). */
    data class Cyclopedia(val itemId: Int, val itemCount: Int) : InspectObjectCommand

    /** `INSPECT_PROFICIENCY`: a `uint16_t` item id plus one unknown byte (`sendWeaponProficiencyWindow`). */
    data class Proficiency(val itemId: Int, val unknown: Int) : InspectObjectCommand

    /** Any other inspection type (including `INSPECT_PLAYERTRADE`): Canary consumes no further payload. */
    data class NoOp(val inspectionType: Int) : InspectObjectCommand
}

/**
 * Player inspection request (client opcode `0xCE`), mirroring
 * `ProtocolGame::parseInspectPlayer` (protocolgame.cpp:3325-3330): the action
 * byte and, for actions 1..5, a `uint32_t` that Canary reads and discards.
 */
data class InspectPlayerCommand(
    val action: Int,
    val playerId: Long?,
) : InspectionCommand {
    override val opcode: Int get() = InspectionPacketHandler.OPCODE_INSPECT_PLAYER
}

/**
 * Decoder for the inspection client opcodes.
 *
 *  - `0xCD` `ProtocolGame::parseInspectionObject` (protocolgame.cpp:2913)
 *  - `0xCE` `ProtocolGame::parseInspectPlayer` (protocolgame.cpp:3325)
 */
object InspectionPacketHandler {

    const val OPCODE_INSPECTION_OBJECT: Int = 0xCD
    const val OPCODE_INSPECT_PLAYER: Int = 0xCE

    /** `InspectObjectTypes` (src/server/server_definitions.hpp:84-90). */
    const val INSPECT_NORMALOBJECT: Int = 0
    const val INSPECT_NPCTRADE: Int = 1
    const val INSPECT_PLAYERTRADE: Int = 2
    const val INSPECT_CYCLOPEDIA: Int = 3
    const val INSPECT_PROFICIENCY: Int = 4

    /** `Player::getID()` reads that discard their `uint32_t` player id (protocolgame.cpp:3327). */
    private const val MIN_INSPECT_PLAYER_ACTION: Int = 1
    private const val MAX_INSPECT_PLAYER_ACTION: Int = 5

    fun parse(opcode: Int, buffer: ByteBuf): InspectionCommand? = when (opcode) {
        OPCODE_INSPECTION_OBJECT -> parseInspectionObject(buffer)
        OPCODE_INSPECT_PLAYER -> parseInspectPlayer(buffer)
        else -> null
    }

    /**
     * 0xCD: reads the inspection type byte and the type-specific payload.
     *
     * @return the decoded command, or `null` when the type-specific payload is
     *   truncated.
     */
    fun parseInspectionObject(buffer: ByteBuf): InspectObjectCommand? {
        if (buffer.readableBytes() < 1) {
            return null
        }

        return when (val inspectionType = buffer.readUnsignedByte().toInt()) {
            INSPECT_NORMALOBJECT -> {
                if (buffer.readableBytes() < 5) {
                    return null
                }
                val x = buffer.readUnsignedShortLE()
                val y = buffer.readUnsignedShortLE()
                val z = buffer.readUnsignedByte().toInt()
                InspectObjectCommand.NormalObject(position = Position(x, y, z))
            }

            INSPECT_NPCTRADE, INSPECT_CYCLOPEDIA -> {
                if (buffer.readableBytes() < 3) {
                    return null
                }
                val itemId = buffer.readUnsignedShortLE()
                val itemCount = buffer.readUnsignedByte().toInt()
                if (inspectionType == INSPECT_CYCLOPEDIA) {
                    InspectObjectCommand.Cyclopedia(itemId = itemId, itemCount = itemCount)
                } else {
                    InspectObjectCommand.NpcTrade(itemId = itemId, itemCount = itemCount)
                }
            }

            INSPECT_PROFICIENCY -> {
                if (buffer.readableBytes() < 3) {
                    return null
                }
                val itemId = buffer.readUnsignedShortLE()
                val unknown = buffer.readUnsignedByte().toInt()
                InspectObjectCommand.Proficiency(itemId = itemId, unknown = unknown)
            }

            else -> InspectObjectCommand.NoOp(inspectionType = inspectionType)
        }
    }

    /**
     * 0xCE: reads the action byte and, for actions 1..5, the discarded
     * `uint32_t` player id.
     */
    fun parseInspectPlayer(buffer: ByteBuf): InspectPlayerCommand? {
        if (buffer.readableBytes() < 1) {
            return null
        }

        val action = buffer.readUnsignedByte().toInt()
        if (action in MIN_INSPECT_PLAYER_ACTION..MAX_INSPECT_PLAYER_ACTION) {
            if (buffer.readableBytes() < 4) {
                return null
            }
            return InspectPlayerCommand(action = action, playerId = buffer.readUnsignedIntLE())
        }
        return InspectPlayerCommand(action = action, playerId = null)
    }
}
