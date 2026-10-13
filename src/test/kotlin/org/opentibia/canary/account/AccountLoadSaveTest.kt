package org.opentibia.canary.account

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Transcript tests for the Canary `Account` load/save lifecycle:
 *  - `Account::load` (src/account/account.cpp:38-57)
 *  - `Account::reload` (src/account/account.cpp:59-65)
 *  - `Account::save` (src/account/account.cpp:67-76)
 *  - `Account::getPassword` (src/account/account.cpp:192-204)
 */
class AccountLoadSaveTest {

    @Test
    fun `load by id copies the account info and marks the account loaded`() {
        val repository = FakeAccountRepository().apply {
            infoById[7] = AccountInfo(
                id = 7,
                premiumRemainingDays = 15,
                premiumLastDay = 1_700_000_000L,
                accountType = AccountType.ACCOUNT_TYPE_GAMEMASTER,
                players = mutableMapOf("Knight Hero" to 1uL),
                oldProtocol = true,
                sessionExpires = 1_700_000_500L,
                premiumDaysPurchased = 30,
                creationTime = 1_600_000_000L,
                houseBidId = 12,
            )
        }
        val account = Account(id = 7, name = "hero", repository = repository)

        assertThat(account.load()).isEqualTo(AccountErrors.Ok)
        assertThat(account.accLoaded).isTrue()
        assertThat(account.getID()).isEqualTo(7)
        assertThat(account.premiumRemainingDays).isEqualTo(15)
        assertThat(account.getPremiumLastDay()).isEqualTo(1_700_000_000L)
        assertThat(account.getAccountType()).isEqualTo(AccountType.ACCOUNT_TYPE_GAMEMASTER)
        assertThat(account.getPremiumDaysPurchased()).isEqualTo(30)
        assertThat(account.getHouseBidId()).isEqualTo(12)
        assertThat(account.getProtocolCompat()).isTrue()
        assertThat(account.creationTime).isEqualTo(1_600_000_000L)
        assertThat(account.sessionExpires).isEqualTo(1_700_000_500L)
    }

    @Test
    fun `load falls back to the descriptor when the id lookup misses`() {
        val repository = FakeAccountRepository().apply {
            infoByName["hero"] = AccountInfo(id = 21, premiumRemainingDays = 3)
        }
        val account = Account(id = 0, name = "hero", repository = repository)

        assertThat(account.load()).isEqualTo(AccountErrors.Ok)
        assertThat(account.getID()).isEqualTo(21)
        assertThat(account.premiumRemainingDays).isEqualTo(3)
    }

    @Test
    fun `load falls back to the session when the name lookup misses`() {
        val repository = FakeAccountRepository().apply {
            infoBySession["session-key"] = AccountInfo(id = 33, premiumRemainingDays = 1)
        }
        val account = Account(name = "session-key", repository = repository)

        assertThat(account.load()).isEqualTo(AccountErrors.Ok)
        assertThat(account.getID()).isEqualTo(33)
    }

    @Test
    fun `load prefers the id lookup over the descriptor`() {
        val repository = FakeAccountRepository().apply {
            infoById[7] = AccountInfo(id = 7, premiumRemainingDays = 5)
            infoByName["hero"] = AccountInfo(id = 99, premiumRemainingDays = 9)
        }
        val account = Account(id = 7, name = "hero", repository = repository)

        assertThat(account.load()).isEqualTo(AccountErrors.Ok)
        assertThat(account.getID()).isEqualTo(7)
        assertThat(account.premiumRemainingDays).isEqualTo(5)
    }

    @Test
    fun `load passes the protocol compat flag to the descriptor lookup`() {
        val repository = FakeAccountRepository().apply {
            infoByName["hero"] = AccountInfo(id = 21)
        }
        val account = Account(id = 0, name = "hero", repository = repository)
        account.setProtocolCompat(true)

        assertThat(account.load()).isEqualTo(AccountErrors.Ok)

        assertThat(repository.lastOldProtocol).isTrue()
    }

