package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import org.opentibia.canary.network.message.readTibiaString

/**
 * Self-contained commands produced by the text-window client opcodes, mirroring
 * the Canary `ProtocolGame` / `Game` handlers in
 * src/server/network/protocol/protocolgame.cpp.
 */
sealed interface TextWindowCommand {
    val opcode: Int
}

/**
 * Submit the edited text of a writable item (client opcode `0x89`), mirroring
 * `ProtocolGame::parseTextWindow` (protocolgame.cpp:2793-2797): the `uint32_t`
 * window text id and the new text handed to `Game::playerWriteItem`
 * (game.cpp:5535).
 */
data class WriteItemCommand(
    val windowTextId: Long,
    val text: String,
) : TextWindowCommand {
    override val opcode: Int get() = TextWindowPacketHandler.OPCODE_TEXT_WINDOW
}

/**
 * Submit a house access-list update (client opcode `0x8A`), mirroring
 * `ProtocolGame::parseHouseWindow` (protocolgame.cpp:2799-2804): the list id
 * byte (named `doorId` in the parse function), the `uint32_t` window text id
 * and the new text handed to `Game::playerUpdateHouseWindow` (game.cpp:5780),
 * whose signature names them `listId` and `windowTextId`.
 */
data class HouseWindowCommand(
    val listId: Int,
    val windowTextId: Long,
    val text: String,
) : TextWindowCommand {
    override val opcode: Int get() = TextWindowPacketHandler.OPCODE_HOUSE_WINDOW
}

/**
 * Decoder for the text-window client opcodes. Multi-byte fields are
 * Little-Endian, matching `NetworkMessage::get<T>()` on the native wire order.
 *
 *  - `0x89` `ProtocolGame::parseTextWindow`  (protocolgame.cpp:2793)
 *  - `0x8A` `ProtocolGame::parseHouseWindow` (protocolgame.cpp:2799)
 */
object TextWindowPacketHandler {

    const val OPCODE_TEXT_WINDOW: Int = 0x89
    const val OPCODE_HOUSE_WINDOW: Int = 0x8A

    fun parse(opcode: Int, buffer: ByteBuf): TextWindowCommand? = when (opcode) {
        OPCODE_TEXT_WINDOW -> parseTextWindow(buffer)
        OPCODE_HOUSE_WINDOW -> parseHouseWindow(buffer)
        else -> null
    }

    /**
     * 0x89: reads the `uint32_t` window text id and the length-prefixed text.
     *
     * @return the decoded command, or `null` when the payload is truncated.
     */
    fun parseTextWindow(buffer: ByteBuf): WriteItemCommand? {
        val windowTextId = readInt(buffer) ?: return null
        val text = readString(buffer) ?: return null
        return WriteItemCommand(windowTextId = windowTextId, text = text)
    }

    /**
     * 0x8A: reads the list id byte, the `uint32_t` window text id and the
     * length-prefixed text.
     *
     * @return the decoded command, or `null` when the payload is truncated.
     */
    fun parseHouseWindow(buffer: ByteBuf): HouseWindowCommand? {
        val listId = readByte(buffer) ?: return null
        val windowTextId = readInt(buffer) ?: return null
        val text = readString(buffer) ?: return null
        return HouseWindowCommand(listId = listId, windowTextId = windowTextId, text = text)
    }

    private fun readByte(buffer: ByteBuf): Int? =
        if (buffer.readableBytes() < 1) null else buffer.readUnsignedByte().toInt()

    private fun readInt(buffer: ByteBuf): Long? =
        if (buffer.readableBytes() < 4) null else buffer.readUnsignedIntLE()

    /**
     * Mirrors `NetworkMessage::getString()`: a `uint16_t` Little-Endian byte
     * length followed by that many bytes, with an explicit bounds check instead
     * of trusting the frame.
     */
    private fun readString(buffer: ByteBuf): String? {
        if (buffer.readableBytes() < 2) return null
        val length = buffer.getUnsignedShortLE(buffer.readerIndex())
        if (buffer.readableBytes() < 2 + length) return null
        return buffer.readTibiaString()
    }
}
