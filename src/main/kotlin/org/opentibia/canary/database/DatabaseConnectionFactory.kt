package org.opentibia.canary.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.oshai.kotlinlogging.KotlinLogging
import org.flywaydb.core.Flyway
import java.sql.ResultSet
import java.sql.SQLException
import javax.sql.DataSource

private val logger = KotlinLogging.logger {}

/**
 * Relational engine the server can target. PostgreSQL 16+ stays the default;
 * [MARIADB] mirrors upstream Canary's MySQL deployment (`Database::connect`,
 * src/database/database.cpp:61) and MariaDB 11.4 compatibility.
 */
enum class DatabaseEngine(val defaultPort: Int) {
    POSTGRESQL(5432),
    MARIADB(3306),
}

data class DatabaseConfig(
    val host: String = "127.0.0.1",
    val port: Int = 5432,
    val database: String = "canary",
    val user: String = "canary",
    val password: String = "canary",
    val poolSize: Int = 10,
    val jdbcUrlOverride: String? = null,
    val engine: DatabaseEngine = DatabaseEngine.POSTGRESQL
) {
    val jdbcUrl: String
        get() = jdbcUrlOverride ?: when (engine) {
            DatabaseEngine.POSTGRESQL -> "jdbc:postgresql://$host:$port/$database"
            DatabaseEngine.MARIADB -> "jdbc:mariadb://$host:$port/$database"
        }
}

object DatabaseConnectionFactory {

    /** Flyway migration tree of the PostgreSQL/H2 schema. */
    const val POSTGRES_MIGRATIONS_LOCATION: String = "classpath:db/migration/postgres"

    /** Flyway migration tree of the MariaDB/MySQL schema. */
    const val MARIADB_MIGRATIONS_LOCATION: String = "classpath:db/migration/mariadb"

    /** Retry budget of `Database::executeQuery` (`src/database/database.cpp:255`). */
    const val QUERY_RETRIES: Int = 10

    /** Retry backoff of `Database::retryQuery` / `Database::storeQuery` (`database.cpp:231,280`). */
    const val QUERY_RETRY_DELAY_MILLIS: Long = 1000

    private const val ER_SERVER_SHUTDOWN = 1053
    private const val CR_CONNECTION_ERROR = 2002
    private const val CR_CONN_HOST_ERROR = 2003
    private const val CR_SERVER_GONE_ERROR = 2006
    private const val CR_SERVER_LOST = 2013

    /**
     * Backoff applied between recoverable-error retries. Production keeps Canary's
     * one-second wait; tests drive it to zero so retry paths run without wall-clock delays.
     */
    @Volatile
    internal var retryDelayMillis: Long = QUERY_RETRY_DELAY_MILLIS

    /**
     * Creates an in-memory H2 DataSource in PostgreSQL compatibility mode for fast, isolated local testing.
     */
    fun createInMemoryDataSource(dbName: String = "canary_test"): HikariDataSource {
        val config = DatabaseConfig(
            user = "sa",
            password = "",
            jdbcUrlOverride = "jdbc:h2:mem:$dbName;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1"
        )
        return createDataSource(config)
    }

    fun createDataSource(config: DatabaseConfig): HikariDataSource {
        val hikariConfig = HikariConfig().apply {
            jdbcUrl = config.jdbcUrl
            driverClassNameForUrl(config.jdbcUrl)?.let { driverClassName = it }
            username = config.user
            password = config.password
            maximumPoolSize = config.poolSize
            connectionTimeout = 5000
            idleTimeout = 600000
            maxLifetime = 1800000
            poolName = "CanaryHikariPool"
            // `Database::connect` enables MYSQL_OPT_RECONNECT and fails fast when the
            // initial connection cannot be established (database.cpp:61-72). Hikari
            // validates borrowed connections and evicts/replaces dead ones; the
            // keepalive probe pre-empts silently dropped idle connections.
            initializationFailTimeout = 1
            keepaliveTime = 30000
        }
        return HikariDataSource(hikariConfig)
    }

