package org.opentibia.canary.lib.logging

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path

class LoggerTest {

    @Test
    fun `set level maps long names, aliases and unknown values`() {
        val logger = LogWithSpdLog()

        logger.setLevel("warning")
        assertThat(logger.getLevel()).isEqualTo("warning")

        logger.setLevel("warn")
        assertThat(logger.getLevel()).isEqualTo("warning")

        logger.setLevel("err")
        assertThat(logger.getLevel()).isEqualTo("error")

        logger.setLevel("critical")
        assertThat(logger.getLevel()).isEqualTo("critical")

        logger.setLevel("bogus")
        assertThat(logger.getLevel()).isEqualTo("off")

        logger.setLevel("info")
        assertThat(logger.getLevel()).isEqualTo("info")
    }

    @Test
    fun `filters messages below the configured level`() {
        val logger = LogWithSpdLog()

        val output = captureStandardOut {
            logger.setLevel("error")
            logger.info("hidden info")
            logger.warn("hidden warn")
            logger.error("visible error")
            logger.critical("visible critical")
        }

        assertThat(output).doesNotContain("hidden info")
        assertThat(output).doesNotContain("hidden warn")
        assertThat(output).contains("[error] visible error")
        assertThat(output).contains("[critical] visible critical")
    }

    @Test
    fun `formats console lines with the spdlog pattern`() {
        val logger = LogWithSpdLog()
        logger.setLevel("info")

        val output = captureStandardOut { logger.info("hello") }

        assertThat(output.lines().first()).matches("""\[\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3}] \[info] hello\s*""")
    }

    @Test
    fun `constructing a logger resets the global level to info`() {
        val logger = LogWithSpdLog()
        logger.setLevel("off")

        LogWithSpdLog()

        assertThat(logger.getLevel()).isEqualTo("info")
    }

    @Test
    fun `debug and trace are no-ops without the debug log build gate`() {
        val logger = InMemoryLogger()
        logger.debug("debug message")
        logger.trace("trace message")

        assertThat(logger.logCount()).isZero()
    }

    @Test
    fun `log profile sanitizes the file name and reuses the sanitized name`(@TempDir directory: Path) {
        val logger = InMemoryLogger(directory)

        logger.logProfile("a:b\\c", 12.5)
        logger.logProfile("a:b\\c", 3.0)

        val profile = directory.resolve("profile_log-a_b_c.txt")
        assertThat(profile).exists()
        val content = Files.readString(profile)
        assertThat(content).contains("Function a:b\\c executed in 12.5 ms")
        assertThat(content).contains("Function a_b_c executed in 3.0 ms")
    }

    @Test
    fun `log profile reports file initialization failures`(@TempDir directory: Path) {
        val blocker = directory.resolve("blocker")
        Files.writeString(blocker, "not a directory")

        val logger = InMemoryLogger(blocker)
        logger.logProfile("name", 1.0)

        assertThat(logger.logs).hasSize(1)
        assertThat(logger.logs.single().level).isEqualTo("error")
        assertThat(logger.logs.single().message).startsWith("Profile log initialization failed:")
    }

    @Test
    fun `profile times the function and logs the info line`(@TempDir directory: Path) {
        val logger = InMemoryLogger(directory)

        val result = logger.profile("quickTask") { 42 }

        assertThat(result).isEqualTo(42)
        assertThat(logger.logs).hasSize(1)
        assertThat(logger.logs.single().level).isEqualTo("info")
        assertThat(logger.logs.single().message).startsWith("Function quickTask executed in ")
        assertThat(logger.logs.single().message).endsWith(" ms")
        assertThat(Files.readString(directory.resolve("profile_log-quickTask.txt")))
            .contains("Function quickTask executed in ")
    }

    private fun captureStandardOut(block: () -> Unit): String {
        val original = System.out
        val buffer = ByteArrayOutputStream()
        try {
            System.setOut(PrintStream(buffer, true))
            block()
        } finally {
            System.setOut(original)
        }
        return buffer.toString()
    }
}
