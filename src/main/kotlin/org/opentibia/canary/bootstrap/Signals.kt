package org.opentibia.canary.bootstrap

import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.atomic.AtomicBoolean

private val logger = KotlinLogging.logger {}

/**
 * Canary's signal set (src/server/signals.hpp:12-26): SIGINT/SIGTERM/SIGBREAK
 * shut the server down, SIGHUP reloads config/data and SIGUSR1 saves the game
 * state. The JVM maps SIGINT/SIGTERM onto shutdown hooks, so the reload/save
 * notifications are delivered through [Signals.dispatchSignalHandler] by the
 * composition root.
 */
enum class Signal(val logName: String) {
    Sigint("SIGINT"),
    Sigterm("SIGTERM"),
    Sighup("SIGHUP"),
    Sigusr1("SIGUSR1"),
    Sigbreak("SIGBREAK"),
}

/**
 * Kotlin port of Canary's `Signals` (src/server/signals.cpp): the first shutdown
 * signal requests a graceful drain, every later signal is ignored ("drained"),
 * exactly like `dispatchSignalHandler` returning early once the game state is
 * `GAME_STATE_SHUTDOWN`. Installing [hook] gives the same observable behavior as
 * `asio::signal_set` without platform-specific signal APIs.
 */
class Signals(
    private val onShutdown: () -> Unit,
    private val onReload: () -> Unit = {},
    private val onSave: () -> Unit = {},
    private val registerHook: (Thread) -> Unit = { Runtime.getRuntime().addShutdownHook(it) },
    private val removeHook: (Thread) -> Unit = { Runtime.getRuntime().removeShutdownHook(it) },
) {
    private val requested = AtomicBoolean(false)
    private val hook = Thread({ requestShutdown() }, "canary-signals-shutdown")

    val isShuttingDown: Boolean get() = requested.get()

    fun install() {
        registerHook(hook)
    }

    fun uninstall() {
        try {
            removeHook(hook)
        } catch (_: IllegalStateException) {
            // JVM shutdown already in progress; the hook has fired or is about to.
        }
    }

    fun requestShutdown() {
        if (requested.compareAndSet(false, true)) {
            logger.info { "Shutdown signal received; draining services..." }
            onShutdown()
        }
    }

    /** `Signals::dispatchSignalHandler` (signals.cpp:62-92), including the shutdown drain guard. */
    fun dispatchSignalHandler(signal: Signal) {
        if (requested.get()) {
            return
        }

        when (signal) {
            Signal.Sigint, Signal.Sigterm, Signal.Sigbreak -> {
                logger.info { "${signal.logName} received, shutting game server down..." }
                requestShutdown()
            }

            Signal.Sighup -> {
                logger.info { "SIGHUP received, reloading config files..." }
                onReload()
            }

            Signal.Sigusr1 -> {
                logger.info { "SIGUSR1 received, saving the game state..." }
                onSave()
            }
        }
    }
}
