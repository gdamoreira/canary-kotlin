package org.opentibia.canary.account

import io.github.oshai.kotlinlogging.KotlinLogging
import org.opentibia.canary.network.crypto.Argon2Security
import org.opentibia.canary.utils.Tools

private val logger = KotlinLogging.logger {}

data class CharacterEntry(
    val name: String,
    val worldName: String = "Canary World",
    val worldIp: String = "127.0.0.1",
    val worldPort: Int = 7172
)

/** `getTimeNow()` (src/utils/utils_definitions.hpp); injectable so premium math is deterministic. */
fun getTimeNow(): Long = System.currentTimeMillis() / 1000L

/**
 * Mirror of Canary `class Account` (src/account/account.hpp:19-142,
 * src/account/account.cpp:20-328).
 *
 * The class keeps the pre-existing Kotlin fields and named constructor
 * parameters (`id`, `name`, `passwordHash`, `premiumDays`, `characters`, ...)
 * so every existing call site keeps compiling. The Canary `AccountInfo` payload
 * is represented by [AccountInfo], and the global `g_accountRepository()` is
 * replaced by the constructor-injected [repository] seam (AGENTS.md 1.1).
 *
 * PARITY NOTES:
 *  - Canary stores `m_descriptor` (email or account name used at login); the
 *    port's [name] plays that role for `getDescriptor`/`load`.
 *  - The port keeps the legacy [premiumDays] display field used by the login
 *    protocol alongside `AccountInfo::premiumRemainingDays`.
 *  - `Account::addPremiumDays`/`updatePremiumTime` take an optional `now`
 *    (defaults to `getTimeNow()`) so tests stay deterministic, like the
 *    pre-existing premium helpers.
 */
