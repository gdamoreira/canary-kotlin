package org.opentibia.canary.database

import com.zaxxer.hikari.HikariDataSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * H2 (Flyway V10) round-trip tests for [JdbcPlayerStashRepository],
 * transcribed from Canary's supply-stash persistence:
 * `IOLoginDataSave::savePlayerStash`
 * (src/io/functions/iologindata_save_player.cpp:374-414) deletes every row and
 * re-inserts the `getStashItems()` map; `IOLoginDataLoad::loadPlayerStashItems`
 * (src/io/functions/iologindata_load_player.cpp:444-470) reads
 * `item_id`/`item_count` (schema.sql:791-800).
 */
class JdbcPlayerStashRepositoryTest {

    private lateinit var dataSource: HikariDataSource
    private lateinit var repository: JdbcPlayerStashRepository

    @BeforeEach
    fun setUp() {
        dataSource = DatabaseConnectionFactory.createInMemoryDataSource("canary_stash_${System.nanoTime()}")
        val applied = DatabaseConnectionFactory.migrate(dataSource)
        assertThat(applied).isGreaterThanOrEqualTo(10)
        repository = JdbcPlayerStashRepository(dataSource)
    }

    @AfterEach
    fun tearDown() {
        dataSource.close()
    }

    @Test
    fun `round-trips stash item counters`() {
        repository.save(1L, mapOf(2148 to 100, 2376 to 7))

        assertThat(repository.load(1L)).containsExactlyInAnyOrderEntriesOf(mapOf(2148 to 100, 2376 to 7))
    }

    @Test
    fun `save replaces the persisted stash`() {
        repository.save(1L, mapOf(2148 to 100))
        repository.save(1L, mapOf(2376 to 3))

        assertThat(repository.load(1L)).containsExactlyEntriesOf(mapOf(2376 to 3))
    }

    @Test
    fun `save with an empty map clears the player rows`() {
        repository.save(1L, mapOf(2148 to 100))

        repository.save(1L, emptyMap())

        assertThat(repository.load(1L)).isEmpty()
    }

    @Test
    fun `persists stash maps for separate players independently`() {
        repository.save(1L, mapOf(2148 to 100))
        repository.save(2L, mapOf(2148 to 55))

        assertThat(repository.load(1L)).containsExactlyEntriesOf(mapOf(2148 to 100))
        assertThat(repository.load(2L)).containsExactlyEntriesOf(mapOf(2148 to 55))
    }

    @Test
    fun `returns an empty map for a player without a persisted stash`() {
        assertThat(repository.load(99L)).isEmpty()
    }
}
