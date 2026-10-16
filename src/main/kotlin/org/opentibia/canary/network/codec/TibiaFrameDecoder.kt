package org.opentibia.canary.network.codec

import io.github.oshai.kotlinlogging.KotlinLogging
import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufUtil
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.ByteToMessageDecoder
import org.opentibia.canary.network.crypto.XteaCipher

private val logger = KotlinLogging.logger {}

/**
 * Splits the TCP byte stream into individual Tibia packets.
 * Tibia packets are framed with a 2-byte Little-Endian unsigned length prefix.
 *
 * `Connection::parseProxyIdentification`
 * (src/server/network/connection/connection.cpp:231-287): a client may open the
 * game connection with the plaintext `SERVER_NAME + "\n"` identification before
 * the first framed packet (OTClient sends the world name for multi-world
 * proxies). The first two bytes select the path: a high length byte of `0x00`
 * or a prefix mismatch with the configured server name is a standard packet,
 * otherwise the remainder of the identification is read and validated
 * case-insensitively; a mismatch closes the channel with the C++ message.
 */
class TibiaFrameDecoder(
    /** `g_configManager().getString(SERVER_NAME)` (connection.cpp:246). */
    private val serverName: String = "",
) : ByteToMessageDecoder() {
    companion object {
        const val MAX_PACKET_SIZE = 65535
    }

    private val expectedIdentification = "$serverName\n"
    private var identifying = true
    private var loggedFirstBytes = false
    private var loginSeen = false

    /**
     * `TransportProfileId.CurrentModern` inbound framing
     * (`TransportCodec::prepareInbound`, transport_codec.cpp:82-118): enabled
     * after the game login resolves the modern profile. The frames are
     * `[u16 block count][u32 sequence][xtea(padding size + payload + padding)]`.
     */
    var modernFraming: Boolean = false
        private set

    var xteaCipher: XteaCipher? = null
        private set

    /** `Protocol::clientSequenceNumber` (protocol.hpp): the expected next sequence. */
    var expectedClientSequence: Int = 0
        private set

    /** Switches this connection to the modern transport after a successful login. */
    fun enableModernFraming(cipher: XteaCipher) {
        modernFraming = true
        xteaCipher = cipher
        expectedClientSequence = 0
    }

    override fun decode(ctx: ChannelHandlerContext, input: ByteBuf, out: MutableList<Any>) {
        if (identifying && !handleProxyIdentification(ctx, input)) {
            return
        }

        if (modernFraming) {
            decodeModern(ctx, input, out)
            return
        }

        if (!loggedFirstBytes && input.readableBytes() > 0) {
            loggedFirstBytes = true
            val preview = ByteBufUtil.hexDump(input, input.readerIndex(), minOf(16, input.readableBytes()))
            logger.debug { "First game packet bytes from ${ctx.channel().remoteAddress()}: $preview" }
        }

        if (input.readableBytes() < 2) {
            return
        }

        input.markReaderIndex()
        val lengthHeader = input.readUnsignedShortLE()

        // `Connection::parsePacket` + `Connection::parseHeader`
        // (connection.cpp:317-331, 380-384): the first packet of a modern
        // connection (`Proto::ClientPendingGame` at offset 5 behind the
        // sequence and the padding-size byte) uses the block-count outer
        // length (`decodeBodySize`: count * 8 + 4) and the 6-byte first-packet
        // header is consumed before the game-login parse.
        val modernLoginFrame = !loginSeen &&
            input.readableBytes() >= 6 &&
            input.getUnsignedByte(input.readerIndex() + 5).toInt() == 0x0A
        val packetLength: Int
        val firstPacketHeaderBytes: Int
        if (modernLoginFrame) {
            packetLength = lengthHeader * 8 + 4
            firstPacketHeaderBytes = 5 // sequence (4) + padding size (1)
        } else {
            packetLength = lengthHeader
            firstPacketHeaderBytes = 0
        }

        if (packetLength == 0 || packetLength > MAX_PACKET_SIZE) {
            ctx.close()
            return
        }

        if (input.readableBytes() < packetLength) {
            input.resetReaderIndex()
            return
        }

        loginSeen = true
        var payloadLength = packetLength
        if (firstPacketHeaderBytes > 0) {
            // `TransportCodec::decryptXtea`'s modern padding handling
            // (transport_codec.cpp:139-146): the padding-size byte follows the
            // sequence; the trailing padding bytes are trimmed from the payload.
            val paddingSize = input.getUnsignedByte(input.readerIndex() + 4).toInt()
            input.skipBytes(firstPacketHeaderBytes)
            payloadLength = packetLength - firstPacketHeaderBytes - paddingSize
            if (payloadLength < 0) {
                ctx.close()
                return
            }
        }

        // Retain and emit the packet payload without the 2-byte outer length
        val frame = input.readRetainedSlice(payloadLength)
        if (firstPacketHeaderBytes > 0) {
            input.skipBytes(packetLength - firstPacketHeaderBytes - payloadLength)
        }
        out.add(frame)
    }

    /**
     * `TransportCodec::prepareInbound`/`decryptXtea` for the modern framing
     * (transport_codec.cpp:82-146): the outer length is a block count
     * (`size = count * 8 + 4`), the checksum is the client's sequence number
     * (validated against `Protocol::clientSequenceNumber`), the decrypted
     * payload starts with the padding-size byte and the payload follows.
     */
    private fun decodeModern(ctx: ChannelHandlerContext, input: ByteBuf, out: MutableList<Any>) {
        if (input.readableBytes() < 2) {
            return
        }

        input.markReaderIndex()
        val blockCount = input.readUnsignedShortLE()
        val encryptedLen = blockCount * 8

        if (blockCount == 0 || encryptedLen > MAX_PACKET_SIZE) {
            ctx.close()
            return
        }

        if (input.readableBytes() < 4 + encryptedLen) {
            input.resetReaderIndex()
            return
        }

        val sequence = input.readIntLE()
        expectedClientSequence += 1
        if (sequence != expectedClientSequence) {
            logger.warn { "Modern game packet sequence mismatch: $sequence != $expectedClientSequence" }
            ctx.close()
            return
        }

        val frame = input.readRetainedSlice(encryptedLen)
        val cipher = xteaCipher
        if (cipher == null || !cipher.decrypt(frame, 0, encryptedLen)) {
            logger.warn { "Modern game packet XTEA decrypt failed" }
            frame.release()
            ctx.close()
            return
        }

        val paddingSize = frame.readUnsignedByte().toInt()
        val payloadLen = encryptedLen - 1 - paddingSize
        if (payloadLen < 0) {
            logger.warn { "Modern game packet padding size $paddingSize exceeds the payload" }
            frame.release()
            ctx.close()
            return
        }

        val payload = frame.readRetainedSlice(payloadLen)
        frame.release()
        out.add(payload)
    }

    /**
     * `Connection::parseProxyIdentification`
     * (src/server/network/connection/connection.cpp:231-287). Returns `true`
     * when the stream may proceed with the standard framing; `false` while more
     * identification bytes are needed or after closing the channel.
     */
    private fun handleProxyIdentification(ctx: ChannelHandlerContext, input: ByteBuf): Boolean {
        // A server name shorter than the two-byte probe cannot identify.
        if (expectedIdentification.length < 2) {
            identifying = false
            return true
        }

        if (input.readableBytes() < 2) {
            return false
        }

        val readerIndex = input.readerIndex()
        val first = input.getUnsignedByte(readerIndex).toInt().toChar()
        val second = input.getUnsignedByte(readerIndex + 1).toInt().toChar()

        // `msgBuffer[1] == 0x00 || strncasecmp(charData, serverName, 2) != 0`
        // (connection.cpp:248): a zero high length byte or a prefix mismatch is
        // a standard packet, not an identification.
        val looksLikeIdentification = second != '\u0000' &&
            first.equals(expectedIdentification[0], ignoreCase = true) &&
            second.equals(expectedIdentification[1], ignoreCase = true)
        if (!looksLikeIdentification) {
            identifying = false
            return true
        }

        if (input.readableBytes() < expectedIdentification.length) {
            return false
        }

        val identification = input.toString(readerIndex, expectedIdentification.length, Charsets.UTF_8)
        if (!identification.equals(expectedIdentification, ignoreCase = true)) {
            logger.error { "[Connection::parseProxyIdentification] Invalid Client Login! Server Name mismatch!" }
            ctx.close()
            return false
        }

        input.skipBytes(expectedIdentification.length)
        identifying = false
        logger.debug { "Proxy identification accepted from ${ctx.channel().remoteAddress()}" }
        return true
    }
}
