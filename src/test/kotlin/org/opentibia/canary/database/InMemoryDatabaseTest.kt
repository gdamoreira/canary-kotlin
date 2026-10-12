package org.opentibia.canary.database

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.opentibia.canary.map.Position
import javax.sql.DataSource

class InMemoryDatabaseTest {

    private lateinit var dataSource: DataSource
    private lateinit var accountRepo: JdbcAccountRepository
    private lateinit var playerRepo: JdbcPlayerRepository

    @BeforeEach
    fun setUp() {
        // Create an in-memory PostgreSQL-compatible H2 database
        val h2DataSource = DatabaseConnectionFactory.createInMemoryDataSource("canary_test_${System.nanoTime()}")
        // Run Flyway migrations directly against the in-memory database
        val migrations = DatabaseConnectionFactory.migrate(h2DataSource)
        assertThat(migrations).isGreaterThanOrEqualTo(2)

        dataSource = h2DataSource
        accountRepo = JdbcAccountRepository(dataSource)
        playerRepo = JdbcPlayerRepository(dataSource)
    }

    @AfterEach
    fun tearDown() {
        (dataSource as? com.zaxxer.hikari.HikariDataSource)?.close()
    }

    @Test
    fun `authenticates seeded test account from in-memory database`() {
        val account = accountRepo.authenticate("1", "1")
        assertThat(account).isNotNull
        assertThat(account?.id).isEqualTo(1L)
        assertThat(account?.name).isEqualTo("1")
        assertThat(account?.characters).hasSize(2)
        assertThat(account?.characters?.map { it.name }).containsExactlyInAnyOrder("Knight Hero", "Sorcerer Master")
    }

    @Test
    fun `loads, modifies, and persists player entity in in-memory database`() {
        val player = playerRepo.loadByName("Knight Hero")
        assertThat(player).isNotNull
        assertThat(player?.level).isEqualTo(100)
        assertThat(player?.health).isEqualTo(1800)

        // Modify in-memory
        player!!.drainHealth(200)
        player.position = Position(1050, 1050, 7)
        playerRepo.save(player)

        // Reload and verify persistence
        val reloaded = playerRepo.loadByName("Knight Hero")
        assertThat(reloaded?.health).isEqualTo(1600)
        assertThat(reloaded?.position).isEqualTo(Position(1050, 1050, 7))
    }
}
