package org.opentibia.canary.bootstrap

import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.atomic.AtomicBoolean

private val logger = KotlinLogging.logger {}

/**
 * Mirrors Canary's `Signals` (src/server/signals.cpp, SIGINT/SIGTERM handlers): the first
 * signal requests a graceful shutdown, every later signal is ignored. The JVM maps
 * SIGINT/SIGTERM onto shutdown hooks, so installing [hook] gives the same observable
 * behavior as `asio::signal_set` without platform-specific signal APIs.
 */
class Signals(
    private val onShutdown: () -> Unit,
    private val registerHook: (Thread) -> Unit = { Runtime.getRuntime().addShutdownHook(it) },
    private val removeHook: (Thread) -> Unit = { Runtime.getRuntime().removeShutdownHook(it) }
) {
    private val requested = AtomicBoolean(false)
    private val hook = Thread({ requestShutdown() }, "canary-signals-shutdown")

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
}
