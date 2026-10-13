package org.opentibia.canary.database

import com.zaxxer.hikari.HikariDataSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.opentibia.canary.items.ItemAttribute

/**
 * H2 (Flyway V10) round-trip tests for [JdbcPlayerRewardsRepository],
 * transcribed from Canary's reward-chest persistence:
 * `IOLoginDataSave::saveRewardItems`
 * (src/io/functions/iologindata_save_player.cpp:599-632) deletes and re-inserts
 * the reward item tree through `saveItems`; `IOLoginDataLoad::loadRewardItems`
 * (src/io/functions/iologindata_load_player.cpp:645-661) reads the same
 * `pid`/`sid`/`itemtype`/`count`/`attributes` columns `ORDER BY pid, sid ASC`
 * (schema.sql:766-777). The attribute blob reuses the V5 `Item::serializeAttr`
 * codec.
 */
class JdbcPlayerRewardsRepositoryTest {

    private lateinit var dataSource: HikariDataSource
    private lateinit var repository: JdbcPlayerRewardsRepository

    @BeforeEach
    fun setUp() {
        dataSource = DatabaseConnectionFactory.createInMemoryDataSource("canary_rewards_${System.nanoTime()}")
        val applied = DatabaseConnectionFactory.migrate(dataSource)
        assertThat(applied).isGreaterThanOrEqualTo(10)
        repository = JdbcPlayerRewardsRepository(dataSource)
    }

    @AfterEach
    fun tearDown() {
        dataSource.close()
    }

    @Test
    fun `round-trips reward rows with pid sid count and attributes`() {
        val items = listOf(
            PlayerContainerItemState(pid = 0, sid = 101, itemId = 2160, count = 10),
            PlayerContainerItemState(
                pid = 101,
                sid = 102,
                itemId = 2376,
                attributes = mapOf(ItemAttribute.ACTIONID to 1234L, ItemAttribute.DATE to 1_700_000_000_000L),
            ),
        )

        repository.save(1L, items)

        assertThat(repository.load(1L)).containsExactlyInAnyOrderElementsOf(items)
    }

    @Test
    fun `loads reward rows in Canary's pid then sid ascending order`() {
        repository.save(
            1L,
            listOf(
                PlayerContainerItemState(pid = 102, sid = 104, itemId = 2148),
                PlayerContainerItemState(pid = 0, sid = 103, itemId = 2160),
                PlayerContainerItemState(pid = 0, sid = 101, itemId = 1987),
            ),
        )

        assertThat(repository.load(1L).map { it.sid }).containsExactly(101, 103, 104)
    }

    @Test
    fun `serializes reward attributes with the V5 wire codec`() {
        repository.save(1L, listOf(PlayerContainerItemState(pid = 0, sid = 101, itemId = 2148, count = 100)))

        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "SELECT attributes FROM player_rewards WHERE player_id = ? AND sid = ?",
            ).use { stmt ->
                stmt.setLong(1, 1L)
                stmt.setInt(2, 101)
                stmt.executeQuery().use { rs ->
                    assertThat(rs.next()).isTrue()
                    assertThat(rs.getBytes("attributes")).containsExactly(15, 100)
                }
            }
        }
    }

    @Test
    fun `save replaces the persisted reward rows`() {
        repository.save(1L, listOf(PlayerContainerItemState(pid = 0, sid = 101, itemId = 2160)))
        repository.save(1L, listOf(PlayerContainerItemState(pid = 0, sid = 201, itemId = 1987)))

        assertThat(repository.load(1L)).containsExactly(PlayerContainerItemState(pid = 0, sid = 201, itemId = 1987))
    }

    @Test
    fun `returns empty rewards for a player without persisted rows`() {
        assertThat(repository.load(99L)).isEmpty()
    }
}
