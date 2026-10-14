package org.opentibia.canary.chat

import org.opentibia.canary.account.AccountType
import org.opentibia.canary.combat.condition.Condition
import org.opentibia.canary.combat.condition.ConditionId
import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.grouping.Party
import org.opentibia.canary.map.Position
import org.opentibia.canary.systems.rewardchest.ReturnValue
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.abs

/** `CHANNEL_GUILD` (`src/utils/const.hpp:20`). */
const val CHANNEL_GUILD = 0x00

/** `CHANNEL_PARTY` (`src/utils/const.hpp:21`). */
const val CHANNEL_PARTY = 0x01

/** `CHANNEL_PRIVATE` (`src/utils/const.hpp:23`). */
const val CHANNEL_PRIVATE = 0xFFFF

/** `MAP_MAX_CLIENT_VIEW_PORT_X` (`src/map/map_const.hpp:12`). */
private const val MAP_MAX_CLIENT_VIEW_PORT_X = 8

/** `MAP_MAX_CLIENT_VIEW_PORT_Y` (`src/map/map_const.hpp:13`). */
private const val MAP_MAX_CLIENT_VIEW_PORT_Y = 6

/**
 * `SpeakClasses` (`src/utils/utils_definitions.hpp:346-368`).
 *
 * The numeric values cross the wire protocol, so they are transcribed 1:1.
 */
enum class SpeakClasses(val value: Int) {
    TALKTYPE_SAY(1),
    TALKTYPE_WHISPER(2),
    TALKTYPE_YELL(3),
    TALKTYPE_PRIVATE_FROM(4),
    TALKTYPE_PRIVATE_TO(5),
    TALKTYPE_CHANNEL_MANAGER(6),
    TALKTYPE_CHANNEL_Y(7),
    TALKTYPE_CHANNEL_O(8),
    TALKTYPE_SPELL_USE(9),
    TALKTYPE_PRIVATE_NP(10),
    TALKTYPE_NPC_UNKOWN(11),
    TALKTYPE_PRIVATE_PN(12),
    TALKTYPE_BROADCAST(13),
    TALKTYPE_CHANNEL_R1(14),
    TALKTYPE_PRIVATE_RED_FROM(15),
    TALKTYPE_PRIVATE_RED_TO(16),
    TALKTYPE_MONSTER_SAY(36),
    TALKTYPE_MONSTER_YELL(37),
    TALKTYPE_MONSTER_LAST_OLDPROTOCOL(38),
    TALKTYPE_CHANNEL_R2(0xFF),
}

/** `ChannelEvent_t` (`src/creatures/creatures_definitions.hpp:776-781`). */
enum class ChannelEvent(val value: Int) {
    CHANNELEVENT_JOIN(0),
    CHANNELEVENT_LEAVE(1),
    CHANNELEVENT_INVITE(2),
    CHANNELEVENT_EXCLUDE(3),
}

/**
 * Protocol boundary for the senders `chat.cpp` invokes on players
 * (`Player::sendCreatureSay`, `sendChannelMessage`, `sendChannelEvent`,
 * `sendToChannel`, `sendClosePrivate`, `sendTextMessage`, `sendCancelMessage`;
 * player.hpp:969-1084).
 *
 * Canary sends straight through `Player::client`; the port keeps the engine
 * deterministic by injecting the seal (AGENTS.md 1.1). [NoopChatClient] models
 * an absent client.
 */
interface ChatClient {
    /** `Player::sendCreatureSay` (game.cpp:7647, 7504-7506). */
    fun sendCreatureSay(recipient: Player, speaker: Creature, type: SpeakClasses, text: String)

    /** `Player::sendChannelMessage` (chat.cpp:170). */
    fun sendChannelMessage(
        recipient: Player,
        author: String,
        text: String,
        type: SpeakClasses,
        channelId: Int,
    )

    /** `Player::sendChannelEvent` (chat.cpp:61, 83, 130, 152). */
    fun sendChannelEvent(recipient: Player, channelId: Int, playerName: String, channelEvent: ChannelEvent)

    /** `Player::sendToChannel` (chat.cpp:208). */
    fun sendToChannel(
        recipient: Player,
        speaker: Creature,
        type: SpeakClasses,
        text: String,
        channelId: Int,
    )

    /** `Player::sendClosePrivate` (chat.cpp:76, 93). */
    fun sendClosePrivate(recipient: Player, channelId: Int)

