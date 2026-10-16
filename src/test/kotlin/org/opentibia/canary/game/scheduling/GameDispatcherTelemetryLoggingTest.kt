package org.opentibia.canary.game.scheduling

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Transcript tests for the periodic telemetry logging
 * (`Dispatcher::logQueueLatency`, src/game/scheduling/dispatcher.cpp:324-363
 * and `Dispatcher::logRuntimeTelemetry`, dispatcher.cpp:390-490): a lane whose
 * oldest ready task exceeds `DISPATCHER_QUEUE_LATENCY_LOG_THRESHOLD` (250 ms) is
 * logged at most once per `DISPATCHER_TELEMETRY_LOG_INTERVAL` (5 s), and the
 * runtime snapshot is emitted on the same interval, but only while
 * `queueLatencyLoggingEnabled` and the normal game state hold.
 */
class GameDispatcherTelemetryLoggingTest {

    private class LogCapture {
        private val root = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger
        private val application = LoggerFactory.getLogger("org.opentibia.canary") as Logger
        private val appender = ListAppender<ILoggingEvent>()
        private var previousLevel: Level? = null
        private var previousApplicationLevel: Level? = null

        fun start() {
            previousLevel = root.level
            root.level = Level.DEBUG
            previousApplicationLevel = application.level
            application.level = Level.DEBUG
            appender.start()
            root.addAppender(appender)
        }

        fun stop() {
            root.detachAppender(appender)
            appender.stop()
            previousLevel?.let { root.level = it }
            application.level = previousApplicationLevel
        }

        fun messages(prefix: String): List<String> =
            appender.list.map { it.formattedMessage }.filter { it.startsWith(prefix) }

        fun internalWorkMessages(work: String): List<String> =
            messages("Dispatcher internal telemetry:").filter { it.contains("work=$work") }
    }

    private class PinnedWorldCommitBatch(
        private val blockedRelease: CountDownLatch,
        private val completed: CompletableDeferred<Unit>,
    ) {
        suspend fun releaseAndAwait() {
            blockedRelease.countDown()
            withTimeout(2_000) { completed.await() }
        }
    }

    /**
     * Pins the worker on a blocking `Maintenance` task, then enqueues two
     * `WorldCommit` tasks and ages them on the injected clock before releasing
     * the pin, so the next serial slice observes a queue older than the log
     * threshold. The first task blocks until [PinnedWorldCommitBatch] releases
     * it, keeping the follow-up task queued while the test inspects the log.
     */
    private suspend fun pinAgedWorldCommitBatch(
        dispatcher: GameDispatcher,
        now: AtomicLong,
        ageMs: Long,
    ): PinnedWorldCommitBatch {
        val gate = CountDownLatch(1)
        val gateStarted = CountDownLatch(1)
        dispatcher.dispatch(DispatcherLane.Maintenance) {
            gateStarted.countDown()
            gate.await(2, TimeUnit.SECONDS)
        }
        assertThat(gateStarted.await(2, TimeUnit.SECONDS)).isTrue()

        now.addAndGet(ageMs)
        val blockedStarted = CountDownLatch(1)
        val blockedRelease = CountDownLatch(1)
        val completed = CompletableDeferred<Unit>()
        dispatcher.dispatch(DispatcherLane.WorldCommit, "aged-blocked") {
            blockedStarted.countDown()
            blockedRelease.await(2, TimeUnit.SECONDS)
        }
        dispatcher.dispatch(DispatcherLane.WorldCommit, "aged-follow-up") {
            completed.complete(Unit)
        }

        now.addAndGet(ageMs)
        gate.countDown()
        assertThat(blockedStarted.await(2, TimeUnit.SECONDS)).isTrue()
        return PinnedWorldCommitBatch(blockedRelease, completed)
    }

    private suspend fun awaitLogs(capture: LogCapture, prefix: String, expected: Int) {
        withTimeout(2_000) {
            while (capture.messages(prefix).size < expected) {
                delay(5)
            }
        }
    }

    @Test
    fun `logQueueLatency logs an aged lane and honors the five-second interval`() = runBlocking<Unit> {
        val now = AtomicLong(1_000)
        val dispatcher = GameDispatcher(clock = { now.get() })
        dispatcher.start()
        val capture = LogCapture()
        capture.start()
        try {
            dispatcher.setQueueLatencyLoggingEnabled(true)

            val first = pinAgedWorldCommitBatch(dispatcher, now, ageMs = 1_000)
            awaitLogs(capture, "Dispatcher queue latency:", expected = 1)
            val firstLog = capture.messages("Dispatcher queue latency:").single()
            assertThat(firstLog).contains("lane=WorldCommit")
            assertThat(firstLog).contains("queued=2")
            assertThat(firstLog).contains("oldest=1000 ms")
            assertThat(firstLog).contains("oldestContext=aged-blocked")
            first.releaseAndAwait()

            val second = pinAgedWorldCommitBatch(dispatcher, now, ageMs = 1_000)
            second.releaseAndAwait()
            delay(100)
            assertThat(capture.messages("Dispatcher queue latency:")).hasSize(1)

            now.addAndGet(5_000)
            val third = pinAgedWorldCommitBatch(dispatcher, now, ageMs = 1_000)
            awaitLogs(capture, "Dispatcher queue latency:", expected = 2)
            third.releaseAndAwait()
        } finally {
            capture.stop()
            dispatcher.shutdown()
        }
    }

