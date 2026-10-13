package org.opentibia.canary.bootstrap

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SignalsTest {

    @Test
    fun `requests shutdown once across repeated signals`() {
        var drains = 0
        val signals = Signals(
            onShutdown = { drains++ },
            registerHook = { },
            removeHook = { }
        )

        signals.requestShutdown()
        signals.requestShutdown()

        assertThat(drains).isEqualTo(1)
    }

    @Test
    fun `installs the JVM shutdown hook on install`() {
        val registered = mutableListOf<Thread>()
        val signals = Signals(
            onShutdown = { },
            registerHook = { registered += it },
            removeHook = { }
        )

        signals.install()

        assertThat(registered).hasSize(1)
        assertThat(registered.single().name).isEqualTo("canary-signals-shutdown")
    }

    @Test
    fun `uninstall removes the registered hook`() {
        val registered = mutableListOf<Thread>()
        val removed = mutableListOf<Thread>()
        val signals = Signals(
            onShutdown = { },
            registerHook = { registered += it },
            removeHook = { removed += it }
        )

        signals.install()
        signals.uninstall()

        assertThat(removed).containsExactly(registered.single())
    }

    @Test
    fun `uninstall tolerates a JVM that is already shutting down`() {
        val signals = Signals(
            onShutdown = { },
            registerHook = { },
            removeHook = { throw IllegalStateException("Shutdown in progress") }
        )
        signals.install()

        signals.uninstall()
    }
}
