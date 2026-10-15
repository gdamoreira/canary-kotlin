package org.opentibia.canary.database

import org.opentibia.canary.creatures.players.pvp.Kill
import javax.sql.DataSource

/**
 * Persistence seam for the player's unjustified kills, mirroring the SQL of
 * `IOLoginDataSave::savePlayerKills`
 * (src/io/functions/iologindata_save_player.cpp:447-476) and
 * `IOLoginDataLoad::loadPlayerKills`
 * (src/io/functions/iologindata_load_player.cpp:371-392).
 */
interface PlayerKillsRepository {
    fun load(playerId: Long): List<Kill>
    fun save(playerId: Long, kills: List<Kill>)
}

/**
 * JDBC persistence for `player_kills` (schema.sql:688-698).
 *
 * Saving replaces all rows of the player (`DELETE` + batched `INSERT`) exactly
 * like `IOLoginDataSave::savePlayerKills`. The frag-time window that
 * `IOLoginDataLoad::loadPlayerKills` applies is a load-time concern and stays in
 * the [org.opentibia.canary.io.iologindata.IOLoginData] service.
 */
class JdbcPlayerKillsRepository(private val dataSource: DataSource) : PlayerKillsRepository {

    override fun save(playerId: Long, kills: List<Kill>) {
        dataSource.connection.use { conn ->
            conn.autoCommit = false
            try {
                conn.prepareStatement("DELETE FROM player_kills WHERE player_id = ?").use { stmt ->
                    stmt.setLong(1, playerId)
                    stmt.executeUpdate()
                }
                if (kills.isNotEmpty()) {
                    conn.prepareStatement(
                        "INSERT INTO player_kills (player_id, ${SqlDialect.from(conn).quoteIdentifier("time")}, " +
                            "target, unavenged) VALUES (?, ?, ?, ?)",
                    ).use { stmt ->
                        for (kill in kills) {
                            stmt.setLong(1, playerId)
                            stmt.setLong(2, kill.time)
                            stmt.setLong(3, kill.target)
                            stmt.setBoolean(4, kill.unavenged)
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

    override fun load(playerId: Long): List<Kill> {
        val result = mutableListOf<Kill>()
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "SELECT target, ${SqlDialect.from(conn).quoteIdentifier("time")}, unavenged " +
                    "FROM player_kills WHERE player_id = ?",
            ).use { stmt ->
                stmt.setLong(1, playerId)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        result.add(
                            Kill(
                                target = rs.getLong("target"),
                                time = rs.getLong("time"),
                                unavenged = rs.getBoolean("unavenged"),
                            ),
                        )
                    }
                }
            }
        }
        return result
    }
}
