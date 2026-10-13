package org.opentibia.canary.database

import com.zaxxer.hikari.HikariDataSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.players.components.PlayerStorageRow

/**
 * H2 (Flyway V10) round-trip tests for [JdbcPlayerStorageRepository],
 * transcribed from Canary's storage-map persistence:
 * `DbPlayerStorageRepository::load/deleteKeys/upsert`
 * (src/io/player_storage_repository_db.cpp:25-57) and the
 * delete-then-upsert ordering of `IOLoginDataSave::savePlayerStorage`
 * (src/io/functions/iologindata_save_player.cpp:837-859).
 */
class JdbcPlayerStorageRepositoryTest {

    private lateinit var dataSource: HikariDataSource
    private lateinit var repository: JdbcPlayerStorageRepository

    @BeforeEach
    fun setUp() {
        dataSource = DatabaseConnectionFactory.createInMemoryDataSource("canary_storage_${System.nanoTime()}")
        val applied = DatabaseConnectionFactory.migrate(dataSource)
        assertThat(applied).isGreaterThanOrEqualTo(10)
        repository = JdbcPlayerStorageRepository(dataSource)
    }

    @AfterEach
    fun tearDown() {
        dataSource.close()
    }

    @Test
    fun `round-trips key value rows for a player`() {
        repository.upsert(1L, mapOf(100 to 1, 200 to -5))

        assertThat(repository.load(1L)).containsExactlyInAnyOrder(
            PlayerStorageRow(key = 100, value = 1),
            PlayerStorageRow(key = 200, value = -5),
        )
    }

    @Test
    fun `upsert overwrites an existing key value like ON DUPLICATE KEY UPDATE`() {
        repository.upsert(1L, mapOf(100 to 1))
        repository.upsert(1L, mapOf(100 to 42))

        assertThat(repository.load(1L)).containsExactly(PlayerStorageRow(key = 100, value = 42))
    }

    @Test
    fun `deleteKeys removes only the listed keys`() {
        repository.upsert(1L, mapOf(100 to 1, 200 to 2, 300 to 3))

        assertThat(repository.deleteKeys(1L, listOf(200))).isTrue()

        assertThat(repository.load(1L)).containsExactlyInAnyOrder(
            PlayerStorageRow(key = 100, value = 1),
            PlayerStorageRow(key = 300, value = 3),
        )
    }

    @Test
    fun `deleteKeys with an empty list keeps every row`() {
        repository.upsert(1L, mapOf(100 to 1))

        assertThat(repository.deleteKeys(1L, emptyList())).isTrue()

        assertThat(repository.load(1L)).containsExactly(PlayerStorageRow(key = 100, value = 1))
    }

    @Test
    fun `upsert with an empty map is a no-op`() {
        assertThat(repository.upsert(1L, emptyMap())).isTrue()
        assertThat(repository.load(1L)).isEmpty()
    }

    @Test
    fun `persists storage maps for separate players independently`() {
        repository.upsert(1L, mapOf(100 to 1))
        repository.upsert(2L, mapOf(100 to 2))

        assertThat(repository.load(1L)).containsExactly(PlayerStorageRow(key = 100, value = 1))
        assertThat(repository.load(2L)).containsExactly(PlayerStorageRow(key = 100, value = 2))
    }

    @Test
    fun `returns an empty map for a player without persisted storage`() {
        assertThat(repository.load(99L)).isEmpty()
    }
}
