package org.opentibia.canary.database

import com.zaxxer.hikari.HikariDataSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.opentibia.canary.account.getTimeNow
import org.opentibia.canary.systems.gamestore.AccountErrors
import org.opentibia.canary.systems.gamestore.CoinTransactionType
import org.opentibia.canary.systems.gamestore.CoinType
import org.opentibia.canary.utils.Tools

/**
 * H2 (PostgreSQL mode) transcript tests for `AccountRepositoryDB`
 * (src/account/account_repository_db.cpp) and the account detail schema added
 * by `V7__account_details.sql`.
 *
 * Expected values are transcribed from Canary:
 *  - account create/save columns: schema.sql:13-32, account_repository_db.cpp:55-74
 *  - premium loyalty sync: account_repository_db.cpp:285-319
 *  - coin columns + get/set: account_repository_db.cpp:20-25,97-142
 *  - two-coin removal: account_repository_db.cpp:144-230
 *  - coin transactions: account_repository_db.cpp:232-262; schema.sql:34-48
 *  - session load: account_repository_db.cpp:39-53; schema.sql:852-859
 *  - ban tables: schema.sql:166-204; ban.cpp:80-108
 */
class JdbcAccountRepositoryTest {

    private lateinit var dataSource: HikariDataSource
    private lateinit var repository: JdbcAccountRepository
    private var appliedMigrations = 0

    @BeforeEach
    fun setUp() {
        dataSource = DatabaseConnectionFactory.createInMemoryDataSource("canary_account_${System.nanoTime()}")
        appliedMigrations = DatabaseConnectionFactory.migrate(dataSource)
        repository = JdbcAccountRepository(dataSource)
    }

    @AfterEach
    fun tearDown() {
        dataSource.close()
    }

    private fun createTestAccount(
        name: String = "RepoAccount",
        premiumDays: Int = 0,
        accountType: Int = 1,
    ): Long {
        val id = repository.createAccount(name, "argon2-hash", "$name@test.com", premiumDays, accountType)
        assertThat(id).isNotNull
        return id!!
    }

    private data class TransactionRow(
        val type: Int,
        val coinType: Int,
        val amount: Int,
        val description: String,
    )

