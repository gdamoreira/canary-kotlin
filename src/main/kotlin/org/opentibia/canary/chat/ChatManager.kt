package org.opentibia.canary.chat

import org.opentibia.canary.creatures.Player

enum class SpeechType(val hearingRadius: Int) {
    Whisper(1),
    Say(4),
    Yell(18)
}

class ChatChannel(
    val id: Int,
    val name: String
) {
    private val members = mutableSetOf<Player>()

    fun join(player: Player): Boolean = members.add(player)

    fun leave(player: Player): Boolean = members.remove(player)

    fun sendMessage(sender: Player, message: String, onDeliver: (recipient: Player, text: String) -> Unit) {
        val formatted = "[$name] ${sender.name}: $message"
        for (member in members) {
            onDeliver(member, formatted)
        }
    }
}

class ChatManager {
    private val channels = mutableMapOf<Int, ChatChannel>()

    fun createChannel(id: Int, name: String): ChatChannel {
        val channel = ChatChannel(id, name)
        channels[id] = channel
        return channel
    }

    fun getChannel(id: Int): ChatChannel? = channels[id]

    fun broadcastSpeech(
        speaker: Player,
        type: SpeechType,
        text: String,
        potentialListeners: List<Player>
    ): List<Player> {
        val radius = type.hearingRadius
        return potentialListeners.filter { listener ->
            speaker.position.isWithinRange(listener.position, radius, radius)
        }
    }
}
