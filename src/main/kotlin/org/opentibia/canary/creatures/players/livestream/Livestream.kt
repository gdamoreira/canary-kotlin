package org.opentibia.canary.creatures.players.livestream

import org.opentibia.canary.chat.ChannelEvent
import org.opentibia.canary.chat.SpeakClasses
import org.opentibia.canary.combat.condition.PlayerIcon
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.database.SqlDialect
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.protocol.MessageClass
import java.lang.ref.WeakReference
import java.util.TreeMap

/** `CHANNEL_LIVESTREAM` (`src/utils/const.hpp:22`). */
const val CHANNEL_LIVESTREAM = 0xFFFE

/** `IconBakragore` (`src/enums/player_icons.hpp:52-62`). */
enum class IconBakragore(val value: Int) {
    None(0),
    Taint1(1),
    Taint2(2),
    Taint3(3),
    Taint4(4),
    Taint5(5),
    Taint6(6),
    Taint7(7),
    Taint8(8),
    Taint9(9),
}

/** `SessionEndInformations` (`src/server/server_definitions.hpp:47-53`). */
enum class SessionEndInformation {
    SESSION_END_LOGOUT,
    SESSION_END_UNK2,
    SESSION_END_FORCECLOSE,
    SESSION_END_UNK3,
}

/**
 * Seam for the outbound `NetworkMessage` (`src/server/network/message/networkmessage.hpp`).
 * [buffer] is the raw message storage, [length] is `NetworkMessage::getLength()`
 * and [initialBufferPosition] is `NetworkMessage::INITIAL_BUFFER_POSITION` (8);
 * the opcode of an outbound packet sits at that offset in [buffer].
 */
class LivestreamMessage(
    val buffer: ByteArray,
    val length: Int,
    val initialBufferPosition: Int = INITIAL_BUFFER_POSITION,
) {
    companion object {
        /** `NetworkMessage::INITIAL_BUFFER_POSITION` (networkmessage.hpp:28). */
        const val INITIAL_BUFFER_POSITION = 8
    }
}

/** `struct LivestreamViewerInfo` (src/creatures/players/livestream/livestream.hpp:19-23). */
data class LivestreamViewerInfo(
    var name: String,
    val id: Int,
    val ip: Long,
)

/** `struct LivestreamState` (src/creatures/players/livestream/livestream.hpp:25-33). */
data class LivestreamState(
    var description: String = "",
    var broadcast: Boolean = false,
    var password: String = "",
    var names: MutableList<String> = mutableListOf(),
    var mutes: MutableList<String> = mutableListOf(),
    var bans: MutableList<String> = mutableListOf(),
    var kick: MutableList<String> = mutableListOf(),
)

/**
 * Result of [LivestreamManager.canWatch]; Kotlin folds Canary's
 * `std::string &reason` out-parameter (livestream.hpp:52) the same way
 * `RemoveCoinsResult` folds its C++ out-parameters.
 */
data class CanWatchResult(
    val allowed: Boolean,
    val reason: String = "",
)

/**
 * Protocol seam for the `ProtocolGame` surface `LivestreamManager` drives
 * (protocolgame.hpp:188-648). A recording/real implementation stands in for
 * `std::shared_ptr<ProtocolGame>`; `null` models an absent client.
 */
interface LivestreamClient {
    /** `ProtocolGame::player` (protocolgame.hpp:621). */
    val player: Player?

    /** `Protocol::getIP()` (`src/server/network/protocol/protocol.hpp:50`). */
    fun getIP(): Long

    /** `ProtocolGame::oldProtocol` (protocolgame.hpp:640). */
    var oldProtocol: Boolean

    /** `ProtocolGame::m_isLivestreamBroadcaster` (protocolgame.hpp:645). */
    var isLivestreamBroadcaster: Boolean

    /** `ProtocolGame::m_livestreamMessageCooldownTime` (protocolgame.hpp:647). */
    var livestreamMessageCooldownTime: Long

    /** `ProtocolGame::m_livestreamMessageCount` (protocolgame.hpp:648). */
    var livestreamMessageCount: Int

    /** `ProtocolGame::writeToOutputBuffer` (protocolgame.hpp). */
    fun writeToOutputBuffer(message: LivestreamMessage)

    /** `ProtocolGame::sendMapDescription` (protocolgame.hpp:480). */
    fun sendMapDescription(position: Position)

    /** `ProtocolGame::sendIcons` (protocolgame.hpp:323). */
    fun sendIcons(icons: Set<PlayerIcon>, bakragore: IconBakragore)

