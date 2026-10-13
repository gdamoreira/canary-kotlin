package org.opentibia.canary.game.scheduling

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class MonsterComputeServiceTest {

    @Test
    fun `resolve worker count leaves small hosts inline`() {
        assertThat(MonsterComputeService.resolveWorkerCount(0, 2)).isZero()
        assertThat(MonsterComputeService.resolveWorkerCount(4, 1)).isZero()
    }

    @Test
    fun `resolve worker count honors the configured threads within bounds`() {
        assertThat(MonsterComputeService.resolveWorkerCount(0, 8)).isEqualTo(3)
        assertThat(MonsterComputeService.resolveWorkerCount(0, 3)).isEqualTo(1)
        assertThat(MonsterComputeService.resolveWorkerCount(2, 8)).isEqualTo(2)
        assertThat(MonsterComputeService.resolveWorkerCount(10, 8)).isEqualTo(4)
    }

    @Test
    fun `selection keeps a visible streak of three before background work`() {
        assertThat(MonsterComputeService.selectNextPriority(false, false, 0)).isNull()

        val first = MonsterComputeService.selectNextPriority(true, false, 0)
        assertThat(first).isEqualTo(MonsterComputePrioritySelection(MonsterComputePriority.Visible, 1))
        val capped = MonsterComputeService.selectNextPriority(true, false, 3)
        assertThat(capped).isEqualTo(MonsterComputePrioritySelection(MonsterComputePriority.Visible, 3))

        val visible = MonsterComputeService.selectNextPriority(true, true, 0)
        assertThat(visible).isEqualTo(MonsterComputePrioritySelection(MonsterComputePriority.Visible, 1))
        val background = MonsterComputeService.selectNextPriority(true, true, 3)
        assertThat(background).isEqualTo(MonsterComputePrioritySelection(MonsterComputePriority.Background, 0))
        val onlyBackground = MonsterComputeService.selectNextPriority(false, true, 0)
        assertThat(onlyBackground).isEqualTo(MonsterComputePrioritySelection(MonsterComputePriority.Background, 0))
    }

    @Test
    fun `inline mode runs work on the caller and queues its completion`() {
        val service = MonsterComputeService()
        service.start(MonsterComputeConfig(configuredThreads = 0, capacity = 16, hardwareConcurrency = 1))
        val workThread = AtomicReference<Thread>()
        val completed = AtomicBoolean(false)

        val submission = service.submit(
            MonsterComputePriority.Visible,
            { _, _ ->
                workThread.set(Thread.currentThread())
                val completion: MonsterComputeCompletion = { completed.set(true) }
                completion
            },
            "inline",
        )

        assertThat(submission.status).isEqualTo(MonsterComputeSubmitStatus.RanInline)
        assertThat(workThread.get()).isEqualTo(Thread.currentThread())
        assertThat(service.getCompletionCount()).isEqualTo(1)

        val drained = service.drainCompletions(4)
        assertThat(drained).isEqualTo(1)
        assertThat(completed.get()).isTrue()

        val stats = service.getStats()
        assertThat(stats.inlineMode).isTrue()
        assertThat(stats.running).isTrue()
        assertThat(stats.workerCount).isZero()
        assertThat(stats.accepted).isEqualTo(1)
        assertThat(stats.completed).isEqualTo(1)

        service.shutdown()
    }

    @Test
    fun `worker mode runs work off the submitting thread and hands completions back`() {
        val service = MonsterComputeService()
        service.start(MonsterComputeConfig(configuredThreads = 1, capacity = 16, hardwareConcurrency = 8))
        val workThread = AtomicReference<Thread>()
        val completionThread = AtomicReference<Thread>()
        val completionRan = AtomicBoolean(false)

        val submission = service.submit(
            MonsterComputePriority.Background,
            { _, _ ->
                workThread.set(Thread.currentThread())
                val completion: MonsterComputeCompletion = {
                    completionThread.set(Thread.currentThread())
                    completionRan.set(true)
                }
                completion
            },
            "off-thread",
        )

        assertThat(submission.status).isEqualTo(MonsterComputeSubmitStatus.Accepted)
        waitForCompletion(service, 1)

        val drained = service.drainCompletions(4)
        assertThat(drained).isEqualTo(1)
        assertThat(completionRan.get()).isTrue()
        assertThat(workThread.get()).isNotEqualTo(Thread.currentThread())
        assertThat(completionThread.get()).isEqualTo(Thread.currentThread())

        service.shutdown()
    }

    @Test
    fun `visible completions drain before background completions`() {
        val service = MonsterComputeService()
        service.start(MonsterComputeConfig(configuredThreads = 1, capacity = 16, hardwareConcurrency = 8))
        val order = java.util.Collections.synchronizedList(mutableListOf<String>())

        for (index in 0 until 3) {
            service.submit(MonsterComputePriority.Visible, { _, _ -> { order.add("visible-$index") } }, "visible-$index")
        }
        service.submit(MonsterComputePriority.Background, { _, _ -> { order.add("background") } }, "background")

        waitForCompletion(service, 4)
        service.drainCompletions(4)

        assertThat(order).hasSize(4)
        assertThat(order.take(3)).containsExactly("visible-0", "visible-1", "visible-2")
        assertThat(order[3]).isEqualTo("background")

        service.shutdown()
    }

    @Test
    fun `background admission stops at the visible reserve`() {
        val service = MonsterComputeService()
        service.start(MonsterComputeConfig(configuredThreads = 0, capacity = 4, hardwareConcurrency = 1))

        repeat(3) {
            val accepted = service.submit(MonsterComputePriority.Background, { _, _ -> null }, "background-$it")
            assertThat(accepted.status).isEqualTo(MonsterComputeSubmitStatus.RanInline)
        }
        val rejectedBackground = service.submit(MonsterComputePriority.Background, { _, _ -> null }, "background-4")
        assertThat(rejectedBackground.status).isEqualTo(MonsterComputeSubmitStatus.QueueFull)

        val visible = service.submit(MonsterComputePriority.Visible, { _, _ -> null }, "visible")
        assertThat(visible.status).isEqualTo(MonsterComputeSubmitStatus.RanInline)
        val rejectedVisible = service.submit(MonsterComputePriority.Visible, { _, _ -> null }, "visible-2")
        assertThat(rejectedVisible.status).isEqualTo(MonsterComputeSubmitStatus.QueueFull)

        val stats = service.getStats()
        assertThat(stats.capacity).isEqualTo(4)
        assertThat(stats.visibleReserve).isEqualTo(1)
        assertThat(stats.rejected).isEqualTo(2)

        service.shutdown()
    }

    @Test
    fun `queues the failure completion when work throws`() {
        val service = MonsterComputeService()
        service.start(MonsterComputeConfig(configuredThreads = 0, capacity = 4, hardwareConcurrency = 1))
        val failureRan = AtomicBoolean(false)

        val submission = service.submit(
            MonsterComputePriority.Visible,
            { _, _ -> throw IllegalStateException("boom") },
            "failure",
            failureCompletion = { failureRan.set(true) },
        )

        assertThat(submission.accepted).isTrue()
        service.drainCompletions(4)

        assertThat(failureRan.get()).isTrue()
        val stats = service.getStats()
        assertThat(stats.failed).isEqualTo(1)
        assertThat(stats.completed).isEqualTo(1)

        service.shutdown()
    }

    @Test
    fun `rejects invalid submissions`() {
        val service = MonsterComputeService()
        service.start(MonsterComputeConfig(configuredThreads = 0, capacity = 4, hardwareConcurrency = 1))

        val nullWork = service.submit(MonsterComputePriority.Visible, null, "context")
        assertThat(nullWork.status).isEqualTo(MonsterComputeSubmitStatus.Invalid)

        val emptyContext = service.submit(MonsterComputePriority.Visible, { _, _ -> null }, "")
        assertThat(emptyContext.status).isEqualTo(MonsterComputeSubmitStatus.Invalid)

        service.shutdown()
    }

    @Test
    fun `rejects submissions after shutdown`() {
        val service = MonsterComputeService()
        service.start(MonsterComputeConfig(configuredThreads = 0, capacity = 4, hardwareConcurrency = 1))
        service.shutdown()

        val submission = service.submit(MonsterComputePriority.Visible, { _, _ -> null }, "late")

        assertThat(submission.status).isEqualTo(MonsterComputeSubmitStatus.Stopping)
        assertThat(service.getStats().running).isFalse()
    }

    @Test
    fun `shutdown cancels queued work and stops workers`() {
        val service = MonsterComputeService()
        service.start(MonsterComputeConfig(configuredThreads = 1, capacity = 16, hardwareConcurrency = 8))
        val blockerStarted = CountDownLatch(1)

        val blocker = service.submit(
            MonsterComputePriority.Visible,
            { _, stop ->
                blockerStarted.countDown()
                while (!stop.isStopRequested()) {
                    Thread.sleep(1)
                }
                null
            },
            "blocker",
        )
        assertThat(blocker.status).isEqualTo(MonsterComputeSubmitStatus.Accepted)
        assertThat(blockerStarted.await(2, TimeUnit.SECONDS)).isTrue()

        val queued = service.submit(MonsterComputePriority.Background, { _, _ -> null }, "queued")
        assertThat(queued.status).isEqualTo(MonsterComputeSubmitStatus.Accepted)

        service.shutdown()

        val stats = service.getStats()
        assertThat(stats.running).isFalse()
        assertThat(stats.workerCount).isZero()
        assertThat(stats.canceled).isGreaterThanOrEqualTo(1)
        assertThat(service.getCompletionCount()).isZero()
    }

    @Test
    fun `start while running is a no-op and shutdown is idempotent`() {
        val service = MonsterComputeService()
        val config = MonsterComputeConfig(configuredThreads = 1, capacity = 16, hardwareConcurrency = 8)

        service.start(config)
        service.start(config)
        assertThat(service.getStats().workerCount).isEqualTo(1)

        service.shutdown()
        service.shutdown()
        assertThat(service.getStats().running).isFalse()
    }

    private fun waitForCompletion(service: MonsterComputeService, count: Int) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (service.getCompletionCount() < count && System.nanoTime() < deadline) {
            Thread.sleep(1)
        }
        assertThat(service.getCompletionCount()).isEqualTo(count)
    }
}
