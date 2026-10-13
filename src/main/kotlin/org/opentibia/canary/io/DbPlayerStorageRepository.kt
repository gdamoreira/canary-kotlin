package org.opentibia.canary.io

import io.github.oshai.kotlinlogging.KotlinLogging
import org.opentibia.canary.creatures.players.components.PlayerStorageRow
import org.opentibia.canary.database.Database
import java.sql.PreparedStatement
import java.sql.SQLException
import javax.sql.DataSource

private val logger = KotlinLogging.logger {}

/**
 * Ports `DbPlayerStorageRepository`
 * (`src/io/player_storage_repository_db.hpp:22-53`,
 * player_storage_repository_db.cpp:25-57).
 *
 * PARITY NOTES:
 *  - `load` runs Canary's `SELECT \`key\`,\`value\` FROM \`player_storage\`
 *    WHERE \`player_id\`={}` (player_storage_repository_db.cpp:27) through a
 *    prepared statement; like `Database::storeQuery`, a failed or empty result
 *    yields an empty row list.
 *  - `deleteKeys` mirrors the single `DELETE ... WHERE player_id={} AND
 *    \`key\` IN (...)` (player_storage_repository_db.cpp:36-43) and returns
 *    `true` early for an empty batch.
 *  - `upsert` mirrors the bulk `DBInsert` with `insert.upsert({ "value" })`
 *    (player_storage_repository_db.cpp:45-57): every key is inserted or has its
 *    `value` overwritten, keys absent from the batch are untouched, and an
 *    empty batch returns `true` early. MySQL's `ON DUPLICATE KEY UPDATE
 *    value=VALUES(value)` becomes the PostgreSQL 15+/H2 compatible bulk
 *    `MERGE ... USING (VALUES ...)`, split into batches
 *    bounded by [maxPacketSize] exactly like `DBInsert::addRow`
 *    (src/database/database.cpp, Kotlin `Database.DEFAULT_MAX_PACKET_SIZE`).
 *  - `g_database()` is replaced by the injected [dataSource] (AGENTS.md 1.1).
 *
 * Not thread-safe; calls must be made from the main game thread, like the C++
 * class (`player_storage_repository_db.hpp:20`).
 */
class DbPlayerStorageRepository(
    private val dataSource: DataSource,
    /**
     * `Database::getMaxPacketSize()` bound used by `DBInsert` when batching
     * (src/database/database.cpp); defaults to Canary's 1 MiB packet limit.
     */
    private val maxPacketSize: Long = Database.DEFAULT_MAX_PACKET_SIZE,
) : PlayerStorageRepository {

    override fun load(playerId: Long): List<PlayerStorageRow> {
        return try {
            dataSource.connection.use { connection ->
                connection.prepareStatement(LOAD_SQL).use { statement ->
                    statement.setLong(1, playerId)
                    statement.executeQuery().use { result ->
                        buildList {
                            while (result.next()) {
                                add(
                                    PlayerStorageRow(
                                        key = result.getInt(KEY_COLUMN),
                                        value = result.getInt(VALUE_COLUMN),
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        } catch (ex: SQLException) {
            logger.error(ex) { "player_storage load failed for player $playerId" }
            emptyList()
        }
    }

    override fun deleteKeys(playerId: Long, keys: List<Int>): Boolean {
        if (keys.isEmpty()) {
            return true
        }
        val placeholders = keys.joinToString(", ") { "?" }
        val sql = "DELETE FROM player_storage WHERE player_id = ? AND \"$KEY_COLUMN\" IN ($placeholders)"
        return execute(sql) { statement ->
            statement.setLong(1, playerId)
            keys.forEachIndexed { index, key -> statement.setInt(index + 2, key) }
        }
    }

    override fun upsert(playerId: Long, kv: Map<Int, Int>): Boolean {
        if (kv.isEmpty()) {
            return true
        }
        // `std::map` iteration order: ascending key (player_storage_repository_db.cpp:51).
        val entries = kv.entries.sortedBy { it.key }
        var index = 0
        while (index < entries.size) {
            var end = index
            var length = UPSERT_HEADER_SQL.length + UPSERT_TAIL_SQL.length
            while (end < entries.size) {
                val addedLength = ROW_SQL.length + if (end == index) 0 else ROW_SEPARATOR_SQL.length
                if (end > index && length + addedLength > maxPacketSize) {
                    break
                }
                length += addedLength
                end++
            }
            if (!executeUpsert(playerId, entries.subList(index, end))) {
                return false
            }
            index = end
        }
        return true
    }

    private fun executeUpsert(playerId: Long, entries: List<Map.Entry<Int, Int>>): Boolean {
        val values = entries.joinToString(ROW_SEPARATOR_SQL) { ROW_SQL }
        val sql = UPSERT_HEADER_SQL + values + UPSERT_TAIL_SQL
        return execute(sql) { statement ->
            var parameter = 1
            for ((key, value) in entries) {
                statement.setLong(parameter++, playerId)
                statement.setInt(parameter++, key)
                statement.setInt(parameter++, value)
            }
        }
    }

    private inline fun execute(sql: String, bind: (PreparedStatement) -> Unit): Boolean =
        try {
            dataSource.connection.use { connection ->
                connection.prepareStatement(sql).use { statement ->
                    bind(statement)
                    statement.executeUpdate()
                }
            }
            true
        } catch (ex: SQLException) {
            logger.error(ex) { "player_storage statement failed: ${sql.take(256)}" }
            false
        }

    private companion object {
        const val KEY_COLUMN = "key"
        const val VALUE_COLUMN = "value"

        const val LOAD_SQL = """SELECT "key", "value" FROM player_storage WHERE player_id = ?"""

        const val ROW_SQL = "(?, ?, ?)"
        const val ROW_SEPARATOR_SQL = ", "

        const val UPSERT_HEADER_SQL =
            "MERGE INTO player_storage AS target USING (VALUES "

        const val UPSERT_TAIL_SQL =
            """) AS source (player_id, "key", "value")""" +
                """ ON target.player_id = source.player_id AND target."key" = source."key" """ +
                """WHEN MATCHED THEN UPDATE SET "value" = source."value" """ +
                """WHEN NOT MATCHED THEN INSERT (player_id, "key", "value")""" +
                """ VALUES (source.player_id, source."key", source."value")"""
    }
}