    /**
     * Hikari driver pinned to the engine the [jdbcUrl] addresses, so an
     * external PostgreSQL/MariaDB pool never depends on driver auto-detection.
     * An explicit override URL (the H2 in-memory test mode) keeps Hikari's
     * URL-based detection, since the engine field does not describe it.
     */
    internal fun driverClassNameForUrl(jdbcUrl: String): String? = when {
        jdbcUrl.startsWith("jdbc:postgresql:") -> "org.postgresql.Driver"
        jdbcUrl.startsWith("jdbc:mariadb:") -> "org.mariadb.jdbc.Driver"
        else -> null
    }

    /**
     * Releases the connection pool, mirroring the `Database` destructor
     * (`database.cpp:35-39`). Idempotent, and a no-op for data sources that are
     * not [AutoCloseable].
     */
    fun closeDataSource(dataSource: DataSource) {
        (dataSource as? AutoCloseable)?.close()
    }

    /**
     * Executes a statement and discards any result rows, mirroring
     * `Database::executeQuery` (`database.cpp:242-259`): retry up to
     * [QUERY_RETRIES] times on recoverable errors.
     */
    fun executeQuery(dataSource: DataSource, query: String): Boolean {
        logger.trace { "Executing Query: $query" }
        return retryQuery(dataSource, query, QUERY_RETRIES)
    }

    /**
     * `Database::retryQuery` (`database.cpp:224-240`): retries only recoverable
     * connection errors, one second apart, for at most [retries] attempts.
     */
    fun retryQuery(dataSource: DataSource, query: String, retries: Int): Boolean {
        var remaining = retries
        while (remaining > 0) {
            try {
                dataSource.connection.use { connection ->
                    connection.createStatement().use { statement ->
                        statement.execute(query)
                    }
                }
                return true
            } catch (e: SQLException) {
                logger.error { "Query: ${query.take(256)}" }
                logger.error { "MySQL error [${e.errorCode}]: ${e.message}" }
                if (!isRecoverable(e)) {
                    return false
                }
                sleepRetry()
                remaining--
            }
        }
        if (remaining == 0) {
            logger.error { "Query ${query.take(256)} failed after $QUERY_RETRIES retries." }
            return false
        }
        return true
    }

    /**
     * `Database::storeQuery` (`database.cpp:261-294`): returns a [DBResult] holding the
     * buffered rows, or `null` when the statement produced no result set or no rows.
     * Recoverable connection errors are retried indefinitely; non-recoverable errors
     * return `null` immediately.
     */
    fun storeQuery(dataSource: DataSource, query: String): DBResult? {
        logger.trace { "Storing Query: $query" }
        while (true) {
            val stored: Pair<Map<String, Int>, List<List<String?>>>? = try {
                dataSource.connection.use { connection ->
                    connection.createStatement().use { statement ->
                        statement.execute(query)
                        statement.resultSet?.use { readRows(it) }
                    }
                }
            } catch (e: SQLException) {
                logger.error { "Query: ${query.take(256)}" }
                logger.error { "Message: ${e.message}" }
                if (!isRecoverable(e)) {
                    return null
                }
                sleepRetry()
                continue
            }

            if (stored == null || stored.second.isEmpty()) {
                return null
            }
            return DBResult(stored.first, stored.second)
        }
    }

    /**
     * `Database::isRecoverableError` (`database.cpp:220-222`): the MySQL client/server
     * error codes that justify a reconnect-and-retry.
     */
    fun isRecoverableError(error: Int): Boolean =
        error == CR_SERVER_LOST ||
            error == CR_SERVER_GONE_ERROR ||
            error == CR_CONN_HOST_ERROR ||
            error == ER_SERVER_SHUTDOWN ||
            error == CR_CONNECTION_ERROR

    /**
     * JDBC translation of `mysql_errno`: vendor connection codes plus SQLState class
     * `08` (connection exception), which PostgreSQL and H2 raise for lost connections.
     */
    private fun isRecoverable(exception: SQLException): Boolean =
        isRecoverableError(exception.errorCode) || exception.sqlState?.startsWith("08") == true

