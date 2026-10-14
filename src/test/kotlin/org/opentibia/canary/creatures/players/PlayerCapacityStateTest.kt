package org.opentibia.canary.creatures.players

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.condition.Stat
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.PlayerFlags_t
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.map.Position

/**
 * Transcribes the Player capacity helpers
 * (src/creatures/players/player.cpp:4557-4573, 5441-5476):
 *
 * - `Player::getBaseCapacity` (player.cpp:5441-5449)
 * - `Player::getCapacity` (player.cpp:5451-5459)
 * - `Player::getBonusCapacity` (player.cpp:5461-5466)
 * - `Player::hasCapacity` (player.cpp:4557-4573)
 *
 * `Player::bonusCapacity` (player.hpp) and the wheel capacity stat are
 * constructor/parameter seams; `Player::getFreeCapacity` is the port member.
 * The C++ `item->getTopParent().get() == this` check is modelled by the
 * [Player.hasCapacity] `carriedByPlayer` seam because `Player` is not a
 * `Cylinder` in this port.
 */
class PlayerCapacityStateTest {

    private fun newPlayer() = Player(id = 1, name = "Hero", maxHealth = 150, health = 150, position = Position(100, 100, 7))

    @Test
    fun `getBaseCapacity returns the raw capacity`() {
        // Player::getBaseCapacity (player.cpp:5441-5449).
        val player = newPlayer()

        assertThat(player.getBaseCapacity()).isEqualTo(400.0)
    }

    @Test
    fun `getCapacity adds bonus, stats and wheel capacity`() {
        // Player::getCapacity (player.cpp:5451-5459).
        val player = newPlayer()
        player.varStats[Stat.STAT_CAPACITY.value] = 50

        assertThat(player.getCapacity(bonusCapacity = 20.0, wheelCapacity = 1)).isEqualTo(570.0)
    }

    @Test
    fun `getBonusCapacity returns the bonus field`() {
        // Player::getBonusCapacity (player.cpp:5461-5466).
        assertThat(newPlayer().getBonusCapacity(bonusCapacity = 25.0)).isEqualTo(25.0)
    }

    @Test
    fun `CannotPickupItem zeroes base and capacity but not the bonus`() {
        // player.cpp:5442-5444, 5452-5454, 5462-5464.
        val player = newPlayer()
        player.setFlag(PlayerFlags_t.CannotPickupItem)

        assertThat(player.getBaseCapacity()).isZero()
        assertThat(player.getCapacity()).isZero()
        assertThat(player.getBonusCapacity()).isEqualTo(4_294_967_295.0)
    }

    @Test
    fun `HasInfiniteCapacity saturates base, capacity and bonus`() {
        // player.cpp:5445-5447, 5455-5457, 5462-5464.
        val player = newPlayer()
        player.setFlag(PlayerFlags_t.HasInfiniteCapacity)

        assertThat(player.getBaseCapacity()).isEqualTo(4_294_967_295.0)
        assertThat(player.getCapacity()).isEqualTo(4_294_967_295.0)
        assertThat(player.getBonusCapacity()).isEqualTo(4_294_967_295.0)
    }

    @Test
    fun `hasCapacity weighs stackable items by count`() {
        // Player::hasCapacity (player.cpp:4557-4573): stackable base weight
        // times count compared against getFreeCapacity.
        val type = ItemType(id = 5, name = "Arrow", weight = 1.5, isStackable = true)
        val item = Item(5, count = 100, type = type)
        val player = newPlayer()

        assertThat(player.hasCapacity(item, 100)).isTrue()
        assertThat(player.hasCapacity(item, 266)).isTrue()
        assertThat(player.hasCapacity(item, 267)).isFalse()
    }

    @Test
    fun `hasCapacity accepts already carried and infinite-capacity items`() {
        // player.cpp:4560-4562: `HasInfiniteCapacity || item->getTopParent() == this`.
        val type = ItemType(id = 5, name = "Gold", weight = 100.0, isStackable = true)
        val item = Item(5, count = 1000, type = type)
        val player = newPlayer()

        assertThat(player.hasCapacity(item, 1000)).isFalse()
        assertThat(player.hasCapacity(item, 1000, carriedByPlayer = true)).isTrue()

        val infinite = newPlayer()
        infinite.setFlag(PlayerFlags_t.HasInfiniteCapacity)
        assertThat(infinite.hasCapacity(item, 1000)).isTrue()
    }

    @Test
    fun `hasCapacity refuses everything for CannotPickupItem`() {
        // player.cpp:4558-4560.
        val player = newPlayer()
        player.setFlag(PlayerFlags_t.CannotPickupItem)

        assertThat(player.hasCapacity(Item(1, 1, ItemType(1, "Rock", weight = 0.1)), 1)).isFalse()
    }

    @Test
    fun `hasCapacity weighs containers by their whole weight`() {
        // player.cpp:4566-4569: containers use `item->getWeight()`.
        val light = Container(1, 10, ItemType(1, "Bag", weight = 300.0))
        val heavy = Container(2, 10, ItemType(2, "Bag", weight = 500.0))
        val player = newPlayer()

        assertThat(player.hasCapacity(light, 1)).isTrue()
        assertThat(player.hasCapacity(heavy, 1)).isFalse()
    }
}
