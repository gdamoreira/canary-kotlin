package org.opentibia.canary.creatures.appearance.outfit

import org.opentibia.canary.creatures.players.components.PlayerSex_t

/**
 * `struct Outfit` (`src/creatures/appearance/outfit/outfit.hpp:23-32`).
 *
 * A registry definition loaded from `data/XML/outfits.xml` by [Outfits]:
 * `name`, `lookType`, `premium`, `unlocked` and `from` in the exact C++
 * constructor order.
 */
data class Outfit(
    val name: String,
    val lookType: Int,
    val premium: Boolean,
    val unlocked: Boolean,
    val from: String,
)

/**
 * `struct OutfitEntry` (`outfit.hpp:15-21`): the player-owned look type plus
 * the addon bitmask of `Player::outfits` (player.hpp:1686). [addons] stays
 * mutable because `Player::addOutfit` / `Player::removeOutfitAddon`
 * (player.cpp:6833-6862) update it in place.
 */
data class OutfitEntry(
    val lookType: Int,
    var addons: Int,
)

/**
 * `struct ProtocolOutfit` (`outfit.hpp:34-41`): an outfit-window row carrying
 * the addons the player owns, built by `ProtocolGame::sendOutfitWindow`
 * (protocolgame.cpp:9079).
 */
data class ProtocolOutfit(
    val name: String,
    val lookType: Int,
    val addons: Int,
)

/**
 * Injected `Player` view for `Outfits::getOutfitByLookType`
 * (outfit.cpp:85-109), whose only player member read is `Player::getSex()`.
 */
fun interface OutfitPlayer {
    fun getSex(): PlayerSex_t
}
