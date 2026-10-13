package org.opentibia.canary.game.scheduling

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DispatcherPolicyTest {

    private class Entry(
        override val meta: TaskMeta,
        override val context: String,
        override val enqueuedAt: Long,
    ) : DispatcherQueueEntry

    private fun entry(
        lane: DispatcherLane,
        context: String,
        readyAt: Long,
        enqueuedAt: Long = 0,
        producerToken: Long = 0,
    ): Entry = Entry(TaskMeta(lane = lane, readyAt = readyAt, producerToken = producerToken), context, enqueuedAt)

    @Test
    fun `inspect queue reports the oldest ready age and its context`() {
        val tasks = listOf(
            entry(DispatcherLane.WorldCommit, "a", readyAt = 100, enqueuedAt = 1),
            entry(DispatcherLane.WorldCommit, "b", readyAt = 50, enqueuedAt = 1),
            entry(DispatcherLane.WorldCommit, "c", readyAt = 20, enqueuedAt = 1),
        )

        val snapshot = DispatcherPolicy.inspectQueueAt(tasks, currentTime = 200)

        assertThat(snapshot.queued).isEqualTo(3)
        assertThat(snapshot.oldestReadyAgeUs).isEqualTo(180)
        assertThat(snapshot.oldestContext).isEqualTo("c")
    }

    @Test
    fun `inspect queue skips entries enqueued before the logging window`() {
        val tasks = listOf(
            entry(DispatcherLane.WorldCommit, "before", readyAt = 0, enqueuedAt = 10),
            entry(DispatcherLane.WorldCommit, "after", readyAt = 150, enqueuedAt = 20),
        )

        val snapshot = DispatcherPolicy.inspectQueueAt(tasks, currentTime = 200, notBefore = 20)

        assertThat(snapshot.queued).isEqualTo(1)
        assertThat(snapshot.oldestReadyAgeUs).isEqualTo(50)
        assertThat(snapshot.oldestContext).isEqualTo("after")
    }

    @Test
    fun `inspect player visible queue ignores non visible lanes`() {
        val tasks = listOf(
            entry(DispatcherLane.WorldCommit, "visible", readyAt = 100),
            entry(DispatcherLane.Maintenance, "background", readyAt = 0),
        )

        val snapshot = DispatcherPolicy.inspectPlayerVisibleQueueAt(tasks, currentTime = 200)

        assertThat(snapshot.queued).isEqualTo(1)
        assertThat(snapshot.oldestContext).isEqualTo("visible")
    }

    @Test
    fun `elapsed never reports negative durations`() {
        assertThat(DispatcherPolicy.elapsed(200, 100)).isZero()
        assertThat(DispatcherPolicy.elapsed(100, 200)).isEqualTo(100)
        assertThat(DispatcherPolicy.elapsed(100, 100)).isZero()
    }

    @Test
    fun `classify admission distinguishes saturation from shutdown`() {
        assertThat(DispatcherPolicy.classifyAdmission(true, true)).isEqualTo(DispatcherAdmissionResult.Accepted)
        assertThat(DispatcherPolicy.classifyAdmission(false, true)).isEqualTo(DispatcherAdmissionResult.ShuttingDown)
        assertThat(DispatcherPolicy.classifyAdmission(false, false)).isEqualTo(DispatcherAdmissionResult.Saturated)
    }

    @Test
    fun `select task count is capped by the budget`() {
        assertThat(DispatcherPolicy.selectTaskCount(10, 4)).isEqualTo(4)
        assertThat(DispatcherPolicy.selectTaskCount(2, 4)).isEqualTo(2)
    }

    @Test
    fun `producer fair index continues after the last served token`() {
        val tasks = listOf(
            entry(DispatcherLane.PlayerWalk, "a", readyAt = 0, producerToken = 5),
            entry(DispatcherLane.PlayerWalk, "b", readyAt = 0, producerToken = 1),
            entry(DispatcherLane.PlayerWalk, "c", readyAt = 0, producerToken = 9),
            entry(DispatcherLane.PlayerWalk, "d", readyAt = 0, producerToken = 7),
        )

        assertThat(DispatcherPolicy.selectProducerFairIndex(tasks, lastProducerToken = 5)).isEqualTo(3)
        assertThat(DispatcherPolicy.selectProducerFairIndex(tasks, lastProducerToken = 9)).isEqualTo(1)
        assertThat(DispatcherPolicy.selectProducerFairIndex(emptyList(), lastProducerToken = 9)).isZero()
    }

    @Test
    fun `requeue unprocessed restores the unconsumed slice in order`() {
        val queue = ArrayDeque<String>()
        val slice = listOf("a", "b", "c", "d")

        val unprocessed = DispatcherPolicy.requeueUnprocessed(queue, slice, consumed = 2)

        assertThat(unprocessed).isEqualTo(2)
        assertThat(queue.toList()).containsExactly("c", "d")
        assertThat(DispatcherPolicy.requeueUnprocessed(queue, slice, consumed = 4)).isZero()
    }

    @Test
    fun `admission counter reserves and releases up to capacity`() {
        val counter = DispatcherAdmissionCounter()

        assertThat(counter.tryReserve(2)).isTrue()
        assertThat(counter.tryReserve(2)).isTrue()
        assertThat(counter.tryReserve(2)).isFalse()
        assertThat(counter.size).isEqualTo(2)

        assertThat(counter.release()).isTrue()
        assertThat(counter.size).isEqualTo(1)
        assertThat(counter.tryReserve(2)).isTrue()
    }

    @Test
    fun `admission counter cannot release below zero`() {
        val counter = DispatcherAdmissionCounter()

        assertThat(counter.release()).isFalse()
        assertThat(counter.size).isZero()
    }

    @Test
    fun `coalesced task state accepts only one pending enqueue`() {
        var now = 1_000L
        val state = CoalescedTaskState { now }

        assertThat(state.tryEnqueue()).isTrue()
        assertThat(state.isPending).isTrue()
        assertThat(state.getOldestReadyAt()).isEqualTo(1_000)

        now = 2_000
        assertThat(state.tryEnqueue()).isFalse()
        assertThat(state.getOldestReadyAt()).isEqualTo(1_000)

        assertThat(state.consume()).isEqualTo(1_000)
        assertThat(state.isPending).isFalse()
        assertThat(state.consume()).isNull()
        assertThat(state.tryEnqueue(5_000)).isTrue()
        assertThat(state.getOldestReadyAt()).isEqualTo(5_000)
    }

    @Test
    fun `policy deadline and elapsed use the injected clock`() {
        var now = 1_000L
        val policy = DispatcherPolicy { now }

        assertThat(policy.now()).isEqualTo(1_000)
        assertThat(policy.elapsedSince(500)).isEqualTo(500)
        assertThat(policy.deadlineReached(2_000)).isFalse()

        now = 2_000
        assertThat(policy.deadlineReached(2_000)).isTrue()
    }

    @Test
    fun `backlog warning requires sustained backlog and respects the repeat interval`() {
        val policy = DispatcherBacklogWarningPolicy()
        val backlog = DispatcherQueueSnapshot(queued = 3, oldestReadyAgeUs = 1_000_000, oldestContext = "x")

        repeat(3) {
            assertThat(policy.update(true, backlog, now = it.toLong(), warningThresholdUs = 100_000)).isFalse()
        }
        assertThat(policy.update(true, backlog, now = 4, warningThresholdUs = 100_000)).isTrue()
        assertThat(policy.update(true, backlog, now = 5, warningThresholdUs = 100_000)).isFalse()
        assertThat(policy.update(true, backlog, now = 30_004, warningThresholdUs = 100_000)).isTrue()
    }

    @Test
    fun `backlog warning resets when the queue drains`() {
        val policy = DispatcherBacklogWarningPolicy()
        val backlog = DispatcherQueueSnapshot(queued = 3, oldestReadyAgeUs = 1_000_000, oldestContext = "x")

        repeat(4) { policy.update(true, backlog, now = it.toLong(), warningThresholdUs = 100_000) }
        assertThat(policy.update(true, DispatcherQueueSnapshot(), now = 10, warningThresholdUs = 100_000)).isFalse()

        repeat(3) {
            assertThat(policy.update(true, backlog, now = (11 + it).toLong(), warningThresholdUs = 100_000)).isFalse()
        }
        assertThat(policy.update(true, backlog, now = 20, warningThresholdUs = 100_000)).isTrue()
    }
}
