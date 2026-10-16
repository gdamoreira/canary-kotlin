package org.opentibia.canary.bootstrap

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.account.AccountRepository
import org.opentibia.canary.config.ServerConfig
import org.opentibia.canary.config.ServerRole
import org.opentibia.canary.creatures.monsters.Monsters
import org.opentibia.canary.creatures.players.grouping.Groups
import org.opentibia.canary.creatures.vocations.Vocations
import org.opentibia.canary.database.DatabaseConfig
import org.opentibia.canary.database.DatabaseConnectionFactory
import org.opentibia.canary.database.JdbcAccountRepository
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.io.appearances.Appearances
import org.opentibia.canary.items.Items
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.network.ListenerBoot
import org.opentibia.canary.systems.imbuement.Imbuements
import javax.sql.DataSource

class ServerBootstrapRoleTest {

    private class RecordingDatabaseBoot(
        private val events: MutableList<String>,
        private val dataSource: DataSource,
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
        }
    }

    private class RecordingListener(
        private val events: MutableList<String>,
        private val dispatcher: GameDispatcher? = null,
    ) : ListenerBoot {
        override fun start() {
            events += if (dispatcher?.isRunning == true) "dispatcher.started" else "dispatcher.notStarted"
            events += "listeners.start"
        }

        override fun stop() {
            events += "listeners.stop"
        }

        override fun closeConnections() {
            events += "listeners.closeConnections"
        }
    }

    private fun richBootstrap(
        events: MutableList<String>,
        dataSource: DataSource,
        role: ServerRole,
        dispatcher: GameDispatcher,
        listenerFactory: ListenerFactory = ListenerFactory { _, _, _, bootedDispatcher, _, _ ->
            events += "world.listeners.create"
            RecordingListener(events, bootedDispatcher)
        },
        loginListenerFactory: LoginListenerFactory = LoginListenerFactory { _, _ ->
            events += "login.listeners.create"
            RecordingListener(events)
        },
    ): ServerBootstrap = ServerBootstrap(
        configBoot = ConfigBoot { _, _ ->
            events += "config.load"
            ServerConfig(role = role, loginPort = 0, gamePort = 0)
        },
        databaseBoot = RecordingDatabaseBoot(events, dataSource),
        mapBoot = MapBoot { _, _, _, _ ->
            events += "map.load"
            GameMap()
        },
        scriptingBoot = ScriptingBoot { _, _, _ ->
            events += "scripting.boot"
            LuaEnvironment()
        },
        appearanceBoot = AppearanceBoot {
            events += "appearance.load"
            Appearances.load(ByteArray(0))
        },
        itemBoot = ItemBoot { _, _ ->
            events += "item.load"
            Items()
        },
        imbuementBoot = ImbuementBoot {
            events += "imbuement.load"
            Imbuements()
        },
        monsterBoot = MonsterBoot { _, _, _, _ ->
            events += "monster.load"
            Monsters()
        },
        vocationsBoot = VocationsBoot { _, _, _ ->
            events += "vocations.load"
            Vocations()
        },
        groupsBoot = GroupsBoot {
            events += "groups.load"
            Groups()
        },
        listenerFactory = listenerFactory,
        loginListenerFactory = loginListenerFactory,
        dispatcher = dispatcher,
    )

    @Test
    fun `login role boots config database and the login listener only`() {
        val events = mutableListOf<String>()
        val dataSource = DatabaseConnectionFactory.createInMemoryDataSource("role_login_boot")
        val dispatcher = GameDispatcher()
        var loginConfig: ServerConfig? = null
        var loginAccounts: AccountRepository? = null
        val bootstrap = richBootstrap(
            events = events,
            dataSource = dataSource,
            role = ServerRole.LOGIN,
            dispatcher = dispatcher,
            loginListenerFactory = LoginListenerFactory { config, accounts ->
                events += "login.listeners.create"
                loginConfig = config
                loginAccounts = accounts
                RecordingListener(events)
            },
        )

        try {
            bootstrap.run()
            assertThat(dispatcher.isRunning).describedAs("LOGIN must not boot the dispatcher").isFalse()
        } finally {
            bootstrap.shutdown()
        }

        assertThat(events).containsExactly(
            "config.load",
            "database.connect",
            "database.migrate",
            "login.listeners.create",
            "dispatcher.notStarted",
            "listeners.start",
            "listeners.stop",
            "listeners.closeConnections",
            "database.close",
        )
        assertThat(loginConfig?.role).isEqualTo(ServerRole.LOGIN)
        assertThat(loginConfig?.loginPort).isEqualTo(0)
        assertThat(loginAccounts).isInstanceOf(JdbcAccountRepository::class.java)
        assertThat(bootstrap.isStarted).isTrue()
        assertThat(bootstrap.isShutdown).isTrue()
    }

    @Test
    fun `game role boots the full world and skips the login listener factory`() {
        val events = mutableListOf<String>()
        val dataSource = DatabaseConnectionFactory.createInMemoryDataSource("role_game_boot")
        var worldRole: ServerRole? = null
        var loginFactoryCalls = 0
        val bootstrap = richBootstrap(
            events = events,
            dataSource = dataSource,
            role = ServerRole.GAME,
            dispatcher = GameDispatcher(),
            listenerFactory = ListenerFactory { config, _, _, bootedDispatcher, _, _ ->
                events += "world.listeners.create"
                worldRole = config.role
                RecordingListener(events, bootedDispatcher)
            },
            loginListenerFactory = LoginListenerFactory { _, _ ->
                loginFactoryCalls++
                RecordingListener(events)
            },
        )

        try {
            bootstrap.run()
        } finally {
            bootstrap.shutdown()
        }

        assertThat(loginFactoryCalls).describedAs("GAME must not compose a login listener").isZero()
        assertThat(worldRole).isEqualTo(ServerRole.GAME)
        assertThat(events).containsSubsequence(
            "config.load",
            "database.connect",
            "database.migrate",
            "appearance.load",
            "imbuement.load",
            "item.load",
            "scripting.boot",
            "monster.load",
            "vocations.load",
            "groups.load",
            "map.load",
            "world.listeners.create",
            "dispatcher.started",
            "listeners.start",
        )
        assertThat(events).contains("database.close")
    }

    @Test
    fun `all role keeps the current world boot and skips the login listener factory`() {
        val events = mutableListOf<String>()
        val dataSource = DatabaseConnectionFactory.createInMemoryDataSource("role_all_boot")
        var worldRole: ServerRole? = null
        var loginFactoryCalls = 0
        val bootstrap = richBootstrap(
            events = events,
            dataSource = dataSource,
            role = ServerRole.ALL,
            dispatcher = GameDispatcher(),
            listenerFactory = ListenerFactory { config, _, _, bootedDispatcher, _, _ ->
                events += "world.listeners.create"
                worldRole = config.role
                RecordingListener(events, bootedDispatcher)
            },
            loginListenerFactory = LoginListenerFactory { _, _ ->
                loginFactoryCalls++
                RecordingListener(events)
            },
        )

        try {
            bootstrap.run()
        } finally {
            bootstrap.shutdown()
        }

        assertThat(loginFactoryCalls).describedAs("ALL keeps the single-process boot").isZero()
        assertThat(worldRole).isEqualTo(ServerRole.ALL)
        assertThat(events).contains("map.load", "scripting.boot", "appearance.load", "world.listeners.create", "listeners.start")
        assertThat(events).contains("database.close")
    }
}
