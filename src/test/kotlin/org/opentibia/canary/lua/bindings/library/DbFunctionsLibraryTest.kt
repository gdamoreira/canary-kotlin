package org.opentibia.canary.lua.bindings.library

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.lua.runtime.LuaEnvironment

/**
 * Transcript tests for the `db` global table and the `Result` table it feeds,
 * mirroring `DBFunctions` (src/lua/functions/core/libs/db_functions.cpp) and
 * `ResultFunctions` (src/lua/functions/core/libs/result_functions.cpp).
 */
class DbFunctionsLibraryTest {

    private class RecordingDbOperations : DbOperations {
        val queries = mutableListOf<String>()
        val storedQueries = mutableListOf<String>()
        var queryResult = true
        var storeResult: LuaResult? = null
        var lastInsertId = 0L
        var tableExists = false
        var escapedString: String? = null
        var escapedBlob: Pair<String, Int>? = null
        var asyncCallback: ((Boolean) -> Unit)? = null
        var asyncStoreCallback: ((LuaResult?) -> Unit)? = null

        override fun query(query: String): Boolean {
            queries += query
            return queryResult
        }

        override fun asyncQuery(query: String, callback: ((Boolean) -> Unit)?) {
            queries += query
            asyncCallback = callback
        }

        override fun storeQuery(query: String): LuaResult? {
            storedQueries += query
            return storeResult
        }

        override fun asyncStoreQuery(query: String, callback: ((LuaResult?) -> Unit)?) {
            storedQueries += query
            asyncStoreCallback = callback
        }

        override fun escapeString(value: String): String {
            escapedString = value
            return "escaped:$value"
        }

        override fun escapeBlob(value: String, length: Int): String {
            escapedBlob = value to length
            return "blob"
        }

        override fun lastInsertId(): Long = lastInsertId

        override fun tableExists(tableName: String): Boolean = tableExists
    }

    private fun environment(operations: DbOperations, results: LuaScriptResults = LuaScriptResults()): LuaEnvironment =
        LuaEnvironment().apply { initState() }.also {
            DbFunctions.install(it, operations, results)
            ResultFunctions.install(it, results)
        }

    @Test
    fun `db query forwards the SQL and returns the database result`() {
        val operations = RecordingDbOperations()
        val environment = environment(operations)

        environment.loadChunk("result = db.query('SELECT 1')")

        assertThat(operations.queries).containsExactly("SELECT 1")
        assertThat(environment.getGlobal("result").toboolean()).isTrue()
    }

    @Test
    fun `db query returns false when the database rejects the statement`() {
        val operations = RecordingDbOperations().apply { queryResult = false }
        val environment = environment(operations)

        environment.loadChunk("result = db.query('BROKEN')")

        assertThat(environment.getGlobal("result").toboolean()).isFalse()
    }

    @Test
    fun `db asyncQuery invokes the Lua callback with the success flag`() {
        val operations = RecordingDbOperations()
        val environment = environment(operations)

        environment.loadChunk(
            """
            seen = nil
            db.asyncQuery('UPDATE players SET online = 0', function(success) seen = success end)
            """.trimIndent(),
        )
        assertThat(operations.queries).containsExactly("UPDATE players SET online = 0")

        operations.asyncCallback?.invoke(true)

        assertThat(environment.getGlobal("seen").toboolean()).isTrue()
    }

    @Test
    fun `db storeQuery returns a result id readable through the Result table`() {
        val operations = RecordingDbOperations().apply {
            storeResult = LuaResult.of(
                columns = listOf("id", "name"),
                rows = listOf(
                    linkedMapOf("id" to 7L, "name" to "Canary"),
                    linkedMapOf("id" to 8L, "name" to "Kotlin"),
                ),
            )
        }
        val environment = environment(operations)

        environment.loadChunk(
            """
            resultId = db.storeQuery('SELECT id, name FROM players')
            firstId = Result.getNumber(resultId, 'id')
            advanced = Result.next(resultId)
            id = Result.getNumber(resultId, 'id')
            name = Result.getString(resultId, 'name')
            freed = Result.free(resultId)
            missing = Result.getNumber(resultId, 'id')
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("firstId").toint()).isEqualTo(7)
        assertThat(environment.getGlobal("advanced").toboolean()).isTrue()
        assertThat(environment.getGlobal("id").toint()).isEqualTo(8)
        assertThat(environment.getGlobal("name").tojstring()).isEqualTo("Kotlin")
        assertThat(environment.getGlobal("freed").toboolean()).isTrue()
        assertThat(environment.getGlobal("missing").toboolean()).isFalse()
    }

    @Test
    fun `db storeQuery returns false when there is no result set`() {
        val operations = RecordingDbOperations()
        val environment = environment(operations)

        environment.loadChunk("result = db.storeQuery('UPDATE players SET online = 0')")

        assertThat(environment.getGlobal("result").toboolean()).isFalse()
    }

    @Test
    fun `db asyncStoreQuery hands the result id to the Lua callback`() {
        val operations = RecordingDbOperations().apply {
            storeResult = LuaResult.of(listOf("n"), listOf(mapOf("n" to 3)))
        }
        val environment = environment(operations)

        environment.loadChunk(
            """
            value = nil
            db.asyncStoreQuery('SELECT n FROM counts', function(resultId) value = Result.getNumber(resultId, 'n') end)
            """.trimIndent(),
        )

        operations.asyncStoreCallback?.invoke(operations.storeResult)

        assertThat(environment.getGlobal("value").toint()).isEqualTo(3)
    }

    @Test
    fun `db escapeString and escapeBlob use the database escaping`() {
        val operations = RecordingDbOperations().apply { lastInsertId = 42L; tableExists = true }
        val environment = environment(operations)

        environment.loadChunk(
            """
            escaped = db.escapeString("O'Hara")
            blob = db.escapeBlob("abcd", 4)
            insertId = db.lastInsertId()
            exists = db.tableExists("players")
            """.trimIndent(),
        )

        assertThat(operations.escapedString).isEqualTo("O'Hara")
        assertThat(operations.escapedBlob).isEqualTo("abcd" to 4)
        assertThat(environment.getGlobal("escaped").tojstring()).isEqualTo("escaped:O'Hara")
        assertThat(environment.getGlobal("blob").tojstring()).isEqualTo("blob")
        assertThat(environment.getGlobal("insertId").toint()).isEqualTo(42)
        assertThat(environment.getGlobal("exists").toboolean()).isTrue()
    }

    @Test
    fun `Result getStream returns the bytes plus the stream length`() {
        val operations = RecordingDbOperations().apply {
            storeResult = LuaResult.of(listOf("data"), listOf(mapOf("data" to byteArrayOf(1, 2, 3))))
        }
        val environment = environment(operations)

        environment.loadChunk(
            """
            resultId = db.storeQuery('SELECT data FROM blobs')
            stream, length = Result.getStream(resultId, 'data')
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("length").toint()).isEqualTo(3)
        assertThat(environment.getGlobal("stream").tojstring()).isEqualTo(String(byteArrayOf(1, 2, 3), Charsets.ISO_8859_1))
    }
}
