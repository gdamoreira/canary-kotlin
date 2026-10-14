package org.opentibia.canary.creatures.players

import org.opentibia.canary.creatures.Player
import org.opentibia.canary.items.Augment_t
import org.opentibia.canary.items.Item

/**
 * The C++ `std::pair<uint16_t, uint8_t>` map key of
 * `Player::getEquippedAugments` (`(spellId, augmentType)`); player.cpp:5938.
 */
data class AugmentKey(val spellId: Int, val type: Augment_t)

/**
 * The player equipment-augment queries
 * (src/creatures/players/player.cpp:5892-5947):
 *
 * - `Player::getEquippedAugmentItemsByType` (player.cpp:5892-5905)
 * - `Player::getEquippedAugmentItems` (player.cpp:5907-5919)
 * - `Player::getEquippedAugments` (player.cpp:5921-5947)
 *
 * The port's [Player] already owns the equipment (`getEquippedItems` extension,
 * player.cpp:5949-5969), so the queries are extension functions. Canary reads
 * the augments through `Item::getAugments()` (`items[id].augments`,
 * item.hpp:424-426), which the port stores on the item's [org.opentibia.canary.items.ItemType].
 * `g_spells().getInstantSpellByName` and `spell->getSpellId()` are folded into
 * the injected [spellIdByName] lookup (AGENTS.md 1.1), which yields `null`/0
 * for unknown names exactly like the C++ `spell ? spell->getSpellId() : 0`.
 */

/** `Player::getEquippedAugmentItemsByType` (player.cpp:5892-5905). */
fun Player.getEquippedAugmentItemsByType(augmentType: Augment_t): List<Item> =
    getEquippedItems().filter { item -> item.type?.augments.orEmpty().any { it.type == augmentType } }

/** `Player::getEquippedAugmentItems` (player.cpp:5907-5919). */
fun Player.getEquippedAugmentItems(): List<Item> =
    getEquippedItems().filter { !it.type?.augments.isNullOrEmpty() }

/**
 * `Player::getEquippedAugments` (player.cpp:5921-5947). The map key is the
 * `(spellId, augmentType)` pair; the value accumulates
 * `augment.value / divisor` where cooldowns use a `-1000.0` divisor and every
 * other type `100.0`. Augments without a resolved spell id and `None`-typed
 * augments are skipped.
 */
fun Player.getEquippedAugments(spellIdByName: (String) -> Int? = { null }): Map<AugmentKey, Double> {
    val equippedAugments = linkedMapOf<AugmentKey, Double>()

    for (item in getEquippedAugmentItems()) {
        for (augment in item.type?.augments.orEmpty()) {
            if (augment.type == Augment_t.None) {
                continue
            }

            val spellId = spellIdByName(augment.spellName) ?: 0
            if (spellId == 0) {
                continue
            }

            val key = AugmentKey(spellId, augment.type)
            val divisor = if (augment.type == Augment_t.Cooldown) -1000.0 else 100.0
            equippedAugments[key] = (equippedAugments[key] ?: 0.0) + augment.value / divisor
        }
    }

    return equippedAugments
}
