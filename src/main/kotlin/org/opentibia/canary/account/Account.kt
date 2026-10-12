package org.opentibia.canary.account

data class CharacterEntry(
    val name: String,
    val worldName: String = "Canary World",
    val worldIp: String = "127.0.0.1",
    val worldPort: Int = 7172
)

data class Account(
    val id: Long,
    val name: String,
    val passwordHash: String,
    val premiumDays: Int = 30,
    val characters: List<CharacterEntry> = emptyList()
)

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
