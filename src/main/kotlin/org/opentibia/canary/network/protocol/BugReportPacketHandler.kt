package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.message.readTibiaString

/**
 * Bug report (client opcode `0xE6`), mirroring
 * `ProtocolGame::parseBugReport` (protocolgame.cpp:4241-4251): the category
 * byte, the message string and, for `BUG_CATEGORY_MAP`, the reported position
 * handed to `Game::playerReportBug` (game.cpp:10193).
 */
data class BugReportCommand(
    val category: Int,
    val message: String,
    val position: Position?,
)

/**
 * Decoder for the bug-report client opcode.
 *
 *  - `0xE6` `ProtocolGame::parseBugReport` (protocolgame.cpp:4241)
 */
object BugReportPacketHandler {

    const val OPCODE_BUG_REPORT: Int = 0xE6

    /** `BugReportType` (src/lua/lua_definitions.hpp:205-208). */
    const val BUG_CATEGORY_MAP: Int = 0

    fun parse(opcode: Int, buffer: ByteBuf): BugReportCommand? {
        if (opcode != OPCODE_BUG_REPORT) {
            return null
        }
        if (buffer.readableBytes() < 1) {
            return null
        }

        val category = buffer.readUnsignedByte().toInt()
        val message = readString(buffer) ?: return null

        val position = if (category == BUG_CATEGORY_MAP) {
            if (buffer.readableBytes() < 5) {
                return null
            }
            val x = buffer.readUnsignedShortLE()
            val y = buffer.readUnsignedShortLE()
            val z = buffer.readUnsignedByte().toInt()
            Position(x, y, z)
        } else {
            null
        }

        return BugReportCommand(category = category, message = message, position = position)
    }

    /**
     * Mirrors `NetworkMessage::getString()`: a `uint16_t` Little-Endian byte
     * length followed by that many bytes, with an explicit bounds check instead
     * of trusting the frame.
     */
    private fun readString(buffer: ByteBuf): String? {
        if (buffer.readableBytes() < 2) {
            return null
        }
        val length = buffer.getUnsignedShortLE(buffer.readerIndex())
        if (buffer.readableBytes() < 2 + length) {
            return null
        }
        return buffer.readTibiaString()
    }
}
