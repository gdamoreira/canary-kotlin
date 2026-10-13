package org.opentibia.canary.chat

import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.systems.rewardchest.ReturnValue

/**
 * Recording stand-in for the `ProtocolGame`/`Player` senders `chat.cpp` calls.
 * Every record transcribes one C++ call site:
 * - `Player::sendCreatureSay` (game.cpp:7647, 7504-7506)
 * - `Player::sendChannelMessage` (chat.cpp:170)
 * - `Player::sendChannelEvent` (chat.cpp:61, 83, 130, 152)
 * - `Player::sendToChannel` (chat.cpp:208)
 * - `Player::sendClosePrivate` (chat.cpp:76, 93)
 * - `Player::sendTextMessage` (chat.cpp:50, 54, 74)
 * - `Player::sendCancelMessage` (game.cpp:7524)
 */
class FakeChatClient : ChatClient {
    data class CreatureSay(
        val recipient: Player,
        val speaker: Creature,
        val type: SpeakClasses,
        val text: String,
    )

    data class ChannelMessage(
        val recipient: Player,
        val author: String,
        val text: String,
        val type: SpeakClasses,
        val channelId: Int,
    )

    data class ChannelEventCall(
        val recipient: Player,
        val channelId: Int,
        val playerName: String,
        val channelEvent: ChannelEvent,
    )

    data class ToChannel(
        val recipient: Player,
        val speaker: Creature,
        val type: SpeakClasses,
        val text: String,
        val channelId: Int,
    )

    data class ClosePrivate(
        val recipient: Player,
        val channelId: Int,
    )

    data class TextMessage(
        val recipient: Player,
        val message: String,
    )

    data class CancelMessage(
        val recipient: Player,
        val returnValue: ReturnValue,
    )

    val creatureSays = mutableListOf<CreatureSay>()
    val channelMessages = mutableListOf<ChannelMessage>()
    val channelEvents = mutableListOf<ChannelEventCall>()
    val toChannel = mutableListOf<ToChannel>()
    val closePrivate = mutableListOf<ClosePrivate>()
    val textMessages = mutableListOf<TextMessage>()
    val cancelMessages = mutableListOf<CancelMessage>()

    override fun sendCreatureSay(recipient: Player, speaker: Creature, type: SpeakClasses, text: String) {
        creatureSays.add(CreatureSay(recipient, speaker, type, text))
    }

    override fun sendChannelMessage(
        recipient: Player,
        author: String,
        text: String,
        type: SpeakClasses,
        channelId: Int,
    ) {
        channelMessages.add(ChannelMessage(recipient, author, text, type, channelId))
    }

    override fun sendChannelEvent(recipient: Player, channelId: Int, playerName: String, channelEvent: ChannelEvent) {
        channelEvents.add(ChannelEventCall(recipient, channelId, playerName, channelEvent))
    }

    override fun sendToChannel(
        recipient: Player,
        speaker: Creature,
        type: SpeakClasses,
        text: String,
        channelId: Int,
    ) {
        toChannel.add(ToChannel(recipient, speaker, type, text, channelId))
    }

    override fun sendClosePrivate(recipient: Player, channelId: Int) {
        closePrivate.add(ClosePrivate(recipient, channelId))
    }

    override fun sendTextMessage(recipient: Player, message: String) {
        textMessages.add(TextMessage(recipient, message))
    }

    override fun sendCancelMessage(recipient: Player, returnValue: ReturnValue) {
        cancelMessages.add(CancelMessage(recipient, returnValue))
    }
}
