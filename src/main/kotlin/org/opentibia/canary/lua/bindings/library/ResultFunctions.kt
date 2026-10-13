package org.opentibia.canary.lua.bindings.library

import org.luaj.vm2.LuaString
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.opentibia.canary.lua.runtime.LuaEnvironment

/**
 * Engine seam behind the global `Result` table. Mirrors the
 * `ScriptEnvironment::getResultByID`/`removeResult` surface the C++ result functions
 * use (`src/lua/scripts/script_environment.hpp:60-62`).
 *
 * A `null` return models the C++ `DBResult_ptr` miss the bindings answer with
 * `false`; [free] mirrors `ScriptEnvironment::removeResult` and returns `false`
 * when the id is unknown.
 */
interface ResultOperations {
    /** `Result.getNumber(resultId, column)`; null when the result id is unknown. */
    fun getNumber(resultId: Long, column: String): Long?

    /** `Result.getString(resultId, column)`; null when the result id is unknown. */
    fun getString(resultId: Long, column: String): String?

    /** `DBResult::getStream(column, length)`; null when the result id is unknown. */
    fun getStream(resultId: Long, column: String): ByteArray?

    /** `Result.next(resultId)`; null when the result id is unknown. */
    fun next(resultId: Long): Boolean?

    /** `Result.free(resultId)`, i.e. `ScriptEnvironment::removeResult`. */
    fun free(resultId: Long): Boolean
}

/**
 * One stored query result, mirroring `DBResult` (`src/database/databasetasks.hpp` /
 * `src/database/database.hpp`): ordered columns plus rows and a forward-only cursor
 * driven by [next].
 */
class LuaResult internal constructor(
    val columns: List<String>,
    val rows: List<Map<String, Any?>>,
) {
    /** `DBResult::DBResult` fetches the first row, so it is current before `next()`. */
    private var cursor: Int = if (rows.isEmpty()) -1 else 0

    /** `DBResult::getNumber<T>`: unknown or non-numeric columns read 0. */
    fun getNumber(column: String): Long {
        val value = currentRow()?.get(column)
        return when (value) {
            is Number -> value.toLong()
            is String -> value.toLongOrNull() ?: 0L
            else -> 0L
        }
    }

    /** `DBResult::getString`: unknown columns read the empty string. */
    fun getString(column: String): String = valueAsString(currentRow()?.get(column))

    /** `DBResult::getStream(column, length)`: raw bytes of a blob column. */
    fun getStream(column: String): ByteArray = when (val value = currentRow()?.get(column)) {
        is ByteArray -> value
        is String -> value.toByteArray(Charsets.ISO_8859_1)
        else -> ByteArray(0)
    }

    /**
     * `DBResult::next` (`src/database/database.cpp:401-408`): fetches the following row
     * and reports whether one exists; a failing fetch clears the current row.
     */
    fun next(): Boolean {
        cursor++
        return cursor < rows.size
    }

    private fun currentRow(): Map<String, Any?>? = rows.getOrNull(cursor)

    private fun valueAsString(value: Any?): String = when (value) {
        null -> ""
        is ByteArray -> String(value, Charsets.ISO_8859_1)
        else -> value.toString()
    }

    companion object {
        /** Builds a result from a fixed column order and row maps. */
        fun of(columns: List<String>, rows: List<Map<String, Any?>>): LuaResult = LuaResult(columns, rows)
    }
}

/**
 * Port of `ScriptEnvironment`'s result registry
 * (`src/lua/scripts/script_environment.cpp:172-200`): `addResult` assigns the next
 * numeric id, `getResultByID` resolves it and `removeResult` releases it.
 *
 * The registry is instance-owned state (AGENTS.md 1.1); the install block shares one
 * instance between `db.storeQuery` and the `Result` table so a stored query can be
 * read from Lua exactly like Canary's script environment.
 */
class LuaScriptResults : ResultOperations {
    private var nextId: Long = 1
    private val results = mutableMapOf<Long, LuaResult>()

    /** `ScriptEnvironment::addResult` (`script_environment.cpp:184-200`). */
    fun add(result: LuaResult): Long {
        val id = nextId++
        results[id] = result
        return id
    }

    /** `ScriptEnvironment::getResultByID` (`script_environment.cpp:172-182`). */
    fun getResult(resultId: Long): LuaResult? = results[resultId]

    override fun getNumber(resultId: Long, column: String): Long? =
        getResult(resultId)?.getNumber(column)

    override fun getString(resultId: Long, column: String): String? =
        getResult(resultId)?.getString(column)

    override fun getStream(resultId: Long, column: String): ByteArray? =
        getResult(resultId)?.getStream(column)

    override fun next(resultId: Long): Boolean? = getResult(resultId)?.next()

    /** `ScriptEnvironment::removeResult` (`script_environment.cpp:202-209`). */
    override fun free(resultId: Long): Boolean = results.remove(resultId) != null
}

/**
 * Registers the global `Result` table with every method of
 * `ResultFunctions::init` (src/lua/functions/core/libs/result_functions.cpp:13-20):
 *
 *   Result.getNumber(resultId, column)
 *   Result.getString(resultId, column)
 *   Result.getStream(resultId, column)
 *   Result.next(resultId)
 *   Result.free(resultId)
 *
 * The `false` fallbacks and the two-value `getStream` return match the C++
 * `lua_pushboolean`/`lua_pushlstring` + length pushes.
 */
object ResultFunctions {
    const val RESULT_GLOBAL = "Result"

    fun install(env: LuaEnvironment, operations: ResultOperations) {
        val result = (env.globals.get(RESULT_GLOBAL) as? LuaTable)
            ?: LuaTable().also { env.globals.set(RESULT_GLOBAL, it) }

        result.set("getNumber", libraryFunction { args ->
            val value = operations.getNumber(luaArgLong(args, 1), luaArgString(args, 2))
            if (value == null) LuaValue.FALSE else LuaValue.valueOf(value.toDouble())
        })

        result.set("getString", libraryFunction { args ->
            val value = operations.getString(luaArgLong(args, 1), luaArgString(args, 2))
            if (value == null) LuaValue.FALSE else LuaValue.valueOf(value)
        })

        result.set("getStream", libraryFunction { args ->
            val value = operations.getStream(luaArgLong(args, 1), luaArgString(args, 2))
            if (value == null) {
                LuaValue.FALSE
            } else {
                LuaValue.varargsOf(LuaString.valueOf(value), LuaValue.valueOf(value.size))
            }
        })

        result.set("next", libraryFunction { args ->
            val value = operations.next(luaArgLong(args, 1))
            if (value == null) LuaValue.FALSE else LuaValue.valueOf(value)
        })

        result.set("free", libraryFunction { args ->
            LuaValue.valueOf(operations.free(luaArgLong(args, 1)))
        })
    }
}
