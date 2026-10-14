package org.opentibia.canary.creatures.players

import org.opentibia.canary.items.BedItem

/**
 * Mirror of the Player bed-item holder
 * (src/creatures/players/player.cpp:7865-7871).
 *
 * Canary keeps the private `std::shared_ptr<BedItem> bedItem = nullptr`
 * directly on `Player` (player.hpp:1756, private section from 1585); the
 * sleep/wake paths assign it through `Player::setBedItem` and the
 * offline-training modal resets it with a null argument (game.cpp:11170). The
 * state is exposed as an injectable holder so the host player can delegate
 * without a global (AGENTS.md 1.1).
 */
class PlayerBedState(
    /** `Player::bedItem` (player.hpp:1756); `nullptr` by default. */
    bedItem: BedItem? = null,
) {

    /** `Player::bedItem` (player.hpp:1756); private in Canary. */
    private var bedItemRef: BedItem? = bedItem

    /** `Player::getBedItem()` (player.cpp:7865-7867). */
    fun getBedItem(): BedItem? = bedItemRef

    /** `Player::setBedItem(b)` (player.cpp:7869-7871): `bedItem = std::move(b)`. */
    fun setBedItem(b: BedItem?) {
        bedItemRef = b
    }
}
