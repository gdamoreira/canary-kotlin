package org.opentibia.canary.kv

import io.github.oshai.kotlinlogging.KotlinLogging
import org.opentibia.canary.database.SqlDialect
import org.opentibia.canary.protobuf.kv.ValueWrapper as ProtoValueWrapper
import java.sql.Connection
import java.sql.SQLException
import javax.sql.DataSource

private val logger = KotlinLogging.logger {}

/**
 * Port of `KVSQL` (`src/kv/kv_sql.hpp`, `src/kv/kv_sql.cpp`): the SQL-backed
 * [KVStore] over the `kv_store` table (schema.sql:861-867, Flyway `V16`).
 *
 * The C++ class receives the DI `Database&`; this port receives its [DataSource],
 * matching the other JDBC repositories. `INSERT ... ON DUPLICATE KEY UPDATE`
 * (`DBInsert::upsert`, database.cpp:453-475) is reproduced with the portable
 * update-then-insert pair used by `JdbcPlayerStorageRepository`, which runs on
 * PostgreSQL 16, MariaDB and the H2 PostgreSQL-mode test database; the
 * `timestamp`/`value` identifiers are quoted by [SqlDialect.quoteIdentifier]
 * because both are keywords under MariaDB/MySQL.
 */
class KVSQL(private val dataSource: DataSource, maxSize: Int = KVStore.MAX_SIZE) : KVStore(maxSize) {

    override fun load(key: String): ValueWrapper? {
        logger.trace { "KVSQL::load($key)" }
        return try {
            dataSource.connection.use { connection ->
                val dialect = SqlDialect.from(connection)
                connection.prepareStatement(
                    "SELECT ${dialect.quoteIdentifier("timestamp")}, ${dialect.quoteIdentifier("value")} " +
                        "FROM kv_store WHERE key_name = ?",
                )
                    .use { statement ->
                        statement.setString(1, key)
                        statement.executeQuery().use { result ->
                            if (!result.next()) {
                                return null
                            }
                            val timestamp = result.getLong("timestamp")
                            val data = result.getBytes("value") ?: return null
                            val protoValue = ProtoValueWrapper()
                            if (!protoValue.parseFromArray(data)) {
                                logger.error { "Failed to deserialize value for key $key" }
                                return null
                            }
                            ProtoSerializable.fromProto(protoValue, timestamp)
                        }
                    }
            }
        } catch (e: SQLException) {
            logger.error(e) { "KVSQL::load failed for key $key" }
            null
        }
    }

    override fun loadPrefix(prefix: String): List<String> {
        logger.trace { "KVSQL::loadPrefix($prefix)" }
        val keys = mutableListOf<String>()
        try {
            dataSource.connection.use { connection ->
                connection.prepareStatement("SELECT key_name FROM kv_store WHERE key_name LIKE ?").use { statement ->
                    statement.setString(1, prefix + "%")
                    statement.executeQuery().use { result ->
                        while (result.next()) {
                            val key = result.getString("key_name")
                            keys += if (prefix.isEmpty()) key else key.replace(prefix, "")
                        }
                    }
                }
            }
        } catch (e: SQLException) {
            logger.error(e) { "KVSQL::loadPrefix failed for prefix $prefix" }
        }
        return keys
    }

    override fun save(key: String, value: ValueWrapper): Boolean {
        val data = ProtoSerializable.toProto(value).serializeToByteArray()
        if (value.isDeleted) {
            return execute("DELETE FROM kv_store WHERE key_name = ?", key)
        }
        return try {
            dataSource.connection.use { connection -> upsert(connection, key, value.timestamp, data) }
        } catch (e: SQLException) {
            logger.error(e) { "KVSQL::save failed for key $key" }
            false
        }
    }

    /**
     * `KVSQL::saveAll()` (`kv_sql.cpp:84-102`): every cached entry inside one
     * transaction; a single failure rolls the batch back.
     */
    override fun saveAll(): Boolean {
        val store = getStore()
        val success = try {
            dataSource.connection.use { connection ->
                connection.autoCommit = false
                try {
                    for ((key, value) in store) {
                        if (!saveWithin(connection, key, value)) {
                            connection.rollback()
                            return@use false
                        }
                    }
                    connection.commit()
                    true
                } catch (e: SQLException) {
                    connection.rollback()
                    logger.error(e) { "[saveAll] Error occurred saving player" }
                    false
                }
            }
        } catch (e: SQLException) {
            logger.error(e) { "[saveAll] Error occurred saving player" }
            false
        }

        return success
    }

    private fun saveWithin(connection: Connection, key: String, value: ValueWrapper): Boolean {
        val data = ProtoSerializable.toProto(value).serializeToByteArray()
        if (value.isDeleted) {
            connection.prepareStatement("DELETE FROM kv_store WHERE key_name = ?").use { statement ->
                statement.setString(1, key)
                statement.executeUpdate()
            }
            return true
        }
        return upsert(connection, key, value.timestamp, data)
    }

    private fun upsert(connection: Connection, key: String, timestamp: Long, data: ByteArray): Boolean {
        val dialect = SqlDialect.from(connection)
        val timestampColumn = dialect.quoteIdentifier("timestamp")
        val valueColumn = dialect.quoteIdentifier("value")
        connection.prepareStatement("UPDATE kv_store SET $timestampColumn = ?, $valueColumn = ? WHERE key_name = ?")
            .use { update ->
                update.setLong(1, timestamp)
                update.setBytes(2, data)
                update.setString(3, key)
                if (update.executeUpdate() > 0) {
                    return true
                }
            }

        connection.prepareStatement("INSERT INTO kv_store (key_name, $timestampColumn, $valueColumn) VALUES (?, ?, ?)")
            .use { insert ->
                insert.setString(1, key)
                insert.setLong(2, timestamp)
                insert.setBytes(3, data)
                insert.executeUpdate()
            }
        return true
    }

    private fun execute(query: String, key: String): Boolean = try {
        dataSource.connection.use { connection ->
            connection.prepareStatement(query).use { statement ->
                statement.setString(1, key)
                statement.executeUpdate()
            }
        }
        true
    } catch (e: SQLException) {
        logger.error(e) { "KVSQL::execute failed for key $key" }
        false
    }
}
