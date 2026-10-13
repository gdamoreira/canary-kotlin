package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import org.opentibia.canary.network.message.readTibiaString

/**
 * Hireling name request (client opcode `0xEC`), mirroring
 * `ProtocolGame::parseSetHirelingName` (protocolgame.cpp:3350-3354): Canary
 * reads the name string and two `uint32_t` fields and discards all three; no
 * `Game` handler or hireling rename engine exists (PARITY GAP), so the decoded
 * command only marks the confinement boundary.
 *
 * [hirelingId] and [unknown] keep the wire fields in read order; Canary assigns
 * neither a name.
 */
data class HirelingNameCommand(
    val name: String,
    val hirelingId: Long,
    val unknown: Long,
)

/**
 * Decoder for the hireling name client opcode.
 *
 *  - `0xEC` `ProtocolGame::parseSetHirelingName` (protocolgame.cpp:3350)
 */
object HirelingNamePacketHandler {

    const val OPCODE_SET_HIRELING_NAME: Int = 0xEC

    fun parse(opcode: Int, buffer: ByteBuf): HirelingNameCommand? {
        if (opcode != OPCODE_SET_HIRELING_NAME) {
            return null
        }

        val name = readString(buffer) ?: return null

        if (buffer.readableBytes() < 8) {
            return null
        }
        val hirelingId = buffer.readUnsignedIntLE()
        val unknown = buffer.readUnsignedIntLE()
        return HirelingNameCommand(name = name, hirelingId = hirelingId, unknown = unknown)
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
