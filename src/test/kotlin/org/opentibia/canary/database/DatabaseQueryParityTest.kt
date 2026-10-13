package org.opentibia.canary.database

import com.zaxxer.hikari.HikariDataSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.sql.SQLException
import java.util.concurrent.atomic.AtomicInteger
import javax.sql.DataSource

/**
 * Transcribes Canary's `Database` query surface:
 * `Database::executeQuery` (src/database/database.cpp:242-259),
 * `Database::storeQuery` (src/database/database.cpp:261-294),
 * `Database::retryQuery` (src/database/database.cpp:224-240) and
 * `Database::isRecoverableError` (src/database/database.cpp:220-222).
 */
class DatabaseQueryParityTest {

    private lateinit var dataSource: HikariDataSource
    private var previousRetryDelayMillis: Long = 0

    @BeforeEach
    fun setUp() {
        dataSource = DatabaseConnectionFactory.createInMemoryDataSource("canary_query_${System.nanoTime()}")
        previousRetryDelayMillis = DatabaseConnectionFactory.retryDelayMillis
        DatabaseConnectionFactory.retryDelayMillis = 0
    }

    @AfterEach
    fun tearDown() {
        DatabaseConnectionFactory.retryDelayMillis = previousRetryDelayMillis
        dataSource.close()
    }

    @Test
    fun `executeQuery persists changes and returns true`() {
        assertThat(
            DatabaseConnectionFactory.executeQuery(
                dataSource,
                "CREATE TABLE accounts (id INT PRIMARY KEY, name VARCHAR(50))"
            )
        ).isTrue()
        assertThat(
            DatabaseConnectionFactory.executeQuery(dataSource, "INSERT INTO accounts (id, name) VALUES (1, 'Canary')")
        ).isTrue()

        val result = DatabaseConnectionFactory.storeQuery(dataSource, "SELECT COUNT(*) AS total FROM accounts")
        assertThat(result).isNotNull
        assertThat(result!!.getNumber("total")).isEqualTo(1)
    }

    @Test
    fun `executeQuery discards select results and remains reusable`() {
        assertThat(DatabaseConnectionFactory.executeQuery(dataSource, "CREATE TABLE items (id INT)")).isTrue()
        assertThat(DatabaseConnectionFactory.executeQuery(dataSource, "INSERT INTO items VALUES (7)")).isTrue()

        assertThat(DatabaseConnectionFactory.executeQuery(dataSource, "SELECT * FROM items")).isTrue()
        assertThat(DatabaseConnectionFactory.executeQuery(dataSource, "SELECT * FROM items")).isTrue()

        val result = DatabaseConnectionFactory.storeQuery(dataSource, "SELECT id FROM items")
        assertThat(result).isNotNull
        assertThat(result!!.getNumber("id")).isEqualTo(7)
    }

    @Test
    fun `executeQuery does not retry non-recoverable errors`() {
        val flaky = FlakyDataSource(dataSource, failures = 3, sqlState = "42000", errorCode = 1064)

        assertThat(DatabaseConnectionFactory.executeQuery(flaky, "SELECT 1")).isFalse()
        assertThat(flaky.attempts.get()).isEqualTo(1)
    }

    @Test
    fun `executeQuery retries recoverable connection errors until the budget is exhausted`() {
        val flaky = FlakyDataSource(dataSource, failures = Int.MAX_VALUE, sqlState = "08S01")

        assertThat(DatabaseConnectionFactory.executeQuery(flaky, "SELECT 1")).isFalse()
        assertThat(flaky.attempts.get()).isEqualTo(DatabaseConnectionFactory.QUERY_RETRIES)
    }

    @Test
    fun `executeQuery returns true when a recoverable failure clears within the budget`() {
        val flaky = FlakyDataSource(dataSource, failures = 3, sqlState = "08S01")

        assertThat(DatabaseConnectionFactory.executeQuery(flaky, "SELECT 1")).isTrue()
        assertThat(flaky.attempts.get()).isEqualTo(4)
    }

    @Test
    fun `retryQuery honours the requested retry budget`() {
        val flaky = FlakyDataSource(dataSource, failures = Int.MAX_VALUE, sqlState = "08S01")

        assertThat(DatabaseConnectionFactory.retryQuery(flaky, "SELECT 1", 2)).isFalse()
        assertThat(flaky.attempts.get()).isEqualTo(2)
    }

    @Test
    fun `storeQuery returns null for statements without a result set`() {
        assertThat(DatabaseConnectionFactory.executeQuery(dataSource, "CREATE TABLE logs (id INT)")).isTrue()

        assertThat(DatabaseConnectionFactory.storeQuery(dataSource, "INSERT INTO logs VALUES (1)")).isNull()
    }

