package org.opentibia.canary.lua.bindings.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.chat.ChannelEvent
import org.opentibia.canary.chat.SpeakClasses
import org.opentibia.canary.combat.condition.PlayerIcon
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.players.livestream.CanWatchResult
import org.opentibia.canary.creatures.players.livestream.IconBakragore
import org.opentibia.canary.creatures.players.livestream.LivestreamClient
import org.opentibia.canary.creatures.players.livestream.LivestreamManager
import org.opentibia.canary.creatures.players.livestream.LivestreamMessage
import org.opentibia.canary.creatures.players.livestream.SessionEndInformation
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.protocol.MessageClass

/**
 * The broadcaster side of `PlayerFunctionsExtra` must drive the live
 * `g_livestream()` registry instead of the per-player ledger: the
 * `player:setLivestreamViewers(data)` binding (player_functions.cpp:5669-5736)
 * feeds `LivestreamManager::applyState` (livestream.cpp:248-290), so a caster
 * that enables the broadcast through Lua is admitted by
 * `LivestreamManager::canWatch` (livestream.cpp:314-375), and
 * `player:getLivestreamViewers()`/`player:getLivestreamViewersCount()`/
 * `player:isLivestreamViewer()` (player_functions.cpp:5634-5660, 5739-5749)
 * read the same session back.
 */
class PlayerExtraLivestreamDelegationTest {

    /** Stand-in for the `ProtocolGame` surface `LivestreamManager` drives. */
    private class RecordingClient(
        var ip: Long = 0,
        override val player: Player? = null,
    ) : LivestreamClient {
        override fun getIP(): Long = ip

        override var oldProtocol: Boolean = false
        override var isLivestreamBroadcaster: Boolean = false
        override var livestreamMessageCooldownTime: Long = 0
        override var livestreamMessageCount: Int = 0

        val sessionEnds = mutableListOf<SessionEndInformation>()

        override fun writeToOutputBuffer(message: LivestreamMessage) = Unit

        override fun sendMapDescription(position: Position) = Unit

        override fun sendIcons(icons: Set<PlayerIcon>, bakragore: IconBakragore) = Unit

        override fun sendSessionEndInformation(information: SessionEndInformation) {
            sessionEnds.add(information)
        }

        override fun sendChannel(channelId: Int, channelName: String) = Unit

        override fun sendChannelMessage(author: String, text: String, type: SpeakClasses, channelId: Int) = Unit

        override fun sendChannelEvent(channelId: Int, viewerName: String, event: ChannelEvent) = Unit

        override fun sendTextMessage(type: MessageClass, text: String) = Unit
    }

    private fun newPlayer(id: Long, name: String): Player = Player(
        id = id,
        name = name,
        maxHealth = 150,
        health = 150,
        position = Position(0, 0, 7),
    )

    private val caster = newPlayer(1L, "Caster")
    private val casterClient = RecordingClient(ip = 10)
    private val clients = mutableMapOf(caster to casterClient)
    private val manager = LivestreamManager(casterClient = { clients[it] })

    private fun installed(
        operations: PlayerExtraOperations = WorldPlayerExtraOperations(
            livestream = { manager },
            livestreamClientOf = { clients[it] },
        ),
        vararg players: Player,
    ): LuaEnvironment {
        val environment = LuaEnvironment().apply { initState() }
        PlayerFunctionsExtra.install(environment, operations)
        players.forEachIndexed { index, player ->
            environment.globals.set(
                if (index == 0) "p" else "q",
                environment.userdata.wrap("Player", player),
            )
        }
        return environment
    }