    /** `Player::sendTextMessage` (chat.cpp:50, 54, 74; game.cpp:7428, 7519). */
    fun sendTextMessage(recipient: Player, message: String)

    /** `Player::sendCancelMessage` (game.cpp:7524). */
    fun sendCancelMessage(recipient: Player, returnValue: ReturnValue)
}

/** No-op protocol client used when the engine runs without a network client. */
object NoopChatClient : ChatClient {
    override fun sendCreatureSay(recipient: Player, speaker: Creature, type: SpeakClasses, text: String) = Unit

    override fun sendChannelMessage(
        recipient: Player,
        author: String,
        text: String,
        type: SpeakClasses,
        channelId: Int,
    ) = Unit

    override fun sendChannelEvent(
        recipient: Player,
        channelId: Int,
        playerName: String,
        channelEvent: ChannelEvent,
    ) = Unit

    override fun sendToChannel(
        recipient: Player,
        speaker: Creature,
        type: SpeakClasses,
        text: String,
        channelId: Int,
    ) = Unit

    override fun sendClosePrivate(recipient: Player, channelId: Int) = Unit

    override fun sendTextMessage(recipient: Player, message: String) = Unit

    override fun sendCancelMessage(recipient: Player, returnValue: ReturnValue) = Unit
}

/**
 * `ChatChannel` (`src/creatures/interactions/chat.hpp:22-64`,
 * chat.cpp:101-337).
 *
 * PARITY NOTES:
 * - The users map is insertion-ordered instead of `std::map`'s id order; every
 *   observable decision here is order-independent.
 * - `executeOnJoinEvent`/`executeOnLeaveEvent`/`executeCanJoinEvent` load Lua
 *   channel scripts and are not ported (AGENTS.md 1.4), so they always pass.
 * - The `CHANNEL_GUILD` MOTD dispatch (chat.cpp:114-122) is an outbound
 *   protocol side effect that lands with the game protocol layer (PARITY GAP).
 */
open class ChatChannel(
    protected val channelId: Int,
    var channelName: String,
    /** `ChatChannel::publicChannel` (chat.hpp:61); `Chat::load` writes it. */
    var publicChannel: Boolean = false,
    protected val client: ChatClient = NoopChatClient,
) {
    /** `ChatChannel::users` (chat.hpp:51), keyed by `Player::getID()`. */
    protected val users = linkedMapOf<Long, Player>()

    /** `ChatChannel::getId()` (chat.cpp:178-180). */
    fun getId(): Int = channelId

    /** `ChatChannel::getName()` (chat.cpp:174-176). */
    fun getName(): String = channelName

    /** `ChatChannel::getUsers()` (chat.cpp:182-184). */
    fun getUsers(): MutableMap<Long, Player> = users

    /** `ChatChannel::getInvitedUsers()` (chat.cpp:186-188) — base returns null. */
    open fun getInvitedUsers(): Map<Long, Player>? = null

    /** `ChatChannel::getOwner()` (chat.cpp:190-192) — base returns 0. */
    open fun getOwner(): Long = 0

    /** `ChatChannel::isPublicChannel()` (chat.cpp:194-196). */
    fun isPublicChannel(): Boolean = publicChannel

    /** `ChatChannel::hasUser()` (chat.cpp:160-162). */
    fun hasUser(player: Player): Boolean = users.containsKey(player.id)

    /** `ChatChannel::addUser()` (chat.cpp:105-136). */
    fun addUser(player: Player): Boolean {
        if (users.containsKey(player.id)) {
            return false
        }

        if (!publicChannel) {
            for ((playerUserId, playerUser) in users) {
                if (playerUserId == 0L) {
                    continue
                }
                client.sendChannelEvent(playerUser, channelId, player.name, ChannelEvent.CHANNELEVENT_JOIN)
            }
        }

        users[player.id] = player
        return true
    }

    /** `ChatChannel::removeUser()` (chat.cpp:138-158). */
    fun removeUser(player: Player): Boolean {
        if (users.remove(player.id) == null) {
            return false
        }

        if (!publicChannel) {
            for ((playerUserId, playerUser) in users) {
                if (playerUserId == 0L) {
                    continue
                }
                client.sendChannelEvent(playerUser, channelId, player.name, ChannelEvent.CHANNELEVENT_LEAVE)
            }
        }

        return true
    }

    /** `ChatChannel::talk()` (chat.cpp:198-211): only members can talk. */
    fun talk(fromPlayer: Player, type: SpeakClasses, text: String): Boolean {
        if (!users.containsKey(fromPlayer.id)) {
            return false
        }

        for ((playerUserId, playerUser) in users) {
            if (playerUserId == 0L) {
                continue
            }
            client.sendToChannel(playerUser, fromPlayer, type, text, channelId)
        }
        return true
    }

    /** `ChatChannel::sendToAll()` (chat.cpp:164-172). */
    fun sendToAll(message: String, type: SpeakClasses) {
        for ((playerUserId, playerUser) in users) {
            if (playerUserId == 0L) {
                continue
            }
            client.sendChannelMessage(playerUser, "", message, type, channelId)
        }
    }
}

