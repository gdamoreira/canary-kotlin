package org.opentibia.canary.database

import org.opentibia.canary.creatures.players.components.PlayerStorageRow
import javax.sql.DataSource

/**
 * Persistence seam for the player storage map, mirroring Canary's
 * `IPlayerStorageRepository` (`src/io/player_storage_repository.hpp`).
 */
interface PlayerStorageRepository {
    /** `DbPlayerStorageRepository::load` (src/io/player_storage_repository_db.cpp:25-34). */
    fun load(playerId: Long): List<PlayerStorageRow>

    /** `DbPlayerStorageRepository::deleteKeys` (src/io/player_storage_repository_db.cpp:36-43). */
    fun deleteKeys(playerId: Long, keys: List<Int>): Boolean

    /** `DbPlayerStorageRepository::upsert` (src/io/player_storage_repository_db.cpp:45-57). */
    fun upsert(playerId: Long, kv: Map<Int, Int>): Boolean
}

/**
 * JDBC persistence for the `player_storage` map (schema.sql:802-812).
 *
 * [upsert] mirrors `DBInsert::upsert({"value"})` through a portable
 * update-then-insert pair so it works on both PostgreSQL 16 and the H2
 * PostgreSQL-mode test database. [deleteKeys] builds the same
 * `DELETE ... WHERE player_id = ? AND key IN (...)` as
 * `DbPlayerStorageRepository::deleteKeys`.
 */
class JdbcPlayerStorageRepository(private val dataSource: DataSource) : PlayerStorageRepository {

    override fun load(playerId: Long): List<PlayerStorageRow> {
        val result = mutableListOf<PlayerStorageRow>()
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "SELECT \"key\", \"value\" FROM player_storage WHERE player_id = ?",
            ).use { stmt ->
                stmt.setLong(1, playerId)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        result.add(PlayerStorageRow(key = rs.getInt("key"), value = rs.getInt("value")))
                    }
                }
            }
        }
        return result
    }

    override fun deleteKeys(playerId: Long, keys: List<Int>): Boolean {
        if (keys.isEmpty()) {
            return true
        }

        val placeholders = keys.joinToString(",") { "?" }
        return try {
            dataSource.connection.use { conn ->
                conn.prepareStatement(
                    "DELETE FROM player_storage WHERE player_id = ? AND \"key\" IN ($placeholders)",
                ).use { stmt ->
                    stmt.setLong(1, playerId)
                    keys.forEachIndexed { index, key -> stmt.setInt(index + 2, key) }
                    stmt.executeUpdate()
                }
            }
            true
        } catch (ex: java.sql.SQLException) {
            false
        }
    }

    override fun upsert(playerId: Long, kv: Map<Int, Int>): Boolean {
        if (kv.isEmpty()) {
            return true
        }

        dataSource.connection.use { conn ->
            conn.autoCommit = false
            try {
                conn.prepareStatement(
                    "UPDATE player_storage SET \"value\" = ? WHERE player_id = ? AND \"key\" = ?",
                ).use { update ->
                    conn.prepareStatement(
                        "INSERT INTO player_storage (player_id, \"key\", \"value\") VALUES (?, ?, ?)",
                    ).use { insert ->
                        for ((key, value) in kv) {
                            update.setInt(1, value)
                            update.setLong(2, playerId)
                            update.setInt(3, key)
                            if (update.executeUpdate() == 0) {
                                insert.setLong(1, playerId)
                                insert.setInt(2, key)
                                insert.setInt(3, value)
                                insert.executeUpdate()
                            }
                        }
                    }
                }
                conn.commit()
            } catch (ex: Exception) {
                conn.rollback()
                throw ex
            }
        }
        return true
    }
}
