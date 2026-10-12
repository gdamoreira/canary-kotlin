package org.opentibia.canary.systems.gamestore

import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Mirror of Canary `CoinType` (src/enums/account_coins.hpp:17-20).
 * Underlying type is uint8_t; values are preserved exactly.
 */
enum class CoinType(val value: Int) {
    Normal(1),
    Tournament(2),
    Transferable(3),
}

/**
 * Mirror of Canary `CoinTransactionType` (src/enums/account_coins.hpp:12-15).
 */
enum class CoinTransactionType(val value: Int) {
    Add(1),
    Remove(2),
}

/**
 * Mirror of Canary `AccountErrors_t` (src/enums/account_errors.hpp:16-23).
 * Returned by the coin ledger exactly as in `Account::getCoins/addCoins/removeCoins`.
 */
enum class AccountErrors(val value: Int) {
    Ok(0),
    Storage(1),
    RemoveCoins(2),
    InvalidLastDay(3),
    LoadingAccount(4),
    NotInitialized(5),
}

/**
 * Mirror of the `coinTypeToColumn` table built by `AccountRepositoryDB`
 * (src/account/account_repository_db.cpp:20-25).
 */
object AccountCoinColumns {
    fun columnOf(type: CoinType): String = when (type) {
        CoinType.Normal -> "coins"
        CoinType.Tournament -> "coins_tournament"
        CoinType.Transferable -> "coins_transferable"
    }
}

/**
 * Result of the two-coin removal. Canary models this with the `AccountErrors_t`
 * return plus the `primaryCoinsRemoved`/`secondaryCoinsRemoved` out-parameters of
 * `AccountRepository::removeCoins` (src/account/account_repository.hpp:40-48);
 * Kotlin folds the out-parameters into this data class.
 */
data class RemoveCoinsResult(
    val error: AccountErrors,
    val primaryCoinsRemoved: UInt = 0u,
    val secondaryCoinsRemoved: UInt = 0u,
)

/**
 * One row of the `coins_transactions` audit table written by
 * `AccountRepositoryDB::registerCoinsTransaction`
 * (src/account/account_repository_db.cpp:232-262).
 */
data class CoinTransactionRecord(
    val accountId: UInt,
    val type: CoinTransactionType,
    val coins: UInt,
    val coinType: CoinType,
    val description: String,
)

/**
 * Dependency-injected replacement for Canary's `AccountRepository` coin access
 * (src/account/account_repository.hpp:38-55, implemented by
 * `AccountRepositoryDB` in src/account/account_repository_db.cpp:97-262).
 *
 * Out-parameters are adapted to Kotlin returns; behavior is preserved.
 */
interface AccountCoinRepository {
    /** Mirrors `AccountRepository::loadByID` (account_repository_db.cpp:28-31). */
    fun loadByID(id: UInt): Boolean

    /** Mirrors `AccountRepository::getCoins`; `null` means the read failed (`Storage`). */
    fun getCoins(id: UInt, coinType: CoinType): UInt?

    /** Mirrors `AccountRepository::setCoins` (account_repository_db.cpp:121-142). */
    fun setCoins(id: UInt, coinType: CoinType, amount: UInt): Boolean

    /** Mirrors `AccountRepository::removeCoins` (account_repository_db.cpp:144-230). */
    fun removeCoins(
        id: UInt,
        primaryType: CoinType,
        secondaryType: CoinType,
        amount: UInt,
        detail: String,
    ): RemoveCoinsResult

    /** Mirrors `AccountRepository::registerCoinsTransaction` (account_repository_db.cpp:232-262). */
    fun registerCoinsTransaction(
        id: UInt,
        type: CoinTransactionType,
        coins: UInt,
        coinType: CoinType,
        description: String,
    ): Boolean
}

/**
 * In-memory stand-in for `AccountRepositoryDB`, used for local runs and tests.
 * Implements the same two-coin split, insufficient-funds check, and
 * rollback-on-failure semantics as the SQL transaction.
 */
class InMemoryAccountCoinRepository : AccountCoinRepository {
    private val balances = mutableMapOf<UInt, MutableMap<CoinType, UInt>>()
    val transactions = mutableListOf<CoinTransactionRecord>()

    fun createAccount(
        id: UInt,
        coins: UInt = 0u,
        tournament: UInt = 0u,
        transferable: UInt = 0u,
    ) {
        balances[id] = mutableMapOf(
            CoinType.Normal to coins,
            CoinType.Tournament to tournament,
            CoinType.Transferable to transferable,
        )
    }

    override fun loadByID(id: UInt): Boolean = balances.containsKey(id)

    override fun getCoins(id: UInt, coinType: CoinType): UInt? = balances[id]?.get(coinType)

    override fun setCoins(id: UInt, coinType: CoinType, amount: UInt): Boolean {
        val account = balances[id] ?: return false
        account[coinType] = amount
        return true
    }

