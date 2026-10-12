package org.opentibia.canary.network.protocol

import org.opentibia.canary.creatures.Player
import org.opentibia.canary.items.Item

enum class TradeState {
    Initiated,
    BothOffered,
    Completed,
    Cancelled
}

class TradeSession(
    val player1: Player,
    val player2: Player,
    var item1: Item? = null,
    var item2: Item? = null
) {
    var state: TradeState = TradeState.Initiated
        private set

    private var player1Accepted = false
    private var player2Accepted = false

    val isCompleted: Boolean get() = state == TradeState.Completed

    fun offerItem(player: Player, item: Item) {
        if (player.id == player1.id) {
            item1 = item
        } else if (player.id == player2.id) {
            item2 = item
        }
        if (item1 != null && item2 != null) {
            state = TradeState.BothOffered
        }
    }

    fun accept(player: Player) {
        if (player.id == player1.id) player1Accepted = true
        if (player.id == player2.id) player2Accepted = true

        if (player1Accepted && player2Accepted) {
            state = TradeState.Completed
        }
    }
}

class TradeManager {
    private val sessions = mutableListOf<TradeSession>()

    fun startTrade(player1: Player, player2: Player, item: Item): TradeSession? {
        if (player1.position.getDistanceTo(player2.position) > 2) return null
        val session = TradeSession(player1, player2, item1 = item)
        sessions.add(session)
        return session
    }
}

typealias ModalCallback = (buttonId: Int, choiceId: Int) -> Unit

class ModalWindowManager {
    private var nextWindowId = 1
    private val activeWindows = mutableMapOf<Pair<Long, Int>, ModalCallback>()

    fun openWindow(player: Player, title: String, message: String, callback: ModalCallback): Int {
        val windowId = nextWindowId++
        activeWindows[player.id to windowId] = callback
        return windowId
    }

    fun handleResponse(player: Player, windowId: Int, buttonId: Int, choiceId: Int): Boolean {
        val callback = activeWindows.remove(player.id to windowId) ?: return false
        callback(buttonId, choiceId)
        return true
    }
}