class Account(
    var id: Long = 0,
    val name: String = "",
    val passwordHash: String = "",
    var premiumDays: Int = 30,
    val characters: List<CharacterEntry> = emptyList(),
    /** `AccountInfo::premiumLastDay` (src/account/account_info.hpp:24). Unix time (seconds) premium runs out. */
    @get:JvmName("premiumLastDayValue")
    var premiumLastDay: Long = 0,
    /** `AccountInfo::premiumRemainingDays` (src/account/account_info.hpp:23). Persisted cache. */
    var premiumRemainingDays: Int = 0,
    /** `AccountInfo::premiumDaysPurchased` (src/account/account_info.hpp:29). */
    @get:JvmName("premiumDaysPurchasedValue")
    var premiumDaysPurchased: Int = 0,
    /** `AccountInfo::accountType` (src/account/account_info.hpp:25). */
    @get:JvmName("accountTypeValue")
    @set:JvmName("accountTypeValueSetter")
    var accountType: AccountType = AccountType.ACCOUNT_TYPE_NORMAL,
    /** `AccountInfo::houseBidId` (src/account/account_info.hpp:31). */
    @get:JvmName("houseBidIdValue")
    @set:JvmName("houseBidIdValueSetter")
    var houseBidId: Int = 0,
    /** `AccountInfo::sessionExpires` (src/account/account_info.hpp:28). */
    var sessionExpires: Long = 0,
    /** `AccountInfo::creationTime` (src/account/account_info.hpp:30). */
    var creationTime: Long = 0,
    /** `AccountInfo::oldProtocol` (src/account/account_info.hpp:27). */
    var oldProtocol: Boolean = false,
    /** `g_accountRepository()` replacement (src/account/account_repository.hpp:18-56). */
    val repository: AccountRepository = NoopAccountRepository,
) {
    /** `m_accLoaded` (src/account/account.hpp:141). Gates every storage operation. */
    var accLoaded: Boolean = false
        private set

    /** `AccountInfo::players` (src/account/account_info.hpp:26): character name to player GUID. */
    private var players: MutableMap<String, ULong> = mutableMapOf()

    /***************************************************************************
     * Account Load/Save
     **************************************************************************/

    /** `Account::load()` (src/account/account.cpp:38-57). */
    fun load(): AccountErrors {
        val snapshot = snapshotInfo()
        val loadedById = id != 0L && repository.loadByID(id, snapshot)
        val loadedByName = !loadedById && name.isNotEmpty() &&
            repository.loadByEmailOrName(oldProtocol, name, snapshot)
        val loadedBySession = !loadedById && !loadedByName && name.isNotEmpty() &&
            repository.loadBySession(name, snapshot)

        if (loadedById || loadedByName || loadedBySession) {
            applyInfo(snapshot)
            accLoaded = true
            return AccountErrors.Ok
        }

        updatePremiumTime()
        return AccountErrors.LoadingAccount
    }

    /** `Account::reload()` (src/account/account.cpp:59-65). */
    fun reload(): AccountErrors {
        if (!accLoaded) {
            return AccountErrors.NotInitialized
        }
        return load()
    }

    /** `Account::save()` (src/account/account.cpp:67-76). */
    fun save(): AccountErrors {
        if (!accLoaded) {
            return AccountErrors.NotInitialized
        }
        if (!repository.save(snapshotInfo())) {
            return AccountErrors.Storage
        }
        return AccountErrors.Ok
    }

    /***************************************************************************
     * Coins
     **************************************************************************/

    /** `Account::getCoins()` (src/account/account.cpp:78-90). */
    fun getCoins(type: CoinType): Pair<UInt, AccountErrors> {
        if (!accLoaded) {
            return 0u to AccountErrors.NotInitialized
        }

        val coins = repository.getCoins(id, type) ?: return 0u to AccountErrors.Storage

        return coins to AccountErrors.Ok
    }

    /** `Account::addCoins()` (src/account/account.cpp:92-115). */
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

        if (!repository.setCoins(id, type, coins + amount)) {
            return AccountErrors.Storage
        }

        registerCoinTransaction(CoinTransactionType.Add, type, amount, detail)

        return AccountErrors.Ok
    }

    /** `Account::removeCoins()` single-type overload (src/account/account.cpp:117-145). */
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

        if (!repository.setCoins(id, type, coins - amount)) {
            return AccountErrors.Storage
        }

        registerCoinTransaction(CoinTransactionType.Remove, type, amount, detail)

        return AccountErrors.Ok
    }

    /** `Account::removeCoins()` two-type overload (src/account/account.cpp:147-164). */
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

        return repository.removeCoins(id, primaryType, secondaryType, amount, detail).error
    }

    /** `Account::registerCoinTransaction()` (src/account/account.cpp:166-182). */
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

        if (!repository.registerCoinsTransaction(id, transactionType, amount, type, detail)) {
            logger.error {
                "Failed to register transaction: 'account:[$id], transaction " +
                    "type:[$transactionType], coins:[$amount], coin type:[$type], description:[$detail]"
            }
        }
    }

    /***************************************************************************
     * Setters and Getters
     **************************************************************************/

    /** `Account::getID()` (src/account/account.cpp:184-186). */
    fun getID(): Long = id

    /** `Account::getDescriptor()` (src/account/account.cpp:188-190). */
    fun getDescriptor(): String = name

    /** `Account::getPassword()` (src/account/account.cpp:192-204). */
    fun getPassword(): String {
        if (!accLoaded) {
            return ""
        }

        val password = repository.getPassword(id)
        if (password == null) {
            logger.error { "Failed to get password for account[$id]!" }
            return ""
        }

        return password
    }

    /** `Account::getPremiumRemainingDays()` (account.cpp:226). */
    fun getPremiumRemainingDays(now: Long = getTimeNow()): Int =
        if (premiumLastDay > now) ((premiumLastDay - now) / SECONDS_PER_DAY).toInt() else 0

    /** `Account::getPremiumLastDay()` (account.cpp:319). */
    fun getPremiumLastDay(): Long = premiumLastDay

    /** `Account::getPremiumDaysPurchased()` (src/account/account.cpp:230-232). */
    fun getPremiumDaysPurchased(): Int = premiumDaysPurchased

    /** `Account::setPremiumDays()` (account.cpp:216). */
    fun setPremiumDays(days: Int, now: Long = getTimeNow()): Account {
        if (days <= 0) {
            premiumRemainingDays = 0
            premiumLastDay = 0
        } else {
            premiumRemainingDays = days
            premiumLastDay = now + days.toLong() * SECONDS_PER_DAY
        }
        return this
    }

    /** `Account::addPremiumDays()` (src/account/account.cpp:206-214). */
    fun addPremiumDays(days: Int, now: Long = getTimeNow()) {
        val timeLeft = maxOf(0L, (premiumLastDay - now) % SECONDS_PER_DAY)
        setPremiumDays(premiumRemainingDays + days, now)
        premiumDaysPurchased += days

        if (timeLeft > 0L) {
            premiumLastDay += timeLeft
        }
    }

    /** `Account::getAccountAgeInDays()` (src/account/account.cpp:315-317). */
    fun getAccountAgeInDays(now: Long = getTimeNow()): Long = (now - creationTime) / SECONDS_PER_DAY

    /** `Account::setAccountType()` (src/account/account.cpp:234-237). */
    fun setAccountType(accountType: AccountType): AccountErrors {
        this.accountType = accountType
        return AccountErrors.Ok
    }

    /** `Account::getAccountType()` (src/account/account.cpp:239-241). */
    fun getAccountType(): AccountType = accountType

    /** `Account::updatePremiumTime()` (src/account/account.cpp:243-269). */
    fun updatePremiumTime(now: Long = getTimeNow()) {
        val lastDay = premiumLastDay
        val remainingDays = premiumRemainingDays

        val daysLeft = (lastDay - now) / SECONDS_PER_DAY
        val timeLeft = (lastDay - now) % SECONDS_PER_DAY

        premiumRemainingDays = if (daysLeft > 0) daysLeft.toInt() else 0

        if (daysLeft == 0L && timeLeft == 0L) {
            setPremiumDays(0, now)
        }

        if (lastDay < now || lastDay == 0L) {
            setPremiumDays(0, now)
        }

        if (remainingDays == premiumRemainingDays) {
            return
        }

        if (AccountErrors.Ok != save()) {
            logger.error { "Failed to update account premium time: [${getDescriptor()}]" }
        }
    }

    /** `Account::getAccountPlayers()` (src/account/account.cpp:271-276). */
    fun getAccountPlayers(): Pair<Map<String, ULong>, AccountErrors> =
        if (accLoaded) {
            players.toMap() to AccountErrors.Ok
        } else {
            emptyMap<String, ULong>() to AccountErrors.NotInitialized
        }

    /** `Account::setHouseBidId()` (src/account/account.cpp:326-328). */
    fun setHouseBidId(houseId: Int) {
        houseBidId = houseId
    }

    /** `Account::getHouseBidId()` (src/account/account.cpp:323-325). */
    fun getHouseBidId(): Int = houseBidId

    /** `Account::setProtocolCompat()` (src/account/account.cpp:278-280). */
    fun setProtocolCompat(toggle: Boolean) {
        oldProtocol = toggle
    }

    /** `Account::getProtocolCompat()` (src/account/account.cpp:281-283). */
    fun getProtocolCompat(): Boolean = oldProtocol

    /***************************************************************************
     * Authentication
     **************************************************************************/

    /** `Account::authenticate()` (src/account/account.cpp:285-288). */
    fun authenticate(): Boolean {
        // authenticate called without secret, so we use session authentication
        return authenticateSession()
    }

    /** `Account::authenticate(secret)` (src/account/account.cpp:290-292). */
    fun authenticate(secret: String): Boolean = authenticatePassword(secret)

    /** `Account::authenticateSession()` (src/account/account.cpp:294-300). */
    fun authenticateSession(now: Long = getTimeNow()): Boolean {
        if (sessionExpires < now) {
            logger.error {
                "Session expired for account[$id] expired at [$sessionExpires] current time [$now]!"
            }
            return false
        }
        return true
    }

    /**
     * `Account::authenticatePassword()` (src/account/account.cpp:302-313): the
     * argon2id hash wins, then the legacy `transformToSHA1` comparison.
     *
     * PARITY NOTE: Canary's regex-based argon verifier also accepts a raw
     * `$salt$hash` pair and hashes with the configured m/t/p constants; the port
     * delegates to Argon2Security, which only recognizes the `$argon2id$` form
     * and uses the parameters embedded in the stored hash.
     */
    fun authenticatePassword(password: String): Boolean {
        val storedPassword = getPassword()

        // `Argon2Security.verifyPassword` verifies the `$argon2id$` form and
        // falls back to plaintext equality for non-argon hashes (the port's
        // Flyway seed and local test accounts), keeping the C++ chain's
        // argon2id step plus the port's plaintext fallback.
        if (Argon2Security.verifyPassword(password, storedPassword)) {
            return true
        }

        if (Tools.transformToSHA1(password) == storedPassword) {
            return true
        }

        logger.error { "Password doesn't match for account id [$id]" }
        return false
    }

    private fun snapshotInfo(): AccountInfo = AccountInfo(
        id = id,
        premiumRemainingDays = premiumRemainingDays,
        premiumLastDay = premiumLastDay,
        accountType = accountType,
        players = LinkedHashMap(players),
        oldProtocol = oldProtocol,
        sessionExpires = sessionExpires,
        premiumDaysPurchased = premiumDaysPurchased,
        creationTime = creationTime,
        houseBidId = houseBidId,
    )

    private fun applyInfo(info: AccountInfo) {
        id = info.id
        premiumRemainingDays = info.premiumRemainingDays
        premiumLastDay = info.premiumLastDay
        accountType = info.accountType
        players = info.players
        oldProtocol = info.oldProtocol
        sessionExpires = info.sessionExpires
        premiumDaysPurchased = info.premiumDaysPurchased
        creationTime = info.creationTime
        houseBidId = info.houseBidId
    }

    companion object {
        const val SECONDS_PER_DAY: Long = 86_400L
    }
}

