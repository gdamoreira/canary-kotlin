package org.opentibia.canary.creatures.players

import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.QuickLootFilter_t
import org.opentibia.canary.items.Item

/**
 * Self-contained mirror of the Player UI interaction state
 * (src/creatures/players/player.cpp:8314-8333, 9021-9030; player.hpp:555-562).
 *
 * The class is intentionally *not* `Player.kt` (per task constraints). Canary
 * keeps the `std::vector<uint32_t> modalWindows` and `lastDepotId` directly on
 * `Player` (player.hpp:1695, 1825); the state is exposed as an injectable seam
 * so the host player can delegate without a global (AGENTS.md 1.1). The
 * protocol dependency is a constructor seam: [hasClient] replaces
 * `Player::client != nullptr` (player.cpp:9022) and the `send` argument
 * replaces `client->sendModalWindow(modalWindow)` (player.cpp:9028).
 *
 * The methods keep the exact `Player::` names of the C++ symbols they
 * transcribe, so every behavior is auditable against the C++ line references.
 */
class PlayerUiState(
    /** `Player::client != nullptr` (player.cpp:9022). */
    private val hasClient: () -> Boolean = { false },
    /** `Player::lastDepotId` (player.hpp:1825); `int16_t`, default -1. */
    lastDepotId: Int = -1,
) {

    /** `Player::modalWindows` (player.hpp:1695). */
    private val modalWindows = mutableListOf<Int>()

    /** `Player::lastDepotId` (player.hpp:1825). */
    private var lastDepotIdValue: Int = lastDepotId.toShort().toInt()

    /** `Player::hasModalWindowOpen` (player.cpp:8314-8316). */
    fun hasModalWindowOpen(modalWindowId: Int): Boolean = modalWindowId in modalWindows

    /** `Player::onModalWindowHandled` (player.cpp:8318-8320) — `std::erase`. */
    fun onModalWindowHandled(modalWindowId: Int) {
        modalWindows.removeAll { it == modalWindowId }
    }

    /** `Player::clearModalWindows` (player.cpp:8331-8333). */
    fun clearModalWindows() {
        modalWindows.clear()
    }

    /**
     * `Player::sendModalWindow` (player.cpp:9021-9030): without a client the id
     * is not tracked; otherwise the id is recorded before the client send. The
     * C++ `ModalWindow` payload is replaced by [modalWindowId] plus the [send]
     * seam.
     */
    fun sendModalWindow(modalWindowId: Int, send: () -> Unit) {
        if (!hasClient()) {
            return
        }

        modalWindows.add(modalWindowId)
        send()
    }

    /** `Player::getLastDepotId` (player.hpp:559-561). */
    fun getLastDepotId(): Int = lastDepotIdValue

    /** `Player::setLastDepotId` (player.hpp:555-558); the C++ `int16_t` truncates. */
    fun setLastDepotId(newId: Int) {
        lastDepotIdValue = newId.toShort().toInt()
    }
}

/**
 * `Player::getQuickLootFilter` (player.cpp:5710-5712): the filter is already a
 * `Player` field (`quickLootFilter`, player.hpp:1859).
 */
fun Player.getQuickLootFilter(): QuickLootFilter_t = quickLootFilter

/**
 * `Player::isQuickLootListedItem` (player.cpp:3419-3426): a null item is never
 * listed; the id list is `Player::quickLootListItemIds` (player.hpp:1684).
 */
fun Player.isQuickLootListedItem(item: Item?): Boolean {
    if (item == null) {
        return false
    }

    return quickLootListItemIds.contains(item.id)
}
