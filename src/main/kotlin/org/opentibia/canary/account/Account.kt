package org.opentibia.canary.account

data class CharacterEntry(
    val name: String,
    val worldName: String = "Canary World",
    val worldIp: String = "127.0.0.1",
    val worldPort: Int = 7172
)

/** `getTimeNow()` (src/utils/utils_definitions.hpp); injectable so premium math is deterministic. */
fun getTimeNow(): Long = System.currentTimeMillis() / 1000L

data class Account(
    val id: Long,
    val name: String,
    val passwordHash: String,
    val premiumDays: Int = 30,
    val characters: List<CharacterEntry> = emptyList(),
    /** `AccountInfo::premiumLastDay` (src/account/account_info.hpp:24). Unix time (seconds) premium runs out. */
    @get:JvmName("premiumLastDayValue")
    val premiumLastDay: Long = 0,
    /** `AccountInfo::premiumRemainingDays` (src/account/account_info.hpp:23). Persisted cache. */
    val premiumRemainingDays: Int = 0,
    /** `AccountInfo::premiumDaysPurchased` (src/account/account_info.hpp:29). */
    val premiumDaysPurchased: Int = 0,
) {
    /** `Account::getPremiumRemainingDays()` (account.cpp:226). */
    fun getPremiumRemainingDays(now: Long = getTimeNow()): Int =
        if (premiumLastDay > now) ((premiumLastDay - now) / SECONDS_PER_DAY).toInt() else 0

    /** `Account::getPremiumLastDay()` (account.cpp:319). */
    fun getPremiumLastDay(): Long = premiumLastDay

    /** `Account::setPremiumDays()` (account.cpp:216). */
    fun setPremiumDays(days: Int, now: Long = getTimeNow()): Account =
        if (days <= 0) {
            copy(premiumRemainingDays = 0, premiumLastDay = 0)
        } else {
            copy(premiumRemainingDays = days, premiumLastDay = now + days.toLong() * SECONDS_PER_DAY)
        }

    companion object {
        const val SECONDS_PER_DAY: Long = 86_400L
    }
}

interface AccountRepository {
    fun findByAccountName(name: String): Account?
    fun authenticate(name: String, password: String): Account?
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
