package org.opentibia.canary.account

/**
 * In-memory [AccountRepository] driven by test fixtures. It records every seam
 * call so the Account transcript tests can assert the model behavior described
 * by Canary `AccountRepository` (src/account/account_repository.hpp:29-55)
 * without touching a database.
 */
class FakeAccountRepository : AccountRepository {

    val infoById = mutableMapOf<Long, AccountInfo>()
    val infoByName = mutableMapOf<String, AccountInfo>()
    val infoBySession = mutableMapOf<String, AccountInfo>()
    val savedInfos = mutableListOf<AccountInfo>()
    val balances = mutableMapOf<Pair<Long, CoinType>, UInt>()
    val transactions = mutableListOf<RecordedCoinTransaction>()

    var storedPassword: String? = null
    var saveResult = true
    var failGetCoins = false
    var failSetCoins = false
    var dualRemoveResult = RemoveCoinsResult(AccountErrors.Ok)
    var dualRemoveCalls = 0
    var lastOldProtocol: Boolean? = null

    override fun findByAccountName(name: String): Account? = null

    override fun authenticate(name: String, password: String): Account? = null

    override fun loadByID(id: Long, acc: AccountInfo): Boolean {
        val loaded = infoById[id] ?: return false
        acc.copyFrom(loaded)
        return true
    }

    override fun loadByEmailOrName(oldProtocol: Boolean, emailOrName: String, acc: AccountInfo): Boolean {
        lastOldProtocol = oldProtocol
        val loaded = infoByName[emailOrName] ?: return false
        acc.copyFrom(loaded)
        return true
    }

    override fun loadBySession(sessionKey: String, acc: AccountInfo): Boolean {
        val loaded = infoBySession[sessionKey] ?: return false
        acc.copyFrom(loaded)
        return true
    }

    override fun save(accInfo: AccountInfo): Boolean {
        savedInfos += accInfo
        return saveResult
    }

    override fun getPassword(id: Long): String? = storedPassword

    override fun getCoins(id: Long, coinType: CoinType): UInt? =
        if (failGetCoins) null else balances[id to coinType] ?: 0u

    override fun setCoins(id: Long, coinType: CoinType, amount: UInt): Boolean {
        if (failSetCoins) {
            return false
        }
        balances[id to coinType] = amount
        return true
    }

    override fun removeCoins(
        id: Long,
        primaryType: CoinType,
        secondaryType: CoinType,
        amount: UInt,
        detail: String,
    ): RemoveCoinsResult {
        dualRemoveCalls++
        return dualRemoveResult
    }

    override fun registerCoinsTransaction(
        id: Long,
        type: CoinTransactionType,
        coins: UInt,
        coinType: CoinType,
        description: String,
    ): Boolean {
        transactions += RecordedCoinTransaction(id, type, coins, coinType, description)
        return true
    }
}

/** Row shape recorded by [FakeAccountRepository.registerCoinsTransaction]. */
data class RecordedCoinTransaction(
    val accountId: Long,
    val type: CoinTransactionType,
    val coins: UInt,
    val coinType: CoinType,
    val description: String,
)

private fun AccountInfo.copyFrom(other: AccountInfo) {
    id = other.id
    premiumRemainingDays = other.premiumRemainingDays
    premiumLastDay = other.premiumLastDay
    accountType = other.accountType
    players = LinkedHashMap(other.players)
    oldProtocol = other.oldProtocol
    sessionExpires = other.sessionExpires
    premiumDaysPurchased = other.premiumDaysPurchased
    creationTime = other.creationTime
    houseBidId = other.houseBidId
}