    private fun sleepRetry() {
        val delay = retryDelayMillis
        if (delay <= 0) {
            return
        }
        try {
            Thread.sleep(delay)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private fun readRows(resultSet: ResultSet): Pair<Map<String, Int>, List<List<String?>>> {
        val metadata = resultSet.metaData
        val columns = LinkedHashMap<String, Int>(metadata.columnCount)
        for (index in 1..metadata.columnCount) {
            columns[metadata.getColumnLabel(index)] = index - 1
        }
        val rows = ArrayList<List<String?>>()
        while (resultSet.next()) {
            rows.add(List(metadata.columnCount) { resultSet.getString(it + 1) })
        }
        return columns to rows
    }

    /**
     * Client-side buffered result set, mirroring Canary's `DBResult`
     * (`database.hpp:94-199`): `mysql_store_result` buffers every row and the first row
     * is current at construction, `next` advances, `hasNext` reports a current row.
     */
    class DBResult internal constructor(
        private val columns: Map<String, Int>,
        private val rows: List<List<String?>>
    ) {
        private var rowIndex: Int = if (rows.isEmpty()) -1 else 0

        fun countResults(): Int = rows.size

        fun hasNext(): Boolean = rowIndex in rows.indices

        fun next(): Boolean {
            rowIndex++
            return hasNext()
        }

        fun getString(column: String): String = valueOrNull(column) ?: ""

        fun getNumber(column: String): Long {
            val index = columns[column]
            if (index == null) {
                logger.error { "[DBResult::getNumber] - Column '$column' doesn't exist in the result set" }
                return 0
            }
            if (!hasNext()) {
                return 0
            }
            val value = rows[rowIndex][index] ?: return 0
            val parsed = value.toLongOrNull()
            if (parsed == null) {
                logger.error { "Column '$column' has an invalid value set, value: $value" }
                return 0
            }
            return parsed
        }

        private fun valueOrNull(column: String): String? {
            val index = columns[column]
            if (index == null) {
                logger.error { "Column '$column' does not exist in result set" }
                return null
            }
            if (!hasNext()) {
                return null
            }
            return rows[rowIndex][index]
        }
    }

    /**
     * Flyway migration tree of [engine]: the PostgreSQL tree also serves the H2
     * PostgreSQL-mode test engine.
     */
    fun migrationsLocationFor(engine: DatabaseEngine): String = when (engine) {
        DatabaseEngine.POSTGRESQL -> POSTGRES_MIGRATIONS_LOCATION
        DatabaseEngine.MARIADB -> MARIADB_MIGRATIONS_LOCATION
    }

    /**
     * Flyway migration tree matching the JDBC product name reported by the
     * connection metadata: H2/PostgreSQL run the PostgreSQL tree,
     * MariaDB/MySQL the MariaDB tree. An unsupported engine fails fast rather
     * than migrating the wrong tree.
     */
    fun migrationsLocationForProductName(productName: String): String =
        when (productName.lowercase()) {
            "postgresql", "h2" -> POSTGRES_MIGRATIONS_LOCATION
            "mariadb", "mysql" -> MARIADB_MIGRATIONS_LOCATION
            else -> throw IllegalArgumentException(
                "Unsupported database product '$productName': expected H2/PostgreSQL or MariaDB/MySQL"
            )
        }

    /**
     * Executes database migrations using Flyway. [locations] defaults to the
     * tree selected from the connection's product name; an explicit array
     * overrides the detection.
     */
    fun migrate(dataSource: DataSource, locations: Array<String>? = null): Int {
        val resolvedLocations = locations
            ?: arrayOf(migrationsLocationForProductName(databaseProductName(dataSource)))
        logger.info { "Running database migrations via Flyway from ${resolvedLocations.joinToString(", ")}..." }
        val flyway = Flyway.configure()
            .dataSource(dataSource)
            .locations(*resolvedLocations)
            .baselineOnMigrate(true)
            .load()

        val result = flyway.migrate()
        logger.info { "Flyway migrations finished. Applied ${result.migrationsExecuted} migrations." }
        return result.migrationsExecuted
    }

    private fun databaseProductName(dataSource: DataSource): String =
        dataSource.connection.use { connection -> connection.metaData.databaseProductName }
}
