package org.opentibia.canary.database

import io.github.oshai.kotlinlogging.KotlinLogging
import org.opentibia.canary.items.ItemAttribute
import javax.sql.DataSource

private val logger = KotlinLogging.logger {}

/**
 * Persisted state of one tree item row, mirroring the tuple Canary writes in
 * `IOLoginDataSave::saveItems`
 * (src/io/functions/iologindata_save_player.cpp:46-112): `pid` is the parent
 * container `sid` (below 100 it is the inventory slot or depot chest id),
 * `sid` is the running item id assigned from 100 upward, and `attributes` is
 * the serialized `Item::serializeAttr` blob. Shared by the depot, inbox,
 * reward and inventory trees (`PlayerItemState`).
 */
data class PlayerContainerItemState(
    val pid: Int,
    val sid: Int,
    val itemId: Int,
    val count: Int = 1,
    val attributes: Map<ItemAttribute, Any> = emptyMap(),
)

interface PlayerDepotRepository {
    fun load(playerId: Long): List<PlayerContainerItemState>
    fun save(playerId: Long, items: List<PlayerContainerItemState>)
    fun loadInbox(playerId: Long): List<PlayerContainerItemState>
    fun saveInbox(playerId: Long, items: List<PlayerContainerItemState>)
}

/**
 * JDBC persistence for a player's depot and inbox item rows.
 *
 * Saving replaces the player's rows in one transaction, mirroring Canary's
 * `DELETE FROM player_depotitems` / `DELETE FROM player_inboxitems` plus the
 * batched `INSERT` in `IOLoginDataSave::savePlayerDepotItems`
 * (src/io/functions/iologindata_save_player.cpp:566-597) and
 * `IOLoginDataSave::savePlayerInbox` (:634-659). Loading mirrors
 * `IOLoginDataLoad::loadPlayerDepotItems` / `loadPlayerInboxItems`
 * (src/io/functions/iologindata_load_player.cpp:662-753), which iterate the
 * `ORDER BY sid DESC` result in descending `sid` order and warn-skip a row
 * whose attribute blob fails to deserialize (iologindata_load_player.cpp:46-60).
 * The `attributes` blob reuses the shared [serializeItemAttributes] V5 codec.
 */
class JdbcPlayerDepotRepository(private val dataSource: DataSource) : PlayerDepotRepository {

    override fun load(playerId: Long): List<PlayerContainerItemState> =
        loadTable("player_depotitems", playerId)

    override fun save(playerId: Long, items: List<PlayerContainerItemState>) {
        replaceTable("player_depotitems", playerId, items)
    }

    override fun loadInbox(playerId: Long): List<PlayerContainerItemState> =
        loadTable("player_inboxitems", playerId)

    override fun saveInbox(playerId: Long, items: List<PlayerContainerItemState>) {
        replaceTable("player_inboxitems", playerId, items)
    }

    private fun replaceTable(table: String, playerId: Long, items: List<PlayerContainerItemState>) {
        dataSource.connection.use { conn ->
            conn.autoCommit = false
            try {
                conn.prepareStatement("DELETE FROM $table WHERE player_id = ?").use { stmt ->
                    stmt.setLong(1, playerId)
                    stmt.executeUpdate()
                }
                val sql = """
                    INSERT INTO $table (player_id, pid, sid, itemtype, count, attributes)
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

    private fun loadTable(table: String, playerId: Long): List<PlayerContainerItemState> {
        val result = mutableListOf<PlayerContainerItemState>()
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """
                SELECT pid, sid, itemtype, count, attributes
                FROM $table
                WHERE player_id = ?
                ORDER BY sid DESC
                """.trimIndent(),
            ).use { stmt ->
                stmt.setLong(1, playerId)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        val pid = rs.getInt("pid")
                        val sid = rs.getInt("sid")
                        val itemId = rs.getInt("itemtype")
                        val count = rs.getInt("count")
                        try {
                            result.add(
                                PlayerContainerItemState(
                                    pid = pid,
                                    sid = sid,
                                    itemId = itemId,
                                    count = count,
                                    attributes = deserializeItemAttributes(rs.getBytes("attributes")),
                                ),
                            )
                        } catch (ex: IllegalArgumentException) {
                            logger.warn { "Skipping item $itemId in $table row $sid of player $playerId: ${ex.message}" }
                        }
                    }
                }
            }
        }
        return result
    }
}
