package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf

/**
 * `ProtocolGame::parseClientDetails` request (client opcode `0xC1`,
 * src/server/network/protocol/protocolgame.cpp:3313-3315). Canary reads the
 * length-prefixed string and discards it; the decoded value is kept here so the
 * caller can route on it.
 */
data class ClientDetailsCommand(
    val details: String,
)

/** Decoder for the client-details opcode `0xC1`. */
object ClientDetailsPacketHandler {

    /** protocolgame.cpp:1980-1982 dispatches this opcode to `parseClientDetails`. */
    const val OPCODE_CLIENT_DETAILS: Int = 0xC1

    /**
     * Reads `msg.getString()` (`NetworkMessage::getString`,
     * src/server/network/message/networkmessage.cpp:91).
     *
     * @return the decoded command, or `null` when the length prefix or the
     *   declared payload is not fully readable.
     */
    fun parse(buffer: ByteBuf): ClientDetailsCommand? {
        if (buffer.readableBytes() < 2) return null
        val length = buffer.readUnsignedShortLE()
        if (buffer.readableBytes() < length) return null

        val bytes = ByteArray(length)
        buffer.readBytes(bytes)
        return ClientDetailsCommand(String(bytes, Charsets.UTF_8))
    }
}
