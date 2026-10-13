package org.opentibia.canary.monitoring

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.math.floor

/** `metrics::latencyNames` (src/lib/metrics/metrics.cpp:87-93). */
val LATENCY_HISTOGRAM_NAMES: List<String> = listOf(
    "method_latency",
    "lua_latency",
    "query_latency",
    "task_latency",
    "lock_latency",
)

/**
 * `HistogramAggregationConfig::boundaries_` (src/lib/metrics/metrics.cpp:54-74),
 * in microseconds. Transcribed verbatim, including the final `+Inf` catch-all.
 */
val LATENCY_BUCKET_BOUNDARIES_US: List<Double> = listOf(
    // Ultra-fine granularity below 10µs
    0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0,
    12.0, 14.0, 16.0, 18.0, 20.0, 22.0, 24.0, 26.0, 28.0, 30.0,
    35.0, 40.0, 45.0, 50.0, 55.0, 60.0, 65.0, 70.0, 75.0, 80.0,
    85.0, 90.0, 95.0, 100.0,
    // Fine granularity between 100µs and 500µs
    120.0, 140.0, 160.0, 180.0, 200.0, 225.0, 250.0, 275.0, 300.0, 325.0, 350.0, 375.0, 400.0, 425.0, 450.0, 475.0, 500.0,
    // Moderate granularity from 500µs to 1ms (1000µs)
    550.0, 600.0, 650.0, 700.0, 750.0, 800.0, 850.0, 900.0, 950.0, 1000.0,
    // Coarser granularity for higher latencies (in microseconds)
    1100.0, 1200.0, 1300.0, 1400.0, 1500.0, 2000.0, 2500.0, 3000.0, 3500.0, 4000.0, 4500.0, 5000.0, 10000.0,
    // Very coarse granularity for latencies in milliseconds
    20000.0, 30000.0, 40000.0, 50000.0, 60000.0, 70000.0, 80000.0, 90000.0, 100000.0,
    200000.0, 300000.0, 400000.0, 500000.0, 600000.0, 700000.0, 800000.0, 900000.0, 1000000.0,
    // Even coarser granularity for latencies in seconds
    2000000.0, 3000000.0, 4000000.0, 5000000.0, 6000000.0, 7000000.0, 8000000.0, 9000000.0, 10000000.0,
    20000000.0, 30000000.0, 40000000.0, 50000000.0, 60000000.0, 70000000.0, 80000000.0, 90000000.0, 100000000.0,
    // And finally a catch-all for anything else
    Double.POSITIVE_INFINITY,
)

/**
 * In-process metrics registry. The two original gauges
 * (`canary_players_online_total`, `canary_game_tick_duration_ms`) are preserved;
 * the counter, up/down-counter and latency-histogram surface ports
 * `metrics::Metrics` (src/lib/metrics/metrics.hpp:95-154,
 * src/lib/metrics/metrics.cpp:17-112).
 *
 * The OpenTelemetry SDK's Prometheus exporter is replaced by [scrape]: counters
 * render with the `_total` suffix and `counter` type, up/down counters as
 * `gauge`, and histograms as cumulative `_bucket`/`_sum`/`_count` families.
 * The HTTP exporter and the OStream periodic reader are not ported.
 */
class MetricsRegistry {

    private val onlinePlayers = AtomicInteger(0)
    private val lastTickDurationMs = AtomicReference("0.0")

    private val lock = Any()
    private val counters = LinkedHashMap<String, LinkedHashMap<Map<String, String>, Double>>()
    private val upDownCounters = LinkedHashMap<String, LinkedHashMap<Map<String, String>, Long>>()
    private val histograms = LinkedHashMap<String, LinkedHashMap<Map<String, String>, Histogram>>()

    @Volatile
    private var enabled = true

    fun setOnlinePlayers(count: Int) {
        onlinePlayers.set(count)
    }

    fun recordTickDurationMs(duration: Double) {
        lastTickDurationMs.set(duration.toString())
    }