/**
 * Kotlin replacement for the global `g_accountRepository()`
 * (src/account/account_repository.hpp:18-58).
 *
 * The two pre-existing login lookups keep their contracts; the Canary storage
 * surface carries default failing implementations so that every existing
 * implementation (and test double) of this interface keeps compiling while the
 * database implementation (`JdbcAccountRepository`) is completed separately.
 */
interface AccountRepository {
    fun findByAccountName(name: String): Account?

    /**
     * `AccountRepositoryDB::loadByEmailOrName` (src/account/account_repository_db.cpp:33-37):
     * modern clients (`oldProtocol = false`) resolve the account by `email`,
     * old-protocol clients by `name`. The default keeps name-based
     * repositories (in-memory/tests) working.
     */
    fun findByEmailOrName(oldProtocol: Boolean, descriptor: String): Account? = findByAccountName(descriptor)

    fun authenticate(name: String, password: String): Account?

    /** `AccountRepository::loadByID` (src/account/account_repository.hpp:29). */
    fun loadByID(id: Long, acc: AccountInfo): Boolean = false

    /** `AccountRepository::loadByEmailOrName` (src/account/account_repository.hpp:30). */
    fun loadByEmailOrName(oldProtocol: Boolean, emailOrName: String, acc: AccountInfo): Boolean = false

