package org.opentibia.canary.bootstrap

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.opentibia.canary.config.ServerConfig
import org.opentibia.canary.database.DatabaseConfig
import org.opentibia.canary.database.DatabaseConnectionFactory
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.io.appearances.Appearances
import org.opentibia.canary.items.Items
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.network.ListenerBoot
import org.opentibia.canary.systems.imbuement.Imbuements
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

        override fun closeConnections() {
            events += "listeners.closeConnections"
        }
    }

    private fun bootstrap(
        events: MutableList<String>,
        dataSource: DataSource,
        dispatcher: GameDispatcher,
        itemBoot: ItemBoot = ItemBoot { _, _ ->
            events += "item.load"
            Items()
        },
        imbuementBoot: ImbuementBoot = ImbuementBoot {
            events += "imbuement.load"
            Imbuements()
        },
        listenerFactory: ListenerFactory = ListenerFactory { _, _, _, bootedDispatcher, _, _ ->
            RecordingListener(events, bootedDispatcher)
        },
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
            appearanceBoot = AppearanceBoot {
                events += "appearance.load"
                Appearances.load(ByteArray(0))
            },
            itemBoot = itemBoot,
            imbuementBoot = imbuementBoot,
            listenerFactory = listenerFactory,
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
                "imbuement.load",
                "item.load",
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
        assertThat(events.count { it == "listeners.closeConnections" }).isEqualTo(1)
        assertThat(events.count { it == "database.close" }).isEqualTo(1)
        assertThat(bootstrap.isShutdown).isTrue()
        assertThat(dispatcher.isRunning).isFalse()
    }

    @Test
    fun `shutdown closes live connections after stopping listeners`() {
        val events = mutableListOf<String>()
        val dataSource = DatabaseConnectionFactory.createInMemoryDataSource("bootstrap_connections")
        val bootstrap = bootstrap(events, dataSource, GameDispatcher())
        bootstrap.run()

        bootstrap.shutdown()

        val stopIndex = events.indexOf("listeners.stop")
        val closeIndex = events.indexOf("listeners.closeConnections")
        val databaseIndex = events.indexOf("database.close")
        assertThat(stopIndex).isGreaterThanOrEqualTo(0)
        assertThat(closeIndex).isGreaterThan(stopIndex)
        assertThat(databaseIndex).isGreaterThan(closeIndex)
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

    @Test
    fun `boot passes the connected data source and booted registries to the listener factory`() {
        val events = mutableListOf<String>()
        val dataSource = DatabaseConnectionFactory.createInMemoryDataSource("bootstrap_composition")
        val registry = Items()
        val imbuements = Imbuements()
        var composition: GameComposition? = null
        val bootstrap = bootstrap(
            events,
            dataSource,
            GameDispatcher(),
            itemBoot = ItemBoot { _, _ ->
                events += "item.load"
                registry
            },
            imbuementBoot = ImbuementBoot {
                events += "imbuement.load"
                imbuements
            },
            listenerFactory = ListenerFactory { _, _, _, bootedDispatcher, _, booted ->
                composition = booted
                RecordingListener(events, bootedDispatcher)
            },
        )

        try {
            bootstrap.run()
        } finally {
            bootstrap.shutdown()
        }

        val booted = composition
        assertThat(booted).isNotNull
        assertThat(booted!!.dataSource).isSameAs(dataSource)
        assertThat(booted.itemTypes).isSameAs(registry)
        assertThat(booted.imbuements).isSameAs(imbuements)
        assertThat(events).containsSubsequence(
            "appearance.load",
            "imbuement.load",
            "item.load",
            "listeners.start",
        )
    }

    @Test
    fun `default registries boot loads the core appearances items and imbuements`() {
        val events = mutableListOf<String>()
        val dataSource = DatabaseConnectionFactory.createInMemoryDataSource("bootstrap_core_files")
        var composition: GameComposition? = null
        val bootstrap = ServerBootstrap(
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
            listenerFactory = ListenerFactory { _, _, _, _, _, booted ->
                composition = booted
                RecordingListener(events, GameDispatcher())
            },
            dispatcher = GameDispatcher(),
        )

        try {
            bootstrap.run()
        } finally {
            bootstrap.shutdown()
        }

        val booted = composition
        assertThat(booted).isNotNull
        assertThat(booted!!.dataSource).isSameAs(dataSource)
        assertThat(booted.itemTypes.getItemType(3031).name).isEqualTo("gold coin")
        assertThat(booted.itemTypes.getItemType(813).name).isEqualTo("terra boots")
        assertThat(booted.itemTypes.getItemType(813).weight).isEqualTo(750.0)
        assertThat(booted.itemTypes.getItemType(813).imbuementSlot).isEqualTo(1)
        assertThat(booted.imbuements.imbuementMap).isNotEmpty()
    }
}
