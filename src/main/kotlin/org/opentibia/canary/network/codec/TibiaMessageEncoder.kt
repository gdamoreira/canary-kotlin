package org.opentibia.canary.network.codec

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.MessageToByteEncoder
import org.opentibia.canary.network.crypto.Adler32Checksum
import org.opentibia.canary.network.crypto.XteaCipher

/**
 * Encodes outbound messages for the client.
 * If an XteaCipher is active, pads to 8 bytes, encrypts in-place, and prepends Adler32 checksum.
 * Finally, prepends the 2-byte outer message length.
 */
class TibiaMessageEncoder(
    var xteaCipher: XteaCipher? = null,
    var checksumEnabled: Boolean = true
) : MessageToByteEncoder<ByteBuf>() {

    override fun encode(ctx: ChannelHandlerContext, msg: ByteBuf, out: ByteBuf) {
        val payloadLen = msg.readableBytes()
        val cipher = xteaCipher

        if (cipher != null) {
            // Calculate padding needed to reach multiple of 8
            val padding = (8 - (payloadLen % 8)) % 8
            val paddedLen = payloadLen + padding
            val checksumLen = if (checksumEnabled) 4 else 0
            val totalInnerLen = checksumLen + paddedLen

            // Write 2-byte outer length
            out.writeShortLE(totalInnerLen)

            // Reserve space for 4-byte checksum if enabled
            val checksumStartIndex = out.writerIndex()
            if (checksumEnabled) {
                out.writeIntLE(0) // placeholder
            }

            val payloadStartIndex = out.writerIndex()
            out.writeBytes(msg)
            // Add padding bytes (0x33 or 0x00)
            repeat(padding) {
                out.writeByte(0x33)
            }

            // Encrypt payload in place
            cipher.encrypt(out, payloadStartIndex, paddedLen)

            // Calculate and write Adler32 checksum over the encrypted body
            if (checksumEnabled) {
                val checksum = Adler32Checksum.compute(out, payloadStartIndex, paddedLen)
                out.setIntLE(checksumStartIndex, checksum.toInt())
            }
        } else {
            // Unencrypted packet (e.g., pre-handshake or raw response)
            out.writeShortLE(payloadLen)
            out.writeBytes(msg)
        }
    }
}
