package org.opentibia.canary.lib.logging

import ch.qos.logback.classic.Level
import org.opentibia.canary.config.ConfigKeys
import org.opentibia.canary.config.ConfigManager
import org.slf4j.LoggerFactory

/**
 * Applies Canary's `logLevel` configuration key (`config.lua.dist`: `logLevel`)
 * to the application logger. The key accepts the Canary `Logger` level names;
 * an unknown value keeps the `logback.xml` default (`info`).
 */
object LogLevels {
    private val logLevelKey = requireNotNull(ConfigKeys.byName("LOGLEVEL"))

    private const val APPLICATION_LOGGER = "org.opentibia.canary"

    fun toLevel(configured: String): Level = when (configured.lowercase()) {
        "trace" -> Level.TRACE
        "debug" -> Level.DEBUG
        "info" -> Level.INFO
        "warn", "warning" -> Level.WARN
        "error", "critical" -> Level.ERROR
        "off" -> Level.OFF
        else -> Level.INFO
    }

    fun apply(configManager: ConfigManager, loggerName: String = APPLICATION_LOGGER) {
        apply(configManager.getString(logLevelKey, logLevelKey.defaultValue as String), loggerName)
    }

    fun apply(configuredLevel: String, loggerName: String = APPLICATION_LOGGER) {
        val logger = LoggerFactory.getLogger(loggerName) as? ch.qos.logback.classic.Logger ?: return
        logger.level = toLevel(configuredLevel)
    }
}
