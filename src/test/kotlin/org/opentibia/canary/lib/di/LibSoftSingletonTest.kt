package org.opentibia.canary.lib.di

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.opentibia.canary.lib.logging.InMemoryLogger
import org.opentibia.canary.lib.logging.Logger

/**
 * Transcript of Canary's `SoftSingletonTest`
 * (tests/unit/lib/di/soft_singleton_test.cpp:14-83).
 */
class LibSoftSingletonTest {

    @AfterEach
    fun restoreContainer() {
        DI.setTestContainer(null)
    }

    @Test
    fun `warns about multiple instances`() {
        val logger = installTestLogger()

        val softSingleton = SoftSingleton("Test")
        val guard = SoftSingletonGuard(softSingleton)
        val guard2 = SoftSingletonGuard(softSingleton)
        softSingleton.increment()

        assertThat(logger.logCount()).isEqualTo(2)
        assertThat(logger.getLogEntry(0)).isEqualTo(
            "warning" to "2 instances created for Test. This is a soft singleton, you probably want to use g_test instead.",
        )
        assertThat(logger.getLogEntry(1)).isEqualTo(
            "warning" to "3 instances created for Test. This is a soft singleton, you probably want to use g_test instead.",
        )

        guard.close()
        guard2.close()
    }

    @Test
    fun `does not warn if instance released`() {
        val logger = installTestLogger()

        val softSingleton = SoftSingleton("Test")
        SoftSingletonGuard(softSingleton).close()
        SoftSingletonGuard(softSingleton).close()

        softSingleton.increment()
        softSingleton.decrement()
        softSingleton.increment()

        assertThat(logger.logCount()).isZero()
    }

    private fun installTestLogger(): InMemoryLogger {
        val injector = Injector()
        InMemoryLogger.install(injector)
        DI.setTestContainer(injector)
        return DI.get(Logger::class) as InMemoryLogger
    }
}
