package org.opentibia.canary.monitoring

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class MetricsRegistry {

    private val onlinePlayers = AtomicInteger(0)
    private val lastTickDurationMs = AtomicReference("0.0")

    fun setOnlinePlayers(count: Int) {
        onlinePlayers.set(count)
    }

    fun recordTickDurationMs(duration: Double) {
        lastTickDurationMs.set(duration.toString())
    }

    fun scrape(): String {
        return buildString {
            appendLine("# HELP canary_players_online_total Total number of active online players.")
            appendLine("# TYPE canary_players_online_total gauge")
            appendLine("canary_players_online_total ${onlinePlayers.get()}")

            appendLine("# HELP canary_game_tick_duration_ms Last game dispatcher tick execution duration in milliseconds.")
            appendLine("# TYPE canary_game_tick_duration_ms gauge")
            appendLine("canary_game_tick_duration_ms ${lastTickDurationMs.get()}")
        }
    }
}
