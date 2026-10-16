package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import org.opentibia.canary.network.crypto.RsaCipher
import org.opentibia.canary.network.crypto.XteaCipher
import org.opentibia.canary.network.message.writeTibiaString

/**
 * Test support for the `ProtocolGame::sendLoginChallenge` handshake
 * (src/server/network/protocol/protocolgame.cpp:1442-1480): every game
 * connection writes the unencrypted modern-framed challenge before the login,
 * so tests that read the first outbound frame consume it through
 * [readChallenge].
 */
object GameLoginTestSupport {

    data class Challenge(val timestamp: Int, val random: Int)

    /** Reads and validates the `0x1F` challenge frame written on channel active. */
    fun readChallenge(channel: EmbeddedChannel): Challenge {
        val frame = channel.readOutbound<ByteBuf>() ?: error("no challenge frame written")
        try {
            val blockCount = frame.readUnsignedShortLE()
            check(blockCount == 1) { "unexpected challenge block count $blockCount" }
            frame.readIntLE() // checksum slot
            val paddingSize = frame.readUnsignedByte().toInt()
            check(paddingSize == 1) { "unexpected challenge padding size $paddingSize" }
            val opcode = frame.readUnsignedByte().toInt()
            check(opcode == 0x1F) { "expected the login challenge, got $opcode" }
            val timestamp = frame.readIntLE()
            val random = frame.readUnsignedByte().toInt()
            return Challenge(timestamp, random)
        } finally {
            frame.release()
        }
    }

    /**
     * Builds a modern (`Proto::ClientPendingGame`) login packet echoing
     * [challenge] (`ProtocolGame::sendLoginPacket`,
     * src/client/protocolgamesend.cpp:50-140).
     */
    fun modernLoginPacket(
        rsaCipher: RsaCipher,
        xteaKey: IntArray,
        sessionKey: String,
        characterName: String,
        challenge: Challenge,
        clientVersion: Int = CLIENT_VERSION,
    ): ByteBuf {
        val rsaPlain = ByteArray(RsaCipher.BLOCK_SIZE)
        val rsaBuf = Unpooled.wrappedBuffer(rsaPlain)
        rsaBuf.writerIndex(0)
        rsaBuf.writeByte(0) // check byte
        for (key in xteaKey) {
            rsaBuf.writeIntLE(key)
        }
        rsaBuf.writeByte(0) // GM flag
        rsaBuf.writeTibiaString(sessionKey)
        rsaBuf.writeTibiaString(characterName)
        rsaBuf.writeIntLE(challenge.timestamp)
        rsaBuf.writeByte(challenge.random)

        val body = Unpooled.buffer()
        body.writeByte(0x0A) // Proto::ClientPendingGame
        body.writeShortLE(2) // OS
        body.writeShortLE(clientVersion)
        body.writeIntLE(clientVersion)
        body.writeTibiaString("")
        body.writeTibiaString("")
        body.writeByte(0)
        body.writeBytes(rsaCipher.encrypt(rsaPlain))

        val packet = Unpooled.buffer()
        packet.writeShortLE(body.readableBytes())
        packet.writeBytes(body)
        return packet
    }

    /** Frames a post-login payload with the modern transport for the server. */
    fun modernFrame(payload: ByteArray, xteaKey: IntArray, sequence: Int): ByteBuf {
        val cipher = XteaCipher(xteaKey)
        val padding = (8 - ((payload.size + 1) % 8)) % 8
        val encryptedLen = 1 + payload.size + padding

        val out = Unpooled.buffer(2 + 4 + encryptedLen)
        out.writeShortLE(encryptedLen / 8)
        out.writeIntLE(sequence)
        val start = out.writerIndex()
        out.writeByte(padding)
        out.writeBytes(payload)
        repeat(padding) { out.writeByte(0x33) }
        cipher.encrypt(out, start, encryptedLen)
        return out
    }

    /** Decrypts a modern-framed outbound frame for assertions. */
    fun readModernFrame(frame: ByteBuf, xteaKey: IntArray): ByteBuf {
        val cipher = XteaCipher(xteaKey)
        frame.readUnsignedShortLE()
        frame.readIntLE()
        val encryptedLen = frame.readableBytes()
        val start = frame.readerIndex()
        cipher.decrypt(frame, start, encryptedLen)
        val paddingSize = frame.readUnsignedByte().toInt()
        return frame.readRetainedSlice(encryptedLen - 1 - paddingSize)
    }
}
