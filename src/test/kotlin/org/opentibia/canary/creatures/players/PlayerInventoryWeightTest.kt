package org.opentibia.canary.creatures.players

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.PlayerFlags_t
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.map.Position

/**
 * Transcript tests for `Player::updateInventoryWeight` and its interaction with
 * `Player::getFreeCapacity`.
 *
 * Source of truth: ../.projects/canary
 *  - src/creatures/players/player.cpp:1072-1084 (`updateInventoryWeight`)
 *  - src/creatures/players/player.cpp:5468-5476 (`getFreeCapacity`)
 *  - src/creatures/players/player.hpp:1798 (`inventoryWeight`)
 */
class PlayerInventoryWeightTest {

    private fun newPlayer() = Player(id = 1, name = "Hero", maxHealth = 150, health = 150, position = Position(100, 100, 7))

    private fun rock(weight: Double = 50.0) = Item(1, 1, ItemType(1, "Rock", weight = weight))

    @Test
    fun `updateInventoryWeight sums every carried item`() {
        // player.cpp:1076-1083.
        val player = newPlayer()
        player.addItem(rock(50.0))
        player.addItem(rock(30.0), org.opentibia.canary.creatures.Slots_t.CONST_SLOT_BACKPACK)

        player.updateInventoryWeight()

        assertThat(player.getFreeCapacity()).isEqualTo(320.0)
    }

    @Test
    fun `addItem and removeItem refresh the cached weight`() {
        // Game::internalAddItem -> postAddNotification -> updateInventoryWeight.
        val player = newPlayer()
        val item = rock(75.0)

        player.addItem(item)
        assertThat(player.getFreeCapacity()).isEqualTo(325.0)

        player.removeItem(item)
        assertThat(player.getFreeCapacity()).isEqualTo(400.0)
    }

    @Test
    fun `updateInventoryWeight keeps the cached weight under infinite capacity`() {
        // player.cpp:1073-1075: the infinite-capacity flag returns before the reset.
        val stack = Item(5, 100, ItemType(5, "Arrow", weight = 1.0, isStackable = true))
        val player = newPlayer()
        player.addItem(stack)
        assertThat(player.getFreeCapacity()).isEqualTo(300.0)

        stack.count = 5
        player.setFlag(PlayerFlags_t.HasInfiniteCapacity)
        player.updateInventoryWeight()
        player.removeFlag(PlayerFlags_t.HasInfiniteCapacity)

        // The cached 100 oz survives the guarded update even though the live
        // inventory now weighs 5 oz.
        assertThat(player.getFreeCapacity()).isEqualTo(300.0)
    }
}
