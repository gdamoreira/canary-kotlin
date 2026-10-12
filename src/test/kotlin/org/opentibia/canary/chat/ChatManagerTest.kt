package org.opentibia.canary.chat

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.map.Position

class ChatManagerTest {

    @Test
    fun `broadcasts local talk to nearby spectators within hearing range`() {
        val chatManager = ChatManager()
        val speaker = Player(1, "Alice", 100, 100, position = Position(100, 100, 7))
        val nearListener = Player(2, "Bob", 100, 100, position = Position(103, 103, 7)) // distance = 3 (within range 4)
        val farListener = Player(3, "Charlie", 100, 100, position = Position(120, 120, 7)) // out of range

        val allPlayers = listOf(speaker, nearListener, farListener)
        val recipients = chatManager.broadcastSpeech(
            speaker = speaker,
            type = SpeechType.Say,
            text = "Hello world!",
            potentialListeners = allPlayers
        )

        assertThat(recipients).contains(speaker, nearListener)
        assertThat(recipients).doesNotContain(farListener)
    }

    @Test
    fun `manages public chat channels and subscriber messaging`() {
        val chatManager = ChatManager()
        val worldChannel = chatManager.createChannel(id = 1, name = "World Chat")

        val player1 = Player(1, "Alice", 100, 100, position = Position(100, 100, 7))
        val player2 = Player(2, "Bob", 100, 100, position = Position(100, 100, 7))

        assertThat(worldChannel.join(player1)).isTrue()
        assertThat(worldChannel.join(player2)).isTrue()

        val messages = mutableListOf<String>()
        worldChannel.sendMessage(player1, "Welcome to World Chat") { recipient, msg ->
            messages.add("${recipient.name}: $msg")
        }

        assertThat(messages).hasSize(2)
        assertThat(messages).contains("Alice: [World Chat] Alice: Welcome to World Chat")
        assertThat(messages).contains("Bob: [World Chat] Alice: Welcome to World Chat")
    }
}
