package org.opentibia.canary.database

import io.github.oshai.kotlinlogging.KotlinLogging
import org.opentibia.canary.account.Account
import org.opentibia.canary.account.AccountRepository
import org.opentibia.canary.account.CharacterEntry
import org.opentibia.canary.account.getTimeNow
import org.opentibia.canary.systems.gamestore.AccountCoinColumns
import org.opentibia.canary.systems.gamestore.AccountCoinRepository
import org.opentibia.canary.systems.gamestore.AccountErrors
import org.opentibia.canary.systems.gamestore.CoinTransactionType
import org.opentibia.canary.systems.gamestore.CoinType
import org.opentibia.canary.systems.gamestore.RemoveCoinsResult
import org.opentibia.canary.utils.Tools
import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException
import javax.sql.DataSource

private val logger = KotlinLogging.logger {}

/** SQLSTATE for unique-key violations (H2 and PostgreSQL). */
private const val UNIQUE_VIOLATION_SQL_STATE = "23505"

/** Bounded retries for the optimistic `MAX(id) + 1` account-id allocation. */
private const val MAX_ACCOUNT_ID_ATTEMPTS = 5

/**
 * Persisted account row, mirroring the `AccountInfo` fields Canary stores in the
 * `accounts` table (src/account/account_info.hpp:19-32). The persisted subset
 * covers the columns read by `AccountRepositoryDB::load`
 * (src/account/account_repository_db.cpp:285-303) and written by
 * `AccountRepositoryDB::save` (account_repository_db.cpp:55-74).
 *
 * `sessionExpires` is only populated by [JdbcAccountRepository.loadBySession]
 * (the `account_sessions.expires` join); other loads return `0`, as in Canary's
 * `0 AS expires` projection.
 */
data class AccountRecord(
    val id: Long,
    val accountType: Int = 1,
    val premiumRemainingDays: Int = 0,
    val premiumLastDay: Long = 0,
    val creationTime: Long = 0,
    val premiumDaysPurchased: Int = 0,
    val houseBidId: Long = 0,
    val sessionExpires: Long = 0,
)

class JdbcAccountRepository(private val dataSource: DataSource) : AccountRepository, AccountCoinRepository {

    override fun findByAccountName(name: String): Account? {
        val sqlAccount = "SELECT id, name, password, premdays FROM accounts WHERE LOWER(name) = LOWER(?)"
        dataSource.connection.use { conn ->
            conn.prepareStatement(sqlAccount).use { stmt ->
                stmt.setString(1, name)
                val rs = stmt.executeQuery()
                if (rs.next()) {
                    val accountId = rs.getLong("id")
                    val accName = rs.getString("name")
                    val password = rs.getString("password")
                    val premdays = rs.getInt("premdays")

                    val characters = loadCharactersForAccount(conn, accountId)

                    return Account(
                        id = accountId,
                        name = accName,
                        passwordHash = password,
                        premiumDays = premdays,
                        characters = characters
                    )
                }
            }
        }
        return null
    }

    override fun authenticate(name: String, password: String): Account? {
        val account = findByAccountName(name) ?: return null
        val matches = org.opentibia.canary.network.crypto.Argon2Security.verifyPassword(password, account.passwordHash)
        return if (matches) account else null
    }

    /**
     * Inserts a new account row. Canary has no server-side account creation
     * (accounts are provisioned by the website), so this mirrors the column set
     * used to seed accounts in the Canary integration fixture
     * (`tests/integration/account/account_repository_db_it.cpp:41-46`): name,
     * password, email, premium days, type and creation time. `premiumDays`
     * additionally seeds `lastday` so the premium window starts at creation.
     *
     * The id is allocated as `MAX(id) + 1` because V2 seeds an explicit id
     * without advancing the identity backing `BIGSERIAL` (the same holds for a
     * PostgreSQL serial): a plain insert would reuse the seeded id. The bounded
     * retry covers the concurrent-allocation race.
     *
     * @return the generated account id, or `null` when the insert failed.
     */
    fun createAccount(
        name: String,
        passwordHash: String,
        email: String = "",
        premiumDays: Int = 0,
        accountType: Int = 1,
    ): Long? {
        if (accountNameExists(name)) {
            return null
        }

        val now = getTimeNow()
        val lastDay = if (premiumDays > 0) now + premiumDays.toLong() * Account.SECONDS_PER_DAY else 0L

        repeat(MAX_ACCOUNT_ID_ATTEMPTS) {
            val id = selectNextAccountId() ?: return null
            try {
                insertAccount(id, name, passwordHash, email, premiumDays, lastDay, accountType, now)
                return id
            } catch (ex: SQLException) {
                if (ex.sqlState != UNIQUE_VIOLATION_SQL_STATE) {
                    logger.error(ex) { "Failed to create account [$name]" }
                    return null
                }
            }
        }

        logger.error { "Failed to create account [$name]: no unique id available" }
        return null
    }

