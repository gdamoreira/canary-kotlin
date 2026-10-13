package org.opentibia.canary.lua.bindings.library

import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.opentibia.canary.database.Database
import org.opentibia.canary.database.DatabaseTasks
import org.opentibia.canary.lua.runtime.LuaEnvironment
import java.sql.SQLException
import javax.sql.DataSource

/**
 * Engine seam behind the global `db` table. Mirrors the `Database`/`DatabaseTasks`
 * surface the C++ functions use (src/lua/functions/core/libs/db_functions.cpp):
 *
 *  - [query] = `Database::executeQuery`
 *  - [asyncQuery] / [asyncStoreQuery] = `g_databaseTasks().execute`/`store`
 *  - [storeQuery] = `Database::storeQuery` + `ScriptEnvironment::addResult`
 *  - [escapeString] / [escapeBlob] = `Database::escapeString`/`escapeBlob`
 *  - [lastInsertId] = `Database::getLastInsertId`
 *  - [tableExists] = `DatabaseManager::tableExists`
 *
 * [storeQuery] hands the raw result to the binding, which assigns it a script result
 * id through the shared [LuaScriptResults] registry exactly like the C++
 * `ScriptEnvironment::addResult` (db_functions.cpp:74).
 */
interface DbOperations {
    /** `db.query(query)`. */
    fun query(query: String): Boolean

    /** `db.asyncQuery(query[, callback])`; a null callback keeps the C++ no-callback path. */
    fun asyncQuery(query: String, callback: ((Boolean) -> Unit)?)

    /** `db.storeQuery(query)`; null is the C++ `false` result. */
    fun storeQuery(query: String): LuaResult?

    /** `db.asyncStoreQuery(query[, callback])`. */
    fun asyncStoreQuery(query: String, callback: ((LuaResult?) -> Unit)?)

    /** `db.escapeString(value)`. */
    fun escapeString(value: String): String

    /** `db.escapeBlob(value, length)`. */
    fun escapeBlob(value: String, length: Int): String

    /** `db.lastInsertId()`. */
    fun lastInsertId(): Long

    /** `db.tableExists(tableName)`. */
    fun tableExists(tableName: String): Boolean
}

/**
 * Documented no-op seam used when no database is wired into the runtime
 * (WorldLuaOperations has no `Database`): queries fail, escapes read empty and
 * async callbacks are never invoked.
 */
object UnwiredDbOperations : DbOperations {
    override fun query(query: String): Boolean = false

    override fun asyncQuery(query: String, callback: ((Boolean) -> Unit)?) {}

    override fun storeQuery(query: String): LuaResult? = null

    override fun asyncStoreQuery(query: String, callback: ((LuaResult?) -> Unit)?) {}

    override fun escapeString(value: String): String = ""

    override fun escapeBlob(value: String, length: Int): String = ""

    override fun lastInsertId(): Long = 0L

    override fun tableExists(tableName: String): Boolean = false
}

/**
 * `Database`-backed [DbOperations]: uses the ported [Database] for execute/escape and
 * buffers result sets into [LuaResult] rows (the role of `DBResult`).
 *
 * Parity gaps:
 *  - `lastInsertId` has no Kotlin `Database` support yet and reads 0.
 *  - `tableExists` runs the exact `information_schema` lookup of
 *    `DatabaseManager::tableExists` (`src/database/databasemanager.cpp:107-125`) with
 *    [schemaName] standing in for the configured `MYSQL_DB`.
 */