/**
 * `PrivateChatChannel` (`src/creatures/interactions/chat.hpp:66-87`,
 * chat.cpp:20-99).
 */
class PrivateChatChannel(
    id: Int,
    name: String,
    client: ChatClient = NoopChatClient,
) : ChatChannel(id, name, client = client) {
    /** `PrivateChatChannel::invites` (chat.hpp:85). */
    private val invites = linkedMapOf<Long, Player>()

    /** `PrivateChatChannel::owner` (chat.hpp:86). */
    private var owner: Long = 0

    /** `PrivateChatChannel::getOwner()` (chat.cpp:23-25). */
    override fun getOwner(): Long = owner

    /** `PrivateChatChannel::setOwner()` (chat.cpp:27-29). */
    fun setOwner(newOwner: Long) {
        owner = newOwner
    }

    /** `PrivateChatChannel::isInvited()` (chat.cpp:31-36): the owner is always invited. */
    fun isInvited(guid: Long): Boolean = guid == owner || invites.containsKey(guid)

    /** `PrivateChatChannel::removeInvite()` (chat.cpp:38-40). */
    fun removeInvite(guid: Long): Boolean = invites.remove(guid) != null

    /** `PrivateChatChannel::invitePlayer()` (chat.cpp:42-63). */
    fun invitePlayer(player: Player, invitePlayer: Player) {
        if (invites.putIfAbsent(invitePlayer.id, invitePlayer) != null) {
            return
        }

        client.sendTextMessage(
            invitePlayer,
            "${player.name} invites you to ${player.getPossessivePronoun()} private chat channel.",
        )
        client.sendTextMessage(player, "${invitePlayer.name} has been invited.")

        for ((playerUserId, playerUser) in users) {
            if (playerUserId == 0L) {
                continue
            }
            client.sendChannelEvent(playerUser, channelId, invitePlayer.name, ChannelEvent.CHANNELEVENT_INVITE)
        }
    }

    /** `PrivateChatChannel::excludePlayer()` (chat.cpp:65-85). */
    fun excludePlayer(player: Player, excludePlayer: Player) {
        if (!removeInvite(excludePlayer.id)) {
            return
        }

        removeUser(excludePlayer)

        client.sendTextMessage(player, "${excludePlayer.name} has been excluded.")
        client.sendClosePrivate(excludePlayer, channelId)

        for ((playerUserId, playerUser) in users) {
            if (playerUserId == 0L) {
                continue
            }
            client.sendChannelEvent(playerUser, channelId, excludePlayer.name, ChannelEvent.CHANNELEVENT_EXCLUDE)
        }
    }

    /** `PrivateChatChannel::closeChannel()` (chat.cpp:87-95). */
    fun closeChannel() {
        for ((playerUserId, playerUser) in users) {
            if (playerUserId == 0L) {
                continue
            }
            client.sendClosePrivate(playerUser, channelId)
        }
    }

    /** `PrivateChatChannel::getInvitedUsers()` (chat.cpp:97-99). */
    override fun getInvitedUsers(): Map<Long, Player> = invites
}

