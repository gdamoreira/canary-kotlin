package org.opentibia.canary.systems.gamestore

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Transcript tests for the In-Game Store account coin ledger engine core.
 *
 * Expected values are transcribed from Canary:
 *  - src/enums/account_coins.hpp:12-20 (CoinTransactionType, CoinType)
 *  - src/enums/account_errors.hpp:16-23 (AccountErrors_t)
 *  - src/account/account.cpp:78-182 (getCoins/addCoins/removeCoins/registerCoinTransaction)
 *  - src/account/account_repository_db.cpp:20-25,144-262 (coin columns + two-coin split)
 */
class AccountCoinLedgerTest {

    @Test
    fun `coin type enum values match canary`() {
        assertThat(CoinType.Normal.value).isEqualTo(1)
        assertThat(CoinType.Tournament.value).isEqualTo(2)
        assertThat(CoinType.Transferable.value).isEqualTo(3)
    }

    @Test
    fun `coin transaction type enum values match canary`() {
        assertThat(CoinTransactionType.Add.value).isEqualTo(1)
        assertThat(CoinTransactionType.Remove.value).isEqualTo(2)
    }

    @Test
    fun `account errors enum values match canary`() {
        assertThat(AccountErrors.Ok.value).isEqualTo(0)
        assertThat(AccountErrors.Storage.value).isEqualTo(1)
        assertThat(AccountErrors.RemoveCoins.value).isEqualTo(2)
        assertThat(AccountErrors.InvalidLastDay.value).isEqualTo(3)
        assertThat(AccountErrors.LoadingAccount.value).isEqualTo(4)
        assertThat(AccountErrors.NotInitialized.value).isEqualTo(5)
    }

    @Test
    fun `coin columns match canary account repository db`() {
        assertThat(AccountCoinColumns.columnOf(CoinType.Normal)).isEqualTo("coins")
        assertThat(AccountCoinColumns.columnOf(CoinType.Tournament)).isEqualTo("coins_tournament")
        assertThat(AccountCoinColumns.columnOf(CoinType.Transferable)).isEqualTo("coins_transferable")
    }

    @Test
    fun `ledger is not initialized before load`() {
        val repo = InMemoryAccountCoinRepository()
        repo.createAccount(7u, coins = 10u)
        val ledger = AccountCoinLedger(7u, repo)

        val (coins, error) = ledger.getCoins(CoinType.Normal)
        assertThat(coins).isEqualTo(0u)
        assertThat(error).isEqualTo(AccountErrors.NotInitialized)
        assertThat(ledger.addCoins(CoinType.Normal, 5u)).isEqualTo(AccountErrors.NotInitialized)
        assertThat(ledger.removeCoins(CoinType.Normal, 5u)).isEqualTo(AccountErrors.NotInitialized)
    }

    @Test
    fun `load marks the account as loaded`() {
        val repo = InMemoryAccountCoinRepository()
        repo.createAccount(7u)
        val ledger = AccountCoinLedger(7u, repo)

        assertThat(ledger.load()).isEqualTo(AccountErrors.Ok)
        assertThat(ledger.accLoaded).isTrue()
        assertThat(ledger.getCoins(CoinType.Normal)).isEqualTo(0u to AccountErrors.Ok)
    }

    @Test
    fun `load returns loading account when the account is missing`() {
        val ledger = AccountCoinLedger(99u, InMemoryAccountCoinRepository())

        assertThat(ledger.load()).isEqualTo(AccountErrors.LoadingAccount)
        assertThat(ledger.accLoaded).isFalse()
    }

    @Test
    fun `add coins of each type`() {
        val repo = InMemoryAccountCoinRepository()
        repo.createAccount(7u)
        val ledger = loadedLedger(7u, repo)

        assertThat(ledger.addCoins(CoinType.Normal, 100u)).isEqualTo(AccountErrors.Ok)
        assertThat(ledger.addCoins(CoinType.Tournament, 20u)).isEqualTo(AccountErrors.Ok)
        assertThat(ledger.addCoins(CoinType.Transferable, 5u)).isEqualTo(AccountErrors.Ok)

        assertThat(ledger.getCoins(CoinType.Normal)).isEqualTo(100u to AccountErrors.Ok)
        assertThat(ledger.getCoins(CoinType.Tournament)).isEqualTo(20u to AccountErrors.Ok)
        assertThat(ledger.getCoins(CoinType.Transferable)).isEqualTo(5u to AccountErrors.Ok)
    }

