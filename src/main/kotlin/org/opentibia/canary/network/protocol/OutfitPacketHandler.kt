package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import org.opentibia.canary.combat.condition.Outfit
import org.opentibia.canary.map.Position

/**
 * Self-contained command produced by the outfit/mount client opcodes, mirroring
 * the Canary `ProtocolGame` / `Game` handlers in
 * src/server/network/protocol/protocolgame.cpp.
 */
sealed interface OutfitCommand {
    val opcode: Int
}

/**
 * Show-off socket payload of an outfit-type-2 request
 * (`Game::playerSetShowOffSocket`, game.cpp:5227): the podium `Position`, the
 * `uint16_t` item id, the stack position byte, the clamped direction
 * (`DIRECTION_NORTH`..`DIRECTION_WEST`) and the podium visibility flag.
 */
data class ShowOffSocket(
    val position: Position,
    val itemId: Int,
    val stackPosition: Int,
    val direction: Int,
    val podiumVisible: Boolean,
)

/**
 * Set outfit request (client opcode `0xD3`), mirroring
 * `ProtocolGame::parseSetOutfit` (protocolgame.cpp:2471-2531) on the current
 * profile.
 *
 * [outfitType] selects what Canary did with the common header:
 *  - `0` change outfit: [outfit] carries the mount and mount-color fields,
 *    [setMount] and [isMountRandomized] are the trailing type-0 fields;
 *  - `1` try outfit: the trailing `uint32_t` is read and discarded;
 *  - `2` show-off socket: [showOffSocket] carries the extra fields and the
 *    mount/mount-color fields are folded into [outfit];
 *  - any other value consumes only the common header, like Canary.
 *
 * The `isOTCR` wing/aura/effect reads (protocolgame.cpp:2503-2510) are not
 * modelled because the port targets the non-OTCR current profile, where Canary
 * stores zeros for those `Outfit_t` members. The `g_modules().executeOnRecvbyte`
 * hook and `player->isRemoved()` guard (protocolgame.cpp:2472-2479) are not
 * modelled either: no module system is ported, so the parse always runs.
 */
data class SetOutfitCommand(
    val outfitType: Int,
    val outfit: Outfit,
    val setMount: Boolean = false,
    val isMountRandomized: Int = 0,
    val showOffSocket: ShowOffSocket? = null,
) : OutfitCommand {
    override val opcode: Int get() = OutfitPacketHandler.OPCODE_SET_OUTFIT
}

/**
 * Toggle mount request (client opcode `0xD4`), mirroring
 * `ProtocolGame::parseToggleMount` (protocolgame.cpp:2533-2536) and
 * `Game::playerToggleMount` (game.cpp:7219).
 */
data class ToggleMountCommand(
    val mount: Boolean,
) : OutfitCommand {
    override val opcode: Int get() = OutfitPacketHandler.OPCODE_TOGGLE_MOUNT
}

/**
 * Decoder for the outfit/mount client opcodes. Multi-byte fields are
 * Little-Endian, matching `NetworkMessage::get<T>()` on the native wire order.
 *
 *  - `0xD3` `ProtocolGame::parseSetOutfit`   (protocolgame.cpp:2471)
 *  - `0xD4` `ProtocolGame::parseToggleMount` (protocolgame.cpp:2533)
 */
object OutfitPacketHandler {

    const val OPCODE_SET_OUTFIT: Int = 0xD3
    const val OPCODE_TOGGLE_MOUNT: Int = 0xD4

    /** `std::min<uint8_t>(132, ...)` applied to every outfit color byte. */
    private const val MAX_OUTFIT_COLOR: Int = 132

    /** `DIRECTION_NORTH` / `DIRECTION_WEST` (src/game/movement/position.hpp:13-16). */
    private const val DIRECTION_NORTH: Int = 0
    private const val DIRECTION_WEST: Int = 3

    fun parse(opcode: Int, buffer: ByteBuf): OutfitCommand? = when (opcode) {
        OPCODE_SET_OUTFIT -> parseSetOutfit(buffer)
        OPCODE_TOGGLE_MOUNT -> parseToggleMount(buffer)
        else -> null
    }

