package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import org.opentibia.canary.map.Position

/**
 * Self-contained commands produced by the read-and-discard client queries,
 * mirroring the Canary `ProtocolGame` handlers in
 * src/server/network/protocol/protocolgame.cpp.
 */
sealed interface AggressionAndReportCommand {
    val opcode: Int
}

/**
 * Client opcode `0x8E` (`ProtocolGame::parseJoinAggression`,
 * protocolgame.cpp:3299-3301): Canary reads a `uint32_t` and discards it
 * without any `Game` call, so the command exists only to pin the consumed byte
 * count. [value] is the raw discarded operand.
 */
data class JoinAggressionCommand(
    val value: Long,
) : AggressionAndReportCommand {
    override val opcode: Int get() = AggressionAndReportPacketHandler.OPCODE_JOIN_AGGRESSION
}

/**
 * Client opcode `0x9D` (`ProtocolGame::parseGetTextForReport`,
 * protocolgame.cpp:3307-3311): Canary reads a `Position`, a `uint16_t` and a
 * byte (`getByte(true)`, the suppressed-log variant) and discards all three
 * without any `Game` call.
 */
data class GetTextForReportCommand(
    val position: Position,
    val itemId: Int,
    val value: Int,
) : AggressionAndReportCommand {
    override val opcode: Int get() = AggressionAndReportPacketHandler.OPCODE_GET_TEXT_FOR_REPORT
}

/**
 * Decoder for the read-and-discard client queries. Multi-byte fields are
 * Little-Endian, matching `NetworkMessage::get<T>()` on the native wire order.
 *
 *  - `0x8E` `ProtocolGame::parseJoinAggression`   (protocolgame.cpp:3299)
 *  - `0x9D` `ProtocolGame::parseGetTextForReport` (protocolgame.cpp:3307)
 */
object AggressionAndReportPacketHandler {

    const val OPCODE_JOIN_AGGRESSION: Int = 0x8E
    const val OPCODE_GET_TEXT_FOR_REPORT: Int = 0x9D

    fun parse(opcode: Int, buffer: ByteBuf): AggressionAndReportCommand? = when (opcode) {
        OPCODE_JOIN_AGGRESSION -> parseJoinAggression(buffer)
        OPCODE_GET_TEXT_FOR_REPORT -> parseGetTextForReport(buffer)
        else -> null
    }

    /**
     * 0x8E: reads the `uint32_t` operand.
     *
     * @return the decoded command, or `null` when the payload is truncated.
     */
    fun parseJoinAggression(buffer: ByteBuf): JoinAggressionCommand? {
        val value = readInt(buffer) ?: return null
        return JoinAggressionCommand(value = value)
    }

    /**
     * 0x9D: reads the `Position`, the `uint16_t` item id and the trailing byte.
     *
     * @return the decoded command, or `null` when the payload is truncated.
     */
    fun parseGetTextForReport(buffer: ByteBuf): GetTextForReportCommand? {
        val position = readPosition(buffer) ?: return null
        val itemId = readShort(buffer) ?: return null
        val value = readByte(buffer) ?: return null
        return GetTextForReportCommand(position = position, itemId = itemId, value = value)
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
