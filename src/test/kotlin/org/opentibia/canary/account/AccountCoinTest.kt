package org.opentibia.canary.account

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Transcript tests for the Canary `Account` coin ledger:
 *  - `Account::getCoins` (src/account/account.cpp:78-90)
 *  - `Account::addCoins` (src/account/account.cpp:92-115)
 *  - `Account::removeCoins` single-type (src/account/account.cpp:117-145)
 *  - `Account::removeCoins` two-type (src/account/account.cpp:147-164)
 *  - `Account::registerCoinTransaction` (src/account/account.cpp:166-182)
 *  - `AccountRepository` coin seam (src/account/account_repository.hpp:38-55)
 */
class AccountCoinTest {

    @Test
    fun `coin ledger is not initialized before load`() {
        val account = Account(id = 7, name = "hero", repository = FakeAccountRepository())

        assertThat(account.getCoins(CoinType.Normal)).isEqualTo(0u to AccountErrors.NotInitialized)
        assertThat(account.addCoins(CoinType.Normal, 5u)).isEqualTo(AccountErrors.NotInitialized)
        assertThat(account.removeCoins(CoinType.Normal, 5u)).isEqualTo(AccountErrors.NotInitialized)
        assertThat(account.removeCoins(CoinType.Tournament, CoinType.Transferable, 5u))
            .isEqualTo(AccountErrors.NotInitialized)
    }

    @Test
    fun `add coins increases the balance and registers an add transaction`() {
        val (account, repository) = loadedAccount()

        assertThat(account.addCoins(CoinType.Normal, 100u, "Store purchase")).isEqualTo(AccountErrors.Ok)

        assertThat(account.getCoins(CoinType.Normal)).isEqualTo(100u to AccountErrors.Ok)
        assertThat(repository.transactions).containsExactly(
            RecordedCoinTransaction(7, CoinTransactionType.Add, 100u, CoinType.Normal, "Store purchase")
        )
    }

    @Test
    fun `add coins uses the default add detail`() {
        val (account, repository) = loadedAccount()

        assertThat(account.addCoins(CoinType.Normal, 1u)).isEqualTo(AccountErrors.Ok)

        assertThat(repository.transactions.single().description).isEqualTo("ADD Coins")
    }

    @Test
    fun `add zero coins is a no-op without a transaction`() {
        val (account, repository) = loadedAccount()
        repository.balances[7L to CoinType.Normal] = 50u

        assertThat(account.addCoins(CoinType.Normal, 0u)).isEqualTo(AccountErrors.Ok)

        assertThat(account.getCoins(CoinType.Normal)).isEqualTo(50u to AccountErrors.Ok)
        assertThat(repository.transactions).isEmpty()
    }

    @Test
    fun `add coins with an empty detail still moves the balance without a transaction`() {
        val (account, repository) = loadedAccount()

        assertThat(account.addCoins(CoinType.Normal, 10u, "")).isEqualTo(AccountErrors.Ok)

        assertThat(account.getCoins(CoinType.Normal)).isEqualTo(10u to AccountErrors.Ok)
        assertThat(repository.transactions).isEmpty()
    }

    @Test
    fun `add coins returns storage when the balance read fails`() {
        val (account, repository) = loadedAccount()
        repository.failGetCoins = true

        assertThat(account.addCoins(CoinType.Normal, 5u)).isEqualTo(AccountErrors.Storage)
    }

    @Test
    fun `add coins returns storage when the balance write fails`() {
        val (account, repository) = loadedAccount()
        repository.failSetCoins = true

        assertThat(account.addCoins(CoinType.Normal, 5u)).isEqualTo(AccountErrors.Storage)
    }

    @Test
    fun `remove coins decreases the balance and registers a remove transaction`() {
        val (account, repository) = loadedAccount()
        repository.balances[7L to CoinType.Normal] = 50u

        assertThat(account.removeCoins(CoinType.Normal, 20u)).isEqualTo(AccountErrors.Ok)

        assertThat(account.getCoins(CoinType.Normal)).isEqualTo(30u to AccountErrors.Ok)
        assertThat(repository.transactions).containsExactly(
            RecordedCoinTransaction(7, CoinTransactionType.Remove, 20u, CoinType.Normal, "REMOVE Coins")
        )
    }

