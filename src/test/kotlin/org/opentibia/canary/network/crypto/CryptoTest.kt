package org.opentibia.canary.network.crypto

import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CryptoTest {

    @Test
    fun `XTEA cipher encrypts and decrypts correctly`() {
        val key = intArrayOf(0x12345678, 0x9ABCDEF0.toInt(), 0x0FEDCBA9, 0x87654321.toInt())
        val cipher = XteaCipher(key)

        val originalData = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16)
        val buf = Unpooled.copiedBuffer(originalData)

        // Encrypt
        val encryptResult = cipher.encrypt(buf, 0, originalData.size)
        assertThat(encryptResult).isTrue()

        val encryptedBytes = ByteArray(originalData.size)
        buf.getBytes(0, encryptedBytes)
        assertThat(encryptedBytes).isNotEqualTo(originalData)

        // Decrypt
        val decryptResult = cipher.decrypt(buf, 0, originalData.size)
        assertThat(decryptResult).isTrue()

        val decryptedBytes = ByteArray(originalData.size)
        buf.getBytes(0, decryptedBytes)
        assertThat(decryptedBytes).isEqualTo(originalData)
    }

    @Test
    fun `RSA cipher encrypts and decrypts round trip`() {
        val rsa = RsaCipher()
        val plain = ByteArray(128)
        plain[0] = 0x00 // OpenTibia check byte
        plain[1] = 0x42
        plain[127] = 0x7F

        val encrypted = rsa.encrypt(plain)
        assertThat(encrypted).isNotEqualTo(plain)

        val testBuffer = encrypted.copyOf()
        val success = rsa.decrypt(testBuffer)
        assertThat(success).isTrue()
        assertThat(testBuffer).isEqualTo(plain)
    }
}
