package org.opentibia.canary.database

import org.assertj.core.api.Assertions.assertThat
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
}

/**
 * A [DataSource] that exposes no lifecycle hook, exercising the non-pool teardown path.
 */
private class DelegatingDataSource(delegate: DataSource) : DataSource by delegate
