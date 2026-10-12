package org.opentibia.canary.monitoring

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class MetricsRegistryTest {

    @Test
    fun `records online players and tick latency exporting prometheus format`() {
        val registry = MetricsRegistry()

        registry.setOnlinePlayers(42)
        registry.recordTickDurationMs(12.5)

        val prometheusText = registry.scrape()

        assertThat(prometheusText).contains("# TYPE canary_players_online_total gauge")
        assertThat(prometheusText).contains("canary_players_online_total 42")
        assertThat(prometheusText).contains("# TYPE canary_game_tick_duration_ms gauge")
        assertThat(prometheusText).contains("canary_game_tick_duration_ms 12.5")
    }
}
