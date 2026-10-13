package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf

/**
 * Commands produced by the client options/check opcodes, mirroring
 * `ProtocolGame` (src/server/network/protocol/protocolgame.cpp).
 */
sealed interface ClientOptionsCommand

/**
 * Client options report (client opcode `0x2E`), mirroring
 * `ProtocolGame::parseSetClientOptions` (protocolgame.cpp:3221-3228): two
 * bytes are read and discarded for the current payload.
 */
data class SetClientOptionsCommand(
    val first: Int,
    val second: Int,
) : ClientOptionsCommand

/**
 * Client check report (client opcode `0x63`), mirroring
 * `ProtocolGame::parseClientCheck` (protocolgame.cpp:3230-3248): a `uint32_t`
 * payload byte count followed by that many raw bytes. The C++ parser logs and
 * returns without consuming when the count exceeds the unread bytes; this
 * decoder rejects such a payload as `null` instead.
 */
class ClientCheckCommand(
    val payloadSize: Long,
    val payload: ByteArray,
) : ClientOptionsCommand {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ClientCheckCommand) return false
        return payloadSize == other.payloadSize && payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int = 31 * payloadSize.hashCode() + payload.contentHashCode()

    override fun toString(): String = "ClientCheckCommand(payloadSize=$payloadSize, payload=${payload.size} bytes)"
}

/**
 * Decoder for the client options/check opcodes. Multi-byte fields are
 * Little-Endian, matching `NetworkMessage::get<T>()` on the native wire order.
 *
 *  - `0x2E` `ProtocolGame::parseSetClientOptions` (protocolgame.cpp:3221)
 *  - `0x63` `ProtocolGame::parseClientCheck`    (protocolgame.cpp:3230)
 */
object ClientOptionsPacketHandler {

    /** Client opcode for `ProtocolGame::parseSetClientOptions`. */
    const val OPCODE_SET_CLIENT_OPTIONS: Int = 0x2E

    /** Client opcode for `ProtocolGame::parseClientCheck`. */
    const val OPCODE_CLIENT_CHECK: Int = 0x63

    fun parse(opcode: Int, buffer: ByteBuf): ClientOptionsCommand? = when (opcode) {
        OPCODE_SET_CLIENT_OPTIONS -> parseSetClientOptions(buffer)
        OPCODE_CLIENT_CHECK -> parseClientCheck(buffer)
        else -> null
    }

    /**
     * 0x2E: reads the two option bytes. The current-payload guard of the C++
     * parser (protocolgame.cpp:3222-3224) is the profile this port targets.
     *
     * @return the decoded command, or `null` when the payload is truncated.
     */
    fun parseSetClientOptions(buffer: ByteBuf): SetClientOptionsCommand? {
        if (buffer.readableBytes() < 2) {
            return null
        }
        val first = buffer.readUnsignedByte().toInt()
        val second = buffer.readUnsignedByte().toInt()
        return SetClientOptionsCommand(first = first, second = second)
    }

    /**
     * 0x63: reads the `uint32_t` payload size and that many raw bytes.
     *
     * @return the decoded command, or `null` when the announced payload is
     *   truncated.
     */
    fun parseClientCheck(buffer: ByteBuf): ClientCheckCommand? {
        if (buffer.readableBytes() < 4) {
            return null
        }
        val payloadSize = buffer.readUnsignedIntLE()
        if (payloadSize > buffer.readableBytes()) {
            return null
        }
        val payload = ByteArray(payloadSize.toInt())
        buffer.readBytes(payload)
        return ClientCheckCommand(payloadSize = payloadSize, payload = payload)
    }
}