    @Test
    fun `logQueueLatency stays silent while queue latency logging is disabled`() = runBlocking<Unit> {
        val now = AtomicLong(1_000)
        val dispatcher = GameDispatcher(clock = { now.get() })
        dispatcher.start()
        val capture = LogCapture()
        capture.start()
        try {
            val batch = pinAgedWorldCommitBatch(dispatcher, now, ageMs = 1_000)
            delay(100)
            assertThat(capture.messages("Dispatcher queue latency:")).isEmpty()
            batch.releaseAndAwait()
        } finally {
            capture.stop()
            dispatcher.shutdown()
        }
    }

    @Test
    fun `logQueueLatency stays silent outside the normal game state`() = runBlocking<Unit> {
        val now = AtomicLong(1_000)
        val dispatcher = GameDispatcher(clock = { now.get() }, isGameStateNormal = { false })
        dispatcher.start()
        val capture = LogCapture()
        capture.start()
        try {
            dispatcher.setQueueLatencyLoggingEnabled(true)
            val batch = pinAgedWorldCommitBatch(dispatcher, now, ageMs = 1_000)
            delay(100)
            assertThat(capture.messages("Dispatcher queue latency:")).isEmpty()
            batch.releaseAndAwait()
        } finally {
            capture.stop()
            dispatcher.shutdown()
        }
    }

    @Test
    fun `the worker loop emits internal work telemetry on the five-second cadence`() = runBlocking<Unit> {
        val now = AtomicLong(10_000)
        val dispatcher = GameDispatcher(clock = { now.get() })
        val capture = LogCapture()
        capture.start()
        dispatcher.start()
        try {
            dispatcher.setQueueLatencyLoggingEnabled(true)
            dispatcher.observeInternalWork(DispatcherInternalWork.MonsterPostThinkLateness, 1, 5_000, "Monster::postThink")

            withTimeout(3_000) {
                while (capture.internalWorkMessages("MonsterPostThinkLateness").isEmpty()) {
                    now.addAndGet(DISPATCHER_TELEMETRY_LOG_INTERVAL_MS)
                    dispatcher.wake()
                    delay(5)
                }
            }
            assertThat(capture.internalWorkMessages("MonsterPostThinkLateness")).hasSize(1)

            val baseline = capture.internalWorkMessages("MonsterPostThinkLateness").size
            dispatcher.observeInternalWork(DispatcherInternalWork.MonsterPostThinkLateness, 1, 5_000, "Monster::postThink")
            dispatcher.wake()
            delay(100)
            assertThat(capture.internalWorkMessages("MonsterPostThinkLateness")).hasSize(baseline)

            now.addAndGet(DISPATCHER_TELEMETRY_LOG_INTERVAL_MS)
            dispatcher.wake()
            withTimeout(2_000) {
                while (capture.internalWorkMessages("MonsterPostThinkLateness").size == baseline) {
                    delay(5)
                }
            }
            assertThat(capture.internalWorkMessages("MonsterPostThinkLateness")).hasSize(baseline + 1)
        } finally {
            capture.stop()
            dispatcher.shutdown()
        }
    }

    @Test
    fun `logRuntimeTelemetry forwards the monster compute snapshot`() = runBlocking<Unit> {
        val now = AtomicLong(10_000)
        val service = MonsterComputeService()
        service.start(MonsterComputeConfig(configuredThreads = 1, capacity = 16, hardwareConcurrency = 8))
        val dispatcher = GameDispatcher(clock = { now.get() }, monsterComputeService = service)
        val capture = LogCapture()
        capture.start()
        dispatcher.start()
        try {
            dispatcher.setQueueLatencyLoggingEnabled(true)
            withTimeout(3_000) {
                while (capture.messages("Monster compute telemetry:").isEmpty()) {
                    now.addAndGet(DISPATCHER_TELEMETRY_LOG_INTERVAL_MS)
                    dispatcher.wake()
                    delay(5)
                }
            }
            assertThat(capture.messages("Monster compute telemetry:").first()).contains("capacity=16")
        } finally {
            capture.stop()
            dispatcher.shutdown()
            service.shutdown()
        }
    }

    @Test
    fun `logRuntimeTelemetry is silent while disabled or outside the normal game state`() = runBlocking<Unit> {
        val capture = LogCapture()
        capture.start()
        val now = AtomicLong(10_000)
        val disabled = GameDispatcher(clock = { now.get() })
        val halted = GameDispatcher(clock = { now.get() }, isGameStateNormal = { false })
        disabled.start()
        halted.start()
        try {
            disabled.logRuntimeTelemetry()
            halted.setQueueLatencyLoggingEnabled(true)
            halted.observeInternalWork(DispatcherInternalWork.MonsterPostThinkLateness, 1, 5_000, "Monster::postThink")
            halted.logRuntimeTelemetry()
            delay(50)

            assertThat(capture.messages("Dispatcher telemetry:")).isEmpty()
            assertThat(capture.messages("Dispatcher scheduled telemetry:")).isEmpty()
            assertThat(capture.messages("Dispatcher internal telemetry:")).isEmpty()
            assertThat(capture.messages("Monster compute telemetry:")).isEmpty()
        } finally {
            capture.stop()
            disabled.shutdown()
            halted.shutdown()
        }
    }
}
