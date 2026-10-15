package org.opentibia.canary.io

import io.github.oshai.kotlinlogging.KotlinLogging
import org.opentibia.canary.creatures.players.components.PlayerStorageRow
import org.opentibia.canary.database.Database
import org.opentibia.canary.database.SqlDialect
import java.sql.Connection
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
 *    empty batch returns `true` early. MariaDB gets the original
 *    `ON DUPLICATE KEY UPDATE value=VALUES(value)`, PostgreSQL the equivalent
 *    `ON CONFLICT ... DO UPDATE`, and H2 (the local test engine) keeps the
 *    shipped `MERGE ... USING (VALUES ...)`, split into batches bounded by
 *    [maxPacketSize] exactly like `DBInsert::addRow`
 *    (src/database/database.cpp, Kotlin `Database.DEFAULT_MAX_PACKET_SIZE`).
 *    `key`/`value` are keywords under MariaDB/MySQL, so every identifier goes
 *    through [SqlDialect.quoteIdentifier].
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
                connection.prepareStatement(loadSql(SqlDialect.from(connection))).use { statement ->
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
        return execute({ dialect -> deleteKeysSql(dialect, placeholders) }) { statement ->
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
        return try {
            dataSource.connection.use { connection ->
                val dialect = SqlDialect.from(connection)
                val batchFixedLength = upsertHeaderSql(dialect).length + upsertTailSql(dialect).length
                var index = 0
                while (index < entries.size) {
                    var end = index
                    var length = batchFixedLength
                    while (end < entries.size) {
                        val addedLength = ROW_SQL.length + if (end == index) 0 else ROW_SEPARATOR_SQL.length
                        if (end > index && length + addedLength > maxPacketSize) {
                            break
                        }
                        length += addedLength
                        end++
                    }
                    executeOn(connection, upsertSql(dialect, end - index), playerId, entries.subList(index, end))
                    index = end
                }
            }
            true
        } catch (ex: SQLException) {
            logger.error(ex) { "player_storage upsert failed for player $playerId" }
            false
        }
    }

    private fun executeOn(
        connection: Connection,
        sql: String,
        playerId: Long,
        entries: List<Map.Entry<Int, Int>>,
    ) {
        connection.prepareStatement(sql).use { statement ->
            var parameter = 1
            for ((key, value) in entries) {
                statement.setLong(parameter++, playerId)
                statement.setInt(parameter++, key)
                statement.setInt(parameter++, value)
            }
            statement.executeUpdate()
        }
    }

    private inline fun execute(buildSql: (SqlDialect) -> String, bind: (PreparedStatement) -> Unit): Boolean =
        try {
            dataSource.connection.use { connection ->
                connection.prepareStatement(buildSql(SqlDialect.from(connection))).use { statement ->
                    bind(statement)
                    statement.executeUpdate()
                }
            }
            true
        } catch (ex: SQLException) {
            logger.error(ex) { "player_storage statement failed" }
            false
        }

    companion object {
        private const val KEY_COLUMN = "key"
        private const val VALUE_COLUMN = "value"

        private const val ROW_SQL = "(?, ?, ?)"
        private const val ROW_SEPARATOR_SQL = ", "

        /** `SELECT` of [load] with the dialect-quoted `key`/`value` columns. */
        internal fun loadSql(dialect: SqlDialect): String =
            "SELECT ${dialect.quoteIdentifier(KEY_COLUMN)}, ${dialect.quoteIdentifier(VALUE_COLUMN)} " +
                "FROM player_storage WHERE player_id = ?"

        /** `DELETE` of [deleteKeys] with the dialect-quoted `key` column. */
        internal fun deleteKeysSql(dialect: SqlDialect, placeholders: String): String =
            "DELETE FROM player_storage WHERE player_id = ? AND " +
                "${dialect.quoteIdentifier(KEY_COLUMN)} IN ($placeholders)"

        /**
         * Bulk upsert of [upsert] for `rows` value rows: H2 keeps its shipped
         * `MERGE ... USING (VALUES ...)` shape, PostgreSQL gets the equivalent
         * `ON CONFLICT ... DO UPDATE`, and MariaDB gets upstream Canary's
         * `INSERT ... ON DUPLICATE KEY UPDATE value = VALUES(value)`
         * (`DBInsert::upsert`, src/database/database.cpp:453-475;
         * player_storage_repository_db.cpp:45-57).
         */
        internal fun upsertSql(dialect: SqlDialect, rows: Int): String {
            val values = List(rows) { ROW_SQL }.joinToString(ROW_SEPARATOR_SQL)
            return upsertHeaderSql(dialect) + values + upsertTailSql(dialect)
        }

        private fun upsertHeaderSql(dialect: SqlDialect): String {
            val key = dialect.quoteIdentifier(KEY_COLUMN)
            val value = dialect.quoteIdentifier(VALUE_COLUMN)
            return when (dialect) {
                SqlDialect.H2 ->
                    "MERGE INTO player_storage AS target USING (VALUES "

                SqlDialect.POSTGRESQL, SqlDialect.MARIADB ->
                    "INSERT INTO player_storage (player_id, $key, $value) VALUES "
            }
        }

        private fun upsertTailSql(dialect: SqlDialect): String {
            val key = dialect.quoteIdentifier(KEY_COLUMN)
            val value = dialect.quoteIdentifier(VALUE_COLUMN)
            return when (dialect) {
                SqlDialect.H2 ->
                    """) AS source (player_id, $key, $value)""" +
                        """ ON target.player_id = source.player_id AND target.$key = source.$key """ +
                        """WHEN MATCHED THEN UPDATE SET $value = source.$value """ +
                        """WHEN NOT MATCHED THEN INSERT (player_id, $key, $value)""" +
                        """ VALUES (source.player_id, source.$key, source.$value)"""

                SqlDialect.POSTGRESQL ->
                    """ ON CONFLICT (player_id, $key) DO UPDATE SET $value = EXCLUDED.$value"""

                SqlDialect.MARIADB ->
                    """ ON DUPLICATE KEY UPDATE $value = VALUES($value)"""
            }
        }
    }
}
