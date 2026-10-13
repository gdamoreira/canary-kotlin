package org.opentibia.canary.database

import io.github.oshai.kotlinlogging.KotlinLogging
import java.sql.Connection
import java.sql.SQLException
import java.util.concurrent.locks.ReentrantLock
import javax.sql.DataSource
import kotlin.concurrent.withLock

private val logger = KotlinLogging.logger {}

/**
 * Ports `Database` from `src/database/database.cpp|.hpp`: transactions, SQL
 * escaping, retry on recoverable errors, and query-size limits over an
 * injected [DataSource] instead of a global MySQL handle.
 */
class Database(
    private val dataSource: DataSource,
    private val maxPacketSize: Long = DEFAULT_MAX_PACKET_SIZE
) {
    companion object {
        const val MAX_QUERY_SIZE: Long = 8L * 1024 * 1024
        const val DEFAULT_MAX_PACKET_SIZE: Long = 1048576L
        const val QUERY_RETRIES: Int = 10

        private const val RETRY_DELAY_MILLIS = 1000L

        private const val CR_SERVER_LOST = 2013
        private const val CR_SERVER_GONE_ERROR = 2006
        private const val CR_CONN_HOST_ERROR = 2003
        private const val ER_SERVER_SHUTDOWN = 1053
        private const val CR_CONNECTION_ERROR = 2002

        fun isRecoverableError(error: Int): Boolean =
            error == CR_SERVER_LOST ||
                error == CR_SERVER_GONE_ERROR ||
                error == CR_CONN_HOST_ERROR ||
                error == ER_SERVER_SHUTDOWN ||
                error == CR_CONNECTION_ERROR
    }

    private val lock = ReentrantLock()
    private var transactionConnection: Connection? = null

    fun getMaxPacketSize(): Long = maxPacketSize

    internal fun beginTransaction(): Boolean {
        lock.lock()
        if (transactionConnection != null) {
            return true
        }

        try {
            val connection = dataSource.connection
            connection.autoCommit = false
            transactionConnection = connection
            return true
        } catch (e: SQLException) {
            logger.error(e) { "[beginTransaction] Failed to begin transaction" }
            lock.unlock()
            return false
        }
    }

    internal fun commit(): Boolean {
        val connection = transactionConnection
        if (connection == null) {
            logger.error { "[commit] Database not initialized!" }
            return false
        }

        return try {
            connection.commit()
            true
        } catch (e: SQLException) {
            logger.error(e) { "[commit] ${e.message}" }
            false
        } finally {
            releaseTransaction(connection)
        }
    }

    internal fun rollback(): Boolean {
        val connection = transactionConnection
        if (connection == null) {
            logger.error { "[rollback] Database not initialized!" }
            return false
        }

        return try {
            connection.rollback()
            true
        } catch (e: SQLException) {
            logger.error(e) { "[rollback] ${e.message}" }
            false
        } finally {
            releaseTransaction(connection)
        }
    }

    fun retryQuery(query: String, retries: Int): Boolean =
        withConnection { connection -> executeWithRetry(connection, query, retries) }

    fun executeQuery(query: String): Boolean =
        withConnection { connection -> executeWithRetry(connection, query, QUERY_RETRIES) }

    fun escapeString(s: String): String =
        buildString(s.length + 2) {
            append('\'')
            for (char in s) {
                if (char == '\'') {
                    append(char)
                }
                append(char)
            }
            append('\'')
        }

    fun escapeBlob(s: ByteArray): String =
        buildString(s.size + 2) {
            append('\'')
            for (byte in s) {
                val char = (byte.toInt() and 0xFF).toChar()
                if (char == '\'') {
                    append('\'')
                }
                append(char)
            }
            append('\'')
        }

    private fun executeWithRetry(connection: Connection, query: String, retries: Int): Boolean {
        var remaining = retries
        while (remaining > 0) {
            try {
                connection.createStatement().use { statement -> statement.execute(query) }
                return true
            } catch (e: SQLException) {
                logger.error { "Query: ${query.take(256)}" }
                logger.error { "Database error [${e.errorCode}]: ${e.message}" }
                if (!isRecoverableError(e.errorCode)) {
                    return false
                }
                Thread.sleep(RETRY_DELAY_MILLIS)
                remaining--
            }
        }

        logger.error { "Query $query failed after $retries retries." }
        return false
    }

    private inline fun <T> withConnection(block: (Connection) -> T): T = lock.withLock {
        val transaction = transactionConnection
        if (transaction != null) {
            block(transaction)
        } else {
            dataSource.connection.use { connection -> block(connection) }
        }
    }

    private fun releaseTransaction(connection: Connection) {
        transactionConnection = null
        try {
            connection.autoCommit = true
        } catch (e: SQLException) {
            logger.error(e) { "[releaseTransaction] Failed to restore auto-commit" }
        } finally {
            connection.close()
            lock.unlock()
        }
    }
}