class DatabaseLuaOperations(
    private val database: Database,
    private val dataSource: DataSource,
    private val schemaName: String = "",
    private val tasks: DatabaseTasks? = null,
) : DbOperations {

    override fun query(query: String): Boolean = database.executeQuery(query)

    /** `g_databaseTasks().execute` (databasetasks.cpp:60-69); without a task queue the callback is dropped. */
    override fun asyncQuery(query: String, callback: ((Boolean) -> Unit)?) {
        val queue = tasks ?: return
        queue.addTask("db.asyncQuery") {
            callback?.invoke(database.executeQuery(query))
        }
    }

    override fun storeQuery(query: String): LuaResult? = readResult(query)

    /** `g_databaseTasks().store` (databasetasks.cpp:71-84). */
    override fun asyncStoreQuery(query: String, callback: ((LuaResult?) -> Unit)?) {
        val queue = tasks ?: return
        queue.addTask("db.asyncStoreQuery") {
            callback?.invoke(readResult(query))
        }
    }

    override fun escapeString(value: String): String = database.escapeString(value)

    /**
     * `Database::escapeBlob(s, length)`: the C++ call reads exactly `length` bytes from
     * the value pointer, so a shorter value is right-padded with zero bytes.
     */
    override fun escapeBlob(value: String, length: Int): String {
        val bytes = value.toByteArray(Charsets.ISO_8859_1).copyOf(length)
        return database.escapeBlob(bytes)
    }

    /** `Database::getLastInsertId` is not modeled by the Kotlin `Database` yet (PARITY GAP). */
    override fun lastInsertId(): Long = 0L

    /** `DatabaseManager::tableExists` (`src/database/databasemanager.cpp:107-125`). */
    override fun tableExists(tableName: String): Boolean {
        val escapedSchema = schemaName.replace("'", "''")
        val escapedTable = tableName.replace("'", "''")
        val query = "SELECT `TABLE_NAME` FROM `information_schema`.`TABLES` " +
            "WHERE `TABLE_SCHEMA` = '$escapedSchema' AND `TABLE_NAME` = '$escapedTable';"
        return storeQuery(query) != null
    }

    /**
     * Buffers a result set the way `Database::storeQuery` does: column labels in
     * result-set order plus every row, released with the statement.
     */
    private fun readResult(query: String): LuaResult? {
        try {
            dataSource.connection.use { connection ->
                connection.createStatement().use { statement ->
                    statement.execute(query)
                    val resultSet = statement.resultSet ?: return null
                    resultSet.use {
                        val metadata = resultSet.metaData
                        val columns = (1..metadata.columnCount).map { metadata.getColumnLabel(it) }
                        val rows = mutableListOf<Map<String, Any?>>()
                        while (resultSet.next()) {
                            val row = LinkedHashMap<String, Any?>(metadata.columnCount)
                            for (index in 1..metadata.columnCount) {
                                row[metadata.getColumnLabel(index)] = resultSet.getObject(index)
                            }
                            rows.add(row)
                        }
                        return if (rows.isEmpty()) null else LuaResult(columns, rows)
                    }
                }
            }
        } catch (e: SQLException) {
            return null
        }
    }
}

/**
 * Registers the global `db` table with every method of `DBFunctions::init`
 * (src/lua/functions/core/libs/db_functions.cpp:17-27):
 *
 *   db.query(query)
 *   db.asyncQuery(query[, callback])
 *   db.storeQuery(query)
 *   db.asyncStoreQuery(query[, callback])
 *   db.escapeString(value)
 *   db.escapeBlob(value, length)
 *   db.lastInsertId()
 *   db.tableExists(tableName)
 *
 * `storeQuery` result ids come from [results], shared with the `Result` table so
 * `Result.getNumber(db.storeQuery(...), column)` resolves (db_functions.cpp:74).
 */
object DbFunctions {
    const val DB_GLOBAL = "db"

    fun install(
        env: LuaEnvironment,
        operations: DbOperations = UnwiredDbOperations,
        results: LuaScriptResults,
    ) {
        val db = (env.globals.get(DB_GLOBAL) as? LuaTable)
            ?: LuaTable().also { env.globals.set(DB_GLOBAL, it) }

        db.set("query", libraryFunction { args ->
            LuaValue.valueOf(operations.query(luaArgString(args, args.narg())))
        })

        db.set("asyncQuery", libraryFunction { args ->
            val callback = if (args.narg() > 1) args.arg(args.narg()) else null
            val luaCallback = if (callback?.isfunction() == true) callback else null
            operations.asyncQuery(luaArgString(args, 1)) { success ->
                luaCallback?.invoke(LuaValue.valueOf(success))
            }
            LuaValue.NIL
        })

        db.set("storeQuery", libraryFunction { args ->
            val result = operations.storeQuery(luaArgString(args, args.narg()))
            if (result == null) LuaValue.FALSE else LuaValue.valueOf(results.add(result).toDouble())
        })

        db.set("asyncStoreQuery", libraryFunction { args ->
            val callback = if (args.narg() > 1) args.arg(args.narg()) else null
            val luaCallback = if (callback?.isfunction() == true) callback else null
            operations.asyncStoreQuery(luaArgString(args, 1)) { result ->
                val value = if (result == null) {
                    LuaValue.FALSE
                } else {
                    LuaValue.valueOf(results.add(result).toDouble())
                }
                luaCallback?.invoke(value)
            }
            LuaValue.NIL
        })

        db.set("escapeString", libraryFunction { args ->
            LuaValue.valueOf(operations.escapeString(luaArgString(args, args.narg())))
        })

        db.set("escapeBlob", libraryFunction { args ->
            LuaValue.valueOf(operations.escapeBlob(luaArgString(args, 1), luaArgInt(args, 2)))
        })

        db.set("lastInsertId", libraryFunction {
            LuaValue.valueOf(operations.lastInsertId().toDouble())
        })

        db.set("tableExists", libraryFunction { args ->
            LuaValue.valueOf(operations.tableExists(luaArgString(args, args.narg())))
        })
    }
}