    @Test
    fun `add zero coins is a no-op without a transaction`() {
        val repo = InMemoryAccountCoinRepository()
        repo.createAccount(7u, coins = 50u)
        val ledger = loadedLedger(7u, repo)

        assertThat(ledger.addCoins(CoinType.Normal, 0u)).isEqualTo(AccountErrors.Ok)
        assertThat(ledger.getCoins(CoinType.Normal)).isEqualTo(50u to AccountErrors.Ok)
        assertThat(repo.transactions).isEmpty()
    }

    @Test
    fun `add coins registers an add transaction with the given detail`() {
        val repo = InMemoryAccountCoinRepository()
        repo.createAccount(7u)
        val ledger = loadedLedger(7u, repo)

        assertThat(ledger.addCoins(CoinType.Transferable, 30u, "Store purchase")).isEqualTo(AccountErrors.Ok)

        assertThat(repo.transactions).containsExactly(
            CoinTransactionRecord(7u, CoinTransactionType.Add, 30u, CoinType.Transferable, "Store purchase")
        )
    }

    @Test
    fun `add coins uses the default add detail`() {
        val repo = InMemoryAccountCoinRepository()
        repo.createAccount(7u)
        val ledger = loadedLedger(7u, repo)

        ledger.addCoins(CoinType.Normal, 1u)

        assertThat(repo.transactions.single().description).isEqualTo("ADD Coins")
    }

    @Test
    fun `add coins with blank detail skips the transaction`() {
        val repo = InMemoryAccountCoinRepository()
        repo.createAccount(7u)
        val ledger = loadedLedger(7u, repo)

        assertThat(ledger.addCoins(CoinType.Normal, 10u, "")).isEqualTo(AccountErrors.Ok)

        assertThat(ledger.getCoins(CoinType.Normal)).isEqualTo(10u to AccountErrors.Ok)
        assertThat(repo.transactions).isEmpty()
    }

    @Test
    fun `remove coins of each type`() {
        val repo = InMemoryAccountCoinRepository()
        repo.createAccount(7u, coins = 100u, tournament = 20u, transferable = 5u)
        val ledger = loadedLedger(7u, repo)

        assertThat(ledger.removeCoins(CoinType.Normal, 40u)).isEqualTo(AccountErrors.Ok)
        assertThat(ledger.removeCoins(CoinType.Tournament, 20u)).isEqualTo(AccountErrors.Ok)
        assertThat(ledger.removeCoins(CoinType.Transferable, 5u)).isEqualTo(AccountErrors.Ok)

        assertThat(ledger.getCoins(CoinType.Normal)).isEqualTo(60u to AccountErrors.Ok)
        assertThat(ledger.getCoins(CoinType.Tournament)).isEqualTo(0u to AccountErrors.Ok)
        assertThat(ledger.getCoins(CoinType.Transferable)).isEqualTo(0u to AccountErrors.Ok)
    }

    @Test
    fun `remove more than the balance returns remove coins and keeps balance`() {
        val repo = InMemoryAccountCoinRepository()
        repo.createAccount(7u, coins = 10u)
        val ledger = loadedLedger(7u, repo)

        assertThat(ledger.removeCoins(CoinType.Normal, 11u)).isEqualTo(AccountErrors.RemoveCoins)
        assertThat(ledger.getCoins(CoinType.Normal)).isEqualTo(10u to AccountErrors.Ok)
        assertThat(repo.transactions).isEmpty()
    }

    @Test
    fun `remove zero coins is a no-op`() {
        val repo = InMemoryAccountCoinRepository()
        repo.createAccount(7u, coins = 10u)
        val ledger = loadedLedger(7u, repo)

        assertThat(ledger.removeCoins(CoinType.Normal, 0u)).isEqualTo(AccountErrors.Ok)
        assertThat(ledger.getCoins(CoinType.Normal)).isEqualTo(10u to AccountErrors.Ok)
        assertThat(repo.transactions).isEmpty()
    }

    @Test
    fun `remove coins registers a remove transaction with the default detail`() {
        val repo = InMemoryAccountCoinRepository()
        repo.createAccount(7u, coins = 10u)
        val ledger = loadedLedger(7u, repo)

        assertThat(ledger.removeCoins(CoinType.Normal, 4u)).isEqualTo(AccountErrors.Ok)

        assertThat(repo.transactions).containsExactly(
            CoinTransactionRecord(7u, CoinTransactionType.Remove, 4u, CoinType.Normal, "REMOVE Coins")
        )
    }

