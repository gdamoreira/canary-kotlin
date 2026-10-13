package org.opentibia.canary.bootstrap

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CrashHandlerTest {

    @Test
    fun `requests a single emergency drain across repeated crashes`() {
        var drains = 0
        val handler = CrashHandler(
            onCrash = { drains++ },
            installHandler = { }
        )

        handler.uncaughtException(Thread("first"), RuntimeException("boom"))
        handler.uncaughtException(Thread("second"), RuntimeException("boom"))

        assertThat(drains).isEqualTo(1)
    }

    @Test
    fun `installs itself as the default uncaught exception handler`() {
        var installed: Thread.UncaughtExceptionHandler? = null
        val handler = CrashHandler(
            onCrash = { },
            installHandler = { installed = it }
        )

        handler.install()

        assertThat(installed).isSameAs(handler)
    }
}
