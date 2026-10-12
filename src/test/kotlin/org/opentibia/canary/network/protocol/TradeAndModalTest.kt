package org.opentibia.canary.network.protocol

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.items.Item
import org.opentibia.canary.map.Position

class TradeAndModalTest {

    @Test
    fun `manages safe trade session and exchanges items on mutual accept`() {
        val tradeManager = TradeManager()

        val player1 = Player(1, "Alice", 100, 100, position = Position(100, 100, 7))
        val player2 = Player(2, "Bob", 100, 100, position = Position(101, 100, 7))

        val sword = Item(id = 2376)
        val shield = Item(id = 2514)

        val session = tradeManager.startTrade(player1, player2, sword)
        assertThat(session).isNotNull
        assertThat(session?.state).isEqualTo(TradeState.Initiated)

        session?.offerItem(player2, shield)
        assertThat(session?.state).isEqualTo(TradeState.BothOffered)

        // Player 1 accepts
        session?.accept(player1)
        assertThat(session?.isCompleted).isFalse()

        // Player 2 accepts -> atomic trade executed
        session?.accept(player2)
        assertThat(session?.isCompleted).isTrue()
    }

    @Test
    fun `dispatches modal window choice to registered callback`() {
        val modalManager = ModalWindowManager()
        val player = Player(1, "Alice", 100, 100, position = Position(100, 100, 7))

        var chosenButton = -1
        val windowId = modalManager.openWindow(
            player = player,
            title = "Quest Prompt",
            message = "Do you accept this task?"
        ) { buttonId, choiceId ->
            chosenButton = buttonId
        }

        // Simulate client response: Button 1 (Yes)
        val handled = modalManager.handleResponse(player, windowId, buttonId = 1, choiceId = 0)
        assertThat(handled).isTrue()
        assertThat(chosenButton).isEqualTo(1)
    }
}