    /** `ProtocolGame::sendSessionEndInformation` (protocolgame.hpp:188). */
    fun sendSessionEndInformation(information: SessionEndInformation)

    /**
     * `ProtocolGame::sendChannel` (protocolgame.hpp:318). Canary's livestream
     * call passes `nullptr` user lists, so the port omits them.
     */
    fun sendChannel(channelId: Int, channelName: String)

    /** `ProtocolGame::sendChannelMessage` (protocolgame.hpp:313). */
    fun sendChannelMessage(author: String, text: String, type: SpeakClasses, channelId: Int)

    /** `ProtocolGame::sendChannelEvent` (protocolgame.hpp:314). */
    fun sendChannelEvent(channelId: Int, viewerName: String, event: ChannelEvent)

    /** `ProtocolGame::sendTextMessage` (`TextMessage`, protocolgame.cpp:6070). */
    fun sendTextMessage(type: MessageClass, text: String)
}

/**
 * Mirror of Canary `LivestreamManager`
 * (src/creatures/players/livestream/livestream.hpp:35-95,
 * livestream.cpp:93-818), the broadcaster/viewer session registry for the
 * in-client livestream feature.
 *
 * AGENTS.md 1.1 replaces the `g_livestream()`/`getInstance()` singleton and the
 * `g_configManager()`/`g_databaseTasks()`/`KV` reads with constructor-injected
 * seams: [now] is `OTSYS_TIME()`, [casterClient] is `Player::getClient()`,
 * [saveLiveRecord] is `Player::kv()->scoped("livestream-system")->set("live-record", ...)`,
 * the four `maximum*`/`casterMinLevel` seams are the `LIVESTREAM_*` config
 * values, [tableExists] is `DatabaseManager::tableExists` and
 * [executeDatabaseTask] is `g_databaseTasks().execute`.
 *
 * PARITY NOTES:
 *  - `Session::caster` and `Session::owner` keep Canary's `std::weak_ptr`
 *    semantics through [WeakReference], so a destroyed player does not keep a
 *    session alive.
 *  - `viewers` and `viewerToCaster` are ordered maps keyed by client identity
 *    (Canary keys by `ProtocolGame` pointer); iteration order is insertion
 *    order instead of pointer order.
 *  - `normalizeViewerName` uses Unicode lower-casing (`String.lowercase`)
 *    rather than Canary's ASCII `asLowerCaseString`; for ASCII viewer names
 *    the behavior is identical.
 *  - [getBroadcastingCasters] uses a stable viewer-count sort
 *    (`std::ranges::sort` is unstable, so C++ tie order is unspecified).
 *  - [canWatch] folds the `reason` out-parameter into [CanWatchResult].
 */
