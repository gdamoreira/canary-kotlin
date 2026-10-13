package org.opentibia.canary.creatures.players.storages

/**
 * `using StashItemList = std::map<uint16_t, uint32_t>`
 * (src/creatures/creatures_definitions.hpp:1707). The `std::map` iterates in
 * ascending item-id order, mirrored here by [Stash]'s sorted backing map.
 */
typealias StashItemList = Map<Int, Int>

/**
 * State holder for Canary's `Player::stashItems` supply stash and its runtime:
 *
 *  - `Player::addItemOnStash`   (src/creatures/players/player.cpp:5404)
 *  - `Player::getStashItemCount` (player.cpp:5414)
 *  - `Player::withdrawItem`      (player.cpp:5422)
 *  - `Player::getStashItems`     (player.cpp:5437)
 *
 * Canary stores the map directly on `Player`; following AGENTS.md 1.1 the state
 * is a dependency that can be injected into the player instead of a global.
 * The `uint32_t` arithmetic is kept on [Int], which wraps identically on
 * overflow (see PARITY GAPS in the wave report).
 */
class Stash {

    /** `StashItemList stashItems` (player.hpp:1826): ordered by item id. */
    private val stashItems = sortedMapOf<Int, Int>()

    /** `Player::addItemOnStash` (player.cpp:5404). */
    fun addItemOnStash(itemId: Int, amount: Int) {
        stashItems[itemId] = (stashItems[itemId] ?: 0) + amount
    }

    /** `Player::getStashItemCount` (player.cpp:5414): 0 when not stashed. */
    fun getStashItemCount(itemId: Int): Int = stashItems[itemId] ?: 0

    /**
     * `Player::withdrawItem` (player.cpp:5422): removes [amount] from the stack,
     * deleting the entry when it empties; returns false (state untouched) for
     * unknown items or an amount larger than the stored stack.
     */
    fun withdrawItem(itemId: Int, amount: Int): Boolean {
        val count = stashItems[itemId] ?: return false
        when {
            count > amount -> stashItems[itemId] = count - amount
            count == amount -> stashItems.remove(itemId)
            else -> return false
        }
        return true
    }

    /** `Player::getStashItems` (player.cpp:5437): a copy of the `StashItemList`. */
    fun getStashItems(): StashItemList = stashItems.toMap()
}
