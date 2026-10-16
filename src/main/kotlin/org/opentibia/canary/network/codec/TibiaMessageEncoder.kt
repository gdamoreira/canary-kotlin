package org.opentibia.canary.network.codec

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.MessageToByteEncoder
import org.opentibia.canary.network.crypto.Adler32Checksum
import org.opentibia.canary.network.crypto.XteaCipher

/**
 * Encodes outbound messages for the client.
 *
 * Legacy framing (`TransportProfileId.LegacyRawWithLoginHeader`): pads the
 * payload to 8 bytes, encrypts in place, prepends the Adler-32 checksum and the
 * 2-byte outer length.
 *
 * Modern framing (`TransportProfileId.CurrentModern`, client >= 14xx): the
 * encrypted payload carries a leading padding-size byte, the outer length is an
 * 8-byte block count and the checksum slot carries a sequence number
 * (`TransportCodec::encodeOutbound`, src/server/network/protocol/transport_codec.cpp:35-80).
 * No compression is emitted (the sequence high bit stays 0), which the client
 * accepts.
 */
class TibiaMessageEncoder(
    var xteaCipher: XteaCipher? = null,
    var checksumEnabled: Boolean = true,
    var modernFraming: Boolean = false,
) : MessageToByteEncoder<ByteBuf>() {

    /** `Protocol::serverSequenceNumber` (protocol.hpp). */
    var serverSequenceNumber: Int = 0
        private set

    override fun encode(ctx: ChannelHandlerContext, msg: ByteBuf, out: ByteBuf) {
        val payloadLen = msg.readableBytes()
        val cipher = xteaCipher

        if (cipher != null) {
            if (modernFraming) {
                encodeModern(msg, out, cipher)
            } else {
                encodeLegacy(msg, out, cipher)
            }
        } else {
            // Unencrypted packet (e.g., pre-handshake or raw response)
            out.writeShortLE(payloadLen)
            out.writeBytes(msg)
        }
    }

    private fun encodeLegacy(msg: ByteBuf, out: ByteBuf, cipher: XteaCipher) {
        val payloadLen = msg.readableBytes()

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
    }

    /**
     * `TransportCodec::encodeOutbound` for `EncryptedPayloadLayout::ModernPaddingByte`
     * over `OuterLengthEncoding::ModernBlockCount` with
     * `CHECKSUM_METHOD_SEQUENCE` (transport_codec.cpp:35-80).
     */
    private fun encodeModern(msg: ByteBuf, out: ByteBuf, cipher: XteaCipher) {
        val payloadLen = msg.readableBytes()

        // [padding size][payload][padding]: the padding aligns the block to 8.
        val padding = (8 - ((payloadLen + 1) % 8)) % 8
        val encryptedLen = 1 + payloadLen + padding

        // Outer length: the encrypted block count.
        out.writeShortLE(encryptedLen / 8)

        // Checksum slot: the sequence number (high bit 0 = uncompressed).
        serverSequenceNumber += 1
        out.writeIntLE(serverSequenceNumber)

        val payloadStartIndex = out.writerIndex()
        out.writeByte(padding)
        out.writeBytes(msg)
        repeat(padding) {
            out.writeByte(0x33)
        }

        cipher.encrypt(out, payloadStartIndex, encryptedLen)
    }

    /**
     * `ProtocolGame::sendLoginChallenge` (protocolgame.cpp:1442-1480) over the
     * unencrypted modern framing: `[u16 block count][u32 sequence 0][padding
     * size 1][0x1F][timestamp][random][padding byte]`. The challenge is written
     * from this encoder's context so the encode path is bypassed.
     */
    fun writeLoginChallenge(ctx: ChannelHandlerContext, timestamp: Int, random: Int) {
        val out = ctx.alloc().buffer(14)
        out.writeShortLE(1) // one encrypted block: padding + 0x1F + ts + random + padding byte
        out.writeIntLE(0) // checksum slot (unencrypted challenge)
        out.writeByte(0x01) // padding size
        out.writeByte(0x1F) // Proto::GameServerChallenge
        out.writeIntLE(timestamp)
        out.writeByte(random)
        out.writeByte(0x71) // the padding byte
        ctx.writeAndFlush(out, ctx.voidPromise())
    }
}
