package org.opentibia.canary.bootstrap

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.opentibia.canary.config.ServerConfig
import org.opentibia.canary.database.DatabaseConfig
import org.opentibia.canary.database.DatabaseConnectionFactory
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.network.ListenerBoot
import javax.sql.DataSource

class ServerBootstrapTest {

    private class RecordingDatabaseBoot(
        private val events: MutableList<String>,
        private val dataSource: DataSource
    ) : DatabaseBoot {
        override fun connect(config: DatabaseConfig?): DataSource {
            events += "database.connect"
            return dataSource
        }

        override fun migrate(dataSource: DataSource): Int {
            events += "database.migrate"
            return 0
        }

        override fun close(dataSource: DataSource) {
            events += "database.close"
            (dataSource as? AutoCloseable)?.close()
        }
    }

    private class RecordingListener(
        private val events: MutableList<String>,
        private val dispatcher: GameDispatcher
    ) : ListenerBoot {
        override fun start() {
            events += if (dispatcher.isRunning) "dispatcher.started" else "dispatcher.notStarted"
            events += "listeners.start"
        }

        override fun stop() {
            events += "listeners.stop"
        }
    }

    private fun bootstrap(
        events: MutableList<String>,
        dataSource: DataSource,
        dispatcher: GameDispatcher
    ): ServerBootstrap {
        return ServerBootstrap(
            configBoot = ConfigBoot { _, _ ->
                events += "config.load"
                ServerConfig()
            },
            databaseBoot = RecordingDatabaseBoot(events, dataSource),
            mapBoot = MapBoot {
                events += "map.load"
                GameMap()
            },
            scriptingBoot = ScriptingBoot {
                events += "scripting.boot"
                LuaEnvironment()
            },
            appearanceBoot = AppearanceBoot { events += "appearance.load" },
            listenerFactory = ListenerFactory { _, _, _, bootedDispatcher, _ ->
                RecordingListener(events, bootedDispatcher)
            },
            dispatcher = dispatcher
        )
    }

    @Test
    fun `boots services in Canary order and binds listeners last`() {
        val events = mutableListOf<String>()
        val dataSource = DatabaseConnectionFactory.createInMemoryDataSource("bootstrap_order")
        val dispatcher = GameDispatcher()
        val bootstrap = bootstrap(events, dataSource, dispatcher)

        try {
            bootstrap.run()

            assertThat(events).containsExactly(
                "config.load",
                "database.connect",
                "database.migrate",
                "map.load",
                "scripting.boot",
                "appearance.load",
                "dispatcher.started",
                "listeners.start"
            )
        } finally {
            bootstrap.shutdown()
        }

        assertThat(bootstrap.isStarted).isTrue()
        assertThat(bootstrap.isShutdown).isTrue()
        assertThat(dispatcher.isRunning).isFalse()
    }

    @Test
    fun `shutdown drains owned resources exactly once`() {
        val events = mutableListOf<String>()
        val dataSource = DatabaseConnectionFactory.createInMemoryDataSource("bootstrap_drain")
        val dispatcher = GameDispatcher()
        val bootstrap = bootstrap(events, dataSource, dispatcher)
        bootstrap.run()

        bootstrap.shutdown()
        bootstrap.shutdown()

        assertThat(events.count { it == "listeners.stop" }).isEqualTo(1)
        assertThat(events.count { it == "database.close" }).isEqualTo(1)
        assertThat(bootstrap.isShutdown).isTrue()
        assertThat(dispatcher.isRunning).isFalse()
    }

    @Test
    fun `run is rejected after shutdown`() {
        val events = mutableListOf<String>()
        val dataSource = DatabaseConnectionFactory.createInMemoryDataSource("bootstrap_reject")
        val bootstrap = bootstrap(events, dataSource, GameDispatcher())

        bootstrap.shutdown()

        assertThatThrownBy { bootstrap.run() }
            .isInstanceOf(IllegalStateException::class.java)
        assertThat(events).isEmpty()
    }

    @Test
    fun `run is idempotent and starts services only once`() {
        val events = mutableListOf<String>()
        val dataSource = DatabaseConnectionFactory.createInMemoryDataSource("bootstrap_idempotent")
        val bootstrap = bootstrap(events, dataSource, GameDispatcher())

        try {
            bootstrap.run()
            bootstrap.run()
        } finally {
            bootstrap.shutdown()
        }

        assertThat(events.count { it == "listeners.start" }).isEqualTo(1)
        assertThat(events.count { it == "database.connect" }).isEqualTo(1)
    }
}
