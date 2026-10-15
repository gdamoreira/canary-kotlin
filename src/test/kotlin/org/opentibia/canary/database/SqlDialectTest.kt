package org.opentibia.canary.database

import com.zaxxer.hikari.HikariDataSource
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Detection half of the runtime SQL portability layer: `IOMapSerialize` picks
 * its upsert shapes from [SqlDialect], which is resolved from the live JDBC
 * connection's product name (MySQL `information_schema`-style metadata).
 */
class SqlDialectTest {

    private lateinit var dataSource: HikariDataSource

    @BeforeEach
    fun setUp() {
        dataSource = DatabaseConnectionFactory.createInMemoryDataSource("canary_sql_dialect_${System.nanoTime()}")
    }

    @AfterEach
    fun tearDown() {
        dataSource.close()
    }

    @Test
    fun `detects H2`() {
        assertThat(SqlDialect.fromProductName("H2")).isEqualTo(SqlDialect.H2)
    }

    @Test
    fun `detects PostgreSQL`() {
        assertThat(SqlDialect.fromProductName("PostgreSQL")).isEqualTo(SqlDialect.POSTGRESQL)
    }

    @Test
    fun `detects MariaDB`() {
        assertThat(SqlDialect.fromProductName("MariaDB")).isEqualTo(SqlDialect.MARIADB)
    }

    @Test
    fun `maps MySQL to the MariaDB dialect`() {
        assertThat(SqlDialect.fromProductName("MySQL")).isEqualTo(SqlDialect.MARIADB)
    }

    @Test
    fun `product name detection ignores case`() {
        assertThat(SqlDialect.fromProductName("h2")).isEqualTo(SqlDialect.H2)
        assertThat(SqlDialect.fromProductName("postgresql")).isEqualTo(SqlDialect.POSTGRESQL)
        assertThat(SqlDialect.fromProductName("mariadb")).isEqualTo(SqlDialect.MARIADB)
        assertThat(SqlDialect.fromProductName("mysql")).isEqualTo(SqlDialect.MARIADB)
    }

    @Test
    fun `unknown product names fail fast`() {
        assertThatThrownBy { SqlDialect.fromProductName("Oracle") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Oracle")
    }

    @Test
    fun `detects the dialect from a live connection`() {
        dataSource.connection.use { connection ->
            assertThat(connection.metaData.databaseProductName).isEqualTo("H2")
            assertThat(SqlDialect.from(connection)).isEqualTo(SqlDialect.H2)
        }
    }

    @Test
    fun `H2 and PostgreSQL quote identifiers with ANSI double quotes`() {
        assertThat(SqlDialect.H2.quoteIdentifier("type")).isEqualTo("\"type\"")
        assertThat(SqlDialect.H2.quoteIdentifier("key")).isEqualTo("\"key\"")
        assertThat(SqlDialect.POSTGRESQL.quoteIdentifier("value")).isEqualTo("\"value\"")
    }

    @Test
    fun `MariaDB quotes identifiers with backticks`() {
        assertThat(SqlDialect.MARIADB.quoteIdentifier("type")).isEqualTo("`type`")
        assertThat(SqlDialect.MARIADB.quoteIdentifier("key")).isEqualTo("`key`")
    }

    @Test
    fun `quoteIdentifier escapes the dialect quote character`() {
        assertThat(SqlDialect.H2.quoteIdentifier("we\"ird")).isEqualTo("\"we\"\"ird\"")
        assertThat(SqlDialect.POSTGRESQL.quoteIdentifier("we\"ird")).isEqualTo("\"we\"\"ird\"")
        assertThat(SqlDialect.MARIADB.quoteIdentifier("we`ird")).isEqualTo("`we``ird`")
    }
}
