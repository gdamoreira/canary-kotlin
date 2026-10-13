package org.opentibia.canary.bootstrap

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ShutdownDrainTest {

    @Test
    fun `drains listeners dispatcher save manager and database in order`() {
        val events = mutableListOf<String>()
        val drain = ShutdownDrain(
            stopListeners = { events += "listeners" },
            shutdownDispatcher = { events += "dispatcher" },
            flushSaveManager = { events += "saveManager" },
            closeDatabase = { events += "database" }
        )

        drain.drain()

        assertThat(events).containsExactly("listeners", "dispatcher", "saveManager", "database")
        assertThat(drain.isDrained).isTrue()
    }

    @Test
    fun `second drain request does not release resources twice`() {
        val counts = mutableMapOf<String, Int>()
        val drain = ShutdownDrain(
            stopListeners = { counts.merge("listeners", 1, Int::plus) },
            shutdownDispatcher = { counts.merge("dispatcher", 1, Int::plus) },
            flushSaveManager = { counts.merge("saveManager", 1, Int::plus) },
            closeDatabase = { counts.merge("database", 1, Int::plus) }
        )

        drain.drain()
        drain.drain()

        assertThat(counts).containsExactlyInAnyOrderEntriesOf(
            mapOf("listeners" to 1, "dispatcher" to 1, "saveManager" to 1, "database" to 1)
        )
        assertThat(drain.isDrained).isTrue()
    }

    @Test
    fun `keeps draining the remaining services when one step fails`() {
        val events = mutableListOf<String>()
        val drain = ShutdownDrain(
            stopListeners = {
                events += "listeners"
                throw IllegalStateException("listener stop failed")
            },
            shutdownDispatcher = { events += "dispatcher" },
            flushSaveManager = { events += "saveManager" },
            closeDatabase = { events += "database" }
        )

        drain.drain()

        assertThat(events).containsExactly("listeners", "dispatcher", "saveManager", "database")
        assertThat(drain.isDrained).isTrue()
    }
}
