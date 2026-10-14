package org.opentibia.canary.creatures.players

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.items.BedItem

/**
 * Transcribes the Player bed-item holder
 * (src/creatures/players/player.cpp:7865-7871):
 *
 * - `Player::getBedItem` (player.cpp:7865-7867)
 * - `Player::setBedItem` (player.cpp:7869-7871)
 *
 * Canary keeps `std::shared_ptr<BedItem> bedItem = nullptr` directly on
 * `Player` (player.hpp:1756); the state is exposed as an injectable holder so
 * the host player can delegate without a global (AGENTS.md 1.1).
 */
class PlayerBedStateTest {

    @Test
    fun `bed item defaults to null`() {
        // player.hpp:1756.
        assertThat(PlayerBedState().getBedItem()).isNull()
    }

    @Test
    fun `setBedItem stores and getBedItem reads the item`() {
        // player.cpp:7865-7871.
        val state = PlayerBedState()
        val bed = BedItem(1743)

        state.setBedItem(bed)
        assertThat(state.getBedItem()).isSameAs(bed)
    }

    @Test
    fun `setBedItem accepts null like the C++ shared_ptr assignment`() {
        // `bedItem = std::move(b)` also clears the holder when b is null
        // (ModalWindow offline-training reset, game.cpp:11170).
        val state = PlayerBedState()
        state.setBedItem(BedItem(1743))

        state.setBedItem(null)
        assertThat(state.getBedItem()).isNull()
    }
}