    private fun coinTransactions(accountId: Long): List<TransactionRow> {
        val rows = mutableListOf<TransactionRow>()
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "SELECT type, coin_type, amount, description FROM coins_transactions WHERE account_id = ? ORDER BY id",
            ).use { stmt ->
                stmt.setLong(1, accountId)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        rows.add(
                            TransactionRow(
                                type = rs.getInt("type"),
                                coinType = rs.getInt("coin_type"),
                                amount = rs.getInt("amount"),
                                description = rs.getString("description"),
                            ),
                        )
                    }
                }
            }
        }
        return rows
    }

    private fun insertSession(sessionId: String, accountId: Long, expires: Long) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "INSERT INTO account_sessions (id, account_id, expires) VALUES (?, ?, ?)",
            ).use { stmt ->
                stmt.setString(1, sessionId)
                stmt.setLong(2, accountId)
                stmt.setLong(3, expires)
                stmt.executeUpdate()
            }
        }
    }

    @Test
    fun `flyway applies the account detail migration through V7`() {
        assertThat(appliedMigrations).isGreaterThanOrEqualTo(7)
    }

    @Test
    fun `creates an account and loads its persisted columns`() {
        val id = createTestAccount("RepoHero", premiumDays = 11, accountType = 3)

        val loaded = repository.loadAccount(id)

        assertThat(loaded).isNotNull
        assertThat(loaded!!.id).isEqualTo(id)
        assertThat(loaded.accountType).isEqualTo(3)
        assertThat(loaded.premiumLastDay).isGreaterThan(getTimeNow())
        assertThat(loaded.premiumRemainingDays).isBetween(10, 11)
        assertThat(loaded.premiumDaysPurchased).isEqualTo(loaded.premiumRemainingDays)
        assertThat(loaded.creationTime).isGreaterThan(0L)
        assertThat(loaded.houseBidId).isEqualTo(0L)
        assertThat(loaded.sessionExpires).isEqualTo(0L)
    }

    @Test
    fun `save persists account type premium creation and house bid columns`() {
        val id = createTestAccount("SaveHero")
        val target = AccountRecord(
            id = id,
            accountType = 3,
            premiumRemainingDays = 10,
            premiumLastDay = getTimeNow() + 10L * 86_400L + 3_600L,
            creationTime = 42_183_281L,
            premiumDaysPurchased = 11,
            houseBidId = 42L,
        )

        assertThat(repository.save(target)).isTrue()
        val loaded = repository.loadAccount(id)

        assertThat(loaded).isNotNull
        assertThat(loaded!!.accountType).isEqualTo(3)
        assertThat(loaded.premiumRemainingDays).isEqualTo(10)
        assertThat(loaded.premiumLastDay).isEqualTo(target.premiumLastDay)
        assertThat(loaded.creationTime).isEqualTo(42_183_281L)
        assertThat(loaded.premiumDaysPurchased).isEqualTo(11)
        assertThat(loaded.houseBidId).isEqualTo(42L)
    }

    @Test
    fun `save returns false when the account does not exist`() {
        assertThat(repository.save(AccountRecord(id = 999_999L))).isFalse()
    }

    @Test
    fun `coin ledger persists normal tournament and transferable balances`() {
        val id = createTestAccount("CoinHero")
        val accountId = id.toUInt()

        assertThat(repository.loadByID(accountId)).isTrue()
        assertThat(repository.loadByID(999_999u)).isFalse()
        assertThat(repository.getCoins(accountId, CoinType.Normal)).isEqualTo(0u)
        assertThat(repository.getCoins(accountId, CoinType.Tournament)).isEqualTo(0u)
        assertThat(repository.getCoins(accountId, CoinType.Transferable)).isEqualTo(0u)

        assertThat(repository.setCoins(accountId, CoinType.Normal, 500u)).isTrue()
        assertThat(repository.setCoins(accountId, CoinType.Tournament, 250u)).isTrue()
        assertThat(repository.setCoins(accountId, CoinType.Transferable, 125u)).isTrue()

        assertThat(repository.getCoins(accountId, CoinType.Normal)).isEqualTo(500u)
        assertThat(repository.getCoins(accountId, CoinType.Tournament)).isEqualTo(250u)
        assertThat(repository.getCoins(accountId, CoinType.Transferable)).isEqualTo(125u)
        assertThat(repository.getCoins(999_999u, CoinType.Normal)).isNull()
        assertThat(repository.setCoins(999_999u, CoinType.Normal, 1u)).isFalse()
    }

    @Test
    fun `registerCoinsTransaction appends coin transaction history`() {
        val id = createTestAccount("TxnHero")
        val accountId = id.toUInt()

        assertThat(
            repository.registerCoinsTransaction(
                accountId,
                CoinTransactionType.Add,
                77u,
                CoinType.Tournament,
                "store purchase",
            ),
        ).isTrue()
        assertThat(
            repository.registerCoinsTransaction(
                accountId,
                CoinTransactionType.Remove,
                12u,
                CoinType.Normal,
                "store refund",
            ),
        ).isTrue()

        assertThat(coinTransactions(id)).containsExactly(
            TransactionRow(type = 1, coinType = 2, amount = 77, description = "store purchase"),
            TransactionRow(type = 2, coinType = 1, amount = 12, description = "store refund"),
        )
    }

    @Test
    fun `removeCoins splits primary and secondary balances and records both removals`() {
        val id = createTestAccount("SplitHero")
        val accountId = id.toUInt()
        repository.setCoins(accountId, CoinType.Normal, 100u)
        repository.setCoins(accountId, CoinType.Transferable, 50u)

        val result = repository.removeCoins(
            accountId,
            CoinType.Normal,
            CoinType.Transferable,
            130u,
            "house purchase",
        )

        assertThat(result.error).isEqualTo(AccountErrors.Ok)
        assertThat(result.primaryCoinsRemoved).isEqualTo(100u)
        assertThat(result.secondaryCoinsRemoved).isEqualTo(30u)
        assertThat(repository.getCoins(accountId, CoinType.Normal)).isEqualTo(0u)
        assertThat(repository.getCoins(accountId, CoinType.Transferable)).isEqualTo(20u)
        assertThat(coinTransactions(id)).containsExactly(
            TransactionRow(type = 2, coinType = 1, amount = 100, description = "house purchase"),
            TransactionRow(type = 2, coinType = 3, amount = 30, description = "house purchase"),
        )
    }

    @Test
    fun `removeCoins rejects insufficient funds without changing balances`() {
        val id = createTestAccount("PoorHero")
        val accountId = id.toUInt()
        repository.setCoins(accountId, CoinType.Normal, 10u)
        repository.setCoins(accountId, CoinType.Transferable, 5u)

        val result = repository.removeCoins(
            accountId,
            CoinType.Normal,
            CoinType.Transferable,
            20u,
            "too much",
        )

        assertThat(result.error).isEqualTo(AccountErrors.RemoveCoins)
        assertThat(result.primaryCoinsRemoved).isEqualTo(0u)
        assertThat(result.secondaryCoinsRemoved).isEqualTo(0u)
        assertThat(repository.getCoins(accountId, CoinType.Normal)).isEqualTo(10u)
        assertThat(repository.getCoins(accountId, CoinType.Transferable)).isEqualTo(5u)
        assertThat(coinTransactions(id)).isEmpty()
    }

    @Test
    fun `removeCoins rejects equal coin types and missing accounts`() {
        val id = createTestAccount("BadCoinHero")
        val accountId = id.toUInt()
        repository.setCoins(accountId, CoinType.Normal, 10u)

        val sameType = repository.removeCoins(accountId, CoinType.Normal, CoinType.Normal, 1u, "same")
        assertThat(sameType.error).isEqualTo(AccountErrors.Storage)

        val missing = repository.removeCoins(999_999u, CoinType.Normal, CoinType.Transferable, 1u, "missing")
        assertThat(missing.error).isEqualTo(AccountErrors.Storage)

        assertThat(repository.getCoins(accountId, CoinType.Normal)).isEqualTo(10u)
        assertThat(coinTransactions(id)).isEmpty()
    }

    @Test
    fun `removeCoins skips transaction history when detail is empty`() {
        val id = createTestAccount("QuietHero")
        val accountId = id.toUInt()
        repository.setCoins(accountId, CoinType.Normal, 30u)

        val result = repository.removeCoins(accountId, CoinType.Normal, CoinType.Transferable, 10u, "")

        assertThat(result.error).isEqualTo(AccountErrors.Ok)
        assertThat(result.primaryCoinsRemoved).isEqualTo(10u)
        assertThat(result.secondaryCoinsRemoved).isEqualTo(0u)
        assertThat(repository.getCoins(accountId, CoinType.Normal)).isEqualTo(20u)
        assertThat(coinTransactions(id)).isEmpty()
    }

    @Test
    fun `loadBySession resolves legacy sha1 session ids`() {
        val id = createTestAccount("SessionHero")
        insertSession(Tools.transformToSHA1("session-token"), id, 1337L)

        val loaded = repository.loadBySession("session-token")

        assertThat(loaded).isNotNull
        assertThat(loaded!!.id).isEqualTo(id)
        assertThat(loaded.sessionExpires).isEqualTo(1337L)
        assertThat(loaded.accountType).isEqualTo(1)
    }

    @Test
    fun `loadBySession prefers the sha256 session id`() {
        val id = createTestAccount("ShaHero")
        insertSession(Tools.transformToSHA1("prefer-token"), id, 1337L)
        insertSession(Tools.transformToSHA256("prefer-token"), id, 7331L)

        val loaded = repository.loadBySession("prefer-token")

        assertThat(loaded).isNotNull
        assertThat(loaded!!.sessionExpires).isEqualTo(7331L)
    }

    @Test
    fun `loadBySession returns null for unknown tokens`() {
        createTestAccount("NoSessionHero")

        assertThat(repository.loadBySession("missing-token")).isNull()
    }

    @Test
    fun `account ban and ban history tables persist rows`() {
        val id = createTestAccount("BannedHero")

        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "INSERT INTO account_bans (account_id, reason, banned_at, expires_at, banned_by) VALUES (?, ?, ?, ?, ?)",
            ).use { stmt ->
                stmt.setLong(1, id)
                stmt.setString(2, "cheating")
                stmt.setLong(3, 1_000L)
                stmt.setLong(4, 2_000L)
                stmt.setLong(5, 1L)
                assertThat(stmt.executeUpdate()).isEqualTo(1)
            }
            conn.prepareStatement(
                "INSERT INTO account_ban_history (account_id, reason, banned_at, expired_at, banned_by) VALUES (?, ?, ?, ?, ?)",
            ).use { stmt ->
                stmt.setLong(1, id)
                stmt.setString(2, "cheating")
                stmt.setLong(3, 1_000L)
                stmt.setLong(4, 2_000L)
                stmt.setLong(5, 1L)
                assertThat(stmt.executeUpdate()).isEqualTo(1)
            }
            conn.prepareStatement(
                "SELECT reason, banned_at, expires_at FROM account_bans WHERE account_id = ?",
            ).use { stmt ->
                stmt.setLong(1, id)
                stmt.executeQuery().use { rs ->
                    assertThat(rs.next()).isTrue()
                    assertThat(rs.getString("reason")).isEqualTo("cheating")
                    assertThat(rs.getLong("banned_at")).isEqualTo(1_000L)
                    assertThat(rs.getLong("expires_at")).isEqualTo(2_000L)
                }
            }
            conn.prepareStatement(
                "SELECT reason, expired_at FROM account_ban_history WHERE account_id = ?",
            ).use { stmt ->
                stmt.setLong(1, id)
                stmt.executeQuery().use { rs ->
                    assertThat(rs.next()).isTrue()
                    assertThat(rs.getString("reason")).isEqualTo("cheating")
                    assertThat(rs.getLong("expired_at")).isEqualTo(2_000L)
                }
            }
        }
    }
}
