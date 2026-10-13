package org.opentibia.canary.lib.logging

import org.opentibia.canary.lib.di.inject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicReference

/**
 * Port of Canary's `LogWithSpdLog` (src/lib/logging/log_with_spd_log.hpp:13-53,
 * src/lib/logging/log_with_spd_log.cpp:13-61).
 *
 * The spdlog console sink is mapped to `System.out` and the global
 * `spdlog::set_level`/`spdlog::get_level` state to a process-wide atomic. The
 * pattern is the C++ release pattern
 * `[%Y-%d-%m %H:%M:%S.%e] [%^%l%$] %v `.
 */
class LogWithSpdLog : Logger() {

    init {
        setLevel("info")
    }

    /** `LogWithSpdLog::setLevel` (log_with_spd_log.cpp:26-30). */
    override fun setLevel(name: String) {
        debug("Setting log level to: $name.")
        level.set(Level.fromString(name))
    }

    /** `LogWithSpdLog::getLevel` (log_with_spd_log.cpp:32-35). */
    override fun getLevel(): String = level.get().displayName

    override fun info(msg: String) = log(Level.INFO, msg)

    override fun warn(msg: String) = log(Level.WARN, msg)

    override fun error(msg: String) = log(Level.ERROR, msg)

    override fun critical(msg: String) = log(Level.CRITICAL, msg)

    private fun log(messageLevel: Level, message: String) {
        if (!messageLevel.enabled(level.get())) {
            return
        }
        println("[${LocalDateTime.now().format(TIMESTAMP_FORMAT)}] [${messageLevel.displayName}] $message ")
    }

    /** `spdlog::level` names (spdlog `common-inl.h` `level_string_views`). */
    enum class Level(val displayName: String) {
        TRACE("trace"),
        DEBUG("debug"),
        INFO("info"),
        WARN("warning"),
        ERROR("error"),
        CRITICAL("critical"),
        OFF("off"),
        ;

        /** `SPDLOG_LOGGER_CALL` level comparison. */
        fun enabled(currentLevel: Level): Boolean = ordinal >= currentLevel.ordinal

        companion object {
            /** `spdlog::level::from_str` (spdlog `common-inl.h`). */
            fun fromString(name: String): Level = entries.firstOrNull { it.displayName == name } ?: when (name) {
                "warn" -> WARN
                "err" -> ERROR
                else -> OFF
            }
        }
    }

    companion object {
        private val level = AtomicReference(Level.INFO)
        private val TIMESTAMP_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-dd-MM HH:mm:ss.SSS")

        /** `LogWithSpdLog::getInstance` (log_with_spd_log.cpp:22-24). */
        fun getInstance(): Logger = inject()
    }
}
