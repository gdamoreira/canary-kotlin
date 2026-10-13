package org.opentibia.canary.game.scheduling

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DispatcherTelemetryTest {

    @Test
    fun `histogram buckets observations by upper bound`() {
        val histogram = ConcurrentLatencyHistogram()
        histogram.observe(0)
        histogram.observe(50)
        histogram.observe(51)
        histogram.observe(30_000_001)

        val snapshot = histogram.snapshotAndReset()

        assertThat(snapshot.samples).isEqualTo(4)
        assertThat(snapshot.buckets[0]).isEqualTo(2)
        assertThat(snapshot.buckets[1]).isEqualTo(1)
        assertThat(snapshot.buckets.last()).isEqualTo(1)
        assertThat(snapshot.totalUs).isEqualTo(30_000_102)
        assertThat(snapshot.maxUs).isEqualTo(30_000_001)
        assertThat(snapshot.mean()).isEqualTo(7_500_025)
    }

    @Test
    fun `percentile returns the bucket upper bound covering the rank`() {
        val histogram = ConcurrentLatencyHistogram()
        repeat(100) { histogram.observe(10) }
        histogram.observe(120)

        val snapshot = histogram.snapshotAndReset()

        assertThat(snapshot.percentile(0.50)).isEqualTo(50)
        assertThat(snapshot.percentile(0.99)).isEqualTo(50)
        assertThat(snapshot.percentile(1.0)).isEqualTo(250)
    }

    @Test
    fun `snapshot and reset clear accumulated samples`() {
        val histogram = ConcurrentLatencyHistogram()
        histogram.observe(100)

        val first = histogram.snapshotAndReset()
        val second = histogram.snapshotAndReset()

        assertThat(first.samples).isEqualTo(1)
        assertThat(second.empty).isTrue()
        assertThat(second.percentile(0.99)).isZero()
        assertThat(second.mean()).isZero()
    }

    @Test
    fun `timed work tracks units and the longest context`() {
        val work = ConcurrentTimedWork()
        work.observe(100, 2, "a")
        work.observe(500, 3, "b")
        work.observe(50, 1, "c")
        work.observe(500, 1, "")

        val snapshot = work.snapshotAndReset()

        assertThat(snapshot.workUnits).isEqualTo(7)
        assertThat(snapshot.longestDurationUs).isEqualTo(500)
        assertThat(snapshot.longestContext).isEqualTo("b")
        assertThat(snapshot.empty).isFalse()
    }

    @Test
    fun `timed work snapshot resets the longest context`() {
        val work = ConcurrentTimedWork()
        work.observe(500, 1, "a")

        work.snapshotAndReset()
        val reset = work.snapshotAndReset()

        assertThat(reset.empty).isTrue()
        assertThat(reset.longestContext).isEmpty()
        assertThat(reset.workUnits).isZero()
    }

    @Test
    fun `histogram records concurrent observations`() {
        val histogram = ConcurrentLatencyHistogram()
        val threads = (1..8).map {
            Thread {
                repeat(1_000) { histogram.observe(1_000) }
            }
        }

        threads.forEach { it.start() }
        threads.forEach { it.join() }

        val snapshot = histogram.snapshotAndReset()

        assertThat(snapshot.samples).isEqualTo(8_000)
        assertThat(snapshot.percentile(0.99)).isEqualTo(1_000)
    }
}
