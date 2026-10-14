package org.opentibia.canary.database

import io.github.oshai.kotlinlogging.KotlinLogging
import javax.sql.DataSource

private val logger = KotlinLogging.logger {}

/**
 * Persisted state of one inventory row: the `(pid, sid, itemtype, count,
 * attributes)` tuple Canary writes into `player_items` in
 * `IOLoginDataSave::saveItems` (src/io/functions/iologindata_save_player.cpp:34-188).
 * `pid` is the inventory slot (`CONST_SLOT_FIRST..CONST_SLOT_LAST`) for a direct
 * slot item and otherwise the parent container's `sid`, and `sid` is the running
 * item id assigned from 101 upward. The tree row shape is shared with the
 * depot, inbox and reward trees ([PlayerContainerItemState]).
 */
typealias PlayerItemState = PlayerContainerItemState

interface PlayerItemsRepository {
    fun load(playerId: Long): List<PlayerItemState>
    fun save(playerId: Long, items: List<PlayerItemState>)
}

/**
 * JDBC persistence for a player's inventory item tree.
 *
 * Saving replaces the player's rows in one transaction, mirroring Canary's
 * `DELETE FROM player_items` + batched `INSERT` in
 * `IOLoginDataSave::savePlayerItem` (src/io/functions/iologindata_save_player.cpp:534-564);
 * the whole `(pid, sid)` container tree is persisted, so a backpack with nested
 * bags round-trips. Loading mirrors `IOLoginDataLoad::loadPlayerInventoryItems`
 * (src/io/functions/iologindata_load_player.cpp:543-632) and returns the rows
 * in Canary's `ORDER BY sid DESC` order; a row whose attribute blob fails to
 * deserialize is warn-skipped like `IOLoginDataLoad::loadItems`
 * (iologindata_load_player.cpp:46-60). The `attributes` blob reuses the shared
 * [serializeItemAttributes] V5 codec.
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
                    INSERT INTO player_inventory_items (player_id, pid, sid, item_id, count, attributes)
                    VALUES (?, ?, ?, ?, ?, ?)
                """.trimIndent()
                conn.prepareStatement(sql).use { stmt ->
                    items.sortedBy { it.sid }.forEach { item ->
                        stmt.setLong(1, playerId)
                        stmt.setInt(2, item.pid)
                        stmt.setInt(3, item.sid)
                        stmt.setInt(4, item.itemId)
                        stmt.setInt(5, item.count)
                        stmt.setBytes(6, serializeItemAttributes(item.count, item.attributes))
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
                SELECT pid, sid, item_id, count, attributes
                FROM player_inventory_items
                WHERE player_id = ?
                ORDER BY sid DESC
                """.trimIndent(),
            ).use { stmt ->
                stmt.setLong(1, playerId)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        val pid = rs.getInt("pid")
                        val sid = rs.getInt("sid")
                        val itemId = rs.getInt("item_id")
                        val count = rs.getInt("count")
                        try {
                            result.add(
                                PlayerItemState(
                                    pid = pid,
                                    sid = sid,
                                    itemId = itemId,
                                    count = count,
                                    attributes = deserializeItemAttributes(rs.getBytes("attributes")),
                                ),
                            )
                        } catch (ex: IllegalArgumentException) {
                            // `IOLoginDataLoad::loadItems` (iologindata_load_player.cpp:46-60)
                            // warns and skips a row whose attributes fail to deserialize.
                            logger.warn { "Skipping item $itemId in row $sid of player $playerId: ${ex.message}" }
                        }
                    }
                }
            }
        }
        return result
    }
}
