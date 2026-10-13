package org.opentibia.canary.creatures.players.livestream

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.chat.ChannelEvent
import org.opentibia.canary.chat.SpeakClasses
import org.opentibia.canary.combat.condition.PlayerIcon
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.protocol.MessageClass

/**
 * Transcript tests for `LivestreamManager`
 * (src/creatures/players/livestream/livestream.hpp:19-97,
 * livestream.cpp:23-818).
 */
class LivestreamTest {

    private data class ChannelMessageCall(
        val author: String,
        val text: String,
        val type: SpeakClasses,
        val channelId: Int,
    )

    private data class ChannelEventCall(
        val channelId: Int,
        val viewerName: String,
        val event: ChannelEvent,
    )

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

        val writtenMessages = mutableListOf<LivestreamMessage>()
        val mapDescriptions = mutableListOf<Position>()
        val iconFrames = mutableListOf<Pair<Set<PlayerIcon>, IconBakragore>>()
        val sessionEnds = mutableListOf<SessionEndInformation>()
        val channels = mutableListOf<Pair<Int, String>>()
        val channelMessages = mutableListOf<ChannelMessageCall>()
        val channelEvents = mutableListOf<ChannelEventCall>()
        val textMessages = mutableListOf<Pair<MessageClass, String>>()

        override fun writeToOutputBuffer(message: LivestreamMessage) {
            writtenMessages.add(message)
        }

        override fun sendMapDescription(position: Position) {
            mapDescriptions.add(position)
        }

        override fun sendIcons(icons: Set<PlayerIcon>, bakragore: IconBakragore) {
            iconFrames.add(icons to bakragore)
        }

        override fun sendSessionEndInformation(information: SessionEndInformation) {
            sessionEnds.add(information)
        }

        override fun sendChannel(channelId: Int, channelName: String) {
            channels.add(channelId to channelName)
        }

        override fun sendChannelMessage(author: String, text: String, type: SpeakClasses, channelId: Int) {
            channelMessages.add(ChannelMessageCall(author, text, type, channelId))
        }

        override fun sendChannelEvent(channelId: Int, viewerName: String, event: ChannelEvent) {
            channelEvents.add(ChannelEventCall(channelId, viewerName, event))
        }

