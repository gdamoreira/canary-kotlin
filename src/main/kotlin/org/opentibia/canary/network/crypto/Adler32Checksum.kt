package org.opentibia.canary.network.crypto

import io.netty.buffer.ByteBuf

object Adler32Checksum {
    private const val MOD_ADLER = 65521L

    /**
     * Computes the Adler-32 checksum conforming to RFC 1950 / OpenTibia network protocol.
     * @param buffer ByteBuf containing the data
     * @param offset start offset
     * @param length number of bytes to include
     * @return unsigned 32-bit checksum value as Long
     */
    fun compute(buffer: ByteBuf, offset: Int, length: Int): Long {
        var a = 1L
        var b = 0L

        for (i in offset until (offset + length)) {
            val byteVal = buffer.getUnsignedByte(i).toLong()
            a = (a + byteVal) % MOD_ADLER
            b = (b + a) % MOD_ADLER
        }

        return ((b shl 16) or a) and 0xFFFFFFFFL
    }

    /**
     * Computes the Adler-32 checksum over a byte array, mirroring Canary's
     * `adlerChecksum(const uint8_t*, size_t)` (src/utils/tools.cpp) used by
     * `Connection::parsePacket`.
     */
    fun compute(buffer: ByteArray, offset: Int, length: Int): Long {
        var a = 1L
        var b = 0L

        for (i in offset until (offset + length)) {
            val byteVal = buffer[i].toLong() and 0xFF
            a = (a + byteVal) % MOD_ADLER
            b = (b + a) % MOD_ADLER
        }

        return ((b shl 16) or a) and 0xFFFFFFFFL
    }
}