    @Test
    fun `remove more than the balance returns remove coins and keeps the balance`() {
        val (account, repository) = loadedAccount()
        repository.balances[7L to CoinType.Normal] = 10u

        assertThat(account.removeCoins(CoinType.Normal, 11u)).isEqualTo(AccountErrors.RemoveCoins)

        assertThat(account.getCoins(CoinType.Normal)).isEqualTo(10u to AccountErrors.Ok)
        assertThat(repository.transactions).isEmpty()
    }

    @Test
    fun `remove zero coins is a no-op`() {
        val (account, repository) = loadedAccount()
        repository.balances[7L to CoinType.Normal] = 10u

        assertThat(account.removeCoins(CoinType.Normal, 0u)).isEqualTo(AccountErrors.Ok)

        assertThat(account.getCoins(CoinType.Normal)).isEqualTo(10u to AccountErrors.Ok)
        assertThat(repository.transactions).isEmpty()
    }

    @Test
    fun `remove coins returns storage when the balance write fails`() {
        val (account, repository) = loadedAccount()
        repository.balances[7L to CoinType.Normal] = 10u
        repository.failSetCoins = true

        assertThat(account.removeCoins(CoinType.Normal, 4u)).isEqualTo(AccountErrors.Storage)
    }

    @Test
    fun `two coin remove delegates to the repository split`() {
        val (account, repository) = loadedAccount()
        repository.dualRemoveResult = RemoveCoinsResult(AccountErrors.Ok, 30u, 20u)

        assertThat(account.removeCoins(CoinType.Tournament, CoinType.Transferable, 50u))
            .isEqualTo(AccountErrors.Ok)

        assertThat(repository.dualRemoveCalls).isEqualTo(1)
    }

    @Test
    fun `two coin remove returns the repository error`() {
        val (account, repository) = loadedAccount()
        repository.dualRemoveResult = RemoveCoinsResult(AccountErrors.RemoveCoins)

        assertThat(account.removeCoins(CoinType.Tournament, CoinType.Transferable, 500u))
            .isEqualTo(AccountErrors.RemoveCoins)
    }

    @Test
    fun `two coin remove with identical types falls back to the single type path`() {
        val (account, repository) = loadedAccount()
        repository.balances[7L to CoinType.Normal] = 40u

        assertThat(account.removeCoins(CoinType.Normal, CoinType.Normal, 15u)).isEqualTo(AccountErrors.Ok)

        assertThat(repository.dualRemoveCalls).isZero()
        assertThat(account.getCoins(CoinType.Normal)).isEqualTo(25u to AccountErrors.Ok)
        assertThat(repository.transactions).containsExactly(
            RecordedCoinTransaction(7, CoinTransactionType.Remove, 15u, CoinType.Normal, "REMOVE Coins")
        )
    }

    @Test
    fun `two coin remove with a zero amount is a no-op`() {
        val (account, repository) = loadedAccount()

        assertThat(account.removeCoins(CoinType.Tournament, CoinType.Transferable, 0u))
            .isEqualTo(AccountErrors.Ok)

        assertThat(repository.dualRemoveCalls).isZero()
    }

    @Test
    fun `get coins returns storage when the repository read fails`() {
        val (account, repository) = loadedAccount()
        repository.failGetCoins = true

        assertThat(account.getCoins(CoinType.Normal)).isEqualTo(0u to AccountErrors.Storage)
    }

    @Test
    fun `register coin transaction skips an empty detail`() {
        val (account, repository) = loadedAccount()

        account.registerCoinTransaction(CoinTransactionType.Add, CoinType.Normal, 5u, "")

        assertThat(repository.transactions).isEmpty()
    }

    @Test
    fun `register coin transaction before load does nothing`() {
        val repository = FakeAccountRepository()
        val account = Account(id = 7, name = "hero", repository = repository)

        account.registerCoinTransaction(CoinTransactionType.Add, CoinType.Normal, 5u, "detail")

        assertThat(repository.transactions).isEmpty()
    }

    @Test
    fun `register coin transaction records the transaction`() {
        val (account, repository) = loadedAccount()

        account.registerCoinTransaction(CoinTransactionType.Add, CoinType.Tournament, 5u, "detail")

        assertThat(repository.transactions).containsExactly(
            RecordedCoinTransaction(7, CoinTransactionType.Add, 5u, CoinType.Tournament, "detail")
        )
    }

    private fun loadedAccount(id: Long = 7): Pair<Account, FakeAccountRepository> {
        val repository = FakeAccountRepository().apply { infoById[id] = AccountInfo(id = id) }
        val account = Account(id = id, name = "hero", repository = repository)
        assertThat(account.load()).isEqualTo(AccountErrors.Ok)
        return account to repository
    }
}
