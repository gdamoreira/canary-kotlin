package org.opentibia.canary.game.scheduling

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.config.ConfigKeys
import org.opentibia.canary.config.ConfigManager
import org.opentibia.canary.config.ConfigSource

/**
 * Transcript tests for `Dispatcher::refreshAdaptiveBudgets`
 * (src/game/scheduling/dispatcher.cpp:547-627) and its worker-loop call site
 * (dispatcher.cpp:147). Configured budgets come from the `ConfigKey_t` values
 * (`getConfiguredDispatcherBudgets`, dispatcher.cpp:38-49) and the active
 * budgets/slice limits refresh at most once per 250 ms
 * (`DISPATCHER_ADAPTIVE_BUDGET_INTERVAL`, dispatcher.cpp:28).
 */
class GameDispatcherBudgetRefreshTest {

    private fun config(values: MutableMap<String, String>): ConfigManager =
        ConfigManager(ConfigKeys.all.associateWith { it.luaIdentifier }, properties = ConfigSource { values })

    @Test
    fun `the refresh loop updates the creature async slice limits from config keys`() = runBlocking<Unit> {
        var now = 10_000L
        val values = mutableMapOf(
            "creatureAsyncTasksPerBucket" to "32",
            "dispatcherSliceDurationMs" to "5",
        )
        val dispatcher = GameDispatcher(clock = { now }, config = config(values))
        dispatcher.start()
        try {
            dispatcher.setQueueLatencyLoggingEnabled(true)
            val expected = CreatureAsyncSliceLimits(tasksPerBucket = 32, maxRuntimeUs = 5_000)
            withTimeout(3_000) {
                while (dispatcher.getCreatureAsyncSliceLimits(playerVisible = true) != expected) {
                    now += 250
                    dispatcher.wake()
                    delay(5)
                }
            }
            assertThat(dispatcher.getCreatureAsyncSliceLimits(playerVisible = true)).isEqualTo(expected)
            assertThat(dispatcher.getCreatureAsyncSliceLimits(playerVisible = false)).isEqualTo(expected)
        } finally {
            dispatcher.shutdown()
        }
    }

    @Test
    fun `the refresh loop applies config changes at most once per 250 ms`() {
        var now = 10_000L
        val values = mutableMapOf("creatureAsyncTasksPerBucket" to "32")
        val config = config(values)
        val dispatcher = GameDispatcher(clock = { now }, config = config)
        dispatcher.start()
        try {
            dispatcher.setQueueLatencyLoggingEnabled(true)
            dispatcher.refreshAdaptiveBudgets()
            assertThat(dispatcher.getCreatureAsyncSliceLimits(true).tasksPerBucket).isEqualTo(32)

            values["creatureAsyncTasksPerBucket"] = "48"
            config.reload()
            now += 200
            dispatcher.refreshAdaptiveBudgets()
            assertThat(dispatcher.getCreatureAsyncSliceLimits(true).tasksPerBucket).isEqualTo(32)

            now += 50
            dispatcher.refreshAdaptiveBudgets()
            assertThat(dispatcher.getCreatureAsyncSliceLimits(true).tasksPerBucket).isEqualTo(48)
        } finally {
            dispatcher.shutdown()
        }
    }
}
