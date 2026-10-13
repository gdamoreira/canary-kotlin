package org.opentibia.canary.lua.bindings.library

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.lua.runtime.LuaEnvironment

/**
 * Transcript tests for the `metrics` table, mirroring `MetricsFunctions`
 * (src/lua/functions/core/libs/metrics_functions.cpp).
 */
class MetricsFunctionsLibraryTest {

    private class RecordingMetricsOperations : MetricsOperations {
        val counters = mutableListOf<Triple<String, Double, Map<String, String>>>()

        override fun addCounter(name: String, value: Double, attributes: Map<String, String>) {
            counters += Triple(name, value, attributes)
        }
    }

    private fun environment(operations: MetricsOperations): LuaEnvironment =
        LuaEnvironment().apply { initState() }.also { MetricsFunctions.install(it, operations) }

    @Test
    fun `metrics addCounter forwards name value and string attributes`() {
        val operations = RecordingMetricsOperations()
        val environment = environment(operations)

        environment.loadChunk("metrics.addCounter('balance_increase', 12.5, {player = 'Alice', context = 'bank'})")

        assertThat(operations.counters).containsExactly(
            Triple("balance_increase", 12.5, mapOf("player" to "Alice", "context" to "bank")),
        )
    }

    @Test
    fun `metrics addCounter uses an empty attribute map when the table is missing`() {
        val operations = RecordingMetricsOperations()
        val environment = environment(operations)

        environment.loadChunk("metrics.addCounter('tick', 3)")

        assertThat(operations.counters).containsExactly(Triple("tick", 3.0, emptyMap()))
    }
}