    @Test
    fun `two coin remove draws from the primary type before the secondary type`() {
        val repo = InMemoryAccountCoinRepository()
        repo.createAccount(7u, tournament = 30u, transferable = 100u)
        val ledger = loadedLedger(7u, repo)

        assertThat(ledger.removeCoins(CoinType.Tournament, CoinType.Transferable, 50u)).isEqualTo(AccountErrors.Ok)

        assertThat(ledger.getCoins(CoinType.Tournament)).isEqualTo(0u to AccountErrors.Ok)
        assertThat(ledger.getCoins(CoinType.Transferable)).isEqualTo(80u to AccountErrors.Ok)
        assertThat(repo.transactions).containsExactly(
            CoinTransactionRecord(7u, CoinTransactionType.Remove, 30u, CoinType.Tournament, "REMOVE Coins"),
            CoinTransactionRecord(7u, CoinTransactionType.Remove, 20u, CoinType.Transferable, "REMOVE Coins")
        )
    }

    @Test
    fun `two coin remove returns remove coins without moving anything when the total is too low`() {
        val repo = InMemoryAccountCoinRepository()
        repo.createAccount(7u, tournament = 10u, transferable = 10u)
        val ledger = loadedLedger(7u, repo)

        assertThat(ledger.removeCoins(CoinType.Tournament, CoinType.Transferable, 21u))
            .isEqualTo(AccountErrors.RemoveCoins)

        assertThat(ledger.getCoins(CoinType.Tournament)).isEqualTo(10u to AccountErrors.Ok)
        assertThat(ledger.getCoins(CoinType.Transferable)).isEqualTo(10u to AccountErrors.Ok)
        assertThat(repo.transactions).isEmpty()
    }

    @Test
    fun `two coin remove with identical types falls back to the single type path`() {
        val repo = InMemoryAccountCoinRepository()
        repo.createAccount(7u, coins = 40u)
        val ledger = loadedLedger(7u, repo)

        assertThat(ledger.removeCoins(CoinType.Normal, CoinType.Normal, 15u)).isEqualTo(AccountErrors.Ok)

        assertThat(ledger.getCoins(CoinType.Normal)).isEqualTo(25u to AccountErrors.Ok)
        assertThat(repo.transactions).containsExactly(
            CoinTransactionRecord(7u, CoinTransactionType.Remove, 15u, CoinType.Normal, "REMOVE Coins")
        )
    }

    @Test
    fun `get coins returns a storage error when the repository has no row`() {
        val ledger = AccountCoinLedger(7u, StorageFailingAccountCoinRepository())
        ledger.load()

        assertThat(ledger.getCoins(CoinType.Normal)).isEqualTo(0u to AccountErrors.Storage)
    }

    @Test
    fun `add coins returns a storage error when set coins fails`() {
        val ledger = AccountCoinLedger(7u, StorageFailingAccountCoinRepository())
        ledger.load()

        assertThat(ledger.addCoins(CoinType.Normal, 10u)).isEqualTo(AccountErrors.Storage)
    }

    private fun loadedLedger(id: UInt, repo: AccountCoinRepository): AccountCoinLedger {
        val ledger = AccountCoinLedger(id, repo)
        assertThat(ledger.load()).isEqualTo(AccountErrors.Ok)
        return ledger
    }
}

/**
 * Repository stub whose account load succeeds but whose coin reads/writes fail,
 * exercising the `Storage` branches of Account::getCoins/addCoins
 * (src/account/account.cpp:84-110).
 */
private class StorageFailingAccountCoinRepository : AccountCoinRepository {
    override fun loadByID(id: UInt): Boolean = true
    override fun getCoins(id: UInt, coinType: CoinType): UInt? = null
    override fun setCoins(id: UInt, coinType: CoinType, amount: UInt): Boolean = false
    override fun removeCoins(
        id: UInt,
        primaryType: CoinType,
        secondaryType: CoinType,
        amount: UInt,
        detail: String,
    ): RemoveCoinsResult = RemoveCoinsResult(AccountErrors.Storage)

    override fun registerCoinsTransaction(
        id: UInt,
        type: CoinTransactionType,
        coins: UInt,
        coinType: CoinType,
        description: String,
    ): Boolean = false
}
