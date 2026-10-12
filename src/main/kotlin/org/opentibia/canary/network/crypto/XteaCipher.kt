package org.opentibia.canary.network.crypto

import io.netty.buffer.ByteBuf

class XteaCipher(private val key: IntArray) {
    init {
        require(key.size == 4) { "XTEA key must contain exactly 4 32-bit integers" }
    }

    companion object {
        private const val DELTA = -0x61C88647 // 0x9E3779B9 as signed 32-bit int
        private const val ROUNDS = 32
        const val BLOCK_SIZE = 8
    }

    fun decrypt(buffer: ByteBuf, offset: Int, length: Int): Boolean {
        if (length % BLOCK_SIZE != 0) return false

        for (i in offset until (offset + length) step BLOCK_SIZE) {
            var v0 = buffer.getIntLE(i)
            var v1 = buffer.getIntLE(i + 4)
            var sum = DELTA * ROUNDS

            for (r in 0 until ROUNDS) {
                v1 -= (((v0 shl 4) xor (v0 ushr 5)) + v0) xor (sum + key[(sum ushr 11) and 3])
                sum -= DELTA
                v0 -= (((v1 shl 4) xor (v1 ushr 5)) + v1) xor (sum + key[sum and 3])
            }

            buffer.setIntLE(i, v0)
            buffer.setIntLE(i + 4, v1)
        }
        return true
    }

    fun encrypt(buffer: ByteBuf, offset: Int, length: Int): Boolean {
        if (length % BLOCK_SIZE != 0) return false

        for (i in offset until (offset + length) step BLOCK_SIZE) {
            var v0 = buffer.getIntLE(i)
            var v1 = buffer.getIntLE(i + 4)
            var sum = 0

            for (r in 0 until ROUNDS) {
                v0 += (((v1 shl 4) xor (v1 ushr 5)) + v1) xor (sum + key[sum and 3])
                sum += DELTA
                v1 += (((v0 shl 4) xor (v0 ushr 5)) + v0) xor (sum + key[(sum ushr 11) and 3])
            }

            buffer.setIntLE(i, v0)
            buffer.setIntLE(i + 4, v1)
        }
        return true
    }
}
