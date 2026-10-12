package org.opentibia.canary.network.crypto

import java.math.BigInteger

/**
 * Handles RSA 1024-bit raw decryption for OpenTibia network handshakes.
 *
 * OpenTibia clients encrypt the 128-byte block using textbook RSA:
 *   c = m^e mod n
 * Server decrypts via:
 *   m = c^d mod n
 * Where n = p * q and e = 65537.
 */
class RsaCipher(
    pString: String = DEFAULT_P,
    qString: String = DEFAULT_Q
) {
    companion object {
        const val BLOCK_SIZE = 128

        // Default OpenTibia / Canary RSA keys (decimal strings)
        const val DEFAULT_P = "14299623962416399520070177382898895550795403345466153217470516082934737582776038882967213386204600674145392845853859217990626450972452084065728686565928113"
        const val DEFAULT_Q = "7630979195970404721891201847792002125535401292779123937207447574596692788513647179235335529307251350570728407373705564708871762033017096809910315212884101"
        private val E = BigInteger.valueOf(65537L)
    }

    private val n: BigInteger
    private val d: BigInteger

    init {
        val p = BigInteger(pString)
        val q = BigInteger(qString)
        n = p.multiply(q)
        val phi = p.subtract(BigInteger.ONE).multiply(q.subtract(BigInteger.ONE))
        d = E.modInverse(phi)
    }

    /**
     * Decrypts 128 bytes in-place using raw RSA (c^d mod n).
     */
    fun decrypt(bytes: ByteArray, offset: Int = 0): Boolean {
        if (bytes.size - offset < BLOCK_SIZE) return false

        val slice = bytes.copyOfRange(offset, offset + BLOCK_SIZE)
        val c = BigInteger(1, slice)
        val m = c.modPow(d, n)

        val decryptedBytes = m.toByteArray()
        // Pad with leading zeros if decrypted result is less than BLOCK_SIZE
        val result = ByteArray(BLOCK_SIZE)
        if (decryptedBytes.size <= BLOCK_SIZE) {
            val startPos = BLOCK_SIZE - decryptedBytes.size
            System.arraycopy(decryptedBytes, 0, result, startPos, decryptedBytes.size)
        } else {
            // If BigInteger prepended a 0x00 sign byte
            System.arraycopy(decryptedBytes, decryptedBytes.size - BLOCK_SIZE, result, 0, BLOCK_SIZE)
        }

        System.arraycopy(result, 0, bytes, offset, BLOCK_SIZE)
        return true
    }

    /**
     * Helper to encrypt 128 bytes for testing (m^e mod n).
     */
    fun encrypt(bytes: ByteArray): ByteArray {
        val m = BigInteger(1, bytes)
        val c = m.modPow(E, n)
        val encryptedBytes = c.toByteArray()
        val result = ByteArray(BLOCK_SIZE)
        if (encryptedBytes.size <= BLOCK_SIZE) {
            val startPos = BLOCK_SIZE - encryptedBytes.size
            System.arraycopy(encryptedBytes, 0, result, startPos, encryptedBytes.size)
        } else {
            System.arraycopy(encryptedBytes, encryptedBytes.size - BLOCK_SIZE, result, 0, BLOCK_SIZE)
        }
        return result
    }
}
