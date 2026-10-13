package org.opentibia.canary.lib.di

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.opentibia.canary.lib.logging.InMemoryLogger
import org.opentibia.canary.lib.logging.LogWithSpdLog
import org.opentibia.canary.lib.logging.Logger

class LibDiContainerTest {

    @AfterEach
    fun restoreContainer() {
        DI.setTestContainer(null)
    }

    @Test
    fun `default container provides a shared logger`() {
        val first = DI.get(Logger::class)
        val second = DI.get(Logger::class)

        assertThat(first).isInstanceOf(LogWithSpdLog::class.java)
        assertThat(second).isSameAs(first)
    }

    @Test
    fun `inject returns the container shared instance`() {
        assertThat(inject<Logger>()).isSameAs(DI.get(Logger::class))
        assertThat(inject<Logger>()).isSameAs(LogWithSpdLog.getInstance())
    }

    @Test
    fun `create returns a distinct instance`() {
        assertThat(DI.create(LogWithSpdLog::class)).isNotSameAs(DI.create(LogWithSpdLog::class))
        assertThat(DI.get(LogWithSpdLog::class)).isSameAs(DI.get(LogWithSpdLog::class))
    }

    @Test
    fun `test container overrides and restores the default container`() {
        val injector = Injector()
        InMemoryLogger.install(injector)

        DI.setTestContainer(injector)

        assertThat(DI.getTestContainer()).isSameAs(injector)
        assertThat(DI.get(Logger::class)).isInstanceOf(InMemoryLogger::class.java)
        assertThat(DI.get(Logger::class)).isSameAs(DI.get(Logger::class))

        DI.setTestContainer(null)

        assertThat(DI.get(Logger::class)).isInstanceOf(LogWithSpdLog::class.java)
    }

    @Test
    fun `unbound concrete types are constructor injected`() {
        val greeter = DI.create(Greeter::class)

        assertThat(greeter.logger).isSameAs(DI.get(Logger::class))
    }

    class Greeter(val logger: Logger)
}
