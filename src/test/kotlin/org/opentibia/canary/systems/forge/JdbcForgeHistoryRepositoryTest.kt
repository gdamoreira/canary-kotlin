package org.opentibia.canary.systems.forge

import com.zaxxer.hikari.HikariDataSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.opentibia.canary.database.DatabaseConnectionFactory

/**
 * H2 (Flyway V1-V8) round-trip tests for [JdbcForgeHistoryRepository],
 * transcribed from Canary's forge-history persistence:
 * `PlayerForgeHistory::load` / `PlayerForgeHistory::save`
 * (src/creatures/players/components/player_forge_history.cpp:43-113) and the
 * `forge_history` table (schema.sql:314-329).
 */
class JdbcForgeHistoryRepositoryTest {

    private lateinit var dataSource: HikariDataSource
    private lateinit var repository: JdbcForgeHistoryRepository

    @BeforeEach
    fun setUp() {
        dataSource = DatabaseConnectionFactory.createInMemoryDataSource("canary_forge_history_${System.nanoTime()}")
        val applied = DatabaseConnectionFactory.migrate(dataSource)
        assertThat(applied).isGreaterThanOrEqualTo(8)
        repository = JdbcForgeHistoryRepository(dataSource)
    }

    @AfterEach
    fun tearDown() {
        dataSource.close()
    }

    @Test
    fun `round-trips the persisted forge history columns through jdbc`() {
        val entry = ForgeHistory(
            actionType = ForgeAction_t.TRANSFER,
            tier = 3,
            bonus = 5,
            createdAt = 1_700_000_000_000L,
            cost = 123_456L,
            dustCost = 100L,
            coresCost = 2L,
            gained = 7L,
            success = true,
            tierLoss = true,
            successCore = true,
            convergence = true,
            description = "<b>Transferred</b> a tier 3 item",
            firstItemName = "Sword",
            secondItemName = "Axe",
        )

        assertThat(repository.insert(1L, listOf(entry))).isTrue()
        val loaded = repository.load(1L)

        assertThat(loaded).hasSize(1)
        val stored = loaded.single()
        assertThat(stored.id).isPositive()
        assertThat(stored).isEqualTo(
            ForgeHistory(
                actionType = ForgeAction_t.TRANSFER,
                createdAt = 1_700_000_000_000L,
                id = stored.id,
                success = true,
                description = "<b>Transferred</b> a tier 3 item",
            ),
        )
    }

    @Test
    fun `loads multiple entries in insertion order scoped to the player`() {
        repository.insert(
            1L,
            listOf(
                ForgeHistory(createdAt = 1000L, description = "first"),
                ForgeHistory(createdAt = 2000L, description = "second"),
            ),
        )
        repository.insert(2L, listOf(ForgeHistory(createdAt = 1000L, description = "other")))

        val first = repository.load(1L)
        assertThat(first.map { it.description }).containsExactly("first", "second")
        assertThat(first.map { it.id }).isSorted()
        assertThat(repository.load(2L).single().description).isEqualTo("other")
    }

    @Test
    fun `deletes only the requested ids for the player`() {
        repository.insert(
            1L,
            listOf(
                ForgeHistory(createdAt = 1000L),
                ForgeHistory(createdAt = 2000L),
                ForgeHistory(createdAt = 3000L),
            ),
        )
        val ids = repository.load(1L).map { it.id }

        assertThat(repository.delete(1L, listOf(ids[0], ids[2]))).isTrue()

        assertThat(repository.load(1L).map { it.createdAt }).containsExactly(2000L)
    }

    @Test
    fun `delete with no ids keeps every entry`() {
        repository.insert(1L, listOf(ForgeHistory(createdAt = 1000L)))

        assertThat(repository.delete(1L, emptyList())).isTrue()

        assertThat(repository.load(1L)).hasSize(1)
    }

    @Test
    fun `insert with no entries succeeds without touching the table`() {
        assertThat(repository.insert(1L, emptyList())).isTrue()

        assertThat(repository.load(1L)).isEmpty()
    }

    @Test
    fun `load returns an empty list for a player without history`() {
        assertThat(repository.load(1L)).isEmpty()
    }

    @Test
    fun `rejects a duplicate done_at and rolls back the whole batch`() {
        assertThat(repository.insert(1L, listOf(ForgeHistory(createdAt = 1000L)))).isTrue()

        val accepted = repository.insert(
            1L,
            listOf(
                ForgeHistory(createdAt = 2000L),
                ForgeHistory(createdAt = 1000L),
            ),
        )

        assertThat(accepted).isFalse()
        assertThat(repository.load(1L).map { it.createdAt }).containsExactly(1000L)
    }

    @Test
    fun `player forge history save and load round-trips through the jdbc repository`() {
        val history = PlayerForgeHistory(playerGuid = 1L, repository = repository)
        history.add(
            ForgeHistory(
                actionType = ForgeAction_t.FUSION,
                description = "fusion one",
                createdAt = 5000L,
                success = true,
            ),
        )
        history.add(
            ForgeHistory(
                actionType = ForgeAction_t.DUSTTOSLIVERS,
                description = "dust one",
                createdAt = 6000L,
            ),
        )

        assertThat(history.save()).isTrue()

        val reloaded = PlayerForgeHistory(playerGuid = 1L, repository = repository)
        assertThat(reloaded.load()).isTrue()
        assertThat(reloaded.get().map { it.description }).containsExactly("fusion one", "dust one")
        assertThat(reloaded.get().map { it.actionType })
            .containsExactly(ForgeAction_t.FUSION, ForgeAction_t.DUSTTOSLIVERS)
        assertThat(reloaded.get().map { it.createdAt }).containsExactly(5000L, 6000L)
        assertThat(reloaded.get().map { it.success }).containsExactly(true, false)
    }

    @Test
    fun `player forge history save without changes leaves rows untouched`() {
        val history = PlayerForgeHistory(playerGuid = 1L, repository = repository)

        assertThat(history.save()).isTrue()

        assertThat(repository.load(1L)).isEmpty()
    }

    @Test
    fun `player forge history save deletes removed entries`() {
        repository.insert(
            1L,
            listOf(
                ForgeHistory(createdAt = 1000L, description = "keep"),
                ForgeHistory(createdAt = 2000L, description = "drop"),
            ),
        )
        val history = PlayerForgeHistory(playerGuid = 1L, repository = repository)
        history.load()
        val dropId = history.get().single { it.description == "drop" }.id

        history.remove(dropId)

        assertThat(history.save()).isTrue()
        assertThat(repository.load(1L).map { it.description }).containsExactly("keep")
    }
}
