package org.opentibia.canary.database

import javax.sql.DataSource

interface PlayerSpellsRepository {
    fun load(playerId: Long): List<String>
    fun save(playerId: Long, spellNames: List<String>)
}

/**
 * JDBC persistence for a player's learned instant-spell names.
 *
 * Saving replaces the player's rows in one transaction, mirroring Canary's
 * `DELETE FROM player_spells` plus the batched `INSERT` in
 * `IOLoginDataSave::savePlayerSpells`
 * (src/io/functions/iologindata_save_player.cpp:416-445). Loading mirrors
 * `IOLoginDataLoad::loadPlayerInstantSpellList`
 * (src/io/functions/iologindata_load_player.cpp:527-541): the names are
 * unordered in the query, so the returned list order is unspecified.
 */
class JdbcPlayerSpellsRepository(private val dataSource: DataSource) : PlayerSpellsRepository {

    override fun save(playerId: Long, spellNames: List<String>) {
        dataSource.connection.use { conn ->
            conn.autoCommit = false
            try {
                conn.prepareStatement("DELETE FROM player_spells WHERE player_id = ?").use { stmt ->
                    stmt.setLong(1, playerId)
                    stmt.executeUpdate()
                }
                if (spellNames.isNotEmpty()) {
                    conn.prepareStatement("INSERT INTO player_spells (player_id, name) VALUES (?, ?)").use { stmt ->
                        spellNames.forEach { spellName ->
                            stmt.setLong(1, playerId)
                            stmt.setString(2, spellName)
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

    override fun load(playerId: Long): List<String> {
        val result = mutableListOf<String>()
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "SELECT name FROM player_spells WHERE player_id = ?",
            ).use { stmt ->
                stmt.setLong(1, playerId)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        result.add(rs.getString("name"))
                    }
                }
            }
        }
        return result
    }
}