    @Test
    fun `storeQuery returns null when the result set has no rows`() {
        assertThat(DatabaseConnectionFactory.executeQuery(dataSource, "CREATE TABLE empty_logs (id INT)")).isTrue()

        assertThat(DatabaseConnectionFactory.storeQuery(dataSource, "SELECT id FROM empty_logs")).isNull()
    }

    @Test
    fun `storeQuery returns null for non-recoverable errors without retrying`() {
        val flaky = FlakyDataSource(dataSource, failures = 3, sqlState = "42000", errorCode = 1064)

        assertThat(DatabaseConnectionFactory.storeQuery(flaky, "SELECT 1")).isNull()
        assertThat(flaky.attempts.get()).isEqualTo(1)
    }

    @Test
    fun `storeQuery retries recoverable errors beyond the executeQuery budget`() {
        val flaky = FlakyDataSource(dataSource, failures = 12, sqlState = "08S01")

        val result = DatabaseConnectionFactory.storeQuery(flaky, "SELECT 1 AS one")

        assertThat(result).isNotNull
        assertThat(result!!.getNumber("one")).isEqualTo(1)
        assertThat(flaky.attempts.get()).isEqualTo(13)
    }

    @Test
    fun `storeQuery exposes column values and row iteration`() {
        assertThat(
            DatabaseConnectionFactory.executeQuery(dataSource, "CREATE TABLE players (name VARCHAR(50), level INT)")
        ).isTrue()
        assertThat(
            DatabaseConnectionFactory.executeQuery(dataSource, "INSERT INTO players VALUES ('Hero', 100)")
        ).isTrue()
        assertThat(
            DatabaseConnectionFactory.executeQuery(dataSource, "INSERT INTO players VALUES ('Mage', 50)")
        ).isTrue()

        val result = DatabaseConnectionFactory.storeQuery(dataSource, "SELECT name, level FROM players ORDER BY level DESC")
        assertThat(result).isNotNull
        assertThat(result!!.countResults()).isEqualTo(2)
        assertThat(result.hasNext()).isTrue()
        assertThat(result.getString("name")).isEqualTo("Hero")
        assertThat(result.getNumber("level")).isEqualTo(100)

        assertThat(result.next()).isTrue()
        assertThat(result.getString("name")).isEqualTo("Mage")
        assertThat(result.getNumber("level")).isEqualTo(50)

        assertThat(result.next()).isFalse()
    }

    @Test
    fun `storeQuery mirrors DBResult defaults for missing and null columns`() {
        assertThat(DatabaseConnectionFactory.executeQuery(dataSource, "CREATE TABLE sample (id INT, nickname VARCHAR(50))")).isTrue()
        assertThat(DatabaseConnectionFactory.executeQuery(dataSource, "INSERT INTO sample VALUES (1, NULL)")).isTrue()

        val result = DatabaseConnectionFactory.storeQuery(dataSource, "SELECT id, nickname FROM sample")
        assertThat(result).isNotNull
        assertThat(result!!.getString("nickname")).isEmpty()
        assertThat(result.getString("unknown")).isEmpty()
        assertThat(result.getNumber("unknown")).isZero()
    }

    @Test
    fun `isRecoverableError matches the Canary recoverable MySQL codes`() {
        assertThat(DatabaseConnectionFactory.isRecoverableError(2013)).isTrue()
        assertThat(DatabaseConnectionFactory.isRecoverableError(2006)).isTrue()
        assertThat(DatabaseConnectionFactory.isRecoverableError(2003)).isTrue()
        assertThat(DatabaseConnectionFactory.isRecoverableError(1053)).isTrue()
        assertThat(DatabaseConnectionFactory.isRecoverableError(2002)).isTrue()
        assertThat(DatabaseConnectionFactory.isRecoverableError(1064)).isFalse()
        assertThat(DatabaseConnectionFactory.isRecoverableError(1062)).isFalse()
    }
}

/**
 * Simulates a database whose first [failures] connection attempts die with the given
 * [sqlState]/[errorCode], then delegates to the real H2 pool to model reconnect recovery.
 */
private class FlakyDataSource(
    private val delegate: DataSource,
    private val failures: Int,
    private val sqlState: String? = "08S01",
    private val errorCode: Int = 0
) : DataSource by delegate {

    val attempts = AtomicInteger(0)

    override fun getConnection(): Connection {
        if (attempts.incrementAndGet() <= failures) {
            throw SQLException("simulated connection loss", sqlState, errorCode)
        }
        return delegate.connection
    }
}
