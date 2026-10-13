package org.opentibia.canary.bootstrap

import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.atomic.AtomicBoolean

private val logger = KotlinLogging.logger {}

/**
 * Last-resort uncaught-exception handler in the spirit of Canary's crash path
 * (`CanaryServer::badAllocationHandler`): log the fault and request a single emergency
 * drain so persistence and database release still run after a fatal thread error.
 */
class CrashHandler(
    private val onCrash: () -> Unit,
    private val installHandler: (Thread.UncaughtExceptionHandler) -> Unit = {
        Thread.setDefaultUncaughtExceptionHandler(it)
    }
) : Thread.UncaughtExceptionHandler {
    private val handled = AtomicBoolean(false)

    fun install() {
        installHandler(this)
    }

    override fun uncaughtException(thread: Thread, error: Throwable) {
        if (!handled.compareAndSet(false, true)) return
        logger.error(error) { "Uncaught exception on thread '${thread.name}'; starting emergency shutdown drain" }
        onCrash()
    }
}
