package org.opentibia.canary.io.iologindata

import io.github.oshai.kotlinlogging.KotlinLogging
import java.sql.Connection
import java.sql.Savepoint
import javax.sql.DataSource

private val logger = KotlinLogging.logger {}

/**
 * Pools the repositories of one player save into a single JDBC transaction.
 *
 * Canary's `Database` owns one connection handle, so `IOLoginData::savePlayer`
 * (src/io/iologindata.cpp:195-211) wraps `savePlayerGuard` in
 * `DBTransaction::executeWithinTransaction`
 * (src/database/database.hpp:233-256) and every guard query issued through
 * `g_database()` joins that one transaction. The port keeps one pooled
 * connection per repository call instead, so this [DataSource] reproduces the
 * single-transaction behavior: construct every repository and persistence class
 * of the save path over this instance and pass the same instance as
 * `IOLoginData.saveTransaction`. While [executeWithinTransaction] runs, every
 * `getConnection()` borrows the one transaction connection through a
 * [Connection] view that neutralizes the repository-local
 * `close`/`commit`/`rollback`/`autoCommit` calls, so only the save scope decides
 * to commit or roll back.
 *
 * The transaction is bound to the calling thread, so saves executed in parallel
 * (`SaveManager`'s executor) keep independent connections exactly like separate
 * `Database` instances.
 */
class PlayerSaveTransaction(private val delegate: DataSource) : DataSource by delegate {

    private val activeConnection = ThreadLocal<Connection?>()

    override fun getConnection(): Connection = activeConnection.get() ?: delegate.connection

    /**
     * `DBTransaction::executeWithinTransaction`
     * (src/database/database.hpp:233-256) with the guard's throw-on-failure
     * contract mapped onto the Boolean result: the transaction commits when
     * [callback] returns `true` and rolls back when it returns `false` or
     * throws. A nested call on the same thread is rejected the way
     * `DBTransaction::begin` rejects a second start.
     */
    fun executeWithinTransaction(callback: () -> Boolean): Boolean {
        if (activeConnection.get() != null) {
            logger.error { "[executeWithinTransaction] Transaction already active on this thread" }
            return false
        }

        val connection = delegate.connection
        val previousAutoCommit = connection.autoCommit
        return try {
            connection.autoCommit = false
            activeConnection.set(ManagedConnection(connection))
            val result = callback()
            if (result) {
                connection.commit()
            } else {
                connection.rollback()
            }
            result
        } catch (ex: Exception) {
            connection.rollback()
            logger.error(ex) { "[executeWithinTransaction] Error occurred during transaction" }
            false
        } finally {
            activeConnection.remove()
            connection.autoCommit = previousAutoCommit
            connection.close()
        }
    }

    /**
     * The repository-facing view of the transaction connection: `close`,
     * `commit`, `rollback` and `autoCommit` changes belong to the save scope and
     * are no-ops while it is active.
     */
    private class ManagedConnection(private val delegate: Connection) : Connection by delegate {

        override fun close() = Unit

        override fun setAutoCommit(autoCommit: Boolean) = Unit

        override fun getAutoCommit(): Boolean = false

        override fun commit() = Unit

        override fun rollback() = Unit

        override fun rollback(savepoint: Savepoint) = Unit

        override fun isClosed(): Boolean = false
    }
}