    /** `AccountRepository::loadBySession` (src/account/account_repository.hpp:31). */
    fun loadBySession(sessionKey: String, acc: AccountInfo): Boolean = false

    /** `AccountRepository::save` (src/account/account_repository.hpp:32). */
    fun save(accInfo: AccountInfo): Boolean = false

    /** `AccountRepository::getCharacterByAccountIdAndName` (src/account/account_repository.hpp:34). */
    fun getCharacterByAccountIdAndName(id: Long, name: String): Boolean = false

    /** `AccountRepository::getPassword` (src/account/account_repository.hpp:36); `null` means the read failed. */
    fun getPassword(id: Long): String? = null

    /** `AccountRepository::getCoins` (src/account/account_repository.hpp:38); `null` means the read failed. */
    fun getCoins(id: Long, coinType: CoinType): UInt? = null

    /** `AccountRepository::setCoins` (src/account/account_repository.hpp:39). */
    fun setCoins(id: Long, coinType: CoinType, amount: UInt): Boolean = false

    /** `AccountRepository::removeCoins` (src/account/account_repository.hpp:40-48). */
    fun removeCoins(
        id: Long,
        primaryType: CoinType,
        secondaryType: CoinType,
        amount: UInt,
        detail: String,
    ): RemoveCoinsResult = RemoveCoinsResult(AccountErrors.Storage)

    /** `AccountRepository::registerCoinsTransaction` (src/account/account_repository.hpp:49-55). */
    fun registerCoinsTransaction(
        id: Long,
        type: CoinTransactionType,
        coins: UInt,
        coinType: CoinType,
        description: String,
    ): Boolean = false
}

/** `g_accountRepository()` stand-in used when no persistence seam is wired. */
object NoopAccountRepository : AccountRepository {
    override fun findByAccountName(name: String): Account? = null

    override fun authenticate(name: String, password: String): Account? = null
}

class InMemoryAccountRepository : AccountRepository {
    private val accounts = mutableMapOf<String, Account>()

    init {
        // Default sample account (matches typical OTS 1/1 test credentials)
        val sampleAccount = Account(
            id = 1,
            name = "1",
            passwordHash = "1",
            premiumDays = 90,
            characters = listOf(
                CharacterEntry("Knight Hero", worldName = "Canary World", worldIp = "127.0.0.1", worldPort = 7172),
                CharacterEntry("Sorcerer Master", worldName = "Canary World", worldIp = "127.0.0.1", worldPort = 7172)
            )
        )
        accounts[sampleAccount.name.lowercase()] = sampleAccount
    }

    override fun findByAccountName(name: String): Account? {
        return accounts[name.lowercase()]
    }

    override fun authenticate(name: String, password: String): Account? {
        val account = findByAccountName(name) ?: return null
        return if (account.passwordHash == password) account else null
    }
}