    @Test
    fun `lua setLivestreamViewers starts the broadcast and canWatch admits viewers`() {
        val environment = installed(players = arrayOf(caster))

        environment.loadChunk(
            """
            enabled = p:setLivestreamViewers({ broadcast = true, description = "Going live", password = "pw" })
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("enabled").toboolean()).isTrue()
        assertThat(manager.isBroadcasting(caster)).isTrue()
        assertThat(manager.getDescription(caster)).isEqualTo("Going live")
        assertThat(manager.getPassword(caster)).isEqualTo("pw")
        assertThat(casterClient.isLivestreamBroadcaster).isTrue()

        val viewer = RecordingClient(ip = 42)
        assertThat(manager.canWatch(caster, viewer, "pw")).isEqualTo(CanWatchResult(true))
        assertThat(manager.canWatch(caster, viewer, "wrong"))
            .isEqualTo(CanWatchResult(false, "Incorrect password. Access to this livestream is protected."))
    }

    @Test
    fun `lua setLivestreamViewers stops the broadcast and disconnects the viewers`() {
        val viewer = RecordingClient(ip = 42)
        val environment = installed(players = arrayOf(caster))
        environment.loadChunk("p:setLivestreamViewers({ broadcast = true, description = \"Going live\", password = \"pw\" })")
        manager.addViewer(caster, viewer)

        environment.loadChunk("stopped = p:setLivestreamViewers({ broadcast = false })")

        assertThat(environment.getGlobal("stopped").toboolean()).isTrue()
        assertThat(manager.isBroadcasting(caster)).isFalse()
        assertThat(casterClient.isLivestreamBroadcaster).isFalse()
        assertThat(viewer.sessionEnds).containsExactly(SessionEndInformation.SESSION_END_LOGOUT)
        assertThat(manager.getViewerCount(caster)).isZero()
        // The absent fields stay untouched, exactly like the C++ `hasField` checks.
        assertThat(manager.getDescription(caster)).isEqualTo("Going live")
        assertThat(manager.getPassword(caster)).isEqualTo("pw")
        assertThat(manager.canWatch(caster, RecordingClient(ip = 42), "pw"))
            .isEqualTo(CanWatchResult(false, "This player is not currently streaming."))
    }

    @Test
    fun `lua getLivestreamViewers and the viewer count read the live session back`() {
        val viewer = RecordingClient(ip = 42)
        val environment = installed(players = arrayOf(caster))
        environment.loadChunk(
            """
            p:setLivestreamViewers({ broadcast = true, description = "Going live", password = "pw" })
            before = p:getLivestreamViewersCount()
            """.trimIndent(),
        )
        manager.addViewer(caster, viewer)

        environment.loadChunk(
            """
            count = p:getLivestreamViewersCount()
            state = p:getLivestreamViewers()
            description = state.description
            broadcast = state.broadcast
            password = state.password
            name = state.names[1]
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("before").toint()).isZero()
        assertThat(environment.getGlobal("count").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("description").tojstring()).isEqualTo("Going live")
        assertThat(environment.getGlobal("broadcast").toboolean()).isTrue()
        assertThat(environment.getGlobal("password").tojstring()).isEqualTo("pw")
        assertThat(environment.getGlobal("name").tojstring()).isEqualTo("Viewer-1")
    }

    @Test
    fun `lua setLivestreamViewers applies mutes bans and kick to the session`() {
        val kicked = RecordingClient(ip = 42)
        val kept = RecordingClient(ip = 43)
        val environment = installed(players = arrayOf(caster))
        environment.loadChunk("p:setLivestreamViewers({ broadcast = true })")
        manager.addViewer(caster, kicked)
        manager.addViewer(caster, kept)

        environment.loadChunk(
            """
            applied = p:setLivestreamViewers({ mutes = { "Zed" }, kick = { "Viewer-1" } })
            mutes = p:getLivestreamViewers().mutes[1]
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("applied").toboolean()).isTrue()
        assertThat(environment.getGlobal("mutes").tojstring()).isEqualTo("zed")
        assertThat(kicked.sessionEnds).containsExactly(SessionEndInformation.SESSION_END_LOGOUT)
        assertThat(manager.isViewer(kicked)).isFalse()
        assertThat(manager.getViewerCount(caster)).isEqualTo(1)
    }

    @Test
    fun `lua isLivestreamViewer follows the manager viewer registry`() {
        val viewerClient = RecordingClient(ip = 7)
        val viewerPlayer = newPlayer(2L, "Watcher")
        clients[viewerPlayer] = viewerClient
        manager.addViewer(caster, viewerClient)
        val environment = installed(players = arrayOf(caster, viewerPlayer))

        environment.loadChunk(
            """
            casterViewer = p:isLivestreamViewer()
            viewerViewer = q:isLivestreamViewer()
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("casterViewer").toboolean()).isFalse()
        assertThat(environment.getGlobal("viewerViewer").toboolean()).isTrue()
    }

    @Test
    fun `an unbound manager keeps the per-player ledger fallback`() {
        val environment = installed(operations = WorldPlayerExtraOperations(), caster)

        environment.loadChunk(
            """
            set = p:setLivestreamViewers({ broadcast = true, description = "ledger" })
            state = p:getLivestreamViewers()
            count = p:getLivestreamViewersCount()
            viewer = p:isLivestreamViewer()
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("set").toboolean()).isTrue()
        assertThat(environment.getGlobal("state").get("broadcast").toboolean()).isTrue()
        assertThat(environment.getGlobal("state").get("description").tojstring()).isEqualTo("ledger")
        assertThat(environment.getGlobal("count").toint()).isZero()
        assertThat(environment.getGlobal("viewer").toboolean()).isFalse()
    }
}