    /** `Metrics::addCounter` (metrics.hpp:106-117). */
    fun addCounter(name: String, value: Double, attrs: Map<String, String> = emptyMap()) {
        if (!enabled) {
            return
        }
        synchronized(lock) {
            val byLabels = counters.getOrPut(name) { LinkedHashMap() }
            byLabels[attrs] = (byLabels[attrs] ?: 0.0) + value
        }
    }

    /** `Metrics::addUpDownCounter` (metrics.hpp:119-130). */
    fun addUpDownCounter(name: String, value: Long, attrs: Map<String, String> = emptyMap()) {
        if (!enabled) {
            return
        }
        synchronized(lock) {
            val byLabels = upDownCounters.getOrPut(name) { LinkedHashMap() }
            byLabels[attrs] = (byLabels[attrs] ?: 0L) + value
        }
    }

    /** `Metrics::initHistograms` (metrics.cpp:46-84). */
    fun initHistograms() {
        synchronized(lock) {
            enabled = true
            LATENCY_HISTOGRAM_NAMES.forEach { name -> histograms.getOrPut(name) { LinkedHashMap() } }
        }
    }

    /**
     * `Histogram<double>::Record` as reached through `ScopedLatency::stop`
     * (metrics.cpp:103-112). Unknown histogram names are a no-op, mirroring the
     * null-histogram branch of the C++ `ScopedLatency` constructor.
     */
    fun recordLatency(name: String, elapsedUs: Double, attrs: Map<String, String> = emptyMap()) {
        if (!enabled) {
            return
        }
        synchronized(lock) {
            val byLabels = histograms[name] ?: return
            byLabels.getOrPut(attrs) { Histogram() }.observe(elapsedUs)
        }
    }

    /** `Metrics::shutdown` (metrics.cpp:86-89). */
    fun shutdown() {
        synchronized(lock) {
            enabled = false
            counters.clear()
            upDownCounters.clear()
            histograms.clear()
        }
    }

    fun scrape(): String = synchronized(lock) {
        buildString {
            appendLine("# HELP canary_players_online_total Total number of active online players.")
            appendLine("# TYPE canary_players_online_total gauge")
            appendLine("canary_players_online_total ${onlinePlayers.get()}")

            appendLine("# HELP canary_game_tick_duration_ms Last game dispatcher tick execution duration in milliseconds.")
            appendLine("# TYPE canary_game_tick_duration_ms gauge")
            appendLine("canary_game_tick_duration_ms ${lastTickDurationMs.get()}")

            counters.entries.sortedBy { it.key }.forEach { (name, byLabels) ->
                val metricName = counterName(name)
                appendLine("# TYPE $metricName counter")
                byLabels.entries.sortedBy { formatLabels(it.key) }.forEach { (attrs, value) ->
                    appendLine("$metricName${formatLabels(attrs)} ${formatNumber(value)}")
                }
            }

            upDownCounters.entries.sortedBy { it.key }.forEach { (name, byLabels) ->
                appendLine("# TYPE $name gauge")
                byLabels.entries.sortedBy { formatLabels(it.key) }.forEach { (attrs, value) ->
                    appendLine("$name${formatLabels(attrs)} $value")
                }
            }

            histograms.entries.sortedBy { it.key }.forEach { (name, byLabels) ->
                appendLine("# TYPE $name histogram")
                byLabels.entries.sortedBy { formatLabels(it.key) }.forEach { (attrs, histogram) ->
                    var cumulative = 0L
                    LATENCY_BUCKET_BOUNDARIES_US.forEachIndexed { index, bound ->
                        cumulative += histogram.buckets[index]
                        if (!bound.isInfinite()) {
                            appendLine("${name}_bucket${formatLabels(attrs + ("le" to formatBound(bound)))} $cumulative")
                        }
                    }
                    appendLine("${name}_bucket${formatLabels(attrs + ("le" to "+Inf"))} $cumulative")
                    appendLine("${name}_sum${formatLabels(attrs)} ${formatNumber(histogram.sum)}")
                    appendLine("${name}_count${formatLabels(attrs)} ${histogram.count}")
                }
            }
        }
    }

