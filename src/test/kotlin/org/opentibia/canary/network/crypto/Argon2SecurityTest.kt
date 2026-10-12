package org.opentibia.canary.network.crypto

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class Argon2SecurityTest {

    @Test
    fun `generates and verifies Argon2id password hash`() {
        val rawPassword = "secretPassword123"
        val hash = Argon2Security.hashPassword(rawPassword)

        assertThat(hash).startsWith("\$argon2id\$")
        assertThat(Argon2Security.verifyPassword(rawPassword, hash)).isTrue()
        assertThat(Argon2Security.verifyPassword("wrongPassword", hash)).isFalse()
    }

    @Test
    fun `falls back to plaintext or sha1 comparison when not argon2 hash`() {
        val legacyPlaintext = "admin123"
        assertThat(Argon2Security.verifyPassword("admin123", legacyPlaintext)).isTrue()
        assertThat(Argon2Security.verifyPassword("wrong", legacyPlaintext)).isFalse()
    }
}