        override fun sendTextMessage(type: MessageClass, text: String) {
            textMessages.add(type to text)
        }
    }

    private class Fixture(
        perIpLimit: Int = 0,
        viewerLimit: Int = 0,
        premiumViewerLimit: Int = 0,
        minLevel: Int = 0,
    ) {
        var now = 1_000_000L
        var tableExists = false
        val liveRecords = mutableListOf<Pair<Player, Int>>()
        val databaseTasks = mutableListOf<String>()
        val clients = mutableMapOf<Player, RecordingClient>()

        val manager = LivestreamManager(
            now = { now },
            casterClient = { clients[it] },
            saveLiveRecord = { player, record -> liveRecords.add(player to record) },
            maximumViewersPerIp = { perIpLimit },
            maximumViewers = { viewerLimit },
            premiumMaximumViewers = { premiumViewerLimit },
            casterMinLevel = { minLevel },
            tableExists = { tableExists },
            executeDatabaseTask = { databaseTasks.add(it) },
        )

        fun caster(
            id: Long = 1L,
            name: String = "Caster",
            premium: Boolean = false,
            level: Int = 100,
        ): Player {
            val player = Player(
                id = id,
                name = name,
                maxHealth = 100,
                health = 100,
                position = Position(10, 20, 7),
                level = level,
            )
            player.premium = premium
            return player
        }

        fun ownerFor(caster: Player): RecordingClient {
            val client = RecordingClient(player = caster)
            clients[caster] = client
            return client
        }

        fun startBroadcast(
            caster: Player,
            password: String = "",
            description: String = "Going live",
        ) {
            manager.applyState(
                caster,
                LivestreamState(description = description, broadcast = true, password = password),
            )
        }
    }

    private fun message(vararg payload: Int): LivestreamMessage {
        val buffer = ByteArray(8 + payload.size)
        payload.forEachIndexed { index, byte -> buffer[8 + index] = byte.toByte() }
        return LivestreamMessage(buffer, payload.size)
    }

    private fun speakMessage(speakType: Int, statementId: Long = 0, name: String = "Bob", channelId: Int? = null): LivestreamMessage {
        val payload = mutableListOf(
            0xAA,
            (statementId and 0xFF).toInt(),
            ((statementId shr 8) and 0xFF).toInt(),
            ((statementId shr 16) and 0xFF).toInt(),
            ((statementId shr 24) and 0xFF).toInt(),
            name.length and 0xFF,
            (name.length shr 8) and 0xFF,
        )
        name.forEach { payload.add(it.code) }
        payload.add(0)
        payload.add(0)
        payload.add(speakType)
        if (channelId != null) {
            payload.add(channelId and 0xFF)
            payload.add((channelId shr 8) and 0xFF)
        }
        return message(*payload.toIntArray())
    }

    // --- state and sessions ---------------------------------------------------

    @Test
    fun `applyState starts a broadcast and marks the owner client`() {
        val fixture = Fixture()
        val caster = fixture.caster()
        val owner = fixture.ownerFor(caster)

        fixture.startBroadcast(caster, password = "secret", description = "hello")

        assertThat(fixture.manager.isBroadcasting(caster)).isTrue()
        assertThat(fixture.manager.getPassword(caster)).isEqualTo("secret")
        assertThat(fixture.manager.getDescription(caster)).isEqualTo("hello")
        assertThat(owner.isLivestreamBroadcaster).isTrue()
        assertThat(owner.channels).containsExactly(CHANNEL_LIVESTREAM to "Livestream")
    }

    @Test
    fun `applyState stopping a broadcast disconnects viewers and clears the owner flag`() {
        val fixture = Fixture()
        val caster = fixture.caster()
        val owner = fixture.ownerFor(caster)
        fixture.startBroadcast(caster)
        val viewer = RecordingClient(ip = 42)
        fixture.manager.addViewer(caster, viewer)

        fixture.manager.applyState(caster, LivestreamState(broadcast = false))

        assertThat(fixture.manager.isBroadcasting(caster)).isFalse()
        assertThat(fixture.manager.isViewer(viewer)).isFalse()
        assertThat(fixture.manager.getViewerCount(caster)).isZero()
        assertThat(viewer.sessionEnds).containsExactly(SessionEndInformation.SESSION_END_LOGOUT)
        assertThat(owner.isLivestreamBroadcaster).isFalse()
        assertThat(fixture.manager.getState(caster).broadcast).isFalse()
    }

    @Test
    fun `applyState disconnects viewers when a new non-empty password is set`() {
        val fixture = Fixture()
        val caster = fixture.caster()
        fixture.ownerFor(caster)
        fixture.startBroadcast(caster, password = "old")
        val viewer = RecordingClient(ip = 42)
        fixture.manager.addViewer(caster, viewer)

        fixture.manager.applyState(caster, LivestreamState(broadcast = true, password = "new"))

        assertThat(viewer.sessionEnds).containsExactly(SessionEndInformation.SESSION_END_LOGOUT)
        assertThat(fixture.manager.getViewerCount(caster)).isZero()
        assertThat(fixture.manager.getPassword(caster)).isEqualTo("new")
    }

    @Test
    fun `applyState keeps viewers when the password stays the same or is cleared`() {
        val fixture = Fixture()
        val caster = fixture.caster()
        fixture.ownerFor(caster)
        fixture.startBroadcast(caster, password = "same")
        val first = RecordingClient(ip = 1)
        fixture.manager.addViewer(caster, first)

        fixture.manager.applyState(caster, LivestreamState(broadcast = true, password = "same"))
        assertThat(fixture.manager.getViewerCount(caster)).isEqualTo(1)

        fixture.manager.applyState(caster, LivestreamState(broadcast = true, password = ""))
        assertThat(fixture.manager.getViewerCount(caster)).isEqualTo(1)
        assertThat(first.sessionEnds).isEmpty()
    }

    @Test
    fun `applyState kicks the listed viewer names`() {
        val fixture = Fixture()
        val caster = fixture.caster()
        fixture.ownerFor(caster)
        fixture.startBroadcast(caster)
        val kicked = RecordingClient(ip = 1)
        val kept = RecordingClient(ip = 2)
        fixture.manager.addViewer(caster, kicked)
        fixture.manager.addViewer(caster, kept)

        fixture.manager.applyState(caster, LivestreamState(broadcast = true, kick = mutableListOf("viewer-1")))

        assertThat(kicked.sessionEnds).containsExactly(SessionEndInformation.SESSION_END_LOGOUT)
        assertThat(fixture.manager.isViewer(kicked)).isFalse()
        assertThat(fixture.manager.getViewerCount(caster)).isEqualTo(1)
        assertThat(fixture.manager.getState(caster).names).containsExactly("Viewer-2")
    }

    @Test
    fun `applyState normalizes mutes and keeps their order`() {
        val fixture = Fixture()
        val caster = fixture.caster()
        fixture.ownerFor(caster)
        fixture.startBroadcast(caster)

        fixture.manager.applyState(
            caster,
            LivestreamState(broadcast = true, mutes = mutableListOf("Zed", "amy")),
        )

        assertThat(fixture.manager.getState(caster).mutes).containsExactly("zed", "amy")
    }

    @Test
    fun `applyState bans a viewing name by recording its ip and kicking it`() {
        val fixture = Fixture()
        val caster = fixture.caster()
        fixture.ownerFor(caster)
        fixture.startBroadcast(caster)
        val viewer = RecordingClient(ip = 42)
        fixture.manager.addViewer(caster, viewer)

        fixture.manager.applyState(caster, LivestreamState(broadcast = true, bans = mutableListOf("viewer-1")))

        assertThat(viewer.sessionEnds).containsExactly(SessionEndInformation.SESSION_END_LOGOUT)
        assertThat(fixture.manager.isViewer(viewer)).isFalse()
        assertThat(fixture.manager.getState(caster).bans).containsExactly("viewer-1")
    }

    @Test
    fun `applyState drops bans absent from the incoming state`() {
        val fixture = Fixture()
        val caster = fixture.caster()
        fixture.ownerFor(caster)
        fixture.startBroadcast(caster)
        fixture.manager.applyState(
            caster,
            LivestreamState(broadcast = true, bans = mutableListOf("zeta", "alpha")),
        )

        fixture.manager.applyState(caster, LivestreamState(broadcast = true, bans = mutableListOf("zeta")))

        assertThat(fixture.manager.getState(caster).bans).containsExactly("zeta")
    }

    @Test
    fun `getState reports description password viewers mutes and bans`() {
        val fixture = Fixture()
        val caster = fixture.caster()
        fixture.ownerFor(caster)
        fixture.startBroadcast(caster, password = "pw", description = "stream")
        fixture.manager.addViewer(caster, RecordingClient(ip = 1))
        fixture.manager.addViewer(caster, RecordingClient(ip = 2))
        fixture.manager.applyState(
            caster,
            LivestreamState(
                description = "stream",
                broadcast = true,
                password = "pw",
                mutes = mutableListOf("viewer-1"),
                bans = mutableListOf("zeta", "alpha"),
            ),
        )

        val state = fixture.manager.getState(caster)
        assertThat(state.description).isEqualTo("stream")
        assertThat(state.broadcast).isTrue()
        assertThat(state.password).isEqualTo("pw")
        assertThat(state.names).containsExactly("Viewer-1", "Viewer-2")
        assertThat(state.mutes).containsExactly("viewer-1")
        assertThat(state.bans).containsExactly("alpha", "zeta")
    }

    @Test
    fun `getState of an unknown caster returns the default state`() {
        val fixture = Fixture()
        val state = fixture.manager.getState(fixture.caster())

        assertThat(state.description).isEmpty()
        assertThat(state.broadcast).isFalse()
        assertThat(state.password).isEmpty()
        assertThat(state.names).isEmpty()
        assertThat(state.mutes).isEmpty()
        assertThat(state.bans).isEmpty()
    }

    @Test
    fun `getState getPassword and getDescription default when no session exists`() {
        val fixture = Fixture()
        val caster = fixture.caster()

        assertThat(fixture.manager.isBroadcasting(caster)).isFalse()
        assertThat(fixture.manager.getPassword(caster)).isEmpty()
        assertThat(fixture.manager.getDescription(caster)).isEmpty()
        assertThat(fixture.manager.getViewerCount(caster)).isZero()
        assertThat(fixture.manager.getCasterForViewer(RecordingClient())).isNull()
    }

    @Test
    fun `setInitialState stores the password description and live record`() {
        val fixture = Fixture()
        val caster = fixture.caster()

        fixture.manager.setInitialState(caster, "pw", "desc", 5)

        assertThat(fixture.manager.getPassword(caster)).isEqualTo("pw")
        assertThat(fixture.manager.getDescription(caster)).isEqualTo("desc")

        fixture.manager.applyState(caster, LivestreamState(broadcast = true))
        fixture.manager.addViewer(caster, RecordingClient(ip = 1))

        assertThat(fixture.liveRecords).isEmpty()
    }

    @Test
    fun `setInitialState ignores a null caster`() {
        val fixture = Fixture()

        fixture.manager.setInitialState(null, "pw", "desc", 1)

        assertThat(fixture.manager.getPassword(null)).isEmpty()
    }

    @Test
    fun `stopBroadcast disconnects viewers and marks the owner not broadcasting`() {
        val fixture = Fixture()
        val caster = fixture.caster()
        val owner = fixture.ownerFor(caster)
        fixture.startBroadcast(caster, password = "pw")
        val viewer = RecordingClient(ip = 1)
        fixture.manager.addViewer(caster, viewer)

        fixture.manager.stopBroadcast(caster, clearAll = false)

        assertThat(fixture.manager.isBroadcasting(caster)).isFalse()
        assertThat(viewer.sessionEnds).containsExactly(SessionEndInformation.SESSION_END_LOGOUT)
        assertThat(owner.isLivestreamBroadcaster).isFalse()
        // Without clearAll the password and description survive the stop.
        assertThat(fixture.manager.getPassword(caster)).isEqualTo("pw")
        assertThat(fixture.manager.getDescription(caster)).isEqualTo("Going live")
    }

    @Test
    fun `stopBroadcast with clearAll resets counters passwords descriptions bans and records`() {
        val fixture = Fixture()
        val caster = fixture.caster()
        fixture.ownerFor(caster)
        fixture.startBroadcast(caster, password = "pw", description = "desc")
        fixture.manager.addViewer(caster, RecordingClient(ip = 1))
        fixture.manager.addViewer(caster, RecordingClient(ip = 2))
        fixture.manager.applyState(caster, LivestreamState(broadcast = true, bans = mutableListOf("somebody")))

        fixture.manager.stopBroadcast(caster, clearAll = true)
        fixture.manager.applyState(caster, LivestreamState(broadcast = true))
        fixture.manager.addViewer(caster, RecordingClient(ip = 3))

        assertThat(fixture.manager.getPassword(caster)).isEmpty()
        assertThat(fixture.manager.getDescription(caster)).isEmpty()
        assertThat(fixture.manager.getState(caster).bans).isEmpty()
        // viewerCounter and liveRecord reset: the next viewer is Viewer-1 and sets record 1 again.
        assertThat(fixture.manager.getState(caster).names).containsExactly("Viewer-1")
        assertThat(fixture.liveRecords).containsExactly(caster to 1, caster to 2, caster to 1)
    }

    @Test
    fun `getBroadcastTimeString formats the elapsed broadcast time`() {
        val fixture = Fixture()
        val caster = fixture.caster()
        fixture.ownerFor(caster)

        assertThat(fixture.manager.getBroadcastTimeString(caster)).isEqualTo("0 hours, 0 minutes and 0 seconds")

        fixture.startBroadcast(caster)
        fixture.now += 3_661_000

        assertThat(fixture.manager.getBroadcastTimeString(caster)).isEqualTo("1 hours, 1 minutes and 1 seconds")
    }

    // --- viewers --------------------------------------------------------------

    @Test
    fun `addViewer names viewers sequentially and announces the join`() {
        val fixture = Fixture()
        val caster = fixture.caster()
        val owner = fixture.ownerFor(caster)
        fixture.startBroadcast(caster)
        val first = RecordingClient(ip = 1)
        val second = RecordingClient(ip = 2)

        fixture.manager.addViewer(caster, first)
        fixture.manager.addViewer(caster, second)

        assertThat(fixture.manager.getViewerCount(caster)).isEqualTo(2)
        assertThat(fixture.manager.isViewer(first)).isTrue()
        assertThat(fixture.manager.isViewer(second)).isTrue()
        assertThat(fixture.manager.getCasterForViewer(first)).isSameAs(caster)
        assertThat(first.channelEvents)
            .containsExactly(
                ChannelEventCall(CHANNEL_LIVESTREAM, "Viewer-1", ChannelEvent.CHANNELEVENT_JOIN),
                ChannelEventCall(CHANNEL_LIVESTREAM, "Viewer-2", ChannelEvent.CHANNELEVENT_JOIN),
            )
        assertThat(second.channelEvents)
            .containsExactly(ChannelEventCall(CHANNEL_LIVESTREAM, "Viewer-2", ChannelEvent.CHANNELEVENT_JOIN))
        assertThat(owner.channelEvents)
            .containsExactly(
                ChannelEventCall(CHANNEL_LIVESTREAM, "Viewer-1", ChannelEvent.CHANNELEVENT_JOIN),
                ChannelEventCall(CHANNEL_LIVESTREAM, "Viewer-2", ChannelEvent.CHANNELEVENT_JOIN),
            )
    }

    @Test
    fun `addViewer with spy skips the announcement but still counts the viewer`() {
        val fixture = Fixture()
        val caster = fixture.caster()
        val owner = fixture.ownerFor(caster)
        fixture.startBroadcast(caster)
        val spy = RecordingClient(ip = 1)

        fixture.manager.addViewer(caster, spy, spy = true)

        assertThat(fixture.manager.getViewerCount(caster)).isEqualTo(1)
        assertThat(fixture.manager.isViewer(spy)).isTrue()
        assertThat(spy.channelEvents).isEmpty()
        assertThat(owner.channelEvents).isEmpty()
        assertThat(fixture.liveRecords).isEmpty()

        // The spy still consumed viewer slot 1.
        val viewer = RecordingClient(ip = 2)
        fixture.manager.addViewer(caster, viewer)
        assertThat(fixture.manager.getState(caster).names).containsExactly("Viewer-1", "Viewer-2")
    }

    @Test
    fun `addViewer ignores a client that already watches`() {
        val fixture = Fixture()
        val caster = fixture.caster()
        fixture.ownerFor(caster)
        fixture.startBroadcast(caster)
        val viewer = RecordingClient(ip = 1)

        fixture.manager.addViewer(caster, viewer)
        fixture.manager.addViewer(caster, viewer)

        assertThat(fixture.manager.getViewerCount(caster)).isEqualTo(1)
        assertThat(fixture.manager.getState(caster).names).containsExactly("Viewer-1")
    }

    @Test
    fun `addViewer records a new live record and notifies the owner`() {
        val fixture = Fixture()
        val caster = fixture.caster()
        val owner = fixture.ownerFor(caster)
        fixture.startBroadcast(caster)
        val first = RecordingClient(ip = 1)
        val second = RecordingClient(ip = 2)
        val third = RecordingClient(ip = 3)

        fixture.manager.addViewer(caster, first)
        fixture.manager.addViewer(caster, second)
        fixture.manager.removeViewer(first)
        fixture.manager.addViewer(caster, third)

        assertThat(fixture.liveRecords).containsExactly(caster to 1, caster to 2)
        assertThat(owner.textMessages).containsExactly(
            MessageClass.MESSAGE_LOOK to "New record: 1 people are watching your livestream now.",
            MessageClass.MESSAGE_LOOK to "New record: 2 people are watching your livestream now.",
        )
    }

    @Test
    fun `removeViewer announces the leave to the remaining viewers`() {
        val fixture = Fixture()
        val caster = fixture.caster()
        val owner = fixture.ownerFor(caster)
        fixture.startBroadcast(caster)
        val first = RecordingClient(ip = 1)
        val second = RecordingClient(ip = 2)
        fixture.manager.addViewer(caster, first)
        fixture.manager.addViewer(caster, second)
        first.channelEvents.clear()
        second.channelEvents.clear()
        owner.channelEvents.clear()

        fixture.manager.removeViewer(first)

        assertThat(fixture.manager.isViewer(first)).isFalse()
        assertThat(fixture.manager.getViewerCount(caster)).isEqualTo(1)
        assertThat(first.channelEvents).isEmpty()
        assertThat(second.channelEvents)
            .containsExactly(ChannelEventCall(CHANNEL_LIVESTREAM, "Viewer-1", ChannelEvent.CHANNELEVENT_LEAVE))
        assertThat(owner.channelEvents)
            .containsExactly(ChannelEventCall(CHANNEL_LIVESTREAM, "Viewer-1", ChannelEvent.CHANNELEVENT_LEAVE))
    }

    @Test
    fun `removeViewer with spy skips the leave announcement`() {
        val fixture = Fixture()
        val caster = fixture.caster()
        val owner = fixture.ownerFor(caster)
        fixture.startBroadcast(caster)
        val viewer = RecordingClient(ip = 1)
        fixture.manager.addViewer(caster, viewer)
        owner.channelEvents.clear()

        fixture.manager.removeViewer(viewer, spy = true)

        assertThat(fixture.manager.isViewer(viewer)).isFalse()
        assertThat(owner.channelEvents).isEmpty()
    }

    @Test
    fun `removeViewer of an unknown client is a no-op`() {
        val fixture = Fixture()
        val caster = fixture.caster()
        fixture.ownerFor(caster)
        fixture.startBroadcast(caster)

        fixture.manager.removeViewer(RecordingClient(ip = 1))

        assertThat(fixture.manager.getViewerCount(caster)).isZero()
    }

    // --- canWatch -------------------------------------------------------------

    @Test
    fun `canWatch reports unavailable for a null or removed caster`() {
        val fixture = Fixture()
        val caster = fixture.caster()
        fixture.ownerFor(caster)
        fixture.startBroadcast(caster)
        val viewer = RecordingClient(ip = 1)

        assertThat(fixture.manager.canWatch(null, viewer, "")).isEqualTo(CanWatchResult(false, "Livestream unavailable."))

        caster.setRemoved()
        assertThat(fixture.manager.canWatch(caster, viewer, "")).isEqualTo(CanWatchResult(false, "Livestream unavailable."))
    }

    @Test
    fun `canWatch reports a caster that is not streaming`() {
        val fixture = Fixture()
        val caster = fixture.caster()
        fixture.ownerFor(caster)

        assertThat(fixture.manager.canWatch(caster, RecordingClient(ip = 1), ""))
            .isEqualTo(CanWatchResult(false, "This player is not currently streaming."))
    }

    @Test
    fun `canWatch reports a caster without a client`() {
        val fixture = Fixture()
        val caster = fixture.caster()
        val sessionCaster = fixture.caster(id = 2)
        fixture.startBroadcast(sessionCaster)

        assertThat(fixture.manager.canWatch(sessionCaster, RecordingClient(ip = 1), ""))
            .isEqualTo(CanWatchResult(false, "This player is no longer broadcasting."))
        assertThat(fixture.manager.canWatch(caster, RecordingClient(ip = 1), ""))
            .isEqualTo(CanWatchResult(false, "This player is not currently streaming."))
    }

    @Test
    fun `canWatch denies an ip banned from viewing`() {
        val fixture = Fixture()
        val caster = fixture.caster()
        fixture.ownerFor(caster)
        fixture.startBroadcast(caster)
        val banned = RecordingClient(ip = 42)
        fixture.manager.addViewer(caster, banned)
        fixture.manager.applyState(caster, LivestreamState(broadcast = true, bans = mutableListOf("Viewer-1")))

        assertThat(fixture.manager.canWatch(caster, RecordingClient(ip = 42), ""))
            .isEqualTo(CanWatchResult(false, "Access denied: You are banned from viewing this livestream."))

        // A different ip is unaffected.
        assertThat(fixture.manager.canWatch(caster, RecordingClient(ip = 8), "").allowed).isTrue()
    }

    @Test
    fun `canWatch requires and trims the password`() {
        val fixture = Fixture()
        val caster = fixture.caster()
        fixture.ownerFor(caster)
        fixture.startBroadcast(caster, password = "secret")

        assertThat(fixture.manager.canWatch(caster, RecordingClient(ip = 1), "wrong"))
            .isEqualTo(CanWatchResult(false, "Incorrect password. Access to this livestream is protected."))
        assertThat(fixture.manager.canWatch(caster, RecordingClient(ip = 1), " secret ").allowed).isTrue()
    }

    @Test
    fun `canWatch enforces the maximum viewers per ip`() {
        val fixture = Fixture(perIpLimit = 2)
        val caster = fixture.caster()
        fixture.ownerFor(caster)
        fixture.startBroadcast(caster)
        fixture.manager.addViewer(caster, RecordingClient(ip = 42))
        fixture.manager.addViewer(caster, RecordingClient(ip = 42))

        assertThat(fixture.manager.canWatch(caster, RecordingClient(ip = 42), ""))
            .isEqualTo(CanWatchResult(false, "Livestream viewer limit per IP reached. Please try again later."))
        assertThat(fixture.manager.canWatch(caster, RecordingClient(ip = 7), "").allowed).isTrue()
    }

    @Test
    fun `canWatch enforces the maximum viewers and the premium limit`() {
        val normal = Fixture(viewerLimit = 1)
        val normalCaster = normal.caster()
        normal.ownerFor(normalCaster)
        normal.startBroadcast(normalCaster)
        normal.manager.addViewer(normalCaster, RecordingClient(ip = 1))
        assertThat(normal.manager.canWatch(normalCaster, RecordingClient(ip = 2), ""))
            .isEqualTo(CanWatchResult(false, "Livestream viewer limit reached. Please try again later."))

        val premium = Fixture(viewerLimit = 1, premiumViewerLimit = 2)
        val premiumCaster = premium.caster(premium = true)
        premium.ownerFor(premiumCaster)
        premium.startBroadcast(premiumCaster)
        premium.manager.addViewer(premiumCaster, RecordingClient(ip = 1))
        assertThat(premium.manager.canWatch(premiumCaster, RecordingClient(ip = 2), "").allowed).isTrue()
    }

    @Test
    fun `canWatch enforces the caster minimum level`() {
        val fixture = Fixture(minLevel = 200)
        val caster = fixture.caster(level = 100)
        fixture.ownerFor(caster)
        fixture.startBroadcast(caster)

        assertThat(fixture.manager.canWatch(caster, RecordingClient(ip = 1), ""))
            .isEqualTo(CanWatchResult(false, "The caster does not meet the minimum level requirement to broadcast."))
    }

    // --- chat -----------------------------------------------------------------

    @Test
    fun `handleChat forwards a viewer message to the livestream channel`() {
        val fixture = Fixture()
        val caster = fixture.caster()
        val owner = fixture.ownerFor(caster)
        fixture.startBroadcast(caster)
        val viewer = RecordingClient(ip = 1)
        fixture.manager.addViewer(caster, viewer)

        fixture.manager.handleChat(viewer, "hello stream")

        val expected = ChannelMessageCall("Viewer-1", "hello stream", SpeakClasses.TALKTYPE_CHANNEL_Y, CHANNEL_LIVESTREAM)
        assertThat(viewer.channelMessages).containsExactly(expected)
        assertThat(owner.channelMessages).containsExactly(expected)
    }

    @Test
    fun `handleChat forwards an owner message to the livestream channel`() {
        val fixture = Fixture()
        val caster = fixture.caster()
        val owner = fixture.ownerFor(caster)
        fixture.startBroadcast(caster)
        val viewer = RecordingClient(ip = 1)
        fixture.manager.addViewer(caster, viewer)

        fixture.manager.handleChat(owner, "welcome")

        val expected = ChannelMessageCall("Caster", "welcome", SpeakClasses.TALKTYPE_CHANNEL_R1, CHANNEL_LIVESTREAM)
        assertThat(owner.channelMessages).containsExactly(expected)
        assertThat(viewer.channelMessages).containsExactly(expected)
    }

    @Test
    fun `handleChat ignores the owner while not broadcasting`() {
        val fixture = Fixture()
        val caster = fixture.caster()
        val owner = fixture.ownerFor(caster)

        fixture.manager.handleChat(owner, "hello")

        assertThat(owner.channelMessages).isEmpty()
    }

    @Test
    fun `handleChat ignores clients that are not part of a session`() {
        val fixture = Fixture()

        fixture.manager.handleChat(RecordingClient(), "hello")
        fixture.manager.handleChat(null, "hello")
        fixture.manager.handleChat(RecordingClient(), "")
    }

    @Test
    fun `handleChat show lists the spectators`() {
        val fixture = Fixture()
        val caster = fixture.caster()
        fixture.ownerFor(caster)
        fixture.startBroadcast(caster)
        val first = RecordingClient(ip = 1)
        val second = RecordingClient(ip = 2)
        fixture.manager.addViewer(caster, first)
        fixture.manager.addViewer(caster, second)

        fixture.manager.handleChat(first, "/show")

        assertThat(first.textMessages)
            .containsExactly(MessageClass.MESSAGE_STATUS to "2 spectators, Viewer-1, Viewer-2.")
    }

    @Test
    fun `handleChat show uses the singular for one spectator`() {
        val fixture = Fixture()
        val caster = fixture.caster()
        fixture.ownerFor(caster)
        fixture.startBroadcast(caster)
        val viewer = RecordingClient(ip = 1)
        fixture.manager.addViewer(caster, viewer)

        fixture.manager.handleChat(viewer, "/show")

        assertThat(viewer.textMessages).containsExactly(MessageClass.MESSAGE_STATUS to "1 spectator, Viewer-1.")
    }

    @Test
    fun `handleChat name renames the viewer and moves its mute`() {
        val fixture = Fixture()
        val caster = fixture.caster()
        val owner = fixture.ownerFor(caster)
        fixture.startBroadcast(caster)
        val viewer = RecordingClient(ip = 1)
        fixture.manager.addViewer(caster, viewer)
        fixture.manager.applyState(caster, LivestreamState(broadcast = true, mutes = mutableListOf("Viewer-1")))
        fixture.manager.handleChat(viewer, "before")

        fixture.manager.handleChat(viewer, "/name Alice")

        assertThat(viewer.textMessages).contains(MessageClass.MESSAGE_FAILURE to "You are muted.")
        assertThat(viewer.textMessages).contains(MessageClass.MESSAGE_STATUS to "Name changed to 'Alice'.")
        assertThat(owner.channelMessages).contains(
            ChannelMessageCall("", "Viewer-1 was renamed to Alice.", SpeakClasses.TALKTYPE_CHANNEL_O, CHANNEL_LIVESTREAM),
        )
        assertThat(fixture.manager.getState(caster).names).containsExactly("Alice")
        assertThat(fixture.manager.getState(caster).mutes).containsExactly("alice")

        // The viewer is still muted under the new name.
        fixture.manager.handleChat(viewer, "after")
        assertThat(viewer.textMessages).contains(MessageClass.MESSAGE_FAILURE to "You are muted.")
    }

    @Test
    fun `handleChat name rejects missing short long banned and duplicate names`() {
        val fixture = Fixture()
        val caster = fixture.caster()
        fixture.ownerFor(caster)
        fixture.startBroadcast(caster)
        val first = RecordingClient(ip = 1)
        val second = RecordingClient(ip = 2)
        fixture.manager.addViewer(caster, first)
        fixture.manager.addViewer(caster, second)
        fixture.manager.applyState(caster, LivestreamState(broadcast = true, bans = mutableListOf("banned")))

        fixture.manager.handleChat(first, "/name")
        assertThat(first.textMessages.last()).isEqualTo(MessageClass.MESSAGE_FAILURE to "Please provide a new name.")

        fixture.manager.handleChat(first, "/name   ")
        assertThat(first.textMessages.last()).isEqualTo(MessageClass.MESSAGE_FAILURE to "Please provide a new name.")

        fixture.manager.handleChat(first, "/name Al")
        assertThat(first.textMessages.last())
            .isEqualTo(MessageClass.MESSAGE_FAILURE to "Name must be between 3 and 30 letters.")

        fixture.manager.handleChat(first, "/name " + "A".repeat(31))
        assertThat(first.textMessages.last())
            .isEqualTo(MessageClass.MESSAGE_FAILURE to "Name must be between 3 and 30 letters.")

        fixture.manager.handleChat(first, "/name Banned")
        assertThat(first.textMessages.last())
            .isEqualTo(MessageClass.MESSAGE_FAILURE to "This name is banned from this livestream.")

        fixture.manager.handleChat(first, "/name Viewer-2")
        assertThat(first.textMessages.last())
            .isEqualTo(MessageClass.MESSAGE_FAILURE to "There is already someone with that name.")

        // Canary also rejects a viewer's own current name.
        fixture.manager.handleChat(first, "/name Viewer-1")
        assertThat(first.textMessages.last())
            .isEqualTo(MessageClass.MESSAGE_FAILURE to "There is already someone with that name.")
    }

    @Test
    fun `handleChat reports available commands for an unknown slash command`() {
        val fixture = Fixture()
        val caster = fixture.caster()
        fixture.ownerFor(caster)
        fixture.startBroadcast(caster)
        val viewer = RecordingClient(ip = 1)
        fixture.manager.addViewer(caster, viewer)

        fixture.manager.handleChat(viewer, "/dance")

        assertThat(viewer.textMessages)
            .containsExactly(MessageClass.MESSAGE_FAILURE to "Available commands: /name, /show.")
    }

    @Test
    fun `handleChat tells a muted viewer that it is muted`() {
        val fixture = Fixture()
        val caster = fixture.caster()
        val owner = fixture.ownerFor(caster)
        fixture.startBroadcast(caster)
        val viewer = RecordingClient(ip = 1)
        fixture.manager.addViewer(caster, viewer)
        fixture.manager.applyState(caster, LivestreamState(broadcast = true, mutes = mutableListOf("viewer-1")))

        fixture.manager.handleChat(viewer, "hello")

        assertThat(viewer.textMessages).containsExactly(MessageClass.MESSAGE_FAILURE to "You are muted.")
        assertThat(owner.channelMessages).isEmpty()
    }

    @Test
    fun `handleChat rate limits a viewer to three messages per five seconds`() {
        val fixture = Fixture()
        val caster = fixture.caster()
        fixture.ownerFor(caster)
        fixture.startBroadcast(caster)
        val viewer = RecordingClient(ip = 1)
        fixture.manager.addViewer(caster, viewer)

        fixture.manager.handleChat(viewer, "one")
        fixture.manager.handleChat(viewer, "two")
        fixture.manager.handleChat(viewer, "three")
        fixture.manager.handleChat(viewer, "four")

        assertThat(viewer.channelMessages.map { it.text }).containsExactly("one", "two", "three")
        assertThat(viewer.textMessages)
            .containsExactly(MessageClass.MESSAGE_STATUS to "Please wait 6 seconds to send another message.")

        fixture.now += 5_001
        fixture.manager.handleChat(viewer, "five")

        assertThat(viewer.channelMessages.map { it.text }).containsExactly("one", "two", "three", "five")
    }

    // --- broadcastPacket ------------------------------------------------------

    @Test
    fun `broadcastPacket forwards a forwardable opcode to every viewer`() {
        val fixture = Fixture()
        val caster = fixture.caster()
        val owner = fixture.ownerFor(caster)
        fixture.startBroadcast(caster)
        val first = RecordingClient(ip = 1)
        val second = RecordingClient(ip = 2)
        fixture.manager.addViewer(caster, first)
        fixture.manager.addViewer(caster, second)
        val message = message(0x1D)

        fixture.manager.broadcastPacket(caster, owner, message)

        assertThat(first.writtenMessages).containsExactly(message)
        assertThat(second.writtenMessages).containsExactly(message)
        assertThat(first.mapDescriptions).isEmpty()
    }

    @Test
    fun `broadcastPacket drops opcodes that are not forwardable`() {
        val fixture = Fixture()
        val caster = fixture.caster()
        val owner = fixture.ownerFor(caster)
        fixture.startBroadcast(caster)
        val viewer = RecordingClient(ip = 1)
        fixture.manager.addViewer(caster, viewer)

        fixture.manager.broadcastPacket(caster, owner, message(0x00))
        fixture.manager.broadcastPacket(caster, owner, LivestreamMessage(ByteArray(8), 0))

        assertThat(viewer.writtenMessages).isEmpty()
    }

    @Test
    fun `broadcastPacket turns a full map packet into a map description and rooted icon`() {
        val fixture = Fixture()
        val caster = fixture.caster()
        val owner = fixture.ownerFor(caster)
        fixture.startBroadcast(caster)
        val current = RecordingClient(ip = 1)
        val old = RecordingClient(ip = 2)
        old.oldProtocol = true
        fixture.manager.addViewer(caster, current)
        fixture.manager.addViewer(caster, old)

        fixture.manager.broadcastPacket(caster, owner, message(0x64))

        assertThat(current.mapDescriptions).containsExactly(caster.position)
        assertThat(current.iconFrames).containsExactly(setOf(PlayerIcon.Rooted) to IconBakragore.None)
        assertThat(old.mapDescriptions).containsExactly(caster.position)
        assertThat(old.iconFrames).isEmpty()
        assertThat(current.writtenMessages).isEmpty()
    }

    @Test
    fun `broadcastPacket forwards allowed text message classes`() {
        val fixture = Fixture()
        val caster = fixture.caster()
        val owner = fixture.ownerFor(caster)
        fixture.startBroadcast(caster)
        val viewer = RecordingClient(ip = 1)
        fixture.manager.addViewer(caster, viewer)

        val damage = message(0xB4, MessageClass.MESSAGE_DAMAGE_DEALT.value)
        val status = message(0xB4, MessageClass.MESSAGE_STATUS.value)
        fixture.manager.broadcastPacket(caster, owner, damage)
        fixture.manager.broadcastPacket(caster, owner, status)

        assertThat(viewer.writtenMessages).containsExactly(damage)
    }

    @Test
    fun `broadcastPacket drops private and livestream channel speak types`() {
        val fixture = Fixture()
        val caster = fixture.caster()
        val owner = fixture.ownerFor(caster)
        owner.oldProtocol = true
        fixture.startBroadcast(caster)
        val viewer = RecordingClient(ip = 1)
        fixture.manager.addViewer(caster, viewer)

        val say = speakMessage(SpeakClasses.TALKTYPE_SAY.value)
        val privateSay = speakMessage(SpeakClasses.TALKTYPE_PRIVATE_FROM.value)
        val livestream = speakMessage(SpeakClasses.TALKTYPE_CHANNEL_Y.value, channelId = CHANNEL_LIVESTREAM)
        val channel = speakMessage(SpeakClasses.TALKTYPE_CHANNEL_Y.value, channelId = 7)
        fixture.manager.broadcastPacket(caster, owner, privateSay)
        fixture.manager.broadcastPacket(caster, owner, livestream)
        assertThat(viewer.writtenMessages).isEmpty()

        fixture.manager.broadcastPacket(caster, owner, say)
        fixture.manager.broadcastPacket(caster, owner, channel)

        assertThat(viewer.writtenMessages).containsExactly(say, channel)
    }

    @Test
    fun `broadcastPacket ignores packets without a broadcasting session or viewers`() {
        val fixture = Fixture()
        val caster = fixture.caster()
        val owner = fixture.ownerFor(caster)
        val message = message(0x1D)

        fixture.manager.broadcastPacket(caster, owner, message)

        fixture.startBroadcast(caster)
        fixture.manager.broadcastPacket(caster, owner, message)

        assertThat(owner.writtenMessages).isEmpty()
    }

    // --- casters and storage --------------------------------------------------

    @Test
    fun `getBroadcastingCasters filters by password and orders by viewer count`() {
        val fixture = Fixture()
        val small = fixture.caster(id = 1, name = "Small")
        val big = fixture.caster(id = 2, name = "Big")
        val locked = fixture.caster(id = 3, name = "Locked")
        val removed = fixture.caster(id = 4, name = "Removed")
        fixture.ownerFor(small)
        fixture.ownerFor(big)
        fixture.ownerFor(locked)
        fixture.ownerFor(removed)
        fixture.startBroadcast(small)
        fixture.startBroadcast(big)
        fixture.startBroadcast(locked, password = "pw")
        fixture.startBroadcast(removed)

        fixture.manager.addViewer(small, RecordingClient(ip = 1))
        fixture.manager.addViewer(big, RecordingClient(ip = 2))
        fixture.manager.addViewer(big, RecordingClient(ip = 3))
        removed.setRemoved()

        assertThat(fixture.manager.getBroadcastingCasters()).containsExactly(big, small, locked)
        assertThat(fixture.manager.getBroadcastingCasters("pw")).containsExactly(locked)
    }

    @Test
    fun `updateViewerStorage writes the viewer count for an active broadcast`() {
        val fixture = Fixture()
        val caster = fixture.caster()
        fixture.ownerFor(caster)
        fixture.startBroadcast(caster)
        fixture.manager.addViewer(caster, RecordingClient(ip = 1))
        fixture.manager.addViewer(caster, RecordingClient(ip = 2))

        fixture.manager.updateViewerStorage(caster)
        assertThat(fixture.databaseTasks).isEmpty()

        fixture.tableExists = true
        fixture.manager.updateViewerStorage(caster)

        assertThat(fixture.databaseTasks).containsExactly(
            "UPDATE `active_livestream_casters` SET `livestream_viewers` = 2 WHERE `caster_id` = 1",
        )
    }

    @Test
    fun `updateViewerStorage ignores casters that are not broadcasting`() {
        val fixture = Fixture()
        val caster = fixture.caster()
        fixture.tableExists = true

        fixture.manager.updateViewerStorage(caster)
        fixture.manager.updateViewerStorage(null)

        assertThat(fixture.databaseTasks).isEmpty()
    }

    @Test
    fun `manager operations ignore null players and clients`() {
        val fixture = Fixture()
        val caster = fixture.caster()

        fixture.manager.applyState(null, LivestreamState(broadcast = true))
        fixture.manager.stopBroadcast(null, clearAll = true)
        fixture.manager.addViewer(null, RecordingClient())
        fixture.manager.addViewer(caster, null)
        fixture.manager.removeViewer(null)
        fixture.manager.broadcastPacket(null, null, message(0x1D))
        fixture.manager.broadcastPacket(caster, null, message(0x1D))

        assertThat(fixture.manager.isBroadcasting(null)).isFalse()
        assertThat(fixture.manager.isViewer(null)).isFalse()
        assertThat(fixture.manager.getViewerCount(null)).isZero()
        assertThat(fixture.manager.getCasterForViewer(null)).isNull()
    }
}
