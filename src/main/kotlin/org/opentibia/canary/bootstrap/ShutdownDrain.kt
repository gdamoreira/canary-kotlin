package org.opentibia.canary.bootstrap

import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.atomic.AtomicBoolean

private val logger = KotlinLogging.logger {}

/**
 * Ordered, idempotent shutdown drain for the server bootstrap. Producers and consumers
 * are stopped before retained global ownership is released (AGENTS.md Static Ownership
 * Lifetime Safety): listeners -> live connections -> monster compute -> dispatcher ->
 * SaveManager flush -> database close, mirroring `CanaryServer::shutdown`
 * (src/canary_server.cpp:627-634) which releases `g_monsterComputeService()` before the
 * dispatcher and `Game::shutdown` (src/game/game.cpp:9497-9513) which stops the service
 * manager and then `ConnectionManager::closeAll()`.
 *
 * The first request runs every step exactly once; repeated SIGINT/SIGTERM notifications
 * or an uncaught-exception drain become no-ops. A failing step is logged and the drain
 * continues so a later resource is never leaked because an earlier one failed.
 */
class ShutdownDrain(
    private val stopListeners: () -> Unit,
    private val closeConnections: () -> Unit = {},
    private val shutdownMonsterCompute: () -> Unit = {},
    private val shutdownDispatcher: () -> Unit,
    private val flushSaveManager: () -> Unit,
    private val closeDatabase: () -> Unit
) {
    private val drained = AtomicBoolean(false)

    val isDrained: Boolean get() = drained.get()

    fun drain() {
        if (!drained.compareAndSet(false, true)) return

        logger.info {
            "Shutdown drain started: listeners -> connections -> monster compute -> dispatcher -> save manager -> database"
        }
        runStep("listeners", stopListeners)
        runStep("connections", closeConnections)
        runStep("monster compute", shutdownMonsterCompute)
        runStep("dispatcher", shutdownDispatcher)
        runStep("save manager", flushSaveManager)
        runStep("database", closeDatabase)
        logger.info { "Shutdown drain complete." }
    }

    private fun runStep(name: String, step: () -> Unit) {
        try {
            step()
        } catch (e: Exception) {
            logger.error(e) { "Shutdown drain step '$name' failed; continuing drain" }
        }
    }
}
