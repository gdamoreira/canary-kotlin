package org.opentibia.canary.game.modal

import org.opentibia.canary.creatures.Player
import org.opentibia.canary.lua.runtime.ScriptingDispatch
import org.opentibia.canary.network.protocol.offline.StartOfflineTrainingCommand
import org.opentibia.canary.systems.offlinetraining.OfflineTrainingApplication

/**
 * Faithful transcription of Canary's `struct ModalWindow`
 * (src/game/modal_window/modal_window.hpp:19-33).
 *
 * [buttons] and [choices] keep Canary's `std::list<std::pair<std::string, uint8_t>>`
 * ordering and pair shape: `(text, id)` as pushed by `ModalWindow:addButton(id, text)`
 * / `ModalWindow:addChoice(id, text)` (src/lua/functions/core/game/modal_window_functions.cpp:137-163).
 * [defaultEnterButton]/[defaultEscapeButton] start at `0xFF` (the client default) and
 * [priority] starts `false`.
 */
class ModalWindow(
    val id: Long,
    var title: String,
    var message: String,
) {
    /** `ModalWindow::buttons` (modal_window.hpp:20). */
    val buttons = mutableListOf<Pair<String, Int>>()

    /** `ModalWindow::choices` (modal_window.hpp:20). */
    val choices = mutableListOf<Pair<String, Int>>()

    /** `ModalWindow::defaultEnterButton` (modal_window.hpp:23). */
    var defaultEnterButton: Int = DEFAULT_BUTTON

    /** `ModalWindow::defaultEscapeButton` (modal_window.hpp:23). */
    var defaultEscapeButton: Int = DEFAULT_BUTTON

    /** `ModalWindow::priority` (modal_window.hpp:24). */
    var priority: Boolean = false

    companion object {
        /** `0xFF` default of both default buttons (modal_window.hpp:30-31). */
        const val DEFAULT_BUTTON: Int = 0xFF
    }
}

/**
 * `ProtocolGame` sender the modal manager forwards to; a `null` client models
 * `Player::client == nullptr`, exactly like the `SpellCooldownClient` seam in
 * `Creature`. The client is per connection, so hosts instantiate one manager per
 * session.
 */
fun interface ModalWindowClient {
    /** `ProtocolGame::sendModalWindow` (src/server/network/protocol/protocolgame.cpp:9630). */
    fun sendModalWindow(modalWindow: ModalWindow)
}

/**
 * Host for the per-player modal window tracking and answer routing of Canary.
 *
 * Canary stores the open window ids on the player (`Player::modalWindows`,
 * player.hpp:1695) and routes answers through `Game::playerAnswerModalWindow`
 * (game.cpp:11144-11176). This port keeps that state in an injectable host
 * (AGENTS.md 1.1) while preserving every method's behavior:
 *  - [sendModalWindow] = `Player::sendModalWindow` (player.cpp:9021-9028)
 *  - [hasModalWindowOpen] = `Player::hasModalWindowOpen` (player.cpp:8314-8316)
 *  - [onModalWindowHandled] = `Player::onModalWindowHandled` (player.cpp:8318-8320)
 *  - [clearModalWindows] = `Player::clearModalWindows` (player.cpp:8331-8333)
 *  - [playerAnswerModalWindow] = `Game::playerAnswerModalWindow` (game.cpp:11144-11176)
 */
class ModalWindowManager(
    /** Runtime entry point for the `onModalWindow` creature events; absent is a no-op. */
    private val scripting: ScriptingDispatch? = null,
    private val client: ModalWindowClient? = null,
) {

    /** `Player::modalWindows` (player.hpp:1695), keyed by player id. */
    private val modalWindows = mutableMapOf<Long, MutableList<Long>>()

    /** `Player::hasModalWindowOpen` (player.cpp:8314-8316). */
    fun hasModalWindowOpen(player: Player, modalWindowId: Long): Boolean =
        modalWindows[player.id]?.contains(modalWindowId) == true

    /** `Player::onModalWindowHandled` (player.cpp:8318-8320): `std::erase`, so every copy. */
    fun onModalWindowHandled(player: Player, modalWindowId: Long) {
        modalWindows[player.id]?.removeAll { it == modalWindowId }
    }

    /** `Player::clearModalWindows` (player.cpp:8331-8333). */
    fun clearModalWindows(player: Player) {
        modalWindows.remove(player.id)
    }

    /**
     * `Player::sendModalWindow` (player.cpp:9021-9028): without a client the window
     * is dropped and never tracked; otherwise the id is appended (no deduplication,
     * mirroring Canary) and the window is handed to the client.
     */
    fun sendModalWindow(player: Player, modalWindow: ModalWindow) {
        val client = this.client ?: return
        playerWindows(player.id).add(modalWindow.id)
        client.sendModalWindow(modalWindow)
    }

    /**
     * `Game::playerAnswerModalWindow` (src/game/game.cpp:11144-11176), with the
     * `getPlayerByID` lookup owned by the caller because the manager is already
     * scoped to one player.
     *
     * Returns whether the answer was accepted: an answer for a window that is not
     * open is dropped, otherwise the window is erased first and the answer is
     * routed either to the hardcoded offline-training branch (`0xFFFFFFFF`) or to
     * every registered `onModalWindow` creature event.
     *
     * PARITY GAP: the offline-training branch checks `bedItem->sleep(player)`
     * before storing the skill and resets `player->setBedItem(nullptr)` afterwards
     * (game.cpp:11160-11170); the Kotlin model has no `BedItem`, so a trainable
     * choice is applied directly and the bed reset is not performed.
     */
    fun playerAnswerModalWindow(player: Player, modalWindowId: Long, button: Int, choice: Int): Boolean {
        if (!hasModalWindowOpen(player, modalWindowId)) {
            return false
        }

        onModalWindowHandled(player, modalWindowId)

        if (modalWindowId == OFFLINE_TRAINING_WINDOW_ID) {
            if (button == 1) {
                val applied = OfflineTrainingApplication.apply(
                    player.features.offlineTraining,
                    StartOfflineTrainingCommand(action = choice),
                ).applied
                if (applied) {
                    return true
                }
            } else {
                player.sendTextMessage("Offline training aborted.")
            }
        } else {
            scripting?.playerAnswerModalWindow(player, modalWindowId, button, choice)
        }

        return true
    }

    private fun playerWindows(playerId: Long): MutableList<Long> =
        modalWindows.getOrPut(playerId) { mutableListOf() }

    companion object {
        /**
         * `std::numeric_limits<uint32_t>::max()` id of `Game::offlineTrainingWindow`
         * (src/game/game.hpp:914).
         */
        const val OFFLINE_TRAINING_WINDOW_ID: Long = 0xFFFFFFFFL
    }
}
