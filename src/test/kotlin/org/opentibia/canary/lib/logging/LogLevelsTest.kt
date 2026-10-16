package org.opentibia.canary.lib.logging

import ch.qos.logback.classic.Level
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory

class LogLevelsTest {
    @Test
    fun `toLevel maps the canary level names`() {
        assertThat(LogLevels.toLevel("trace")).isEqualTo(Level.TRACE)
        assertThat(LogLevels.toLevel("debug")).isEqualTo(Level.DEBUG)
        assertThat(LogLevels.toLevel("info")).isEqualTo(Level.INFO)
        assertThat(LogLevels.toLevel("warn")).isEqualTo(Level.WARN)
        assertThat(LogLevels.toLevel("warning")).isEqualTo(Level.WARN)
        assertThat(LogLevels.toLevel("error")).isEqualTo(Level.ERROR)
        assertThat(LogLevels.toLevel("critical")).isEqualTo(Level.ERROR)
        assertThat(LogLevels.toLevel("off")).isEqualTo(Level.OFF)
        assertThat(LogLevels.toLevel("unexpected")).isEqualTo(Level.INFO)
    }

    @Test
    fun `apply sets the configured level on the named logger`() {
        LogLevels.apply("debug", "org.opentibia.canary.test.loglevels")

        val logger = LoggerFactory.getLogger("org.opentibia.canary.test.loglevels")
        assertThat((logger as ch.qos.logback.classic.Logger).level).isEqualTo(Level.DEBUG)
        logger.level = null
    }
}
