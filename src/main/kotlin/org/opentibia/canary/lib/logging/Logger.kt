package org.opentibia.canary.lib.logging

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Port of Canary's `Logger` (src/lib/logging/logger.hpp:17-143,
 * src/lib/logging/logger.cpp:15-74). `logProfile` and `profile` keep their
 * concrete C++ behavior; `debug`/`trace` stay no-ops because the port mirrors
 * the release build (the C++ `DEBUG_LOG` compile-time gate).
 *
 * `profileLogDirectory` is the Kotlin seam for the C++ hardcoded `"log/"`
 * prefix, so tests can write the profile sinks into a temporary directory.
 */
abstract class Logger protected constructor(
    private val profileLogDirectory: Path = DEFAULT_PROFILE_LOG_DIRECTORY,
) {

    abstract fun setLevel(name: String)

    abstract fun getLevel(): String

    abstract fun info(msg: String)

    abstract fun warn(msg: String)

    abstract fun error(msg: String)

    abstract fun critical(msg: String)

    open fun debug(msg: String) {
        // Release-build parity: the C++ implementation is empty without DEBUG_LOG.
    }

    open fun trace(msg: String) {
        // Release-build parity: the C++ implementation is empty without DEBUG_LOG.
    }

    private val profileLoggers = LinkedHashMap<Path, ProfileFileLogger>()

    /**
     * `Logger::logProfile` (src/lib/logging/logger.cpp:26-48). The first call for a
     * file logs the original `name`; later calls use the sanitized name, exactly
     * like the C++ implementation.
     */
    fun logProfile(name: String, durationMs: Double) {
        val mutableName = name.replace(':', '_').replace('\\', '_').replace('/', '_')
        val filename = profileLogDirectory.resolve("profile_log-$mutableName.txt")

        synchronized(profileLoggers) {
            val existing = profileLoggers[filename]
            if (existing == null) {
                try {
                    val profileLogger = ProfileFileLogger(filename, mutableName)
                    profileLoggers[filename] = profileLogger
                    profileLogger.info("Function $name executed in $durationMs ms")
                } catch (e: IOException) {
                    error("Profile log initialization failed: ${e.message ?: e.toString()}")
                }
            } else {
                existing.info("Function $mutableName executed in $durationMs ms")
            }
        }
    }

    /** `Logger::profile` (src/lib/logging/logger.hpp:63-74). */
    fun <T> profile(name: String, func: () -> T): T {
        val begin = System.nanoTime()
        val result = func()
        val durationMs = (System.nanoTime() - begin) / 1_000_000.0

        logProfile(name, durationMs)
        info("Function $name executed in $durationMs ms")

        return result
    }

    /** The spdlog `basic_file_sink_mt` behind the C++ `profile_loggers_` map. */
    private class ProfileFileLogger(private val filename: Path, private val name: String) {
        init {
            filename.parent?.let { Files.createDirectories(it) }
            Files.writeString(
                filename,
                "",
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE,
            )
        }

        fun info(message: String) {
            val timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT)
            Files.writeString(
                filename,
                "[$timestamp] [$name] [info] $message${System.lineSeparator()}",
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND,
                StandardOpenOption.WRITE,
            )
        }

        companion object {
            private val TIMESTAMP_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
        }
    }

    companion object {
        @JvmField
        val DEFAULT_PROFILE_LOG_DIRECTORY: Path = Path.of("log")
    }
}