/**
 * `Chat` registry (`src/creatures/interactions/chat.hpp:91-130`,
 * chat.cpp:339-729).
 *
 * The singleton `g_chat()` becomes a constructor-injected [ChatManager]
 * (AGENTS.md 1.1). PARITY GAPS: the Lua `scriptInterface` and the
 * `canJoin`/`onJoin`/`onLeave`/`onSpeak` channel events (AGENTS.md 1.4); the
 * `Chat::load` `script` attribute loading; and `Game::playerSaySpell` /
 * talkaction handling, which stays Lua and therefore runs before the port's
 * mute gate instead of before it (game.cpp:7420-7422).
 *
 * `broadcastSpeech`/`playerSay` transcribe the speech part of
 * `Game::internalCreatureSay`/`playerSay`/`playerWhisper`/`playerYell`
 * (game.cpp:7411-7657).
 */
class ChatManager(
    private val client: ChatClient = NoopChatClient,
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    /** `Chat::normalChannels` (chat.hpp:122). */
    private val normalChannels = linkedMapOf<Int, ChatChannel>()

    /** `Chat::privateChannels` (chat.hpp:123). */
    private val privateChannels = linkedMapOf<Int, PrivateChatChannel>()

    /** `Chat::partyChannels` (chat.hpp:124), keyed by party identity. */
    private val partyChannels = linkedMapOf<Party, ChatChannel>()

    /** `Chat::guildChannels` (chat.hpp:125), keyed by guild id. */
    private val guildChannels = linkedMapOf<Long, ChatChannel>()

    /** `Chat::dummyPrivate` (chat.hpp:129). */
    private val dummyPrivate = PrivateChatChannel(CHANNEL_PRIVATE, "Private Chat Channel", client)

    /**
     * `Chat::load()` (chat.cpp:349-410). The port takes the XML content instead
     * of reading `CORE_DIRECTORY/chatchannels/chatchannels.xml` so the data
     * pipeline can hand it over (AGENTS.md 1.1); the `script` attribute and its
     * Lua event registration are not ported (AGENTS.md 1.4).
     */
    fun load(xmlContent: String): Boolean {
        val document = try {
            val factory = DocumentBuilderFactory.newInstance()
            factory.newDocumentBuilder().parse(ByteArrayInputStream(xmlContent.toByteArray()))
        } catch (_: Exception) {
            return false
        }

        val channelNodes = document.getElementsByTagName("channel")
        for (index in 0 until channelNodes.length) {
            val channelNode = channelNodes.item(index) as? Element ?: continue
            val channelId = channelNode.getAttribute("id").toIntOrNull() ?: continue
            val channelName = channelNode.getAttribute("name")
            val isPublic = when (channelNode.getAttribute("public").lowercase()) {
                "1", "true" -> true
                else -> false
            }

            val existing = normalChannels[channelId]
            if (existing != null) {
                existing.publicChannel = isPublic
                existing.channelName = channelName

                val tempUserMap = existing.getUsers().toMap()
                existing.getUsers().clear()
                for ((playerUserId, playerUser) in tempUserMap) {
                    if (playerUserId == 0L) {
                        continue
                    }
                    existing.addUser(playerUser)
                }
                continue
            }

            normalChannels[channelId] = ChatChannel(channelId, channelName, isPublic, client)
        }
        return true
    }

    /** `Chat::createChannel()` (chat.cpp:412-462). */
    fun createChannel(player: Player, channelId: Int): ChatChannel? {
        if (getChannel(player, channelId) != null) {
            return null
        }

        when (channelId) {
            CHANNEL_GUILD -> {
                val guild = player.getGuild() ?: return null
                if (guildChannels.containsKey(guild.id)) {
                    return null
                }
                val channel = ChatChannel(channelId, guild.name, client = client)
                guildChannels[guild.id] = channel
                return channel
            }

            CHANNEL_PARTY -> {
                val party = player.getParty() ?: return null
                if (partyChannels.containsKey(party)) {
                    return null
                }
                val channel = ChatChannel(channelId, "Party", client = client)
                partyChannels[party] = channel
                return channel
            }

            CHANNEL_PRIVATE -> {
                if (!player.isPremium() || getPrivateChannel(player) != null) {
                    return null
                }

                for (slot in PRIVATE_CHANNEL_FIRST_SLOT until PRIVATE_CHANNEL_LAST_SLOT) {
                    if (privateChannels.containsKey(slot)) {
                        continue
                    }
                    val channel = PrivateChatChannel(slot, "${player.name}'s Channel", client)
                    channel.setOwner(player.getGUID())
                    privateChannels[slot] = channel
                    return channel
                }
                return null
            }

            else -> return null
        }
    }

    /** `Chat::deleteChannel()` (chat.cpp:464-509). */
    fun deleteChannel(player: Player, channelId: Int): Boolean {
        when (channelId) {
            CHANNEL_GUILD -> {
                val guild = player.getGuild() ?: return false
                if (guildChannels.remove(guild.id) == null) {
                    return false
                }
            }

            CHANNEL_PARTY -> {
                val party = player.getParty() ?: return false
                if (partyChannels.remove(party) == null) {
                    return false
                }
            }

            else -> {
                val channel = privateChannels.remove(channelId) ?: return false
                channel.closeChannel()
            }
        }
        return true
    }

    /** `Chat::addUserToChannel()` (chat.cpp:511-517). */
    fun addUserToChannel(player: Player, channelId: Int): ChatChannel? {
        val channel = getChannel(player, channelId) ?: return null
        return if (channel.addUser(player)) channel else null
    }

    /** `Chat::removeUserFromChannel()` (chat.cpp:519-529). */
    fun removeUserFromChannel(player: Player, channelId: Int): Boolean {
        val channel = getChannel(player, channelId) ?: return false
        if (!channel.removeUser(player)) {
            return false
        }

        if (channel.getOwner() == player.getGUID()) {
            deleteChannel(player, channelId)
        }
        return true
    }

    /** `Chat::removeUserFromAllChannels()` (chat.cpp:531-568). */
    fun removeUserFromAllChannels(player: Player) {
        for ((playerUserId, playerUser) in normalChannels) {
            if (playerUserId == 0) {
                continue
            }
            playerUser.removeUser(player)
        }

        for (playerUser in partyChannels.values) {
            playerUser.removeUser(player)
        }

        for ((playerUserId, playerUser) in guildChannels) {
            if (playerUserId == 0L) {
                continue
            }
            playerUser.removeUser(player)
        }

        val iterator = privateChannels.iterator()
        while (iterator.hasNext()) {
            val channel = iterator.next().value
            channel.removeInvite(player.getGUID())
            channel.removeUser(player)
            if (channel.getOwner() == player.getGUID()) {
                channel.closeChannel()
                iterator.remove()
            }
        }
    }

    /** `Chat::talkToChannel()` (chat.cpp:570-592). */
    fun talkToChannel(player: Player, type: SpeakClasses, text: String, channelId: Int): Boolean {
        val channel = getChannel(player, channelId) ?: return false

        var speakType = type
        if (channelId == CHANNEL_GUILD) {
            val rank = player.getGuildRank()
            if (rank != null && rank.level > 1) {
                speakType = SpeakClasses.TALKTYPE_CHANNEL_O
            } else if (speakType != SpeakClasses.TALKTYPE_CHANNEL_Y) {
                speakType = SpeakClasses.TALKTYPE_CHANNEL_Y
            }
        } else if (speakType != SpeakClasses.TALKTYPE_CHANNEL_Y &&
            (channelId == CHANNEL_PRIVATE || channelId == CHANNEL_PARTY)
        ) {
            speakType = SpeakClasses.TALKTYPE_CHANNEL_Y
        }

        // executeOnSpeakEvent (Lua) is not ported and passes (AGENTS.md 1.4).
        return channel.talk(player, speakType, text)
    }

    /** `Chat::getChannelList()` (chat.cpp:594-653). */
    fun getChannelList(player: Player): List<ChatChannel> {
        val list = mutableListOf<ChatChannel>()

        if (player.getGuild() != null) {
            val channel = getChannel(player, CHANNEL_GUILD) ?: createChannel(player, CHANNEL_GUILD)
            if (channel != null) {
                list.add(channel)
            }
        }

        if (player.getParty() != null) {
            val channel = getChannel(player, CHANNEL_PARTY) ?: createChannel(player, CHANNEL_PARTY)
            if (channel != null) {
                list.add(channel)
            }
        }

        for ((playerUserId, _) in normalChannels) {
            if (playerUserId == 0) {
                continue
            }
            val channel = getChannel(player, playerUserId)
            if (channel != null) {
                list.add(channel)
            }
        }

        var hasPrivate = false
        for ((playerUserId, channel) in privateChannels) {
            if (playerUserId == 0) {
                continue
            }
            val guid = player.getGUID()
            if (channel.isInvited(guid)) {
                list.add(channel)
            }
            if (channel.getOwner() == guid) {
                hasPrivate = true
            }
        }

        if (!hasPrivate && player.isPremium()) {
            list.add(dummyPrivate)
        }
        return list
    }

    /** `Chat::getChannel()` (chat.cpp:655-697). */
    fun getChannel(player: Player, channelId: Int): ChatChannel? {
        when (channelId) {
            CHANNEL_GUILD -> {
                val guild = player.getGuild() ?: return null
                return guildChannels[guild.id]
            }

            CHANNEL_PARTY -> {
                val party = player.getParty() ?: return null
                return partyChannels[party]
            }

            else -> {
                normalChannels[channelId]?.let { channel ->
                    // executeCanJoinEvent (Lua) is not ported and passes (AGENTS.md 1.4).
                    return channel
                }

                val privateChannel = privateChannels[channelId]
                if (privateChannel != null && privateChannel.isInvited(player.getGUID())) {
                    return privateChannel
                }
                return null
            }
        }
    }

    /** `Chat::getGuildChannelById()` (chat.cpp:699-705). */
    fun getGuildChannelById(guildId: Long): ChatChannel? = guildChannels[guildId]

    /** `Chat::getChannelById()` (chat.cpp:707-713). */
    fun getChannelById(channelId: Int): ChatChannel? = normalChannels[channelId]

    /** `Chat::getPrivateChannel()` (chat.cpp:715-725). */
    fun getPrivateChannel(player: Player): PrivateChatChannel? =
        privateChannels.values.firstOrNull { it.getOwner() == player.getGUID() }

    /**
     * Speech spectator computation from `Game::internalCreatureSay`
     * (game.cpp:7627-7641): yell spans ±18x/±14y across floors, every other
     * speech type spans the ±8x/±6y client viewport on the same floor.
     */
    fun broadcastSpeech(
        speaker: Creature,
        type: SpeakClasses,
        text: String,
        potentialListeners: List<Player>,
    ): List<Player> {
        if (text.isEmpty()) {
            return emptyList()
        }

        val speakerPosition = speaker.position
        return potentialListeners.filter { listener ->
            when (type) {
                SpeakClasses.TALKTYPE_YELL, SpeakClasses.TALKTYPE_MONSTER_YELL ->
                    isInRange(speakerPosition, listener.position, YELL_RANGE_X, YELL_RANGE_Y)

                SpeakClasses.TALKTYPE_WHISPER ->
                    speakerPosition.isWithinRange(listener.position, WHISPER_RANGE, WHISPER_RANGE)

                else ->
                    speakerPosition.isWithinRange(
                        listener.position,
                        MAP_MAX_CLIENT_VIEW_PORT_X,
                        MAP_MAX_CLIENT_VIEW_PORT_Y,
                    )
            }
        }
    }

    /**
     * `Game::playerSay()` (game.cpp:7411-7475), minus the Lua talkaction/spell
     * pipeline, private/NPC/broadcast speech (PARITY GAPS). The anti-spam
     * `Player::removeMessageBuffer` feed (game.cpp:7437) runs for every type
     * except `TALKTYPE_PRIVATE_PN`. [potentialListeners] replaces the map
     * spectator query. Returns whether the say was accepted and dispatched.
     *
     * PARITY GAP: `Game::playerSay` (Game.kt:3013) still lacks the guarded
     * `player.removeMessageBuffer()` call after its access check (game.cpp:7437);
     * adding it there is the same one-liner and that file is outside this
     * change's ownership.
     */
    fun playerSay(
        player: Player,
        channelId: Int,
        type: SpeakClasses,
        text: String,
        potentialListeners: List<Player>,
    ): Boolean {
        val muteTime = player.isMuted()
        if (muteTime > 0) {
            client.sendTextMessage(player, "You are still muted for $muteTime seconds.")
            return false
        }

        if (text.isNotEmpty() && text.first() == '/' && player.isAccessPlayer()) {
            return false
        }

        if (type != SpeakClasses.TALKTYPE_PRIVATE_PN) {
            player.removeMessageBuffer()
        }

        return when (type) {
            SpeakClasses.TALKTYPE_SAY ->
                sendSpeech(player, SpeakClasses.TALKTYPE_SAY, text, potentialListeners)

            SpeakClasses.TALKTYPE_WHISPER ->
                playerWhisper(player, text, potentialListeners)

            SpeakClasses.TALKTYPE_YELL ->
                playerYell(player, text, potentialListeners)

            SpeakClasses.TALKTYPE_CHANNEL_O,
            SpeakClasses.TALKTYPE_CHANNEL_Y,
            SpeakClasses.TALKTYPE_CHANNEL_R1,
            ->
                talkToChannel(player, type, text, channelId)

            else -> false
        }
    }

    /**
     * `Game::playerYell()` (game.cpp:7517-7535): level 1 and `YELLTICKS` are
     * rejected, everyone below gamemaster gains the 30s exhaust condition and
     * the yell is uppercased before the wide broadcast.
     */
    fun playerYell(player: Player, text: String, potentialListeners: List<Player>): Boolean {
        if (player.level == 1) {
            client.sendTextMessage(player, "You may not yell as long as you are on level 1.")
            return false
        }

        if (player.hasCondition(ConditionType.CONDITION_YELLTICKS)) {
            client.sendCancelMessage(player, ReturnValue.YOUAREEXHAUSTED)
            return false
        }

        if (player.getAccountType() < AccountType.ACCOUNT_TYPE_GAMEMASTER) {
            player.addCondition(
                Condition.createCondition(
                    id = ConditionId.CONDITIONID_DEFAULT,
                    type = ConditionType.CONDITION_YELLTICKS,
                    ticks = YELL_TICKS,
                    now = now,
                ),
            )
        }

        return sendSpeech(player, SpeakClasses.TALKTYPE_YELL, text.uppercase(), potentialListeners)
    }

    /**
     * `Game::playerWhisper()` (game.cpp:7497-7515): every viewport spectator
     * beyond one tile gets `pspsps` instead of the text. The `onCreatureSay`
     * event is Lua (PARITY GAP).
     */
    private fun playerWhisper(player: Player, text: String, potentialListeners: List<Player>): Boolean {
        val spectators = potentialListeners.filter {
            player.position.isWithinRange(
                it.position,
                MAP_MAX_CLIENT_VIEW_PORT_X,
                MAP_MAX_CLIENT_VIEW_PORT_Y,
            )
        }

        for (spectator in spectators) {
            if (!player.position.isWithinRange(spectator.position, WHISPER_RANGE, WHISPER_RANGE)) {
                client.sendCreatureSay(spectator, player, SpeakClasses.TALKTYPE_WHISPER, "pspsps")
            } else {
                client.sendCreatureSay(spectator, player, SpeakClasses.TALKTYPE_WHISPER, text)
            }
        }
        return true
    }

    /** `Game::internalCreatureSay` send loop (game.cpp:7643-7650). */
    private fun sendSpeech(
        speaker: Player,
        type: SpeakClasses,
        text: String,
        potentialListeners: List<Player>,
    ): Boolean {
        val recipients = broadcastSpeech(speaker, type, text, potentialListeners)
        for (recipient in recipients) {
            client.sendCreatureSay(recipient, speaker, type, text)
        }
        return true
    }

    /** `Spectators::find(..., multifloor)` range check (`spectators.cpp:121-165`). */
    private fun isInRange(from: Position, to: Position, rangeX: Int, rangeY: Int): Boolean =
        abs(from.x - to.x) <= rangeX && abs(from.y - to.y) <= rangeY

    companion object {
        /** `Position::areInRange<1, 1>` radius used by `Game::playerWhisper` (game.cpp:7503). */
        private const val WHISPER_RANGE = 1

        /** `internalCreatureSay` yell range (`game.cpp:7637`): `(8 + 1) * 2`. */
        private const val YELL_RANGE_X = (MAP_MAX_CLIENT_VIEW_PORT_X + 1) * 2

        /** `internalCreatureSay` yell range (`game.cpp:7637`): `(6 + 1) * 2`. */
        private const val YELL_RANGE_Y = (MAP_MAX_CLIENT_VIEW_PORT_Y + 1) * 2

        /** `Game::playerYell` `CONDITION_YELLTICKS` duration (game.cpp:7529). */
        private const val YELL_TICKS = 30_000

        /** `Chat::createChannel` private slot search start (chat.cpp:447). */
        private const val PRIVATE_CHANNEL_FIRST_SLOT = 100

        /** `Chat::createChannel` private slot search bound (chat.cpp:447). */
        private const val PRIVATE_CHANNEL_LAST_SLOT = 10_000
    }
}
