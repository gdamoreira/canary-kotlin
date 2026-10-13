package org.opentibia.canary.game.scheduling

import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicLongArray
import kotlin.math.ceil

/**
 * `dispatcher::telemetry::LATENCY_BUCKET_UPPER_BOUNDS_US`
 * (src/game/scheduling/dispatcher_telemetry.hpp:29-48).
 */
val LATENCY_BUCKET_UPPER_BOUNDS_US: LongArray = longArrayOf(
    50,
    100,
    250,
    500,
    1000,
    2000,
    5000,
    10000,
    20000,
    50000,
    100000,
    250000,
    500000,
    1000000,
    2000000,
    5000000,
    10000000,
    30000000,
)

/** `dispatcher::telemetry::LatencySnapshot` (src/game/scheduling/dispatcher_telemetry.hpp:50-86). */
class LatencySnapshot(
    val buckets: List<Long>,
    val samples: Long,
    val totalUs: Long,
    val maxUs: Long,
) {
    val empty: Boolean get() = samples == 0L

    fun percentile(percentileValue: Double): Long {
        if (empty) {
            return 0
        }

        val normalized = percentileValue.coerceIn(0.0, 1.0)
        val rank = maxOf(1L, ceil(normalized * samples.toDouble()).toLong())
        var cumulative = 0L
        for (index in buckets.indices) {
            cumulative += buckets[index]
            if (cumulative < rank) {
                continue
            }

            return if (index < LATENCY_BUCKET_UPPER_BOUNDS_US.size) {
                LATENCY_BUCKET_UPPER_BOUNDS_US[index]
            } else {
                maxUs
            }
        }

        return maxUs
    }

    fun mean(): Long = if (empty) 0 else totalUs / samples
}

/** `dispatcher::telemetry::ConcurrentLatencyHistogram` (src/game/scheduling/dispatcher_telemetry.hpp:88-120). */
class ConcurrentLatencyHistogram {
    private val buckets = AtomicLongArray(LATENCY_BUCKET_UPPER_BOUNDS_US.size + 1)
    private val totalUs = AtomicLong(0)
    private val maxUs = AtomicLong(0)

    fun observe(durationUs: Long) {
        val value = maxOf(0L, durationUs)
        buckets.incrementAndGet(bucketIndex(value))
        totalUs.addAndGet(value)

        var currentMax = maxUs.get()
        while (value > currentMax && !maxUs.compareAndSet(currentMax, value)) {
            currentMax = maxUs.get()
        }
    }

    fun snapshotAndReset(): LatencySnapshot {
        val snapshotBuckets = MutableList(buckets.length()) { 0L }
        var samples = 0L
        for (index in 0 until buckets.length()) {
            val value = buckets.getAndSet(index, 0)
            snapshotBuckets[index] = value
            samples += value
        }
        return LatencySnapshot(snapshotBuckets, samples, totalUs.getAndSet(0), maxUs.getAndSet(0))
    }

    fun reset(): Unit {
        snapshotAndReset()
    }

    private fun bucketIndex(value: Long): Int {
        for (index in LATENCY_BUCKET_UPPER_BOUNDS_US.indices) {
            if (value <= LATENCY_BUCKET_UPPER_BOUNDS_US[index]) {
                return index
            }
        }
        return LATENCY_BUCKET_UPPER_BOUNDS_US.size
    }
}

/** `dispatcher::telemetry::TimedWorkSnapshot` (src/game/scheduling/dispatcher_telemetry.hpp:122-131). */
class TimedWorkSnapshot(
    val latency: LatencySnapshot,
    val workUnits: Long = 0,
    val longestDurationUs: Long = 0,
    val longestContext: String = "",
) {
    val empty: Boolean get() = latency.empty
}

/** `dispatcher::telemetry::ConcurrentTimedWork` (src/game/scheduling/dispatcher_telemetry.hpp:133-189). */
class ConcurrentTimedWork {
    private val latency = ConcurrentLatencyHistogram()
    private val workUnits = AtomicLong(0)
    private val longestContextDurationUs = AtomicLong(0)
    private val longestContextLock = Any()
    private var longestContextDuration = 0L
    private var longestContext = ""

    fun observe(durationUs: Long, units: Long = 1, context: String = "") {
        latency.observe(durationUs)
        workUnits.addAndGet(units)

        if (context.isEmpty()) {
            return
        }

        val value = maxOf(0L, durationUs)
        var currentMax = longestContextDurationUs.get()
        while (value > currentMax) {
            if (!longestContextDurationUs.compareAndSet(currentMax, value)) {
                currentMax = longestContextDurationUs.get()
                continue
            }

            synchronized(longestContextLock) {
                if (value >= longestContextDuration) {
                    longestContext = context
                    longestContextDuration = value
                }
            }
            break
        }
    }

    fun snapshotAndReset(): TimedWorkSnapshot {
        val latencySnapshot = latency.snapshotAndReset()
        val units = workUnits.getAndSet(0)
        longestContextDurationUs.set(0)
        val context: String
        val duration: Long
        synchronized(longestContextLock) {
            context = longestContext
            duration = longestContextDuration
            longestContext = ""
            longestContextDuration = 0
        }
        return TimedWorkSnapshot(latencySnapshot, units, duration, context)
    }

    fun reset(): Unit {
        snapshotAndReset()
    }
}
