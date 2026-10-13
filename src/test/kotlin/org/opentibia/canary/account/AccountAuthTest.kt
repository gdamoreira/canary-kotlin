package org.opentibia.canary.account

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.network.crypto.Argon2Security
import org.opentibia.canary.utils.Tools

/**
 * Transcript tests for the Canary `Account` authentication paths:
 *  - `Account::authenticate` (src/account/account.cpp:285-288)
 *  - `Account::authenticate(secret)` (src/account/account.cpp:290-292)
 *  - `Account::authenticateSession` (src/account/account.cpp:294-300)
 *  - `Account::authenticatePassword` (src/account/account.cpp:302-313)
 *  - `Account::getPassword` gate (src/account/account.cpp:192-204)
 */
class AccountAuthTest {

    private val now = 1_700_000_000L

    @Test
    fun `authenticatePassword accepts a matching argon2id hash`() {
        val account = loadedAccount(ARGON2_HASH)

        assertThat(account.authenticatePassword("s3cret")).isTrue()
    }

    @Test
    fun `authenticatePassword rejects a wrong password`() {
        val account = loadedAccount(ARGON2_HASH)

        assertThat(account.authenticatePassword("guess")).isFalse()
    }

    @Test
    fun `authenticatePassword accepts a legacy sha1 hash`() {
        val account = loadedAccount(SHA1_HASH)

        assertThat(account.authenticatePassword("legacy")).isTrue()
    }

    @Test
    fun `authenticatePassword rejects the legacy hash with the wrong password`() {
        val account = loadedAccount(SHA1_HASH)

        assertThat(account.authenticatePassword("other")).isFalse()
    }

    @Test
    fun `authenticatePassword fails before the account is loaded`() {
        val account = Account(id = 7, name = "hero", repository = FakeAccountRepository())

        assertThat(account.authenticatePassword("s3cret")).isFalse()
    }

    @Test
    fun `authenticate with a secret authenticates the password`() {
        val account = loadedAccount(ARGON2_HASH)

        assertThat(account.authenticate("s3cret")).isTrue()
        assertThat(account.authenticate("guess")).isFalse()
    }

    @Test
    fun `authenticate without a secret authenticates the session`() {
        val unexpired = Account(id = 7, name = "hero", sessionExpires = getTimeNow() + 60)
        val expired = Account(id = 7, name = "hero", sessionExpires = getTimeNow() - 60)

        assertThat(unexpired.authenticate()).isTrue()
        assertThat(expired.authenticate()).isFalse()
    }

    @Test
    fun `authenticateSession accepts a session that expires exactly now`() {
        val account = Account(id = 7, name = "hero", sessionExpires = now)

        assertThat(account.authenticateSession(now)).isTrue()
    }

    @Test
    fun `authenticateSession rejects an expired session`() {
        val account = Account(id = 7, name = "hero", sessionExpires = now - 1)

        assertThat(account.authenticateSession(now)).isFalse()
    }

    private fun loadedAccount(passwordHash: String): Account {
        val repository = FakeAccountRepository().apply {
            infoById[7] = AccountInfo(id = 7)
            storedPassword = passwordHash
        }
        return Account(id = 7, name = "hero", repository = repository).apply {
            assertThat(load()).isEqualTo(AccountErrors.Ok)
        }
    }

    private companion object {
        val ARGON2_HASH: String = Argon2Security.hashPassword("s3cret")
        val SHA1_HASH: String = Tools.transformToSHA1("legacy")
    }
}
