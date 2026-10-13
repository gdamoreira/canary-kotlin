package org.opentibia.canary.database

import com.zaxxer.hikari.HikariDataSource
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.sql.SQLException

/**
 * H2 (Flyway V1-V6) round-trip tests for [JdbcPlayerSpellsRepository],
 * transcribed from Canary's learned-spell persistence:
 * `IOLoginDataSave::savePlayerSpells`
 * (src/io/functions/iologindata_save_player.cpp:416-445) and
 * `IOLoginDataLoad::loadPlayerInstantSpellList`
 * (src/io/functions/iologindata_load_player.cpp:527-541), backed by the
 * `player_spells` composite primary key (schema.sql:780-789).
 */
class JdbcPlayerSpellsRepositoryTest {

    private lateinit var dataSource: HikariDataSource
    private lateinit var repository: JdbcPlayerSpellsRepository

    @BeforeEach
    fun setUp() {
        dataSource = DatabaseConnectionFactory.createInMemoryDataSource("canary_spells_${System.nanoTime()}")
        val applied = DatabaseConnectionFactory.migrate(dataSource)
        assertThat(applied).isGreaterThanOrEqualTo(6)
        repository = JdbcPlayerSpellsRepository(dataSource)
    }

    @AfterEach
    fun tearDown() {
        dataSource.close()
    }

    @Test
    fun `round-trips learned spell names`() {
        repository.save(1L, listOf("exura", "exura gran", "utani hur"))

        assertThat(repository.load(1L)).containsExactlyInAnyOrder("exura", "exura gran", "utani hur")
    }

    @Test
    fun `returns an empty spell list for a player without learned spells`() {
        assertThat(repository.load(2L)).isEmpty()
    }

    @Test
    fun `replaces the persisted spell list on resave`() {
        repository.save(1L, listOf("exura", "exura gran", "utani hur"))
        repository.save(1L, listOf("exura ico"))

        assertThat(repository.load(1L)).containsExactly("exura ico")
    }

    @Test
    fun `persists spell lists for separate players independently`() {
        repository.save(1L, listOf("exura"))
        repository.save(2L, listOf("exori frigo"))

        assertThat(repository.load(1L)).containsExactly("exura")
        assertThat(repository.load(2L)).containsExactly("exori frigo")
    }

    @Test
    fun `rolls back the replacement when the new list violates the composite key`() {
        repository.save(1L, listOf("exura"))

        assertThatThrownBy { repository.save(1L, listOf("utani hur", "utani hur")) }
            .isInstanceOf(SQLException::class.java)

        assertThat(repository.load(1L)).containsExactly("exura")
    }
}