    override fun removeCoins(
        id: UInt,
        primaryType: CoinType,
        secondaryType: CoinType,
        amount: UInt,
        detail: String,
    ): RemoveCoinsResult {
        val account = balances[id] ?: return RemoveCoinsResult(AccountErrors.Storage)
        if (primaryType == secondaryType) {
            return RemoveCoinsResult(AccountErrors.Storage)
        }

        val primaryCoins = account[primaryType] ?: return RemoveCoinsResult(AccountErrors.Storage)
        val secondaryCoins = account[secondaryType] ?: return RemoveCoinsResult(AccountErrors.Storage)

        if (primaryCoins.toULong() + secondaryCoins.toULong() < amount) {
            return RemoveCoinsResult(AccountErrors.RemoveCoins)
        }

        val primaryCoinsRemoved = minOf(primaryCoins, amount)
        val secondaryCoinsRemoved = amount - primaryCoinsRemoved

        logger.info {
            "[removeCoins]: account:[$id], primary:[$primaryCoinsRemoved], secondary:[$secondaryCoinsRemoved]"
        }

        account[primaryType] = primaryCoins - primaryCoinsRemoved
        account[secondaryType] = secondaryCoins - secondaryCoinsRemoved

        if (detail.isNotEmpty() && primaryCoinsRemoved > 0u &&
            !registerCoinsTransaction(id, CoinTransactionType.Remove, primaryCoinsRemoved, primaryType, detail)
        ) {
            account[primaryType] = primaryCoins
            account[secondaryType] = secondaryCoins
            return RemoveCoinsResult(AccountErrors.Storage)
        }

        if (detail.isNotEmpty() && secondaryCoinsRemoved > 0u &&
            !registerCoinsTransaction(id, CoinTransactionType.Remove, secondaryCoinsRemoved, secondaryType, detail)
        ) {
            account[primaryType] = primaryCoins
            account[secondaryType] = secondaryCoins
            return RemoveCoinsResult(AccountErrors.Storage)
        }

        return RemoveCoinsResult(AccountErrors.Ok, primaryCoinsRemoved, secondaryCoinsRemoved)
    }

    override fun registerCoinsTransaction(
        id: UInt,
        type: CoinTransactionType,
        coins: UInt,
        coinType: CoinType,
        description: String,
    ): Boolean {
        transactions.add(CoinTransactionRecord(id, type, coins, coinType, description))
        return true
    }
}

/**
 * Coin-scoped mirror of Canary class `Account`
 * (src/account/account.hpp:19-142). Implements the coin subset exactly:
 * `load`, `getCoins`, `addCoins`, `removeCoins` (both overloads), and
 * `registerCoinTransaction` (src/account/account.cpp:38-182).
 *
 * `accLoaded` mirrors the private `m_accLoaded` gate: no coin operation touches
 * storage until `load()` succeeds.
 */
class AccountCoinLedger(
    private val accountId: UInt,
    private val repository: AccountCoinRepository,
) {
    var accLoaded: Boolean = false
        private set

    /**
     * Mirrors the account-id branch of `Account::load`
     * (src/account/account.cpp:38-43). The descriptor-based branches are out of
     * scope for the coin-only port.
     */
    fun load(): AccountErrors {
        if (accountId != 0u && repository.loadByID(accountId)) {
            accLoaded = true
            return AccountErrors.Ok
        }
        return AccountErrors.LoadingAccount
    }

    /** Mirror of `Account::getCoins` (src/account/account.cpp:78-90). */
    fun getCoins(type: CoinType): Pair<UInt, AccountErrors> {
        if (!accLoaded) {
            return 0u to AccountErrors.NotInitialized
        }

        val coins = repository.getCoins(accountId, type) ?: return 0u to AccountErrors.Storage

        return coins to AccountErrors.Ok
    }

    /** Mirror of `Account::addCoins` (src/account/account.cpp:92-115). */
    fun addCoins(type: CoinType, amount: UInt, detail: String = "ADD Coins"): AccountErrors {
        if (!accLoaded) {
            return AccountErrors.NotInitialized
        }

        if (amount == 0u) {
            return AccountErrors.Ok
        }

        val (coins, result) = getCoins(type)
        if (result != AccountErrors.Ok) {
            return result
        }

        if (!repository.setCoins(accountId, type, coins + amount)) {
            return AccountErrors.Storage
        }

        registerCoinTransaction(CoinTransactionType.Add, type, amount, detail)

        return AccountErrors.Ok
    }

    /** Mirror of `Account::removeCoins` single-type overload (src/account/account.cpp:117-145). */
    fun removeCoins(type: CoinType, amount: UInt, detail: String = "REMOVE Coins"): AccountErrors {
        if (!accLoaded) {
            return AccountErrors.NotInitialized
        }

        if (amount == 0u) {
            return AccountErrors.Ok
        }

        val (coins, result) = getCoins(type)
        if (result != AccountErrors.Ok) {
            return result
        }

        if (coins < amount) {
            logger.info { "Account doesn't have enough coins! current[$coins], remove:[$amount]" }
            return AccountErrors.RemoveCoins
        }

        if (!repository.setCoins(accountId, type, coins - amount)) {
            return AccountErrors.Storage
        }

        registerCoinTransaction(CoinTransactionType.Remove, type, amount, detail)

        return AccountErrors.Ok
    }

    /** Mirror of `Account::removeCoins` two-type overload (src/account/account.cpp:147-164). */
    fun removeCoins(
        primaryType: CoinType,
        secondaryType: CoinType,
        amount: UInt,
        detail: String = "REMOVE Coins",
    ): AccountErrors {
        if (!accLoaded) {
            return AccountErrors.NotInitialized
        }

        if (amount == 0u) {
            return AccountErrors.Ok
        }

        if (primaryType == secondaryType) {
            return removeCoins(primaryType, amount, detail)
        }

        return repository.removeCoins(accountId, primaryType, secondaryType, amount, detail).error
    }

    /**
     * Mirror of `Account::registerCoinTransaction` (src/account/account.cpp:166-182).
     * Failures are logged and swallowed, as in Canary.
     */
    fun registerCoinTransaction(
        transactionType: CoinTransactionType,
        type: CoinType,
        amount: UInt,
        detail: String,
    ) {
        if (!accLoaded) {
            return
        }

        if (detail.isEmpty()) {
            return
        }

        if (!repository.registerCoinsTransaction(accountId, transactionType, amount, type, detail)) {
            logger.error {
                "Failed to register transaction: 'account:[$accountId], transaction " +
                    "type:[$transactionType], coins:[$amount], coin type:[$type], description:[$detail]"
            }
        }
    }
}
