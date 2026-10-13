package org.opentibia.canary.database

import io.github.oshai.kotlinlogging.KotlinLogging
import org.opentibia.canary.items.ItemAttribute
import javax.sql.DataSource

private val logger = KotlinLogging.logger {}

/**
 * Persisted state of one equipped inventory item, mirroring the row Canary
 * writes per inventory slot (`pid`/`sid`/`itemtype`/`count`/`attributes` in
 * `IOLoginDataSave::savePlayerItems`, src/io/functions/iologindata_save_player.cpp:98-107)
 * as the flat `player_inventory_items` layout.
 */
data class PlayerItemState(
    val slot: Int,
    val itemId: Int,
    val count: Int = 1,
    val attributes: Map<ItemAttribute, Any> = emptyMap(),
)

interface PlayerItemsRepository {
    fun load(playerId: Long): List<PlayerItemState>
    fun save(playerId: Long, items: List<PlayerItemState>)
}

/**
 * JDBC persistence for a player's equipped inventory slots.
 *
 * Saving replaces the player's rows in one transaction, mirroring Canary's
 * `DELETE FROM player_items` + batched `INSERT` in
 * `IOLoginDataSave::savePlayerItems` (src/io/functions/iologindata_save_player.cpp:543-563).
 * The `attributes` blob uses the shared [serializeItemAttributes] wire codec.
 */
class JdbcPlayerItemsRepository(private val dataSource: DataSource) : PlayerItemsRepository {

    override fun save(playerId: Long, items: List<PlayerItemState>) {
        dataSource.connection.use { conn ->
            conn.autoCommit = false
            try {
                conn.prepareStatement("DELETE FROM player_inventory_items WHERE player_id = ?").use { stmt ->
                    stmt.setLong(1, playerId)
                    stmt.executeUpdate()
                }
                val sql = """
                    INSERT INTO player_inventory_items (player_id, slot, item_id, count, attributes)
                    VALUES (?, ?, ?, ?, ?)
                """.trimIndent()
                conn.prepareStatement(sql).use { stmt ->
                    items.sortedBy { it.slot }.forEach { item ->
                        stmt.setLong(1, playerId)
                        stmt.setInt(2, item.slot)
                        stmt.setInt(3, item.itemId)
                        stmt.setInt(4, item.count)
                        stmt.setBytes(5, serializeItemAttributes(item.count, item.attributes))
                        stmt.addBatch()
                    }
                    stmt.executeBatch()
                }
                conn.commit()
            } catch (ex: Exception) {
                conn.rollback()
                throw ex
            }
        }
    }

    override fun load(playerId: Long): List<PlayerItemState> {
        val result = mutableListOf<PlayerItemState>()
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """
                SELECT slot, item_id, count, attributes
                FROM player_inventory_items
                WHERE player_id = ?
                ORDER BY slot
                """.trimIndent(),
            ).use { stmt ->
                stmt.setLong(1, playerId)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        val slot = rs.getInt("slot")
                        val itemId = rs.getInt("item_id")
                        val count = rs.getInt("count")
                        try {
                            result.add(PlayerItemState(slot, itemId, count, deserializeItemAttributes(rs.getBytes("attributes"))))
                        } catch (ex: IllegalArgumentException) {
                            // `IOLoginDataLoad::loadItems` (iologindata_load_player.cpp:46-60)
                            // warns and skips a row whose attributes fail to deserialize.
                            logger.warn { "Skipping item $itemId in slot $slot of player $playerId: ${ex.message}" }
                        }
                    }
                }
            }
        }
        return result
    }
}
