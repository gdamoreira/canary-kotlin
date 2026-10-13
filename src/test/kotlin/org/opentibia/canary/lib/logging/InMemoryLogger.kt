package org.opentibia.canary.lib.logging

import org.opentibia.canary.lib.di.Injector
import java.nio.file.Path

/**
 * Test double for [Logger], transcribed from Canary's `InMemoryLogger`
 * (tests/unit/lib/logging/in_memory_logger.hpp:19-113). Records the calls made
 * through the `Logger` interface and reports `"DEBUG"` from [getLevel], exactly
 * like the C++ stub. `debug`/`trace` stay no-ops, mirroring the release build.
 */
class InMemoryLogger(profileLogDirectory: Path = Logger.DEFAULT_PROFILE_LOG_DIRECTORY) :
    Logger(profileLogDirectory) {

    data class LogEntry(val level: String, val message: String)

    val logs = mutableListOf<LogEntry>()

    /** `InMemoryLogger::reset`. */
    fun reset(): InMemoryLogger {
        logs.clear()
        return this
    }

    /** `InMemoryLogger::logCount`. */
    fun logCount(): Int = logs.size

    /** `InMemoryLogger::hasLogEntry`. */
    fun hasLogEntry(level: String, expectedMessage: String): Boolean =
        logs.any { it.level == level && it.message == expectedMessage }

    /** `InMemoryLogger::getLogEntry`; returns empty strings when out of bounds. */
    fun getLogEntry(index: Int): Pair<String, String> =
        if (index < logs.size) logs[index].level to logs[index].message else "" to ""

    override fun setLevel(name: String) {
        // The C++ stub ignores the requested level.
    }

    override fun getLevel(): String = "DEBUG"

    override fun info(msg: String) {
        logs += LogEntry("info", msg)
    }

    override fun warn(msg: String) {
        logs += LogEntry("warning", msg)
    }

    override fun error(msg: String) {
        logs += LogEntry("error", msg)
    }

    override fun critical(msg: String) {
        logs += LogEntry("critical", msg)
    }

    companion object {
        /** `InMemoryLogger::install` (tests/unit/lib/logging/in_memory_logger.hpp:40-43). */
        fun install(injector: Injector): Injector = injector.bind(Logger::class) { InMemoryLogger() }
    }
}