/**
 * Ports `DBTransaction` from `src/database/database.hpp`: the transaction state
 * machine and the two `executeWithinTransaction*` helpers.
 */
class DBTransaction private constructor(private val database: Database) {

    private enum class State {
        NoStart,
        Start,
        Commit
    }

    private var state = State.NoStart

    private fun begin(): Boolean {
        if (state != State.NoStart) {
            return false
        }

        state = State.Start
        if (!database.beginTransaction()) {
            state = State.NoStart
            return false
        }
        return true
    }

    private fun rollback() {
        if (state != State.Start) {
            return
        }

        state = State.NoStart
        try {
            database.rollback()
        } catch (e: Exception) {
            logger.error(e) { "[rollback] An error occurred while rolling back the transaction" }
        }
    }

    private fun commit(): Boolean {
        if (state != State.Start) {
            logger.error { "Transaction not started" }
            return false
        }

        state = State.Commit
        return try {
            if (!database.commit()) {
                state = State.NoStart
                logger.error { "[commit] Commit returned false" }
                false
            } else {
                true
            }
        } catch (e: Exception) {
            state = State.NoStart
            logger.error(e) { "[commit] An error occurred while committing the transaction" }
            false
        }
    }

    companion object {
        fun executeWithinTransaction(database: Database, callback: () -> Boolean): Boolean {
            val transaction = DBTransaction(database)
            return try {
                if (!transaction.begin()) {
                    logger.error { "[executeWithinTransaction] Failed to begin transaction" }
                    return false
                }

                val result = callback()
                if (!transaction.commit()) {
                    return false
                }
                result
            } catch (e: Exception) {
                transaction.rollback()
                logger.error(e) { "[executeWithinTransaction] Error occurred during transaction" }
                false
            }
        }

        fun executeWithinTransactionRollbackOnFailure(database: Database, callback: () -> Boolean): Boolean {
            val transaction = DBTransaction(database)
            return try {
                if (!transaction.begin()) {
                    logger.error { "[executeWithinTransactionRollbackOnFailure] Failed to begin transaction" }
                    return false
                }

                if (!callback()) {
                    transaction.rollback()
                    return false
                }

                transaction.commit()
            } catch (e: Exception) {
                transaction.rollback()
                logger.error(e) { "[executeWithinTransactionRollbackOnFailure] Error occurred during transaction" }
                false
            }
        }
    }
}

/**
 * Ports the query-size limited batching of `DBInsert` from
 * `src/database/database.cpp|.hpp`.
 */
class DBInsert(private val database: Database, insertQuery: String) {

    private val query: String = insertQuery
    private val values = StringBuilder()
    private var length: Long = insertQuery.length.toLong()

    fun addRow(row: String): Boolean {
        val rowLength = row.length.toLong()
        var addedLength = if (values.isEmpty()) rowLength + 2 else rowLength + 3
        val maxPacketSize = database.getMaxPacketSize()

        if (length + addedLength > maxPacketSize) {
            if (values.isEmpty() || !execute()) {
                return false
            }

            addedLength = rowLength + 2
            if (length + addedLength > maxPacketSize) {
                return false
            }
        }

        length += addedLength
        if (values.isEmpty()) {
            values.append('(').append(row).append(')')
        } else {
            values.append(',').append('(').append(row).append(')')
        }
        return true
    }

    fun execute(): Boolean {
        if (values.isEmpty()) {
            return true
        }

        val baseHasSpace = query.isNotEmpty() && query.last() == ' '
        val separatorSize = if (baseHasSpace) 0 else 1
        val queryPrefixSize = query.length.toLong() + separatorSize
        if (queryPrefixSize >= Database.MAX_QUERY_SIZE) {
            return false
        }

        var currentBatch = values.toString()
        while (currentBatch.isNotEmpty()) {
            var cutPos = Database.MAX_QUERY_SIZE - queryPrefixSize
            if (cutPos < currentBatch.length.toLong()) {
                val boundary = currentBatch.lastIndexOf("),(", cutPos.toInt())
                if (boundary == -1) {
                    return false
                }
                cutPos = (boundary + 2).toLong()
            } else {
                cutPos = currentBatch.length.toLong()
            }

            var batchValues = currentBatch.substring(0, cutPos.toInt())
            if (batchValues.isNotEmpty() && batchValues.last() == ',') {
                batchValues = batchValues.dropLast(1)
            }
            currentBatch = currentBatch.substring(cutPos.toInt())

            val sql = buildString {
                append(query)
                if (!baseHasSpace) {
                    append(' ')
                }
                append(batchValues)
            }
            if (!database.executeQuery(sql)) {
                return false
            }
        }

        values.clear()
        length = query.length.toLong()
        return true
    }
}
