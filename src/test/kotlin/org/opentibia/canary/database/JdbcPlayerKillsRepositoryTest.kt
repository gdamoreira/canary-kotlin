package org.opentibia.canary.database

import com.zaxxer.hikari.HikariDataSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.players.pvp.Kill

/**
 * H2 (Flyway V10) round-trip tests for [JdbcPlayerKillsRepository],
 * transcribed from Canary's unjustified-kill persistence:
 * `IOLoginDataSave::savePlayerKills`
 * (src/io/functions/iologindata_save_player.cpp:447-476) replaces every row of
 * the player and re-inserts `unjustifiedKills`; `IOLoginDataLoad::loadPlayerKills`
 * (src/io/functions/iologindata_load_player.cpp:371-392) reads
 * `player_id`, `time`, `target`, `unavenged` (schema.sql:688-698).
 */
class JdbcPlayerKillsRepositoryTest {

    private lateinit var dataSource: HikariDataSource
    private lateinit var repository: JdbcPlayerKillsRepository

    @BeforeEach
    fun setUp() {
        dataSource = DatabaseConnectionFactory.createInMemoryDataSource("canary_kills_${System.nanoTime()}")
        val applied = DatabaseConnectionFactory.migrate(dataSource)
        assertThat(applied).isGreaterThanOrEqualTo(10)
        repository = JdbcPlayerKillsRepository(dataSource)
    }

    @AfterEach
    fun tearDown() {
        dataSource.close()
    }

    @Test
    fun `round-trips target time and unavenged state`() {
        val kills = listOf(
            Kill(target = 42L, time = 1_700_000_000L, unavenged = true),
            Kill(target = 43L, time = 1_700_000_100L, unavenged = false),
        )

        repository.save(1L, kills)

        assertThat(repository.load(1L)).containsExactlyInAnyOrderElementsOf(kills)
    }

    @Test
    fun `save replaces the persisted kills like the delete-insert flow`() {
        repository.save(1L, listOf(Kill(target = 42L, time = 100L, unavenged = true)))
        repository.save(1L, listOf(Kill(target = 43L, time = 200L, unavenged = false)))

        assertThat(repository.load(1L)).containsExactly(Kill(target = 43L, time = 200L, unavenged = false))
    }

    @Test
    fun `save with an empty list clears the player rows`() {
        repository.save(1L, listOf(Kill(target = 42L, time = 100L, unavenged = true)))

        repository.save(1L, emptyList())

        assertThat(repository.load(1L)).isEmpty()
    }

    @Test
    fun `persists kills for separate players independently`() {
        repository.save(1L, listOf(Kill(target = 42L, time = 100L, unavenged = true)))
        repository.save(2L, listOf(Kill(target = 43L, time = 200L, unavenged = true)))

        assertThat(repository.load(1L).map { it.target }).containsExactly(42L)
        assertThat(repository.load(2L).map { it.target }).containsExactly(43L)
    }

    @Test
    fun `returns an empty list for a player without persisted kills`() {
        assertThat(repository.load(99L)).isEmpty()
    }
}
