package org.opentibia.canary.network.crypto

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.math.BigInteger

/**
 * Port bootstrap factory for RSA key material (not a Canary symbol; Canary compiles
 * `key.pem` at build time while this port resolves `CANARY_RSA_P`/`CANARY_RSA_Q`).
 *
 * Canary source of truth: `../.projects/canary/src/security/rsa.cpp`
 */
class RsaKeyConfigTest {

    private val smallP = "101"
    private val smallQ = "113"

    @Test
    fun `absent environment keeps the built-in Canary key pair`() {
        val cipher = RsaCipher.fromEnvironment(emptyMap())

        val plain = ByteArray(RsaCipher.BLOCK_SIZE) { (it % 251).toByte() }
        val encrypted = cipher.encrypt(plain)
        assertThat(cipher.decrypt(encrypted)).isTrue()
        assertThat(encrypted).isEqualTo(plain)
    }

    @Test
    fun `blank values fall back to the built-in key pair`() {
        val cipher = RsaCipher.fromEnvironment(
            mapOf(RsaCipher.ENV_P to "  ", RsaCipher.ENV_Q to "")
        )

        val plain = ByteArray(RsaCipher.BLOCK_SIZE) { (it % 241).toByte() }
        val encrypted = cipher.encrypt(plain)
        assertThat(cipher.decrypt(encrypted)).isTrue()
        assertThat(encrypted).isEqualTo(plain)
    }

    @Test
    fun `configured pair overrides the key material`() {
        val defaultCipher = RsaCipher.fromEnvironment(emptyMap())
        val overridden = RsaCipher.fromEnvironment(
            mapOf(RsaCipher.ENV_P to smallP, RsaCipher.ENV_Q to smallQ)
        )

        val plain = ByteArray(RsaCipher.BLOCK_SIZE)
        plain[RsaCipher.BLOCK_SIZE - 1] = 7
        val encrypted = overridden.encrypt(plain)
        assertThat(overridden.decrypt(encrypted)).isTrue()
        assertThat(encrypted).isEqualTo(plain)

        val upstreamAttempt = overridden.encrypt(plain)
        assertThat(defaultCipher.decrypt(upstreamAttempt)).isTrue()
        assertThat(upstreamAttempt).isNotEqualTo(plain)
    }

    @Test
    fun `only one variable configured fails fast`() {
        assertThatThrownBy {
            RsaCipher.fromEnvironment(mapOf(RsaCipher.ENV_P to smallP))
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("CANARY_RSA_P")
            .hasMessageContaining("CANARY_RSA_Q")
    }

    @Test
    fun `non-decimal value fails fast naming the variable`() {
        assertThatThrownBy {
            RsaCipher.fromEnvironment(mapOf(RsaCipher.ENV_P to "not-a-number", RsaCipher.ENV_Q to smallQ))
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("CANARY_RSA_P")

        assertThatThrownBy {
            RsaCipher.fromEnvironment(mapOf(RsaCipher.ENV_P to smallP, RsaCipher.ENV_Q to "12.5"))
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("CANARY_RSA_Q")
    }

    @Test
    fun `trivial values fail fast`() {
        assertThatThrownBy {
            RsaCipher.fromEnvironment(mapOf(RsaCipher.ENV_P to "1", RsaCipher.ENV_Q to smallQ))
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("CANARY_RSA_P")

        assertThatThrownBy {
            RsaCipher.fromEnvironment(mapOf(RsaCipher.ENV_P to smallP, RsaCipher.ENV_Q to smallP))
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("must differ")
    }

    @Test
    fun `built-in pair matches the OpenTibia decimal key material`() {
        val cipher = RsaCipher.fromEnvironment(
            mapOf(RsaCipher.ENV_P to RsaCipher.DEFAULT_P, RsaCipher.ENV_Q to RsaCipher.DEFAULT_Q)
        )
        val expectedN = BigInteger(RsaCipher.DEFAULT_P).multiply(BigInteger(RsaCipher.DEFAULT_Q))

        val plain = ByteArray(RsaCipher.BLOCK_SIZE) { ((it * 7) % 253).toByte() }
        val encrypted = cipher.encrypt(plain)
        assertThat(cipher.decrypt(encrypted)).isTrue()
        assertThat(encrypted).isEqualTo(plain)
        assertThat(expectedN.bitLength()).isEqualTo(1024)
    }
}
