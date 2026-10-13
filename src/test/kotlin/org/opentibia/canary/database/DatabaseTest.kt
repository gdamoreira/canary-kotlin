package org.opentibia.canary.database

import com.zaxxer.hikari.HikariDataSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Statement
import java.util.concurrent.atomic.AtomicInteger
import javax.sql.DataSource

class DatabaseTest {

    private lateinit var dataSource: HikariDataSource
    private lateinit var database: Database

    @BeforeEach
    fun setUp() {
        dataSource = DatabaseConnectionFactory.createInMemoryDataSource("db_core_${System.nanoTime()}")
        database = Database(dataSource)
        dataSource.connection.use { conn ->
            conn.createStatement().use { stmt ->
                stmt.execute("CREATE TABLE core_probe (id INT PRIMARY KEY, name VARCHAR(255))")
            }
        }
    }

    @AfterEach
    fun tearDown() {
        dataSource.close()
    }

    private fun countRows(): Int =
        dataSource.connection.use { conn ->
            conn.createStatement().use { stmt ->
                stmt.executeQuery("SELECT COUNT(*) FROM core_probe").use { rs ->
                    rs.next()
                    rs.getInt(1)
                }
            }
        }

    @Test
    fun `executeWithinTransaction commits when callback succeeds`() {
        val result = DBTransaction.executeWithinTransaction(database) {
            database.executeQuery("INSERT INTO core_probe VALUES (1, 'committed')")
            true
        }

        assertThat(result).isTrue
        assertThat(countRows()).isEqualTo(1)
    }

    @Test
    fun `executeWithinTransaction commits even when callback reports failure`() {
        val result = DBTransaction.executeWithinTransaction(database) {
            database.executeQuery("INSERT INTO core_probe VALUES (1, 'committed')")
            false
        }

        assertThat(result).isFalse
        assertThat(countRows()).isEqualTo(1)
    }

    @Test
    fun `executeWithinTransaction rolls back when callback throws`() {
        val result = DBTransaction.executeWithinTransaction(database) {
            database.executeQuery("INSERT INTO core_probe VALUES (1, 'rolled back')")
            throw IllegalStateException("callback failure")
        }

        assertThat(result).isFalse
        assertThat(countRows()).isZero
    }

    @Test
    fun `executeWithinTransactionRollbackOnFailure rolls back when callback reports failure`() {
        val result = DBTransaction.executeWithinTransactionRollbackOnFailure(database) {
            database.executeQuery("INSERT INTO core_probe VALUES (1, 'rolled back')")
            false
        }

        assertThat(result).isFalse
        assertThat(countRows()).isZero
    }

    @Test
    fun `executeWithinTransactionRollbackOnFailure commits when callback succeeds`() {
        val result = DBTransaction.executeWithinTransactionRollbackOnFailure(database) {
            database.executeQuery("INSERT INTO core_probe VALUES (1, 'committed')")
            true
        }

        assertThat(result).isTrue
        assertThat(countRows()).isEqualTo(1)
    }

    @Test
    fun `executeWithinTransactionRollbackOnFailure rolls back when callback throws`() {
        val result = DBTransaction.executeWithinTransactionRollbackOnFailure(database) {
            database.executeQuery("INSERT INTO core_probe VALUES (1, 'rolled back')")
            throw IllegalStateException("callback failure")
        }

        assertThat(result).isFalse
        assertThat(countRows()).isZero
    }

    @Test
    fun `rollback releases the transaction connection for later queries`() {
        DBTransaction.executeWithinTransactionRollbackOnFailure(database) {
            database.executeQuery("INSERT INTO core_probe VALUES (1, 'rolled back')")
            false
        }

        assertThat(database.executeQuery("INSERT INTO core_probe VALUES (2, 'after rollback')")).isTrue
        assertThat(countRows()).isEqualTo(1)
    }

    @Test
    fun `escapeString wraps the value in single quotes`() {
        assertThat(database.escapeString("Canary")).isEqualTo("'Canary'")
    }

    @Test
    fun `escapeString doubles embedded single quotes`() {
        assertThat(database.escapeString("O'Brien")).isEqualTo("'O''Brien'")
    }

    @Test
    fun `escapeString quotes an empty value`() {
        assertThat(database.escapeString("")).isEqualTo("''")
    }

    @Test
    fun `escapeString output is safe to concatenate into a real query`() {
        val injection = "x\\'); DROP TABLE core_probe; --"
        val sql = "INSERT INTO core_probe (id, name) VALUES (1, ${database.escapeString(injection)})"

        assertThat(database.executeQuery(sql)).isTrue
        dataSource.connection.use { conn ->
            conn.createStatement().use { stmt ->
                stmt.executeQuery("SELECT name FROM core_probe WHERE id = 1").use { rs ->
                    assertThat(rs.next()).isTrue
                    assertThat(rs.getString(1)).isEqualTo(injection)
                }
            }
        }
    }

