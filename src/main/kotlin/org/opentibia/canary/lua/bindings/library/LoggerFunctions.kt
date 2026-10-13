package org.opentibia.canary.lua.bindings.library

import io.github.oshai.kotlinlogging.KotlinLogging
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.Varargs
import org.opentibia.canary.lua.runtime.LuaEnvironment

private val logger = KotlinLogging.logger {}

/**
 * Engine seam behind the global `logger` and deprecated `Spdlog` tables. Mirrors
 * `LoggerFunctions::luaLogger*`/`luaSpdlog*`
 * (src/lua/functions/core/libs/logger_functions.cpp:29-119).
 */
interface LoggerOperations {
    fun info(message: String)
    fun warn(message: String)
    fun error(message: String)
    fun debug(message: String)
    fun trace(message: String)

    /** `Lua::reportErrorFunc`: the invalid-parameter and format-error sink. */
    fun reportError(message: String)
}

/** [LoggerOperations] backed by the port's kotlin-logging logger (`g_logger()`). */
object KotlinLoggerOperations : LoggerOperations {
    override fun info(message: String) = logger.info { message }
    override fun warn(message: String) = logger.warn { message }
    override fun error(message: String) = logger.error { message }
    override fun debug(message: String) = logger.debug { message }
    override fun trace(message: String) = logger.trace { message }
    override fun reportError(message: String) = logger.error { message }
}

/**
 * Port of `Lua::getFormatedLoggerMessage`
 * (src/lua/functions/lua_functions_loader.cpp:367-404): renders `{}` placeholders
 * with the collected arguments.
 *
 * The C++ implementation delegates to `fmt::vformat`; this port supports sequential
 * `{}` placeholders plus `{{`/`}}` escapes. Positional/indexed/format-spec
 * placeholders raise the same format error (`null` return) the C++ catch reports.
 * Userdata render their Java type name in place of Canary's `LuaData_t` name.
 */
internal fun formatLoggerMessage(format: String, args: Varargs): String? {
    val output = StringBuilder(format.length)
    var argIndex = 2
    var index = 0
    while (index < format.length) {
        val char = format[index]
        when {
            char == '{' && index + 1 < format.length && format[index + 1] == '{' -> {
                output.append('{')
                index += 2
            }

            char == '}' && index + 1 < format.length && format[index + 1] == '}' -> {
                output.append('}')
                index += 2
            }

            char == '{' -> {
                val end = format.indexOf('}', index + 1)
                if (end == -1 || end != index + 1) {
                    return null
                }
                if (argIndex > args.narg()) {
                    return null
                }
                output.append(loggerArgument(args.arg(argIndex)))
                argIndex++
                index = end + 1
            }

            char == '}' -> return null

            else -> {
                output.append(char)
                index++
            }
        }
    }
    return output.toString()
}

/** The argument rendering of `Lua::getFormatedLoggerMessage` (lines 372-391). */
private fun loggerArgument(value: LuaValue): String = when {
    value.isstring() -> value.tojstring()
    value.isnumber() -> value.tojstring()
    value.isboolean() -> if (value.toboolean()) "true" else "false"
    value.isuserdata() -> value.touserdata()?.javaClass?.simpleName ?: "userdata"
    value.istable() -> "table"
    value.isnil() -> "nil"
    value.isfunction() -> "function"
    else -> ""
}

/**
 * Registers the compatibility `Spdlog` table and the `logger` table with every method
 * of `LoggerFunctions::init` (logger_functions.cpp:13-27):
 *
 *   Spdlog.info(text) / warn / error / debug
 *   logger.info(text, ...) / warn / error / debug / trace
 *
 * `Spdlog` logs the raw first argument (`Lua::isString` only, numbers are rejected
 * like the C++ `lua_pushnil` branch); `logger` renders the fmt-style message and
 * reports `"First parameter needs to be a string"` when the first argument is not.
 */
object LoggerFunctions {
    const val SPDLOG_GLOBAL = "Spdlog"
    const val LOGGER_GLOBAL = "logger"

    fun install(env: LuaEnvironment, operations: LoggerOperations = KotlinLoggerOperations) {
        val spdlog = (env.globals.get(SPDLOG_GLOBAL) as? LuaTable)
            ?: LuaTable().also { env.globals.set(SPDLOG_GLOBAL, it) }
        spdlog.set("info", rawLoggerFunction(operations::info))
        spdlog.set("warn", rawLoggerFunction(operations::warn))
        spdlog.set("error", rawLoggerFunction(operations::error))
        spdlog.set("debug", rawLoggerFunction(operations::debug))

        val loggerTable = (env.globals.get(LOGGER_GLOBAL) as? LuaTable)
            ?: LuaTable().also { env.globals.set(LOGGER_GLOBAL, it) }
        loggerTable.set("info", formattedLoggerFunction(operations, operations::info))
        loggerTable.set("warn", formattedLoggerFunction(operations, operations::warn))
        loggerTable.set("error", formattedLoggerFunction(operations, operations::error))
        loggerTable.set("debug", formattedLoggerFunction(operations, operations::debug))
        loggerTable.set("trace", formattedLoggerFunction(operations, operations::trace))
    }

    private fun rawLoggerFunction(log: (String) -> Unit) = libraryFunction { args ->
        if (args.arg(1).type() == LuaValue.TSTRING) {
            log(args.arg(1).tojstring())
        }
        LuaValue.NIL
    }

    private fun formattedLoggerFunction(
        operations: LoggerOperations,
        log: (String) -> Unit,
    ) = libraryFunction { args ->
        if (args.arg(1).type() != LuaValue.TSTRING) {
            operations.reportError("First parameter needs to be a string")
            return@libraryFunction LuaValue.NIL
        }

        val message = formatLoggerMessage(args.arg(1).tojstring(), args)
        if (message == null) {
            operations.reportError("Format error, invalid format string")
        } else {
            log(message)
        }
        LuaValue.NIL
    }
}
