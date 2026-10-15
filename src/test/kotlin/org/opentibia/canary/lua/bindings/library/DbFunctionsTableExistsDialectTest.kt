package org.opentibia.canary.lua.bindings.library

import com.zaxxer.hikari.HikariDataSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.opentibia.canary.database.Database
import org.opentibia.canary.database.DatabaseConnectionFactory
import org.opentibia.canary.database.SqlDialect

/**
 * Runtime SQL portability of `DatabaseManager::tableExists`
 * (databasemanager.cpp:58-64): MariaDB keeps the backtick-quoted
 * `information_schema`.`TABLES` lookup, while PostgreSQL (and H2 in PostgreSQL
 * mode) gets the lowercase `information_schema.tables` form selected through
 * [SqlDialect], detected from the live connection exactly like
 * `IOMapSerialize`'s upserts.
 */
class DbFunctionsTableExistsDialectTest {

    private lateinit var dataSource: HikariDataSource

    @BeforeEach
    fun setUp() {
        dataSource = DatabaseConnectionFactory.createInMemoryDataSource("table_exists_dialect_${System.nanoTime()}")
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("CREATE TABLE players (id BIGINT PRIMARY KEY)")
            }
        }
    }

    @AfterEach
    fun tearDown() {
        dataSource.close()
    }

    @Test
    fun `MariaDB keeps the upstream information_schema query`() {
        assertThat(tableExistsQuery(SqlDialect.MARIADB, "canary", "players"))
            .isEqualTo(
                "SELECT `TABLE_NAME` FROM `information_schema`.`TABLES` " +
                    "WHERE `TABLE_SCHEMA` = 'canary' AND `TABLE_NAME` = 'players';",
            )
    }

    @Test
    fun `PostgreSQL and H2 share the portable information_schema query`() {
        val expected = "SELECT table_name FROM information_schema.tables " +
            "WHERE table_schema = 'public' AND table_name = 'players' LIMIT 1"
        for (dialect in listOf(SqlDialect.POSTGRESQL, SqlDialect.H2)) {
            assertThat(tableExistsQuery(dialect, "public", "players")).isEqualTo(expected)
        }
    }

    @Test
    fun `tableExists detects the H2 dialect and answers from information_schema`() {
        val database = Database(dataSource)
        dataSource.connection.use { connection ->
            val operations = DatabaseLuaOperations(database, dataSource, schemaName = connection.schema)

            assertThat(operations.tableExists("players")).isTrue()
            assertThat(operations.tableExists("missing_table")).isFalse()
        }
    }
}
