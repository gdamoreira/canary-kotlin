package org.opentibia.canary.database

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import javax.sql.DataSource

/**
 * Transcribes the Canary `Database` handle lifecycle:
 * `Database::connect` automatic reconnect (src/database/database.cpp:61-63) and
 * `Database::~Database` handle teardown (src/database/database.cpp:35-39).
 */
class DatabaseConnectionFactoryTest {

    @Test
    fun `createInMemoryDataSource opens a working pool and closeDataSource shuts it down`() {
        val dataSource = DatabaseConnectionFactory.createInMemoryDataSource("canary_lifecycle_${System.nanoTime()}")

        assertThat(DatabaseConnectionFactory.executeQuery(dataSource, "SELECT 1")).isTrue()

        DatabaseConnectionFactory.closeDataSource(dataSource)
    }

    @Test
    fun `queries fail cleanly once the pool has been closed`() {
        val dataSource = DatabaseConnectionFactory.createInMemoryDataSource("canary_closed_${System.nanoTime()}")
        assertThat(DatabaseConnectionFactory.executeQuery(dataSource, "SELECT 1")).isTrue()

        DatabaseConnectionFactory.closeDataSource(dataSource)

        assertThat(DatabaseConnectionFactory.executeQuery(dataSource, "SELECT 1")).isFalse()
        assertThat(DatabaseConnectionFactory.storeQuery(dataSource, "SELECT 1")).isNull()
    }

    @Test
    fun `closeDataSource is idempotent`() {
        val dataSource = DatabaseConnectionFactory.createInMemoryDataSource("canary_idempotent_${System.nanoTime()}")

        DatabaseConnectionFactory.closeDataSource(dataSource)
        DatabaseConnectionFactory.closeDataSource(dataSource)

        assertThat(DatabaseConnectionFactory.executeQuery(dataSource, "SELECT 1")).isFalse()
    }

    @Test
    fun `closeDataSource leaves a non-closeable data source untouched`() {
        val hikari = DatabaseConnectionFactory.createInMemoryDataSource("canary_plain_${System.nanoTime()}")
        try {
            val plain: DataSource = DelegatingDataSource(hikari)

            DatabaseConnectionFactory.closeDataSource(plain)

            assertThat(DatabaseConnectionFactory.executeQuery(plain, "SELECT 1")).isTrue()
        } finally {
            hikari.close()
        }
    }

    @Test
    fun `default configuration builds the PostgreSQL JDBC URL`() {
        assertThat(DatabaseConfig().jdbcUrl).isEqualTo("jdbc:postgresql://127.0.0.1:5432/canary")
    }

    @Test
    fun `MariaDB configuration builds the MariaDB JDBC URL`() {
        val config = DatabaseConfig(
            host = "db.example.org",
            port = 3306,
            database = "canary",
            engine = DatabaseEngine.MARIADB
        )

        assertThat(config.jdbcUrl).isEqualTo("jdbc:mariadb://db.example.org:3306/canary")
    }

    @Test
    fun `an explicit JDBC URL override wins over the engine`() {
        val config = DatabaseConfig(
            engine = DatabaseEngine.MARIADB,
            jdbcUrlOverride = "jdbc:h2:mem:canary_override;MODE=PostgreSQL"
        )

        assertThat(config.jdbcUrl).isEqualTo("jdbc:h2:mem:canary_override;MODE=PostgreSQL")
    }

    @Test
    fun `migration locations follow the configured engine`() {
        assertThat(DatabaseConnectionFactory.migrationsLocationFor(DatabaseEngine.POSTGRESQL))
            .isEqualTo("classpath:db/migration/postgres")
        assertThat(DatabaseConnectionFactory.migrationsLocationFor(DatabaseEngine.MARIADB))
            .isEqualTo("classpath:db/migration/mariadb")
    }

    @Test
    fun `database product names select the matching migration tree`() {
        assertThat(DatabaseConnectionFactory.migrationsLocationForProductName("H2"))
            .isEqualTo("classpath:db/migration/postgres")
        assertThat(DatabaseConnectionFactory.migrationsLocationForProductName("PostgreSQL"))
            .isEqualTo("classpath:db/migration/postgres")
        assertThat(DatabaseConnectionFactory.migrationsLocationForProductName("MariaDB"))
            .isEqualTo("classpath:db/migration/mariadb")
        assertThat(DatabaseConnectionFactory.migrationsLocationForProductName("MySQL"))
            .isEqualTo("classpath:db/migration/mariadb")
    }

    @Test
    fun `an unsupported database product name fails fast`() {
        assertThatThrownBy { DatabaseConnectionFactory.migrationsLocationForProductName("SQLite") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("SQLite")
    }

    @Test
    fun `Hikari driver selection follows the resolved JDBC URL`() {
        assertThat(DatabaseConnectionFactory.driverClassNameForUrl("jdbc:postgresql://127.0.0.1:5432/canary"))
            .isEqualTo("org.postgresql.Driver")
        assertThat(DatabaseConnectionFactory.driverClassNameForUrl("jdbc:mariadb://127.0.0.1:3306/canary"))
            .isEqualTo("org.mariadb.jdbc.Driver")
        assertThat(DatabaseConnectionFactory.driverClassNameForUrl("jdbc:h2:mem:canary;MODE=PostgreSQL"))
            .isNull()
    }

    @Test
    fun `migrate resolves the postgres tree from the in-memory H2 product name`() {
        val dataSource = DatabaseConnectionFactory.createInMemoryDataSource("canary_migration_tree_${System.nanoTime()}")
        try {
            val applied = DatabaseConnectionFactory.migrate(dataSource)

            assertThat(applied).isGreaterThanOrEqualTo(21)
            assertThat(DatabaseConnectionFactory.executeQuery(dataSource, "SELECT key_name FROM kv_store")).isTrue()
        } finally {
            DatabaseConnectionFactory.closeDataSource(dataSource)
        }
    }
}

/**
 * A [DataSource] that exposes no lifecycle hook, exercising the non-pool teardown path.
 */
private class DelegatingDataSource(delegate: DataSource) : DataSource by delegate
