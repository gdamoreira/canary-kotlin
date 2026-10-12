package org.opentibia.canary.game.scheduling

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

private val logger = KotlinLogging.logger {}

enum class DispatcherLane {
    ProtocolInput,
    WorldCommit,
    Maintenance
}

data class ScheduledTask(
    private val job: Job
) {
    fun cancel() {
        job.cancel()
    }
}

class GameDispatcher(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default.limitedParallelism(1))
) {
    private val eventChannel = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private val isRunning = AtomicBoolean(false)
    private var workerJob: Job? = null

    fun start() {
        if (isRunning.compareAndSet(false, true)) {
            workerJob = scope.launch {
                for (event in eventChannel) {
                    try {
                        event()
                    } catch (e: Exception) {
                        logger.error(e) { "Unhandled exception during event execution on GameDispatcher" }
                    }
                }
            }
        }
    }

    fun dispatch(lane: DispatcherLane = DispatcherLane.WorldCommit, task: suspend () -> Unit) {
        if (!isRunning.get()) return
        eventChannel.trySend(task)
    }

    fun schedule(delayMs: Long, lane: DispatcherLane = DispatcherLane.WorldCommit, task: suspend () -> Unit): ScheduledTask {
        val job = scope.launch {
            delay(delayMs)
            dispatch(lane, task)
        }
        return ScheduledTask(job)
    }

    fun shutdown() {
        if (isRunning.compareAndSet(true, false)) {
            eventChannel.close()
            workerJob?.cancel()
            scope.cancel()
        }
    }
}