    private class Histogram {
        val buckets = LongArray(LATENCY_BUCKET_BOUNDARIES_US.size)
        var sum = 0.0
        var count = 0L

        fun observe(value: Double) {
            buckets[bucketIndex(value)]++
            sum += value
            count++
        }

        private fun bucketIndex(value: Double): Int {
            for (index in LATENCY_BUCKET_BOUNDARIES_US.indices) {
                if (value <= LATENCY_BUCKET_BOUNDARIES_US[index]) {
                    return index
                }
            }
            return LATENCY_BUCKET_BOUNDARIES_US.size - 1
        }
    }
}

/**
 * `metrics::ScopedLatency` (src/lib/metrics/metrics.hpp:55-72,
 * metrics.cpp:91-112). The C++ `g_metrics().latencyHistograms[...]` lookup is a
 * constructor-injected [MetricsRegistry]; `stop` records microseconds with the
 * `{scopeKey: name}` attribute and is idempotent. The C++ destructor is mapped to
 * [close] so the latency can be used with `use { }`.
 */
open class ScopedLatency(
    private val registry: MetricsRegistry,
    private val name: String,
    private val histogramName: String,
    private val scopeKey: String,
    attrs: Map<String, String> = emptyMap(),
) : AutoCloseable {

    private val attrs: Map<String, String> = attrs + (scopeKey to name)
    private val begin = System.nanoTime()
    private var stopped = false

    @Synchronized
    fun stop() {
        if (stopped) {
            return
        }
        stopped = true

        val elapsedUs = (System.nanoTime() - begin) / 1_000.0
        registry.recordLatency(histogramName, elapsedUs, attrs)
    }

    override fun close() {
        stop()
    }
}

/** `method_latency` from `DEFINE_LATENCY_CLASS` (src/lib/metrics/metrics.hpp:74-81). */
class MethodLatency(registry: MetricsRegistry, name: String) :
    ScopedLatency(registry, name, "method_latency", "method")

/** `lua_latency` from `DEFINE_LATENCY_CLASS` (src/lib/metrics/metrics.hpp:74-81). */
class LuaLatency(registry: MetricsRegistry, name: String) :
    ScopedLatency(registry, name, "lua_latency", "scope")

/** `query_latency` from `DEFINE_LATENCY_CLASS` (src/lib/metrics/metrics.hpp:74-81). */
class QueryLatency(registry: MetricsRegistry, name: String) :
    ScopedLatency(registry, name, "query_latency", "truncated_query")

/** `task_latency` from `DEFINE_LATENCY_CLASS` (src/lib/metrics/metrics.hpp:74-81). */
class TaskLatency(registry: MetricsRegistry, name: String) :
    ScopedLatency(registry, name, "task_latency", "task")

/** `lock_latency` from `DEFINE_LATENCY_CLASS` (src/lib/metrics/metrics.hpp:74-81). */
class LockLatency(registry: MetricsRegistry, name: String) :
    ScopedLatency(registry, name, "lock_latency", "scope")

private fun counterName(name: String): String = if (name.endsWith("_total")) name else "${name}_total"

private fun formatNumber(value: Double): String {
    if (value.isFinite() && value == floor(value) && abs(value) < 1e15) {
        return value.toLong().toString()
    }
    return value.toString()
}

private fun formatBound(value: Double): String = if (value.isInfinite()) "+Inf" else formatNumber(value)

private fun formatLabels(attrs: Map<String, String>): String {
    if (attrs.isEmpty()) {
        return ""
    }
    return attrs.entries
        .sortedBy { it.key }
        .joinToString(separator = ",", prefix = "{", postfix = "}") { (key, value) ->
            "$key=\"${escapeLabelValue(value)}\""
        }
}

private fun escapeLabelValue(value: String): String = buildString(value.length) {
    value.forEach { character ->
        when (character) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            else -> append(character)
        }
    }
}
