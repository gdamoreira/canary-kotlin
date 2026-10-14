package org.opentibia.canary.network.protocol.login

import io.netty.buffer.ByteBuf
import org.opentibia.canary.network.crypto.Adler32Checksum

/**
 * Outbound login challenge serialization, ported from Canary
 * src/server/network/protocol/protocolgame.cpp:
 *
 *  - `ProtocolGame::sendLoginChallenge` (0x1F, protocolgame.cpp:1442-1488)
 *
 * The `initialConnectionBehavior.challenge.layout` selection, the
 * `challengeTimestamp`/`challengeRandom` protocol state and the
 * `disconnect()` fallback stay with the caller: both layouts are exposed as
 * pure frame builders. [writeCurrentLoginChallenge] writes the modern layout
 * (`uint32_t` checksum placeholder, `0x01 0x1F`, timestamp, random, `0x71`)
 * with the Adler-32 of the eight bytes at offset 4 back-filled into the
 * placeholder. [writeLegacyLoginChallenge] writes the Cipsoft860/Tibia1100
 * layout (`uint32_t` checksum, `uint16_t` inner length, `0x1F`, timestamp,
 * random) with the Adler-32 over the inner-length header plus the six body
 * bytes, reproducing Canary's `writeLegacyInnerLength`/`writeChecksum`
 * header-prepend order. Every multi-byte field is Little-Endian.
 */
object LoginChallengeWriter {

    const val PACKET_LOGIN_CHALLENGE: Int = 0x1F

    /** `ChallengeLayout::CurrentLoginChallenge` trailer (protocolgame.cpp:1480). */
    private const val CURRENT_TRAILER = 0x71

    /** `ChallengeLayout::CurrentLoginChallenge` type byte (protocolgame.cpp:1473). */
    private const val CURRENT_TYPE = 0x01

    /** `ChallengeLayout::Cipsoft860/Tibia1100` inner body length (protocolgame.cpp:1455). */
    private const val LEGACY_INNER_LENGTH = 6

    /** `sendLoginChallenge` current layout (protocolgame.cpp:1468-1487). */
    fun writeCurrentLoginChallenge(out: ByteBuf, timestamp: Long, random: Int) {
        val start = out.writerIndex()

        out.writeIntLE(0)
        out.writeByte(CURRENT_TYPE)
        out.writeByte(PACKET_LOGIN_CHALLENGE)
        out.writeIntLE(timestamp.toInt())
        out.writeByte(random)
        out.writeByte(CURRENT_TRAILER)

        val checksum = Adler32Checksum.compute(out, start + 4, 8)
        out.setIntLE(start, checksum.toInt())
    }

    /** `sendLoginChallenge` Cipsoft860/Tibia1100 layout (protocolgame.cpp:1448-1461). */
    fun writeLegacyLoginChallenge(out: ByteBuf, timestamp: Long, random: Int) {
        val start = out.writerIndex()

        out.writeIntLE(0)
        out.writeShortLE(LEGACY_INNER_LENGTH)
        out.writeByte(PACKET_LOGIN_CHALLENGE)
        out.writeIntLE(timestamp.toInt())
        out.writeByte(random)

        val checksum = Adler32Checksum.compute(out, start + 4, 8)
        out.setIntLE(start, checksum.toInt())
    }
}
