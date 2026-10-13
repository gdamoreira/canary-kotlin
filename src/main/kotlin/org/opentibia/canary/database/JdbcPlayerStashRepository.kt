package org.opentibia.canary.database

import javax.sql.DataSource

/**
 * Persistence seam for the player supply stash, mirroring the SQL of
 * `IOLoginDataSave::savePlayerStash`
 * (src/io/functions/iologindata_save_player.cpp:374-414) and
 * `IOLoginDataLoad::loadPlayerStashItems`
 * (src/io/functions/iologindata_load_player.cpp:444-470).
 */
interface PlayerStashRepository {
    fun load(playerId: Long): Map<Int, Int>
    fun save(playerId: Long, items: Map<Int, Int>)
}

/**
 * JDBC persistence for `player_stash` (schema.sql:791-800).
 *
 * Saving replaces every row of the player (`DELETE` + batched `INSERT`) exactly
 * like `IOLoginDataSave::savePlayerStash`, which iterates the ordered
 * `Player::getStashItems()` `std::map` by ascending item id. The decay/ware
 * item filters of the C++ loop depend on the `Item::items` registry and stay in
 * the caller (PARITY GAP in the service).
 */
class JdbcPlayerStashRepository(private val dataSource: DataSource) : PlayerStashRepository {

    override fun save(playerId: Long, items: Map<Int, Int>) {
        dataSource.connection.use { conn ->
            conn.autoCommit = false
            try {
                conn.prepareStatement("DELETE FROM player_stash WHERE player_id = ?").use { stmt ->
                    stmt.setLong(1, playerId)
                    stmt.executeUpdate()
                }
                if (items.isNotEmpty()) {
                    conn.prepareStatement(
                        "INSERT INTO player_stash (player_id, item_id, item_count) VALUES (?, ?, ?)",
                    ).use { stmt ->
                        for ((itemId, itemCount) in items.toSortedMap()) {
                            stmt.setLong(1, playerId)
                            stmt.setInt(2, itemId)
                            stmt.setInt(3, itemCount)
                            stmt.addBatch()
                        }
                        stmt.executeBatch()
                    }
                }
                conn.commit()
            } catch (ex: Exception) {
                conn.rollback()
                throw ex
            }
        }
    }

    override fun load(playerId: Long): Map<Int, Int> {
        val result = java.util.TreeMap<Int, Int>()
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "SELECT item_id, item_count FROM player_stash WHERE player_id = ?",
            ).use { stmt ->
                stmt.setLong(1, playerId)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        result[rs.getInt("item_id")] = rs.getInt("item_count")
                    }
                }
            }
        }
        return result
    }
}
