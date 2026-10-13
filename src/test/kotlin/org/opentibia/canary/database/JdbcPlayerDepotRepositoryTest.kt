package org.opentibia.canary.database

import com.zaxxer.hikari.HikariDataSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.opentibia.canary.items.ItemAttribute

/**
 * H2 (Flyway V1-V6) round-trip tests for [JdbcPlayerDepotRepository],
 * transcribed from Canary's depot/inbox persistence:
 * `IOLoginDataSave::savePlayerDepotItems`
 * (src/io/functions/iologindata_save_player.cpp:566-597),
 * `IOLoginDataSave::savePlayerInbox` (:634-659),
 * `IOLoginDataLoad::loadPlayerDepotItems`
 * (src/io/functions/iologindata_load_player.cpp:662-706),
 * `IOLoginDataLoad::loadPlayerInboxItems` (:708-753) and the shared
 * `IOLoginDataSave::saveItems` / `IOLoginDataLoad::loadItems`
 * (iologindata_save_player.cpp:46-112, iologindata_load_player.cpp:34-65).
 * The `attributes` blob reuses the V5 `Item::serializeAttr` codec.
 */
class JdbcPlayerDepotRepositoryTest {

    private lateinit var dataSource: HikariDataSource
    private lateinit var repository: JdbcPlayerDepotRepository

    @BeforeEach
    fun setUp() {
        dataSource = DatabaseConnectionFactory.createInMemoryDataSource("canary_depot_${System.nanoTime()}")
        val applied = DatabaseConnectionFactory.migrate(dataSource)
        assertThat(applied).isGreaterThanOrEqualTo(6)
        repository = JdbcPlayerDepotRepository(dataSource)
    }

    @AfterEach
    fun tearDown() {
        dataSource.close()
    }

    @Test
    fun `round-trips depot rows with pid sid count and attributes`() {
        val items = listOf(
            PlayerContainerItemState(pid = 1, sid = 101, itemId = 2148, count = 100),
            PlayerContainerItemState(
                pid = 1,
                sid = 102,
                itemId = 2376,
                attributes = mapOf(ItemAttribute.ACTIONID to 1234L, ItemAttribute.TEXT to "depot"),
            ),
        )

        repository.save(1L, items)

        assertThat(repository.load(1L)).containsExactlyInAnyOrderElementsOf(items)
    }

    @Test
    fun `round-trips item rows nested under a container pid`() {
        val nested = PlayerContainerItemState(pid = 101, sid = 102, itemId = 2160, count = 7)

        repository.save(1L, listOf(PlayerContainerItemState(pid = 1, sid = 101, itemId = 1987), nested))

        assertThat(repository.load(1L)).contains(nested)
    }

    @Test
    fun `serializes depot item attributes with the V5 wire codec`() {
        repository.save(1L, listOf(PlayerContainerItemState(pid = 1, sid = 101, itemId = 2148, count = 100)))

        assertThat(rawAttributes("player_depotitems", 1L, 101)).containsExactly(15, 100)
    }

    @Test
    fun `loads depot items in Canary's descending sid order`() {
        repository.save(
            1L,
            listOf(
                PlayerContainerItemState(pid = 1, sid = 101, itemId = 2148),
                PlayerContainerItemState(pid = 1, sid = 103, itemId = 2160),
                PlayerContainerItemState(pid = 1, sid = 102, itemId = 2376),
            ),
        )

        assertThat(repository.load(1L).map { it.sid }).containsExactly(103, 102, 101)
    }

    @Test
    fun `replaces the persisted depot items on resave`() {
        repository.save(1L, listOf(PlayerContainerItemState(pid = 1, sid = 101, itemId = 2148, count = 100)))
        repository.save(1L, listOf(PlayerContainerItemState(pid = 2, sid = 101, itemId = 2376)))

        assertThat(repository.load(1L)).containsExactly(PlayerContainerItemState(pid = 2, sid = 101, itemId = 2376))
    }

    @Test
    fun `returns an empty depot for a player without persisted items`() {
        assertThat(repository.load(2L)).isEmpty()
    }

    @Test
    fun `round-trips inbox rows separately from depot rows`() {
        val inboxItems = listOf(
            PlayerContainerItemState(pid = 0, sid = 101, itemId = 2595, count = 1),
            PlayerContainerItemState(pid = 101, sid = 102, itemId = 2148, count = 50),
        )
        repository.save(1L, listOf(PlayerContainerItemState(pid = 0, sid = 101, itemId = 1987)))

        repository.saveInbox(1L, inboxItems)

        assertThat(repository.loadInbox(1L)).containsExactlyInAnyOrderElementsOf(inboxItems)
        assertThat(repository.load(1L)).containsExactly(PlayerContainerItemState(pid = 0, sid = 101, itemId = 1987))
    }

    @Test
    fun `persists depot rows for separate players independently`() {
        repository.save(1L, listOf(PlayerContainerItemState(pid = 1, sid = 101, itemId = 2148)))
        repository.save(2L, listOf(PlayerContainerItemState(pid = 1, sid = 101, itemId = 2376)))

        assertThat(repository.load(1L).map { it.itemId }).containsExactly(2148)
        assertThat(repository.load(2L).map { it.itemId }).containsExactly(2376)
    }

    @Test
    fun `skips persisted depot items whose blob carries an unsupported attribute code`() {
        insertRawItem("player_depotitems", 2L, pid = 0, sid = 101, itemId = 2376, blob = byteArrayOf(99, 0))

        assertThat(repository.load(2L)).isEmpty()
    }

    private fun rawAttributes(table: String, playerId: Long, sid: Int): ByteArray {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "SELECT attributes FROM $table WHERE player_id = ? AND sid = ?",
            ).use { stmt ->
                stmt.setLong(1, playerId)
                stmt.setInt(2, sid)
                stmt.executeQuery().use { rs ->
                    assertThat(rs.next()).isTrue()
                    return rs.getBytes("attributes")
                }
            }
        }
    }

    private fun insertRawItem(table: String, playerId: Long, pid: Int, sid: Int, itemId: Int, blob: ByteArray) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "INSERT INTO $table (player_id, pid, sid, itemtype, count, attributes) VALUES (?, ?, ?, ?, ?, ?)",
            ).use { stmt ->
                stmt.setLong(1, playerId)
                stmt.setInt(2, pid)
                stmt.setInt(3, sid)
                stmt.setInt(4, itemId)
                stmt.setInt(5, 1)
                stmt.setBytes(6, blob)
                stmt.executeUpdate()
            }
        }
    }
}
