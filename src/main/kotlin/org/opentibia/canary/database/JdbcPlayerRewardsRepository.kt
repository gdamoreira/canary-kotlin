package org.opentibia.canary.database

import io.github.oshai.kotlinlogging.KotlinLogging
import javax.sql.DataSource

private val logger = KotlinLogging.logger {}

/**
 * Persistence seam for the reward-chest item tree, mirroring the SQL of
 * `IOLoginDataSave::saveRewardItems`
 * (src/io/functions/iologindata_save_player.cpp:599-632) and
 * `IOLoginDataLoad::loadRewardItems`
 * (src/io/functions/iologindata_load_player.cpp:645-661).
 */
interface PlayerRewardsRepository {
    fun load(playerId: Long): List<PlayerContainerItemState>
    fun save(playerId: Long, items: List<PlayerContainerItemState>)
}

/**
 * JDBC persistence for `player_rewards` (schema.sql:766-777), using the same
 * `pid`/`sid`/`itemtype`/`count`/`attributes` row shape as the depot and inbox
 * tables. Saving replaces all rows of the player; loading mirrors the C++
 * `ORDER BY pid, sid ASC`. The attribute blob reuses the V5
 * [serializeItemAttributes] `Item::serializeAttr` codec, and a corrupt blob is
 * warn-skipped like `IOLoginDataLoad::loadItems`
 * (iologindata_load_player.cpp:46-60).
 */
class JdbcPlayerRewardsRepository(private val dataSource: DataSource) : PlayerRewardsRepository {

    override fun save(playerId: Long, items: List<PlayerContainerItemState>) {
        dataSource.connection.use { conn ->
            conn.autoCommit = false
            try {
                conn.prepareStatement("DELETE FROM player_rewards WHERE player_id = ?").use { stmt ->
                    stmt.setLong(1, playerId)
                    stmt.executeUpdate()
                }
                conn.prepareStatement(
                    """
                    INSERT INTO player_rewards (player_id, pid, sid, itemtype, count, attributes)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """.trimIndent(),
                ).use { stmt ->
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

    override fun load(playerId: Long): List<PlayerContainerItemState> {
        val result = mutableListOf<PlayerContainerItemState>()
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """
                SELECT pid, sid, itemtype, count, attributes
                FROM player_rewards
                WHERE player_id = ?
                ORDER BY pid, sid ASC
                """.trimIndent(),
            ).use { stmt ->
                stmt.setLong(1, playerId)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        val sid = rs.getInt("sid")
                        val itemId = rs.getInt("itemtype")
                        val pid = rs.getInt("pid")
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
                            logger.warn { "Skipping reward item $itemId in row $sid of player $playerId: ${ex.message}" }
                        }
                    }
                }
            }
        }
        return result
    }
}