    /**
     * 0xD3: reads the outfit type byte, the common header (`uint16_t` look
     * type, four color bytes clamped to 132 and the addons byte) and then the
     * type-specific tail.
     *
     * @return the decoded command, or `null` when the consumed payload is
     *   truncated.
     */
    fun parseSetOutfit(buffer: ByteBuf): SetOutfitCommand? {
        val outfitType = readByte(buffer) ?: return null
        val outfit = readOutfitHeader(buffer) ?: return null

        return when (outfitType) {
            0 -> {
                outfit.lookMount = readShort(buffer) ?: return null
                outfit.lookMountHead = readClampedColor(buffer) ?: return null
                outfit.lookMountBody = readClampedColor(buffer) ?: return null
                outfit.lookMountLegs = readClampedColor(buffer) ?: return null
                outfit.lookMountFeet = readClampedColor(buffer) ?: return null
                val setMount = readBoolean(buffer) ?: return null
                outfit.lookFamiliarsType = readShort(buffer) ?: return null
                val isMountRandomized = readByte(buffer) ?: return null
                SetOutfitCommand(
                    outfitType = outfitType,
                    outfit = outfit,
                    setMount = setMount,
                    isMountRandomized = isMountRandomized,
                )
            }

            1 -> {
                // "Try outfit" value, read and discarded (protocolgame.cpp:2516).
                readInt(buffer) ?: return null
                outfit.lookMount = 0
                SetOutfitCommand(outfitType = outfitType, outfit = outfit)
            }

            2 -> {
                val position = readPosition(buffer) ?: return null
                val itemId = readShort(buffer) ?: return null
                val stackPosition = readByte(buffer) ?: return null
                outfit.lookMount = readShort(buffer) ?: return null
                outfit.lookMountHead = readClampedColor(buffer) ?: return null
                outfit.lookMountBody = readClampedColor(buffer) ?: return null
                outfit.lookMountLegs = readClampedColor(buffer) ?: return null
                outfit.lookMountFeet = readClampedColor(buffer) ?: return null
                val direction = (readByte(buffer) ?: return null).coerceIn(DIRECTION_NORTH, DIRECTION_WEST)
                val podiumVisible = readBoolean(buffer) ?: return null
                SetOutfitCommand(
                    outfitType = outfitType,
                    outfit = outfit,
                    showOffSocket = ShowOffSocket(
                        position = position,
                        itemId = itemId,
                        stackPosition = stackPosition,
                        direction = direction,
                        podiumVisible = podiumVisible,
                    ),
                )
            }

            else -> SetOutfitCommand(outfitType = outfitType, outfit = outfit)
        }
    }

    /**
     * 0xD4: reads the mount flag byte (true when nonzero).
     *
     * @return the decoded command, or `null` when the payload is truncated.
     */
    fun parseToggleMount(buffer: ByteBuf): ToggleMountCommand? {
        val mount = readBoolean(buffer) ?: return null
        return ToggleMountCommand(mount = mount)
    }

    private fun readOutfitHeader(buffer: ByteBuf): Outfit? {
        val lookType = readShort(buffer) ?: return null
        val lookHead = readClampedColor(buffer) ?: return null
        val lookBody = readClampedColor(buffer) ?: return null
        val lookLegs = readClampedColor(buffer) ?: return null
        val lookFeet = readClampedColor(buffer) ?: return null
        val lookAddons = readByte(buffer) ?: return null
        return Outfit(
            lookType = lookType,
            lookHead = lookHead,
            lookBody = lookBody,
            lookLegs = lookLegs,
            lookFeet = lookFeet,
            lookAddons = lookAddons,
        )
    }

    private fun readByte(buffer: ByteBuf): Int? =
        if (buffer.readableBytes() < 1) null else buffer.readUnsignedByte().toInt()

    private fun readClampedColor(buffer: ByteBuf): Int? =
        readByte(buffer)?.coerceAtMost(MAX_OUTFIT_COLOR)

    private fun readShort(buffer: ByteBuf): Int? =
        if (buffer.readableBytes() < 2) null else buffer.readUnsignedShortLE()

    private fun readInt(buffer: ByteBuf): Long? =
        if (buffer.readableBytes() < 4) null else buffer.readUnsignedIntLE()

    private fun readBoolean(buffer: ByteBuf): Boolean? =
        if (buffer.readableBytes() < 1) null else buffer.readUnsignedByte().toInt() != 0

    private fun readPosition(buffer: ByteBuf): Position? {
        if (buffer.readableBytes() < 5) return null
        val x = buffer.readUnsignedShortLE()
        val y = buffer.readUnsignedShortLE()
        val z = buffer.readUnsignedByte().toInt()
        return Position(x, y, z)
    }
}
