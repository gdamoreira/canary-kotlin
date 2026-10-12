package org.opentibia.canary.network.message

import io.netty.buffer.ByteBuf
import java.nio.charset.StandardCharsets

/**
 * Reads a length-prefixed Tibia string (2-byte unsigned length in Little-Endian + UTF-8 payload).
 */
fun ByteBuf.readTibiaString(): String {
    val length = this.readUnsignedShortLE()
    val bytes = ByteArray(length)
    this.readBytes(bytes)
    return String(bytes, StandardCharsets.UTF_8)
}

/**
 * Writes a length-prefixed Tibia string (2-byte unsigned length in Little-Endian + UTF-8 payload).
 */
fun ByteBuf.writeTibiaString(value: String): ByteBuf {
    val bytes = value.toByteArray(StandardCharsets.UTF_8)
    this.writeShortLE(bytes.size)
    this.writeBytes(bytes)
    return this
}

/**
 * Encodes Tibia's variable-length integer format (>= client 15.00).
 */
fun ByteBuf.writeTibiaVarCount(count: Long): ByteBuf {
    when {
        count in 0..63 -> {
            this.writeByte(count.toInt())
        }
        count in 64..16383 -> {
            val b1 = 0x40 or ((count shr 8) and 0x3F).toInt()
            val b2 = (count and 0xFF).toInt()
            this.writeByte(b1)
            this.writeByte(b2)
        }
        count in 16384..1073741823 -> {
            val b1 = 0x80 or ((count shr 24) and 0x3F).toInt()
            val b2 = ((count shr 16) and 0xFF).toInt()
            val b3 = ((count shr 8) and 0xFF).toInt()
            val b4 = (count and 0xFF).toInt()
            this.writeByte(b1)
            this.writeByte(b2)
            this.writeByte(b3)
            this.writeByte(b4)
        }
        else -> {
            this.writeInt(0) // Fallback for unsupported count
        }
    }
    return this
}
