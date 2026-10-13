package org.opentibia.canary.lua.bindings.library

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.lua.runtime.LuaEnvironment

/**
 * Transcript tests for the `logger` and deprecated `Spdlog` tables, mirroring
 * `LoggerFunctions` (src/lua/functions/core/libs/logger_functions.cpp) and
 * `Lua::getFormatedLoggerMessage`
 * (src/lua/functions/lua_functions_loader.cpp:367-404).
 */
class LoggerFunctionsLibraryTest {

    private class RecordingLoggerOperations : LoggerOperations {
        val info = mutableListOf<String>()
        val warn = mutableListOf<String>()
        val error = mutableListOf<String>()
        val debug = mutableListOf<String>()
        val trace = mutableListOf<String>()
        val reported = mutableListOf<String>()

        override fun info(message: String) {
            info += message
        }

        override fun warn(message: String) {
            warn += message
        }

        override fun error(message: String) {
            error += message
        }

        override fun debug(message: String) {
            debug += message
        }

        override fun trace(message: String) {
            trace += message
        }

        override fun reportError(message: String) {
            reported += message
        }
    }

    private fun environment(operations: LoggerOperations): LuaEnvironment =
        LuaEnvironment().apply { initState() }.also { LoggerFunctions.install(it, operations) }

    @Test
    fun `logger renders fmt placeholders like getFormatedLoggerMessage`() {
        val operations = RecordingLoggerOperations()
        val environment = environment(operations)

        environment.loadChunk(
            """
            logger.info('hello {}', 'world')
            logger.warn('{} and {}', 'a', true)
            logger.debug('plain')
            logger.trace('trace {}', 7)
            logger.error('fail')
            """.trimIndent(),
        )

        assertThat(operations.info).containsExactly("hello world")
        assertThat(operations.warn).containsExactly("a and true")
        assertThat(operations.debug).containsExactly("plain")
        assertThat(operations.trace).containsExactly("trace 7")
        assertThat(operations.error).containsExactly("fail")
    }

    @Test
    fun `logger reports a non-string first parameter`() {
        val operations = RecordingLoggerOperations()
        val environment = environment(operations)

        environment.loadChunk("logger.info(42)")

        assertThat(operations.info).isEmpty()
        assertThat(operations.reported).containsExactly("First parameter needs to be a string")
    }

    @Test
    fun `logger reports a format error like the C++ catch branch`() {
        val operations = RecordingLoggerOperations()
        val environment = environment(operations)

        environment.loadChunk("logger.info('broken {name}', 1)")

        assertThat(operations.info).isEmpty()
        assertThat(operations.reported).hasSize(1)
    }

    @Test
    fun `Spdlog logs the raw string and rejects non-strings`() {
        val operations = RecordingLoggerOperations()
        val environment = environment(operations)

        environment.loadChunk(
            """
            Spdlog.info('raw {}')
            Spdlog.warn(10)
            Spdlog.debug('dbg')
            Spdlog.error('err')
            """.trimIndent(),
        )

        assertThat(operations.info).containsExactly("raw {}")
        assertThat(operations.warn).isEmpty()
        assertThat(operations.debug).containsExactly("dbg")
        assertThat(operations.error).containsExactly("err")
    }
}