    /** Mirrors `AccountRepositoryDB::loadByID` (src/account/account_repository_db.cpp:28-31). */
    fun loadAccount(id: Long): AccountRecord? {
        val sql = """
            SELECT id, "type", premdays, lastday, creation, premdays_purchased, house_bid_id, 0 AS expires
            FROM accounts
            WHERE id = ?
        """.trimIndent()

        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setLong(1, id)
                stmt.executeQuery().use { rs ->
                    if (!rs.next()) {
                        return null
                    }
                    return setupLoyaltyInfo(readAccountRecord(rs, id))
                }
            }
        }
    }

    /** Mirrors `AccountRepositoryDB::save` (src/account/account_repository_db.cpp:55-74). */
    fun save(record: AccountRecord): Boolean {
        val sql = """
            UPDATE accounts
            SET "type" = ?, premdays = ?, lastday = ?, creation = ?, premdays_purchased = ?, house_bid_id = ?
            WHERE id = ?
        """.trimIndent()

        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setInt(1, record.accountType)
                stmt.setInt(2, record.premiumRemainingDays)
                stmt.setLong(3, record.premiumLastDay)
                stmt.setLong(4, record.creationTime)
                stmt.setInt(5, record.premiumDaysPurchased)
                stmt.setLong(6, record.houseBidId)
                stmt.setLong(7, record.id)
                return stmt.executeUpdate() > 0
            }
        }
    }

    /**
     * Mirrors `AccountRepositoryDB::loadBySession`
     * (src/account/account_repository_db.cpp:39-53): the SHA-256 session key is
     * preferred over the legacy SHA-1 key.
     */
    fun loadBySession(sessionKey: String): AccountRecord? {
        val sha256SessionId = Tools.transformToSHA256(sessionKey)
        val sha1SessionId = Tools.transformToSHA1(sessionKey)

        val sql = """
            SELECT accounts.id, "type", premdays, lastday, creation, premdays_purchased, house_bid_id, account_sessions.expires
            FROM accounts
            INNER JOIN account_sessions ON account_sessions.account_id = accounts.id
            WHERE account_sessions.id IN (?, ?)
            ORDER BY CASE account_sessions.id WHEN ? THEN 0 ELSE 1 END
            LIMIT 1
        """.trimIndent()

        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, sha256SessionId)
                stmt.setString(2, sha1SessionId)
                stmt.setString(3, sha256SessionId)
                stmt.executeQuery().use { rs ->
                    if (!rs.next()) {
                        return null
                    }
                    return setupLoyaltyInfo(readAccountRecord(rs, rs.getLong("id")))
                }
            }
        }
    }

    override fun loadByID(id: UInt): Boolean {
        dataSource.connection.use { conn ->
            conn.prepareStatement("SELECT 1 FROM accounts WHERE id = ?").use { stmt ->
                stmt.setLong(1, id.toLong())
                stmt.executeQuery().use { rs -> return rs.next() }
            }
        }
    }

    /** Mirrors `AccountRepositoryDB::getCoins` (src/account/account_repository_db.cpp:97-119); `null` is `false`. */
    override fun getCoins(id: UInt, coinType: CoinType): UInt? {
        val column = AccountCoinColumns.columnOf(coinType)
        val sql = "SELECT \"$column\" FROM accounts WHERE id = ?"

        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setLong(1, id.toLong())
                stmt.executeQuery().use { rs ->
                    return if (rs.next()) rs.getLong(column).toUInt() else null
                }
            }
        }
    }

    /** Mirrors `AccountRepositoryDB::setCoins` (src/account/account_repository_db.cpp:121-142). */
    override fun setCoins(id: UInt, coinType: CoinType, amount: UInt): Boolean {
        val column = AccountCoinColumns.columnOf(coinType)
        val sql = "UPDATE accounts SET \"$column\" = ? WHERE id = ?"

        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setLong(1, amount.toLong())
                stmt.setLong(2, id.toLong())
                return stmt.executeUpdate() > 0
            }
        }
    }

    /**
     * Mirrors `AccountRepositoryDB::removeCoins` (src/account/account_repository_db.cpp:144-230):
     * the primary balance is drained first, the remainder comes from the
     * secondary balance, and one `coins_transactions` row is registered per
     * non-zero removal when a detail is supplied. The read is done `FOR UPDATE`
     * inside a transaction, rolled back on any failure.
     */
    override fun removeCoins(
        id: UInt,
        primaryType: CoinType,
        secondaryType: CoinType,
        amount: UInt,
        detail: String,
    ): RemoveCoinsResult {
        if (primaryType == secondaryType) {
            return RemoveCoinsResult(AccountErrors.Storage)
        }

        val primaryColumn = AccountCoinColumns.columnOf(primaryType)
        val secondaryColumn = AccountCoinColumns.columnOf(secondaryType)

        dataSource.connection.use { conn ->
            conn.autoCommit = false
            try {
                val select = "SELECT \"$primaryColumn\", \"$secondaryColumn\" FROM accounts WHERE id = ? FOR UPDATE"
                val primaryCoins: UInt
                val secondaryCoins: UInt
                conn.prepareStatement(select).use { stmt ->
                    stmt.setLong(1, id.toLong())
                    stmt.executeQuery().use { rs ->
                        if (!rs.next()) {
                            conn.rollback()
                            return RemoveCoinsResult(AccountErrors.Storage)
                        }
                        primaryCoins = rs.getLong(primaryColumn).toUInt()
                        secondaryCoins = rs.getLong(secondaryColumn).toUInt()
                    }
                }

                if (primaryCoins.toULong() + secondaryCoins.toULong() < amount.toULong()) {
                    conn.rollback()
                    return RemoveCoinsResult(AccountErrors.RemoveCoins)
                }

                val primaryCoinsRemoved = minOf(primaryCoins, amount)
                val secondaryCoinsRemoved = amount - primaryCoinsRemoved

                val update = "UPDATE accounts SET \"$primaryColumn\" = ?, \"$secondaryColumn\" = ? WHERE id = ?"
                conn.prepareStatement(update).use { stmt ->
                    stmt.setLong(1, (primaryCoins - primaryCoinsRemoved).toLong())
                    stmt.setLong(2, (secondaryCoins - secondaryCoinsRemoved).toLong())
                    stmt.setLong(3, id.toLong())
                    if (stmt.executeUpdate() == 0) {
                        conn.rollback()
                        return RemoveCoinsResult(AccountErrors.Storage)
                    }
                }

                if (detail.isNotEmpty() && primaryCoinsRemoved > 0u &&
                    !insertTransaction(conn, id, CoinTransactionType.Remove, primaryCoinsRemoved, primaryType, detail)
                ) {
                    conn.rollback()
                    return RemoveCoinsResult(AccountErrors.Storage)
                }

                if (detail.isNotEmpty() && secondaryCoinsRemoved > 0u &&
                    !insertTransaction(conn, id, CoinTransactionType.Remove, secondaryCoinsRemoved, secondaryType, detail)
                ) {
                    conn.rollback()
                    return RemoveCoinsResult(AccountErrors.Storage)
                }

                conn.commit()
                return RemoveCoinsResult(AccountErrors.Ok, primaryCoinsRemoved, secondaryCoinsRemoved)
            } catch (ex: SQLException) {
                conn.rollback()
                logger.error(ex) { "Error removing coins from account [$id]" }
                return RemoveCoinsResult(AccountErrors.Storage)
            } finally {
                conn.autoCommit = true
            }
        }
    }

    /** Mirrors `AccountRepositoryDB::registerCoinsTransaction` (src/account/account_repository_db.cpp:232-262). */
    override fun registerCoinsTransaction(
        id: UInt,
        type: CoinTransactionType,
        coins: UInt,
        coinType: CoinType,
        description: String,
    ): Boolean {
        dataSource.connection.use { conn ->
            return insertTransaction(conn, id, type, coins, coinType, description)
        }
    }

    /**
     * Mirrors `AccountRepositoryDB::setupLoyaltyInfo`
     * (src/account/account_repository_db.cpp:305-319): the purchased premium
     * days are synced up to the remaining days and a missing creation time is
     * stamped, persisting the account when either changed.
     */
    private fun setupLoyaltyInfo(record: AccountRecord): AccountRecord {
        if (record.premiumDaysPurchased >= record.premiumRemainingDays && record.creationTime != 0L) {
            return record
        }

        var updated = record
        if (record.premiumDaysPurchased < record.premiumRemainingDays) {
            updated = updated.copy(premiumDaysPurchased = record.premiumRemainingDays)
        }

        if (record.creationTime == 0L) {
            updated = updated.copy(creationTime = getTimeNow())
        }

        save(updated)
        return updated
    }

    private fun readAccountRecord(rs: ResultSet, id: Long): AccountRecord {
        val lastDay = rs.getLong("lastday")
        val now = getTimeNow()
        val premiumRemainingDays =
            if (lastDay > now) ((lastDay - now) / Account.SECONDS_PER_DAY).toInt() else 0

        return AccountRecord(
            id = id,
            accountType = rs.getInt("type"),
            premiumRemainingDays = premiumRemainingDays,
            premiumLastDay = lastDay,
            creationTime = rs.getLong("creation"),
            premiumDaysPurchased = rs.getInt("premdays_purchased"),
            houseBidId = rs.getLong("house_bid_id"),
            sessionExpires = rs.getLong("expires"),
        )
    }

    private fun insertTransaction(
        conn: Connection,
        id: UInt,
        type: CoinTransactionType,
        coins: UInt,
        coinType: CoinType,
        description: String,
    ): Boolean {
        val sql = "INSERT INTO coins_transactions (account_id, type, coin_type, amount, description) VALUES (?, ?, ?, ?, ?)"
        return try {
            conn.prepareStatement(sql).use { stmt ->
                stmt.setLong(1, id.toLong())
                stmt.setInt(2, type.value)
                stmt.setInt(3, coinType.value)
                stmt.setLong(4, coins.toLong())
                stmt.setString(5, description)
                stmt.executeUpdate() > 0
            }
        } catch (ex: SQLException) {
            logger.error(ex) {
                "Error registering coin transaction! account_id:[$id], type:[$type], " +
                    "coin_type:[$coinType], coins:[$coins], description:[$description]"
            }
            false
        }
    }

    private fun accountNameExists(name: String): Boolean {
        dataSource.connection.use { conn ->
            conn.prepareStatement("SELECT 1 FROM accounts WHERE name = ?").use { stmt ->
                stmt.setString(1, name)
                stmt.executeQuery().use { rs -> return rs.next() }
            }
        }
    }

    private fun selectNextAccountId(): Long? {
        dataSource.connection.use { conn ->
            conn.prepareStatement("SELECT COALESCE(MAX(id), 0) + 1 FROM accounts").use { stmt ->
                stmt.executeQuery().use { rs -> return if (rs.next()) rs.getLong(1) else null }
            }
        }
    }

    private fun insertAccount(
        id: Long,
        name: String,
        passwordHash: String,
        email: String,
        premiumDays: Int,
        lastDay: Long,
        accountType: Int,
        creation: Long,
    ) {
        val sql = """
            INSERT INTO accounts (id, name, "password", email, premdays, lastday, "type", creation)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setLong(1, id)
                stmt.setString(2, name)
                stmt.setString(3, passwordHash)
                stmt.setString(4, email)
                stmt.setInt(5, premiumDays)
                stmt.setLong(6, lastDay)
                stmt.setInt(7, accountType)
                stmt.setLong(8, creation)
                stmt.executeUpdate()
            }
        }
    }

    private fun loadCharactersForAccount(conn: java.sql.Connection, accountId: Long): List<CharacterEntry> {
        val sqlPlayers = "SELECT name FROM players WHERE account_id = ? AND deletion = 0"
        val list = mutableListOf<CharacterEntry>()
        conn.prepareStatement(sqlPlayers).use { stmt ->
            stmt.setLong(1, accountId)
            val rs = stmt.executeQuery()
            while (rs.next()) {
                list.add(CharacterEntry(name = rs.getString("name")))
            }
        }
        return list
    }
}