    @Test
    fun `escapeString preserves non-ASCII text in a real query`() {
        val value = "Acao d'O'Brien 日本語"
        val sql = "INSERT INTO core_probe (id, name) VALUES (1, ${database.escapeString(value)})"

        assertThat(database.executeQuery(sql)).isTrue
        dataSource.connection.use { conn ->
            conn.createStatement().use { stmt ->
                stmt.executeQuery("SELECT name FROM core_probe WHERE id = 1").use { rs ->
                    assertThat(rs.next()).isTrue
                    assertThat(rs.getString(1)).isEqualTo(value)
                }
            }
        }
    }

    @Test
    fun `escapeBlob escapes quote bytes in the payload`() {
        assertThat(database.escapeBlob("a'b".toByteArray(Charsets.UTF_8))).isEqualTo("'a''b'")
    }

    @Test
    fun `retryQuery returns true when the query succeeds immediately`() {
        assertThat(database.retryQuery("INSERT INTO core_probe VALUES (1, 'direct')", 1)).isTrue
        assertThat(countRows()).isEqualTo(1)
    }

    @Test
    fun `retryQuery retries recoverable errors until the query succeeds`() {
        val flaky = FlakyDataSource(dataSource, failures = 1, errorCode = 2013)
        val retrying = Database(flaky)

        assertThat(retrying.retryQuery("INSERT INTO core_probe VALUES (1, 'retried')", 3)).isTrue
        assertThat(flaky.attempts.get()).isEqualTo(2)
        assertThat(countRows()).isEqualTo(1)
    }

    @Test
    fun `retryQuery does not retry non-recoverable errors`() {
        val flaky = FlakyDataSource(dataSource, failures = 5, errorCode = 1062)
        val retrying = Database(flaky)

        assertThat(retrying.retryQuery("INSERT INTO core_probe VALUES (1, 'duplicate')", 3)).isFalse
        assertThat(flaky.attempts.get()).isEqualTo(1)
    }

    @Test
    fun `retryQuery returns false when retries is zero`() {
        val flaky = FlakyDataSource(dataSource, failures = 5, errorCode = 2013)
        val retrying = Database(flaky)

        assertThat(retrying.retryQuery("SELECT 1", 0)).isFalse
        assertThat(flaky.attempts.get()).isZero
    }

    @Test
    fun `executeQuery retries recoverable errors transparently`() {
        val flaky = FlakyDataSource(dataSource, failures = 1, errorCode = 2013)
        val retrying = Database(flaky)

        assertThat(retrying.executeQuery("INSERT INTO core_probe VALUES (1, 'execute retry')")).isTrue
        assertThat(flaky.attempts.get()).isEqualTo(2)
        assertThat(countRows()).isEqualTo(1)
    }

    @Test
    fun `isRecoverableError matches the MySQL client errors from Canary`() {
        assertThat(listOf(2013, 2006, 2003, 1053, 2002).all { Database.isRecoverableError(it) }).isTrue
        assertThat(listOf(1062, 0, 2005).none { Database.isRecoverableError(it) }).isTrue
    }

    @Test
    fun `MAX_QUERY_SIZE is eight mebibytes`() {
        assertThat(Database.MAX_QUERY_SIZE).isEqualTo(8L * 1024 * 1024)
    }

    @Test
    fun `getMaxPacketSize defaults to one mebibyte`() {
        assertThat(Database(dataSource).getMaxPacketSize()).isEqualTo(1048576L)
    }

    @Test
    fun `getMaxPacketSize reflects the configured value`() {
        assertThat(Database(dataSource, maxPacketSize = 4096L).getMaxPacketSize()).isEqualTo(4096L)
    }

    private class FlakyDataSource(
        private val delegate: DataSource,
        private val failures: Int,
        private val errorCode: Int
    ) : DataSource by delegate {

        val attempts = AtomicInteger(0)

        override fun getConnection(): Connection =
            FlakyConnection(delegate.connection, failures, errorCode, attempts)
    }

    private class FlakyConnection(
        private val delegate: Connection,
        private val failures: Int,
        private val errorCode: Int,
        private val attempts: AtomicInteger
    ) : Connection by delegate {

        override fun createStatement(): Statement =
            FlakyStatement(delegate.createStatement(), failures, errorCode, attempts)

        override fun createStatement(resultSetType: Int, resultSetConcurrency: Int): Statement =
            FlakyStatement(delegate.createStatement(resultSetType, resultSetConcurrency), failures, errorCode, attempts)

        override fun createStatement(resultSetType: Int, resultSetConcurrency: Int, resultSetHoldability: Int): Statement =
            FlakyStatement(delegate.createStatement(resultSetType, resultSetConcurrency, resultSetHoldability), failures, errorCode, attempts)
    }

    private class FlakyStatement(
        private val delegate: Statement,
        private val failures: Int,
        private val errorCode: Int,
        private val attempts: AtomicInteger
    ) : Statement by delegate {

        override fun execute(sql: String): Boolean {
            countAttempt()
            return delegate.execute(sql)
        }

        override fun executeQuery(sql: String): ResultSet {
            countAttempt()
            return delegate.executeQuery(sql)
        }

        override fun executeUpdate(sql: String): Int {
            countAttempt()
            return delegate.executeUpdate(sql)
        }

        private fun countAttempt() {
            if (attempts.incrementAndGet() <= failures) {
                throw SQLException("simulated recoverable failure", "08S01", errorCode)
            }
        }
    }
}
