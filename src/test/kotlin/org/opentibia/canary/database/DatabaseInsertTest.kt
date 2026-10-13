package org.opentibia.canary.database

import com.zaxxer.hikari.HikariDataSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Statement
import java.util.concurrent.atomic.AtomicInteger
import javax.sql.DataSource

class DatabaseInsertTest {

    private lateinit var dataSource: HikariDataSource
    private lateinit var database: Database

    @BeforeEach
    fun setUp() {
        dataSource = DatabaseConnectionFactory.createInMemoryDataSource("db_insert_${System.nanoTime()}")
        database = Database(dataSource)
        dataSource.connection.use { conn ->
            conn.createStatement().use { stmt ->
                stmt.execute("CREATE TABLE insert_probe (id INT, payload CLOB)")
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
                stmt.executeQuery("SELECT COUNT(*) FROM insert_probe").use { rs ->
                    rs.next()
                    rs.getInt(1)
                }
            }
        }

    @Test
    fun `execute returns true without querying when no rows were added`() {
        val insert = DBInsert(database, "INSERT INTO insert_probe (id) VALUES")

        assertThat(insert.execute()).isTrue
        assertThat(countRows()).isZero
    }

    @Test
    fun `addRow returns false when a single row exceeds the max packet size`() {
        val constrained = Database(dataSource, maxPacketSize = 8L)
        val insert = DBInsert(constrained, "INSERT INTO insert_probe (id) VALUES")

        assertThat(insert.addRow("1")).isFalse
        assertThat(insert.execute()).isTrue
        assertThat(countRows()).isZero
    }

    @Test
    fun `addRow flushes accumulated rows when the max packet size is reached`() {
        val counting = CountingDataSource(dataSource)
        val constrained = Database(counting, maxPacketSize = 40L)
        val insert = DBInsert(constrained, "INSERT INTO insert_probe (id) VALUES")

        assertThat(insert.addRow("1")).isTrue
        assertThat(insert.addRow("2")).isTrue
        assertThat(insert.addRow("3")).isTrue
        assertThat(counting.executions.get()).isEqualTo(2)
        assertThat(countRows()).isEqualTo(2)

        assertThat(insert.execute()).isTrue
        assertThat(counting.executions.get()).isEqualTo(3)
        assertThat(countRows()).isEqualTo(3)
    }

    @Test
    fun `execute rejects a batch whose base query already exceeds the query size limit`() {
        val counting = CountingDataSource(dataSource)
        val oversized = Database(counting, maxPacketSize = Database.MAX_QUERY_SIZE * 2)
        val base = "INSERT INTO insert_probe (id, payload) VALUES " + "x".repeat(Database.MAX_QUERY_SIZE.toInt())
        val insert = DBInsert(oversized, base)

        assertThat(insert.addRow("1, 'a'")).isTrue
        assertThat(insert.execute()).isFalse
        assertThat(counting.executions.get()).isZero
        assertThat(countRows()).isZero
    }

    @Test
    fun `execute splits a batch that exceeds the query size limit at row boundaries`() {
        val counting = CountingDataSource(dataSource)
        val oversized = Database(counting, maxPacketSize = Database.MAX_QUERY_SIZE * 2)
        val insert = DBInsert(oversized, "INSERT INTO insert_probe (id, payload) VALUES")
        val payload = "y".repeat(2 * 1024 * 1024)

        repeat(5) { index ->
            assertThat(insert.addRow("$index, '$payload'")).isTrue
        }
        assertThat(insert.execute()).isTrue
        assertThat(counting.executions.get()).isEqualTo(2)
        assertThat(countRows()).isEqualTo(5)
    }

    private class CountingDataSource(private val delegate: DataSource) : DataSource by delegate {

        val executions = AtomicInteger(0)

        override fun getConnection(): Connection = CountingConnection(delegate.connection, executions)
    }

    private class CountingConnection(
        private val delegate: Connection,
        private val executions: AtomicInteger
    ) : Connection by delegate {

        override fun createStatement(): Statement = CountingStatement(delegate.createStatement(), executions)

        override fun createStatement(resultSetType: Int, resultSetConcurrency: Int): Statement =
            CountingStatement(delegate.createStatement(resultSetType, resultSetConcurrency), executions)

        override fun createStatement(resultSetType: Int, resultSetConcurrency: Int, resultSetHoldability: Int): Statement =
            CountingStatement(delegate.createStatement(resultSetType, resultSetConcurrency, resultSetHoldability), executions)
    }

    private class CountingStatement(
        private val delegate: Statement,
        private val executions: AtomicInteger
    ) : Statement by delegate {

        override fun execute(sql: String): Boolean {
            executions.incrementAndGet()
            return delegate.execute(sql)
        }

        override fun executeQuery(sql: String): ResultSet {
            executions.incrementAndGet()
            return delegate.executeQuery(sql)
        }

        override fun executeUpdate(sql: String): Int {
            executions.incrementAndGet()
            return delegate.executeUpdate(sql)
        }
    }
}
