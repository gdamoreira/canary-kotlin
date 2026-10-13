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

    @Test
    fun `sigint and sigterm request a single shutdown`() {
        var drains = 0
        val signals = Signals(onShutdown = { drains++ }, registerHook = { }, removeHook = { })

        signals.dispatchSignalHandler(Signal.Sigint)
        signals.dispatchSignalHandler(Signal.Sigterm)

        assertThat(drains).isEqualTo(1)
        assertThat(signals.isShuttingDown).isTrue()
    }

    @Test
    fun `sigbreak shuts down like the windows console signal`() {
        var drains = 0
        val signals = Signals(onShutdown = { drains++ }, registerHook = { }, removeHook = { })

        signals.dispatchSignalHandler(Signal.Sigbreak)

        assertThat(drains).isEqualTo(1)
    }

    @Test
    fun `sighup reloads and sigusr1 saves without shutting down`() {
        var drains = 0
        var reloads = 0
        var saves = 0
        val signals = Signals(
            onShutdown = { drains++ },
            onReload = { reloads++ },
            onSave = { saves++ },
            registerHook = { },
            removeHook = { }
        )

        signals.dispatchSignalHandler(Signal.Sighup)
        signals.dispatchSignalHandler(Signal.Sigusr1)

        assertThat(reloads).isEqualTo(1)
        assertThat(saves).isEqualTo(1)
        assertThat(drains).isEqualTo(0)
        assertThat(signals.isShuttingDown).isFalse()
    }

    @Test
    fun `signals are drained after shutdown starts`() {
        var drains = 0
        var reloads = 0
        var saves = 0
        val signals = Signals(
            onShutdown = { drains++ },
            onReload = { reloads++ },
            onSave = { saves++ },
            registerHook = { },
            removeHook = { }
        )

        signals.dispatchSignalHandler(Signal.Sigint)
        signals.dispatchSignalHandler(Signal.Sigterm)
        signals.dispatchSignalHandler(Signal.Sighup)
        signals.dispatchSignalHandler(Signal.Sigusr1)
        signals.dispatchSignalHandler(Signal.Sigbreak)

        assertThat(drains).isEqualTo(1)
        assertThat(reloads).isEqualTo(0)
        assertThat(saves).isEqualTo(0)
    }

    @Test
    fun `requestShutdown drains later signal dispatches`() {
        var drains = 0
        var reloads = 0
        val signals = Signals(
            onShutdown = { drains++ },
            onReload = { reloads++ },
            registerHook = { },
            removeHook = { }
        )

        signals.requestShutdown()
        signals.dispatchSignalHandler(Signal.Sighup)

        assertThat(drains).isEqualTo(1)
        assertThat(reloads).isEqualTo(0)
        assertThat(signals.isShuttingDown).isTrue()
    }
}