    @Test
    fun `load returns loading account and stays unloaded when every lookup misses`() {
        val account = Account(id = 7, name = "hero", repository = FakeAccountRepository())

        assertThat(account.load()).isEqualTo(AccountErrors.LoadingAccount)
        assertThat(account.accLoaded).isFalse()
    }

    @Test
    fun `load failure updates the premium time before returning loading account`() {
        val account = Account(
            id = 7,
            name = "hero",
            premiumLastDay = getTimeNow() - 1,
            premiumRemainingDays = 5,
            repository = FakeAccountRepository(),
        )

        assertThat(account.load()).isEqualTo(AccountErrors.LoadingAccount)

        assertThat(account.premiumRemainingDays).isZero()
        assertThat(account.getPremiumLastDay()).isZero()
    }

    @Test
    fun `reload returns not initialized before the first load`() {
        val account = Account(id = 7, name = "hero", repository = FakeAccountRepository())

        assertThat(account.reload()).isEqualTo(AccountErrors.NotInitialized)
    }

    @Test
    fun `reload re-reads the account after a successful load`() {
        val repository = FakeAccountRepository().apply {
            infoById[7] = AccountInfo(id = 7, premiumRemainingDays = 5)
        }
        val account = Account(id = 7, name = "hero", repository = repository)
        assertThat(account.load()).isEqualTo(AccountErrors.Ok)

        repository.infoById[7] = AccountInfo(id = 7, premiumRemainingDays = 8)

        assertThat(account.reload()).isEqualTo(AccountErrors.Ok)
        assertThat(account.premiumRemainingDays).isEqualTo(8)
    }

    @Test
    fun `save returns not initialized before load`() {
        val account = Account(id = 7, name = "hero", repository = FakeAccountRepository())

        assertThat(account.save()).isEqualTo(AccountErrors.NotInitialized)
    }

    @Test
    fun `save stores the account info snapshot and returns ok`() {
        val repository = FakeAccountRepository().apply {
            infoById[7] = AccountInfo(id = 7, premiumRemainingDays = 4)
        }
        val account = Account(id = 7, name = "hero", repository = repository)
        assertThat(account.load()).isEqualTo(AccountErrors.Ok)

        assertThat(account.save()).isEqualTo(AccountErrors.Ok)
        assertThat(repository.savedInfos).hasSize(1)
        assertThat(repository.savedInfos.single().id).isEqualTo(7)
        assertThat(repository.savedInfos.single().premiumRemainingDays).isEqualTo(4)
    }

    @Test
    fun `save returns storage when the repository write fails`() {
        val repository = FakeAccountRepository().apply {
            infoById[7] = AccountInfo(id = 7)
            saveResult = false
        }
        val account = Account(id = 7, name = "hero", repository = repository)
        assertThat(account.load()).isEqualTo(AccountErrors.Ok)

        assertThat(account.save()).isEqualTo(AccountErrors.Storage)
    }

    @Test
    fun `get password returns empty before load`() {
        val repository = FakeAccountRepository().apply { storedPassword = "hash" }
        val account = Account(id = 7, name = "hero", repository = repository)

        assertThat(account.getPassword()).isEmpty()
    }

    @Test
    fun `get password reads from the repository when loaded`() {
        val repository = FakeAccountRepository().apply {
            infoById[7] = AccountInfo(id = 7)
            storedPassword = "hash"
        }
        val account = Account(id = 7, name = "hero", repository = repository)
        assertThat(account.load()).isEqualTo(AccountErrors.Ok)

        assertThat(account.getPassword()).isEqualTo("hash")
    }

    @Test
    fun `get password returns empty when the repository has none`() {
        val repository = FakeAccountRepository().apply {
            infoById[7] = AccountInfo(id = 7)
        }
        val account = Account(id = 7, name = "hero", repository = repository)
        assertThat(account.load()).isEqualTo(AccountErrors.Ok)

        assertThat(account.getPassword()).isEmpty()
    }
}