class LivestreamManager(
    private val now: () -> Long = { System.currentTimeMillis() },
    /** `Player::getClient()` (player.hpp) — the caster's protocol client. */
    private val casterClient: (Player) -> LivestreamClient? = { null },
    /** `Player::kv()->scoped("livestream-system")->set("live-record", ...)` (livestream.cpp:400). */
    private val saveLiveRecord: (Player, Int) -> Unit = { _, _ -> },
    /** `g_configManager().getNumber(LIVESTREAM_MAXIMUM_VIEWERS_PER_IP)`. */
    private val maximumViewersPerIp: () -> Int = { 0 },
    /** `g_configManager().getNumber(LIVESTREAM_MAXIMUM_VIEWERS)`. */
    private val maximumViewers: () -> Int = { 0 },
    /** `g_configManager().getNumber(LIVESTREAM_PREMIUM_MAXIMUM_VIEWERS)`. */
    private val premiumMaximumViewers: () -> Int = { 0 },
    /** `g_configManager().getNumber(LIVESTREAM_CASTER_MIN_LEVEL)`. */
    private val casterMinLevel: () -> Int = { 0 },
    /** `DatabaseManager::tableExists` (livestream.cpp:530). */
    private val tableExists: (String) -> Boolean = { false },
    /** `g_databaseTasks().execute` (livestream.cpp:534). */
    private val executeDatabaseTask: (String) -> Unit = {},
    /**
     * Runtime SQL dialect of the `active_livestream_casters` update
     * (livestream.cpp:534): MariaDB keeps the upstream backtick statement,
     * PostgreSQL/H2 get it unquoted. Defaults to upstream's MariaDB until the
     * composition root wires the detected dialect (`SqlDialect.from`, wave 73
     * K3).
     */
    private val sqlDialect: () -> SqlDialect = { SqlDialect.MARIADB },
) {

    /** `LivestreamManager::Session` (livestream.hpp:60-72). */
    private class Session {
        var caster: WeakReference<Player>? = null
        var owner: WeakReference<LivestreamClient>? = null
        val viewers = linkedMapOf<LivestreamClient, LivestreamViewerInfo>()
        val mutes = mutableListOf<String>()
        val bans = TreeMap<String, Long>()
        var password = ""
        var description = ""
        var broadcasting = false
        var broadcastStarted = 0L
        var liveRecord = 0
        var viewerCounter = 0
    }

    /** `LivestreamManager::sessions` (livestream.hpp:93), keyed by caster guid. */
    private val sessions = linkedMapOf<Long, Session>()

    /** `LivestreamManager::viewerToCaster` (livestream.hpp:94). */
    private val viewerToCaster = linkedMapOf<LivestreamClient, Long>()

    /** `LivestreamManager::getCasterGuid` (livestream.cpp:102-104). */
    private fun getCasterGuid(caster: Player?): Long = caster?.getGUID() ?: 0L

    /** `LivestreamManager::getSession` (livestream.cpp:116-134). */
    private fun getSession(caster: Player?): Session? {
        val guid = getCasterGuid(caster)
        if (guid == 0L) {
            return null
        }
        return sessions[guid]
    }

    /** `LivestreamManager::ensureSession` (livestream.cpp:106-114). */
    private fun ensureSession(caster: Player): Session {
        val session = sessions.getOrPut(getCasterGuid(caster)) { Session() }
        session.caster = WeakReference(caster)
        session.owner = casterClient(caster)?.let { WeakReference(it) }
        return session
    }

    /** `LivestreamManager::isBroadcasting` (livestream.cpp:136-139). */
    fun isBroadcasting(caster: Player?): Boolean {
        val session = getSession(caster) ?: return false
        return session.broadcasting
    }

    /** `LivestreamManager::isViewer` (livestream.cpp:141-143). */
    fun isViewer(client: LivestreamClient?): Boolean = client != null && viewerToCaster.containsKey(client)

    /** `LivestreamManager::getCasterForViewer` (livestream.cpp:145-161). */
    fun getCasterForViewer(client: LivestreamClient?): Player? {
        if (client == null) {
            return null
        }
        val guid = viewerToCaster[client] ?: return null
        return sessions[guid]?.caster?.get()
    }

    /** `LivestreamManager::getState` (livestream.cpp:163-186). */
    fun getState(caster: Player?): LivestreamState {
        val state = LivestreamState()
        val session = getSession(caster) ?: return state

        state.description = session.description
        state.broadcast = session.broadcasting
        state.password = session.password
        for (viewer in session.viewers.values) {
            state.names.add(viewer.name)
        }
        state.mutes.addAll(session.mutes)
        state.bans.addAll(session.bans.keys)
        return state
    }

    /** `LivestreamManager::getViewerCount` (livestream.cpp:188-191). */
    fun getViewerCount(caster: Player?): Int {
        val session = getSession(caster) ?: return 0
        return session.viewers.size
    }

    /** `LivestreamManager::getPassword` (livestream.cpp:193-196). */
    fun getPassword(caster: Player?): String {
        val session = getSession(caster) ?: return ""
        return session.password
    }

    /** `LivestreamManager::getDescription` (livestream.cpp:198-201). */
    fun getDescription(caster: Player?): String {
        val session = getSession(caster) ?: return ""
        return session.description
    }

    /** `LivestreamManager::getBroadcastTimeString` (livestream.cpp:203-214). */
    fun getBroadcastTimeString(caster: Player?): String {
        val session = getSession(caster)
        if (session == null || session.broadcastStarted == 0L) {
            return "0 hours, 0 minutes and 0 seconds"
        }

        val seconds = maxOf(0L, (now() - session.broadcastStarted) / 1000L)
        val hour = seconds / 3600L
        val minute = (seconds / 60L) % 60L
        val second = seconds % 60L
        return "$hour hours, $minute minutes and $second seconds"
    }

    /** `LivestreamManager::getBroadcastingCasters` (livestream.cpp:216-235). */
    fun getBroadcastingCasters(password: String = ""): List<Player> {
        val casters = mutableListOf<Player>()
        for (session in sessions.values) {
            val caster = session.caster?.get() ?: continue
            if (caster.isRemoved() || !session.broadcasting) {
                continue
            }

            if (password.isNotEmpty() && session.password != password) {
                continue
            }

            casters.add(caster)
        }

        return casters.sortedByDescending { getViewerCount(it) }
    }

    /** `LivestreamManager::setInitialState` (livestream.cpp:237-246). */
    fun setInitialState(caster: Player?, password: String, description: String, liveRecord: Int) {
        if (caster == null) {
            return
        }

        val session = ensureSession(caster)
        session.password = password
        session.description = description
        session.liveRecord = liveRecord
    }

    /** `LivestreamManager::applyState` (livestream.cpp:248-290). */
    fun applyState(caster: Player?, state: LivestreamState) {
        if (caster == null) {
            return
        }

        val session = ensureSession(caster)
        val wasBroadcasting = session.broadcasting
        val passwordChanged = session.password != state.password
        if (passwordChanged && state.password.isNotEmpty()) {
            disconnectViewers(session, clearMutes = false)
        }

        if (!state.broadcast && wasBroadcasting) {
            disconnectViewers(session, clearMutes = false)
        }

        kickViewers(session, state.kick)
        session.mutes.clear()
        for (mute in state.mutes) {
            session.mutes.add(normalizeViewerName(mute))
        }
        setBans(session, state.bans)

        if (!wasBroadcasting && state.broadcast) {
            session.broadcastStarted = now()
            session.owner = casterClient(caster)?.let { WeakReference(it) }
            session.owner?.get()?.sendChannel(CHANNEL_LIVESTREAM, "Livestream")
        }

        session.broadcasting = state.broadcast
        session.password = state.password
        session.description = state.description
        session.owner?.get()?.isLivestreamBroadcaster = session.broadcasting

        if (!session.broadcasting && session.viewers.isEmpty() &&
            session.password.isEmpty() && session.description.isEmpty()
        ) {
            sessions.remove(getCasterGuid(caster))
        }
    }

    /** `LivestreamManager::stopBroadcast` (livestream.cpp:292-312). */
    fun stopBroadcast(caster: Player?, clearAll: Boolean) {
        val session = getSession(caster) ?: return

        disconnectViewers(session, clearMutes = true)
        session.broadcasting = false
        session.broadcastStarted = 0L
        session.owner?.get()?.isLivestreamBroadcaster = false

        if (clearAll) {
            session.viewerCounter = 0
            session.password = ""
            session.description = ""
            session.bans.clear()
            session.liveRecord = 0
        }
    }

    /** `LivestreamManager::canWatch` (livestream.cpp:314-375). */
    fun canWatch(caster: Player?, viewer: LivestreamClient?, password: String): CanWatchResult {
        if (caster == null || caster.isRemoved()) {
            return CanWatchResult(false, "Livestream unavailable.")
        }

        val session = getSession(caster)
        if (session == null || !session.broadcasting) {
            return CanWatchResult(false, "This player is not currently streaming.")
        }

        if (casterClient(caster) == null) {
            return CanWatchResult(false, "This player is no longer broadcasting.")
        }

        val viewerIp = viewer?.getIP() ?: 0L
        val bannedByIp = session.bans.values.any { banIp -> viewerIp != 0L && banIp == viewerIp }
        if (bannedByIp) {
            return CanWatchResult(false, "Access denied: You are banned from viewing this livestream.")
        }

        if (session.password.isNotEmpty() && password.trim() != session.password) {
            return CanWatchResult(false, "Incorrect password. Access to this livestream is protected.")
        }

        val viewersByIp = session.viewers.values.count { it.ip == viewerIp }
        val maxViewersPerIp = maximumViewersPerIp()
        if (maxViewersPerIp > 0 && viewersByIp >= maxViewersPerIp) {
            return CanWatchResult(false, "Livestream viewer limit per IP reached. Please try again later.")
        }

        val maxViewers = if (caster.isPremium()) premiumMaximumViewers() else maximumViewers()
        if (maxViewers > 0 && session.viewers.size >= maxViewers) {
            return CanWatchResult(false, "Livestream viewer limit reached. Please try again later.")
        }

        val minLevel = casterMinLevel()
        if (minLevel > 0 && caster.level < minLevel) {
            return CanWatchResult(false, "The caster does not meet the minimum level requirement to broadcast.")
        }

        return CanWatchResult(true)
    }

    /** `LivestreamManager::addViewer` (livestream.cpp:377-406). */
    fun addViewer(caster: Player?, viewer: LivestreamClient?, spy: Boolean = false) {
        if (caster == null || viewer == null) {
            return
        }

        val session = ensureSession(caster)
        if (session.viewers.containsKey(viewer)) {
            return
        }

        ++session.viewerCounter
        val viewerName = "Viewer-${session.viewerCounter}"
        session.viewers[viewer] = LivestreamViewerInfo(viewerName, session.viewerCounter, viewer.getIP())
        viewerToCaster[viewer] = caster.getGUID()

        if (spy) {
            return
        }

        sendChannelEvent(session, viewerName, ChannelEvent.CHANNELEVENT_JOIN)
        if (session.viewers.size > session.liveRecord) {
            session.liveRecord = session.viewers.size
            saveLiveRecord(caster, session.liveRecord)
            session.owner?.get()?.sendTextMessage(
                MessageClass.MESSAGE_LOOK,
                "New record: ${session.liveRecord} people are watching your livestream now.",
            )
        }
    }

    /** `LivestreamManager::removeViewer` (livestream.cpp:408-439). */
    fun removeViewer(viewer: LivestreamClient?, spy: Boolean = false) {
        if (viewer == null) {
            return
        }

        val casterGuid = viewerToCaster[viewer] ?: return
        val session = sessions[casterGuid]
        if (session == null) {
            viewerToCaster.remove(viewer)
            return
        }

        val viewerInfo = session.viewers.remove(viewer)
        if (viewerInfo == null) {
            viewerToCaster.remove(viewer)
            return
        }

        val normalizedName = normalizeViewerName(viewerInfo.name)
        session.mutes.removeAll { it == normalizedName }
        viewerToCaster.remove(viewer)

        if (!spy) {
            sendChannelEvent(session, viewerInfo.name, ChannelEvent.CHANNELEVENT_LEAVE)
        }
    }

    /** `LivestreamManager::handleChat` (livestream.cpp:441-484). */
    fun handleChat(client: LivestreamClient?, text: String) {
        if (client == null || text.isEmpty()) {
            return
        }

        val casterGuid = viewerToCaster[client]
        if (casterGuid == null) {
            val caster = client.player
            val session = getSession(caster)
            if (caster != null && session != null && session.broadcasting) {
                sendChannelMessage(session, caster.name, text, SpeakClasses.TALKTYPE_CHANNEL_R1)
            }
            return
        }

        val session = sessions[casterGuid] ?: return
        val viewer = session.viewers[client] ?: return

        if (text[0] == '/') {
            val params = text.substring(1).split(" ", limit = 2)
            if (params.isEmpty()) {
                return
            }

            when {
                params[0].equals("show", ignoreCase = true) -> showViewers(client, session)
                params[0].equals("name", ignoreCase = true) -> changeViewerName(client, session, viewer, params)
                else -> client.sendTextMessage(MessageClass.MESSAGE_FAILURE, "Available commands: /name, /show.")
            }
            return
        }

        handleViewerMessage(client, session, viewer, text)
    }

    /** `LivestreamManager::broadcastPacket` (livestream.cpp:486-518). */
    fun broadcastPacket(caster: Player?, owner: LivestreamClient?, message: LivestreamMessage) {
        if (caster == null || owner == null || message.length == 0) {
            return
        }

        val opcode = message.buffer[message.initialBufferPosition].toInt() and 0xFF
        val refreshMap = isMapRefreshPacket(opcode)
        if (!refreshMap && !isForwardablePacket(owner, message)) {
            return
        }

        val session = getSession(caster) ?: return
        if (!session.broadcasting || session.viewers.isEmpty()) {
            return
        }

        for (viewer in session.viewers.keys) {
            if (!refreshMap) {
                viewer.writeToOutputBuffer(message)
                continue
            }

            viewer.sendMapDescription(caster.position)
            if (!viewer.oldProtocol) {
                viewer.sendIcons(setOf(PlayerIcon.Rooted), IconBakragore.None)
            }
        }
    }

    /** `LivestreamManager::updateViewerStorage` (livestream.cpp:520-535). */
    fun updateViewerStorage(caster: Player?) {
        if (caster == null) {
            return
        }

        val session = getSession(caster) ?: return
        if (!session.broadcasting) {
            return
        }

        if (!tableExists("active_livestream_casters")) {
            return
        }

        executeDatabaseTask(viewerStorageUpdateSql(session.viewers.size, caster.getGUID()))
    }

    /**
     * `UPDATE active_livestream_casters` (livestream.cpp:534) per
     * [SqlDialect]: MariaDB keeps the upstream backtick statement,
     * PostgreSQL/H2 get the unquoted form.
     */
    private fun viewerStorageUpdateSql(viewers: Int, casterGuid: Long): String =
        if (sqlDialect() == SqlDialect.MARIADB) {
            "UPDATE `active_livestream_casters` SET `livestream_viewers` = " +
                "$viewers WHERE `caster_id` = $casterGuid"
        } else {
            "UPDATE active_livestream_casters SET livestream_viewers = " +
                "$viewers WHERE caster_id = $casterGuid"
        }

    /** `LivestreamManager::isMapRefreshPacket` (livestream.cpp:537-539). */
    private fun isMapRefreshPacket(opcode: Int): Boolean = opcode == 0x64

    /** `LivestreamManager::isForwardablePacket` (livestream.cpp:541-597). */
    private fun isForwardablePacket(owner: LivestreamClient, message: LivestreamMessage): Boolean {
        if (message.length == 0) {
            return false
        }

        val opcode = message.buffer[message.initialBufferPosition].toInt() and 0xFF
        return when (opcode) {
            0x1D, 0x1E, 0x65, 0x66, 0x67, 0x68, 0x69, 0x6A, 0x6B, 0x6C, 0x6D, 0x6E, 0x6F,
            0x70, 0x71, 0x72, 0x78, 0x79, 0x82, 0x83, 0x84, 0x85, 0x86, 0x8B, 0x8C, 0x8D,
            0x8E, 0x8F, 0x90, 0x91, 0x92, 0x93, 0x95, 0x96, 0x97, 0xA0, 0xA1, 0xA3, 0xA7,
            0xB5, 0xEF,
            -> true
            0xAA -> isForwardableSpeakPacket(owner, message)
            0xB4 -> isForwardableTextMessage(message)
            else -> false
        }
    }

    /** `LivestreamManager::isForwardableSpeakPacket` (livestream.cpp:599-634). */
    private fun isForwardableSpeakPacket(owner: LivestreamClient, message: LivestreamMessage): Boolean {
        val buffer = message.buffer
        val end = message.initialBufferPosition + message.length
        var position = message.initialBufferPosition + 1
        if (position + 4 >= end) {
            return false
        }

        val statementId = readU32(buffer, position).toLong()
        position += 4
        if (position >= end) {
            return false
        }

        position = advanceSpeakMetadata(owner.oldProtocol, buffer, end, statementId, position) ?: return false
        if (position >= end) {
            return false
        }

        val speakType = speakTypeOf(buffer[position].toInt() and 0xFF)
        if (speakType != null && isChannelSpeakType(speakType)) {
            if (position + 3 > end) {
                return false
            }

            val channelId = readU16(buffer, position + 1)
            if (channelId == CHANNEL_LIVESTREAM) {
                return false
            }
        }

        return speakType == null || !isPrivateSpeakType(speakType)
    }

    /** `LivestreamManager::isForwardableTextMessage` (livestream.cpp:636-654). */
    private fun isForwardableTextMessage(message: LivestreamMessage): Boolean {
        if (message.length < 2) {
            return false
        }

        val messageClass = MessageClass.fromValue(message.buffer[message.initialBufferPosition + 1].toInt() and 0xFF)
        return messageClass in FORWARDABLE_TEXT_MESSAGES
    }

    /** `LivestreamManager::disconnectViewers` (livestream.cpp:660-672). */
    private fun disconnectViewers(session: Session, clearMutes: Boolean) {
        for (viewer in session.viewers.keys) {
            viewer.sendSessionEndInformation(SessionEndInformation.SESSION_END_LOGOUT)
            viewerToCaster.remove(viewer)
        }

        session.viewers.clear()
        if (clearMutes) {
            session.mutes.clear()
        }
    }

    /** `LivestreamManager::kickViewers` (livestream.cpp:674-690). */
    private fun kickViewers(session: Session, names: List<String>) {
        for (name in names) {
            val lowerName = normalizeViewerName(name)
            val iterator = session.viewers.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (normalizeViewerName(entry.value.name) != lowerName) {
                    continue
                }

                entry.key.sendSessionEndInformation(SessionEndInformation.SESSION_END_LOGOUT)
                viewerToCaster.remove(entry.key)
                iterator.remove()
            }
        }
    }

    /** `LivestreamManager::setBans` (livestream.cpp:692-720). */
    private fun setBans(session: Session, bans: List<String>) {
        val normalizedBans = linkedSetOf<String>()
        for (ban in bans) {
            normalizedBans.add(normalizeViewerName(ban))
        }

        session.bans.keys.removeAll { it !in normalizedBans }
        for (ban in normalizedBans) {
            session.bans.putIfAbsent(ban, 0L)
        }

        val iterator = session.viewers.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val normalizedViewerName = normalizeViewerName(entry.value.name)
            if (normalizedViewerName !in normalizedBans) {
                continue
            }

            session.bans[normalizedViewerName] = entry.value.ip
            entry.key.sendSessionEndInformation(SessionEndInformation.SESSION_END_LOGOUT)
            viewerToCaster.remove(entry.key)
            iterator.remove()
        }
    }

    /** `LivestreamManager::sendChannelMessage` (livestream.cpp:722-732). */
    private fun sendChannelMessage(session: Session, author: String, text: String, type: SpeakClasses) {
        session.owner?.get()?.sendChannelMessage(author, text, type, CHANNEL_LIVESTREAM)
        for (viewer in session.viewers.keys) {
            viewer.sendChannelMessage(author, text, type, CHANNEL_LIVESTREAM)
        }
    }

    /** `LivestreamManager::sendChannelEvent` (livestream.cpp:734-744). */
    private fun sendChannelEvent(session: Session, viewerName: String, event: ChannelEvent) {
        session.owner?.get()?.sendChannelEvent(CHANNEL_LIVESTREAM, viewerName, event)
        for (viewer in session.viewers.keys) {
            viewer.sendChannelEvent(CHANNEL_LIVESTREAM, viewerName, event)
        }
    }

    /** `LivestreamManager::showViewers` (livestream.cpp:746-754). */
    private fun showViewers(client: LivestreamClient, session: Session) {
        val names = session.viewers.values.map { it.name }
        val spectator = if (names.size == 1) "" else "s"
        client.sendTextMessage(
            MessageClass.MESSAGE_STATUS,
            "${names.size} spectator$spectator, ${names.joinToString(", ")}.",
        )
    }

    /** `LivestreamManager::changeViewerName` (livestream.cpp:756-796). */
    private fun changeViewerName(
        client: LivestreamClient,
        session: Session,
        viewer: LivestreamViewerInfo,
        params: List<String>,
    ) {
        if (params.size <= 1) {
            client.sendTextMessage(MessageClass.MESSAGE_FAILURE, "Please provide a new name.")
            return
        }

        val newName = params[1].trim()
        if (newName.isEmpty()) {
            client.sendTextMessage(MessageClass.MESSAGE_FAILURE, "Please provide a new name.")
            return
        }

        if (newName.length < MIN_VIEWER_NAME_LENGTH || newName.length > MAX_VIEWER_NAME_LENGTH) {
            client.sendTextMessage(
                MessageClass.MESSAGE_FAILURE,
                "Name must be between $MIN_VIEWER_NAME_LENGTH and $MAX_VIEWER_NAME_LENGTH letters.",
            )
            return
        }

        val normalizedName = normalizeViewerName(newName)
        if (session.bans.containsKey(normalizedName)) {
            client.sendTextMessage(MessageClass.MESSAGE_FAILURE, "This name is banned from this livestream.")
            return
        }

        val nameUsed = session.viewers.values.any { normalizeViewerName(it.name) == normalizedName }
        if (nameUsed) {
            client.sendTextMessage(MessageClass.MESSAGE_FAILURE, "There is already someone with that name.")
            return
        }

        sendChannelMessage(session, "", "${viewer.name} was renamed to $newName.", SpeakClasses.TALKTYPE_CHANNEL_O)

        val muteIndex = session.mutes.indexOfFirst { it == normalizeViewerName(viewer.name) }
        if (muteIndex >= 0) {
            session.mutes[muteIndex] = normalizedName
        }

        viewer.name = newName
        client.sendTextMessage(MessageClass.MESSAGE_STATUS, "Name changed to '$newName'.")
    }

    /** `LivestreamManager::handleViewerMessage` (livestream.cpp:798-818). */
    private fun handleViewerMessage(
        client: LivestreamClient,
        session: Session,
        viewer: LivestreamViewerInfo,
        text: String,
    ) {
        val current = now()
        if (client.livestreamMessageCooldownTime + MESSAGE_COOLDOWN_MS < current) {
            client.livestreamMessageCooldownTime = current
            client.livestreamMessageCount = 0
        }

        if (client.livestreamMessageCount >= MESSAGE_LIMIT) {
            val seconds = ((client.livestreamMessageCooldownTime + MESSAGE_COOLDOWN_MS - current) / 1000L) + 1L
            client.sendTextMessage(
                MessageClass.MESSAGE_STATUS,
                "Please wait $seconds seconds to send another message.",
            )
            return
        }

        ++client.livestreamMessageCount

        if (session.mutes.contains(normalizeViewerName(viewer.name))) {
            client.sendTextMessage(MessageClass.MESSAGE_FAILURE, "You are muted.")
            return
        }

        sendChannelMessage(session, viewer.name, text, SpeakClasses.TALKTYPE_CHANNEL_Y)
    }

    /** `LivestreamManager::normalizeViewerName` (livestream.cpp:656-658). */
    private fun normalizeViewerName(name: String): String = name.lowercase()

    /** `LivestreamManager::advanceSpeakMetadata` (livestream.cpp:64-90); `null` mirrors `false`. */
    private fun advanceSpeakMetadata(
        oldProtocol: Boolean,
        buffer: ByteArray,
        end: Int,
        statementId: Long,
        position: Int,
    ): Int? {
        var pos = position
        if (pos + 2 > end) {
            return pos
        }

        val nameLength = readU16(buffer, pos)
        if (nameLength <= MAX_VIEWER_NAME_LENGTH && pos + 2 + nameLength <= end) {
            pos += 2 + nameLength
            if (!oldProtocol && statementId != 0L) {
                ++pos
            }
            if (pos + 2 > end) {
                return null
            }
            pos += 2
            return pos
        }

        if (pos + 4 > end) {
            return null
        }
        pos += 4
        if (!oldProtocol && statementId != 0L) {
            ++pos
        }
        return pos
    }

    /** `readU16` (livestream.cpp:29-31). */
    private fun readU16(buffer: ByteArray, position: Int): Int =
        (buffer[position].toInt() and 0xFF) or ((buffer[position + 1].toInt() and 0xFF) shl 8)

    /** `readU32` (livestream.cpp:33-35). */
    private fun readU32(buffer: ByteArray, position: Int): Int =
        (buffer[position].toInt() and 0xFF) or
            ((buffer[position + 1].toInt() and 0xFF) shl 8) or
            ((buffer[position + 2].toInt() and 0xFF) shl 16) or
            ((buffer[position + 3].toInt() and 0xFF) shl 24)

    /** `speakType` lookup replacing the C++ `static_cast<SpeakClasses>` switch. */
    private fun speakTypeOf(value: Int): SpeakClasses? = SpeakClasses.entries.firstOrNull { it.value == value }

    /** `isPrivateSpeakType` (livestream.cpp:37-49). */
    private fun isPrivateSpeakType(type: SpeakClasses): Boolean = when (type) {
        SpeakClasses.TALKTYPE_PRIVATE_FROM,
        SpeakClasses.TALKTYPE_PRIVATE_TO,
        SpeakClasses.TALKTYPE_PRIVATE_NP,
        SpeakClasses.TALKTYPE_PRIVATE_PN,
        SpeakClasses.TALKTYPE_PRIVATE_RED_FROM,
        SpeakClasses.TALKTYPE_PRIVATE_RED_TO,
        -> true
        else -> false
    }

    /** `isChannelSpeakType` (livestream.cpp:51-62). */
    private fun isChannelSpeakType(type: SpeakClasses): Boolean = when (type) {
        SpeakClasses.TALKTYPE_CHANNEL_MANAGER,
        SpeakClasses.TALKTYPE_CHANNEL_Y,
        SpeakClasses.TALKTYPE_CHANNEL_O,
        SpeakClasses.TALKTYPE_CHANNEL_R1,
        SpeakClasses.TALKTYPE_CHANNEL_R2,
        -> true
        else -> false
    }

    private companion object {
        /** `MESSAGE_COOLDOWN_MS` (livestream.cpp:24). */
        const val MESSAGE_COOLDOWN_MS = 5000L

        /** `MESSAGE_LIMIT` (livestream.cpp:25). */
        const val MESSAGE_LIMIT = 3

        /** `MIN_VIEWER_NAME_LENGTH` (livestream.cpp:26). */
        const val MIN_VIEWER_NAME_LENGTH = 3

        /** `MAX_VIEWER_NAME_LENGTH` = `NETWORKMESSAGE_PLAYERNAME_MAXLENGTH` (livestream.cpp:27, const.hpp:15). */
        const val MAX_VIEWER_NAME_LENGTH = 30

        /** `isForwardableTextMessage` forwardable classes (livestream.cpp:642-652). */
        val FORWARDABLE_TEXT_MESSAGES = setOf(
            MessageClass.MESSAGE_DAMAGE_DEALT,
            MessageClass.MESSAGE_DAMAGE_RECEIVED,
            MessageClass.MESSAGE_DAMAGE_OTHERS,
            MessageClass.MESSAGE_HEALED,
            MessageClass.MESSAGE_HEALED_OTHERS,
            MessageClass.MESSAGE_EXPERIENCE,
            MessageClass.MESSAGE_EXPERIENCE_OTHERS,
        )
    }
}
