package org.opentibia.canary.monitoring

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class MetricsRegistryExtendedTest {

    @Test
    fun `counter accumulates per label set and renders the prometheus counter suffix`() {
        val registry = MetricsRegistry()

        registry.addCounter("balance_decrease", 50.0, mapOf("player" to "X", "context" to "market_fee"))
        registry.addCounter("balance_decrease", 25.0, mapOf("player" to "X", "context" to "market_fee"))
        registry.addCounter("balance_increase", 10.0, mapOf("player" to "Y"))

        val text = registry.scrape()

        assertThat(text).contains("# TYPE balance_decrease_total counter")
        assertThat(text).contains("balance_decrease_total{context=\"market_fee\",player=\"X\"} 75")
        assertThat(text).contains("balance_increase_total{player=\"Y\"} 10")
    }

    @Test
    fun `up down counter accumulates and renders as a gauge`() {
        val registry = MetricsRegistry()

        registry.addUpDownCounter("players_online", 1)
        registry.addUpDownCounter("players_online", 1)
        registry.addUpDownCounter("players_online", -1)

        val text = registry.scrape()

        assertThat(text).contains("# TYPE players_online gauge")
        assertThat(text).contains("players_online 1")
    }

    @Test
    fun `latency histogram renders cumulative buckets sum and count`() {
        val registry = MetricsRegistry()
        registry.initHistograms()

        registry.recordLatency("method_latency", 120.0, mapOf("method" to "getName"))

        val text = registry.scrape()

        assertThat(text).contains("# TYPE method_latency histogram")
        assertThat(text).contains("method_latency_bucket{le=\"100\",method=\"getName\"} 0")
        assertThat(text).contains("method_latency_bucket{le=\"120\",method=\"getName\"} 1")
        assertThat(text).contains("method_latency_bucket{le=\"+Inf\",method=\"getName\"} 1")
        assertThat(text).contains("method_latency_sum{method=\"getName\"} 120")
        assertThat(text).contains("method_latency_count{method=\"getName\"} 1")
    }

    @Test
    fun `scoped latency records microseconds with the scope attribute and stops once`() {
        val registry = MetricsRegistry()
        registry.initHistograms()

        val latency = MethodLatency(registry, "loadPlayer")
        latency.stop()
        latency.close()

        val text = registry.scrape()

        assertThat(text).contains("method_latency_count{method=\"loadPlayer\"} 1")
        assertThat(text).contains("method_latency_bucket{le=\"+Inf\",method=\"loadPlayer\"} 1")
    }

    @Test
    fun `scoped latency is a no-op when the histogram was not initialized`() {
        val registry = MetricsRegistry()

        ScopedLatency(registry, "loadPlayer", "method_latency", "method").close()

        assertThat(registry.scrape()).doesNotContain("method_latency")
    }

    @Test
    fun `latency classes map to their histogram names and scope keys`() {
        val registry = MetricsRegistry()
        registry.initHistograms()

        MethodLatency(registry, "m").close()
        LuaLatency(registry, "l").close()
        QueryLatency(registry, "q").close()
        TaskLatency(registry, "t").close()
        LockLatency(registry, "k").close()

        val text = registry.scrape()

        assertThat(text).contains("method_latency_count{method=\"m\"} 1")
        assertThat(text).contains("lua_latency_count{scope=\"l\"} 1")
        assertThat(text).contains("query_latency_count{truncated_query=\"q\"} 1")
        assertThat(text).contains("task_latency_count{task=\"t\"} 1")
        assertThat(text).contains("lock_latency_count{scope=\"k\"} 1")
    }

    @Test
    fun `shutdown disables recording`() {
        val registry = MetricsRegistry()
        registry.addCounter("balance_decrease", 5.0)

        registry.shutdown()
        registry.addCounter("balance_decrease", 5.0)

        assertThat(registry.scrape()).doesNotContain("balance_decrease_total")
    }

    @Test
    fun `latency names and bucket boundaries match the canary histogram configuration`() {
        assertThat(LATENCY_HISTOGRAM_NAMES).containsExactly(
            "method_latency",
            "lua_latency",
            "query_latency",
            "task_latency",
            "lock_latency",
        )
        assertThat(LATENCY_BUCKET_BOUNDARIES_US).hasSize(112)
        assertThat(LATENCY_BUCKET_BOUNDARIES_US.first()).isZero()
        assertThat(LATENCY_BUCKET_BOUNDARIES_US.last()).isInfinite()
        assertThat(LATENCY_BUCKET_BOUNDARIES_US)
            .contains(0.0, 10.0, 100.0, 500.0, 1000.0, 10_000.0, 1_000_000.0, 100_000_000.0)
    }
}
