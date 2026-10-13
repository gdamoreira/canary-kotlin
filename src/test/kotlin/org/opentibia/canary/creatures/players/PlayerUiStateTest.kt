package org.opentibia.canary.creatures.players

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.QuickLootFilter_t
import org.opentibia.canary.items.Item
import org.opentibia.canary.map.Position

/**
 * Transcribes the Player UI interaction state
 * (src/creatures/players/player.cpp:3419-3426, 5710-5712, 8314-8333):
 *
 * - `Player::isQuickLootListedItem` (player.cpp:3419-3426)
 * - `Player::getQuickLootFilter` (player.cpp:5710-5712)
 * - `Player::hasModalWindowOpen` (player.cpp:8314-8316)
 * - `Player::onModalWindowHandled` (player.cpp:8318-8320)
 * - `Player::clearModalWindows` (player.cpp:8331-8333)
 * - `Player::sendModalWindow` (player.cpp:9021-9030)
 * - `Player::getLastDepotId` / `setLastDepotId` (player.hpp:555-562)
 *
 * `Player::lastDepotId` defaults to -1 (player.hpp:1825) and
 * `quickLootListItemIds` is the quick-loot id list (player.hpp:1684).
 */
class PlayerUiStateTest {

    private fun newPlayer() = Player(
        id = 11,
        name = "Ui Hero",
        maxHealth = 150,
        health = 150,
        position = Position(100, 100, 7),
    )

    @Test
    fun `quick loot filter reads the player field`() {
        // Player::getQuickLootFilter (player.cpp:5710-5712).
        val player = newPlayer()

        assertThat(player.getQuickLootFilter()).isEqualTo(QuickLootFilter_t.QUICKLOOTFILTER_SKIPPEDLOOT)

        player.quickLootFilter = QuickLootFilter_t.QUICKLOOTFILTER_ACCEPTEDLOOT
        assertThat(player.getQuickLootFilter()).isEqualTo(QuickLootFilter_t.QUICKLOOTFILTER_ACCEPTEDLOOT)
    }

    @Test
    fun `isQuickLootListedItem matches the item id`() {
        // Player::isQuickLootListedItem (player.cpp:3419-3426).
        val player = newPlayer()
        player.quickLootListItemIds = listOf(2160, 2148)

        assertThat(player.isQuickLootListedItem(Item(2148))).isTrue()
        assertThat(player.isQuickLootListedItem(Item(2160))).isTrue()
        assertThat(player.isQuickLootListedItem(Item(1234))).isFalse()
        assertThat(player.isQuickLootListedItem(null)).isFalse()
    }

    @Test
    fun `last depot id defaults to minus one and is an int16`() {
        // player.hpp:1825 `int16_t lastDepotId = -1;` and the inline accessors
        // (player.hpp:555-562).
        val state = PlayerUiState()

        assertThat(state.getLastDepotId()).isEqualTo(-1)

        state.setLastDepotId(42)
        assertThat(state.getLastDepotId()).isEqualTo(42)

        state.setLastDepotId(70_000)
        assertThat(state.getLastDepotId()).isEqualTo(70_000.toShort().toInt())
    }

    @Test
    fun `sendModalWindow tracks the id and forwards to the client`() {
        // Player::sendModalWindow (player.cpp:9021-9030): without a client the
        // id is not tracked.
        val withoutClient = PlayerUiState(hasClient = { false })
        withoutClient.sendModalWindow(7) {}
        assertThat(withoutClient.hasModalWindowOpen(7)).isFalse()

        val sent = mutableListOf<Int>()
        val state = PlayerUiState(hasClient = { true })
        state.sendModalWindow(7) { sent.add(7) }
        state.sendModalWindow(9) { sent.add(9) }

        assertThat(state.hasModalWindowOpen(7)).isTrue()
        assertThat(state.hasModalWindowOpen(9)).isTrue()
        assertThat(state.hasModalWindowOpen(8)).isFalse()
        assertThat(sent).containsExactly(7, 9)
    }

    @Test
    fun `onModalWindowHandled erases one id and clearModalWindows erases all`() {
        // player.cpp:8318-8320 and 8331-8333.
        val state = PlayerUiState(hasClient = { true })
        state.sendModalWindow(7) {}
        state.sendModalWindow(9) {}

        state.onModalWindowHandled(7)
        assertThat(state.hasModalWindowOpen(7)).isFalse()
        assertThat(state.hasModalWindowOpen(9)).isTrue()

        state.clearModalWindows()
        assertThat(state.hasModalWindowOpen(9)).isFalse()
    }
}
