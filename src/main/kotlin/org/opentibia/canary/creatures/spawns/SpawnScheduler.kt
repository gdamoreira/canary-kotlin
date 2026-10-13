package org.opentibia.canary.creatures.spawns

/**
 * Port seam for `g_dispatcher().scheduleEvent` / `g_dispatcher().stopEvent`
 * (src/game/scheduling/dispatcher.hpp) used by the spawn engines. Canary
 * resolves the dispatcher through its DI container; the spawn engines receive
 * this abstraction instead so they stay free of global state.
 */
fun interface SpawnScheduler {
    fun schedule(delayMs: Long, task: () -> Unit): SpawnTask
}

/** Handle for a scheduled spawn task, mirroring the event id of `ScheduledEvent`. */
fun interface SpawnTask {
    fun cancel()
}
