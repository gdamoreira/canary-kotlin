package org.opentibia.canary.creatures

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.map.Position
import org.opentibia.canary.systems.rewardchest.ReturnValue

/**
 * Transcribes the inventory primitives of Canary's Player:
 * `Player::getInventoryItem` (src/creatures/players/player.cpp:337),
 * `Player::getItemTypeCount` (player.cpp:5151-5179),
 * `Player::removeItem` (player.cpp:10251-10281) and
 * `Player::getFreeCapacity` (player.cpp:5468-5476).
 * Slot assignment mirrors `Game::internalPlayerAddItem`/`internalAddItem`
 * (src/game/game.cpp:2693-3003).
 */
class PlayerInventoryTest {

    private val gold = ItemType(id = 2148, name = "gold coin", isStackable = true, weight = 0.1)

    private fun newPlayer(capacity: Double = 400.0) = Player(
        id = 42,
        name = "Inventory Hero",
        maxHealth = 150,
        health = 150,
        capacity = capacity,
        position = Position(100, 100, 7),
    )

    @Test
    fun `slot enum matches Canary Slots_t values`() {
        assertThat(Slots_t.CONST_SLOT_WHEREEVER.value).isEqualTo(0)
        assertThat(Slots_t.CONST_SLOT_HEAD.value).isEqualTo(1)
        assertThat(Slots_t.CONST_SLOT_NECKLACE.value).isEqualTo(2)
        assertThat(Slots_t.CONST_SLOT_BACKPACK.value).isEqualTo(3)
        assertThat(Slots_t.CONST_SLOT_ARMOR.value).isEqualTo(4)
        assertThat(Slots_t.CONST_SLOT_RIGHT.value).isEqualTo(5)
        assertThat(Slots_t.CONST_SLOT_LEFT.value).isEqualTo(6)
        assertThat(Slots_t.CONST_SLOT_LEGS.value).isEqualTo(7)
        assertThat(Slots_t.CONST_SLOT_FEET.value).isEqualTo(8)
        assertThat(Slots_t.CONST_SLOT_RING.value).isEqualTo(9)
        assertThat(Slots_t.CONST_SLOT_AMMO.value).isEqualTo(10)
        assertThat(Slots_t.CONST_SLOT_STORE_INBOX.value).isEqualTo(11)
        assertThat(Slots_t.FIRST).isEqualTo(1)
        assertThat(Slots_t.LAST).isEqualTo(11)
    }

    @Test
    fun `addItem places an item in the first free slot when wherever`() {
        val player = newPlayer()
        val sword = Item(id = 2376, type = ItemType(id = 2376, name = "sword", weight = 40.0))

        val result = player.addItem(sword)

        assertThat(result).isEqualTo(ReturnValue.NOERROR)
        assertThat(player.getInventoryItem(Slots_t.CONST_SLOT_HEAD)).isSameAs(sword)
    }

    @Test
    fun `addItem honors an explicit slot`() {
        val player = newPlayer()
        val ring = Item(id = 2210, type = ItemType(id = 2210, name = "ring", weight = 1.0))

        assertThat(player.addItem(ring, Slots_t.CONST_SLOT_RING)).isEqualTo(ReturnValue.NOERROR)
        assertThat(player.getInventoryItem(Slots_t.CONST_SLOT_RING)).isSameAs(ring)
        assertThat(player.getInventoryItem(Slots_t.CONST_SLOT_HEAD)).isNull()
    }

    @Test
    fun `getInventoryItem returns null for the wherever slot`() {
        val player = newPlayer()
        player.addItem(Item(id = 2376, type = ItemType(id = 2376, name = "sword", weight = 40.0)))

        assertThat(player.getInventoryItem(Slots_t.CONST_SLOT_WHEREEVER)).isNull()
    }

    @Test
    fun `addItem fails when every slot is occupied`() {
        val player = newPlayer()
        for (slot in Slots_t.entries.filter { it.value in Slots_t.FIRST..Slots_t.LAST }) {
            assertThat(player.addItem(Item(id = 1000 + slot.value), slot)).isEqualTo(ReturnValue.NOERROR)
        }

        assertThat(player.addItem(Item(id = 9999))).isEqualTo(ReturnValue.NOTENOUGHROOM)
    }

    @Test
    fun `addItem rejects an item heavier than the free capacity`() {
        val player = newPlayer(capacity = 10.0)
        val anvil = Item(id = 1, type = ItemType(id = 1, name = "anvil", weight = 50.0))

        assertThat(player.addItem(anvil)).isEqualTo(ReturnValue.NOTENOUGHCAPACITY)
        assertThat(player.getInventoryItem(Slots_t.CONST_SLOT_HEAD)).isNull()
    }

    @Test
    fun `getFreeCapacity subtracts the carried weight from capacity`() {
        val player = newPlayer(capacity = 100.0)
        assertThat(player.getFreeCapacity()).isEqualTo(100.0)

        player.addItem(Item(id = 1, type = ItemType(id = 1, name = "rock", weight = 30.0)))

        assertThat(player.getFreeCapacity()).isEqualTo(70.0)
    }

    @Test
    fun `getItemCount sums stackable amounts across slots`() {
        val player = newPlayer()
        player.addItem(Item(id = 2148, count = 100, type = gold), Slots_t.CONST_SLOT_BACKPACK)
        player.addItem(Item(id = 2148, count = 50, type = gold), Slots_t.CONST_SLOT_AMMO)

        assertThat(player.getItemCount(2148)).isEqualTo(150L)
        assertThat(player.getItemCount(2160)).isEqualTo(0L)
    }

    @Test
    fun `removeItem clamps the count and clears an emptied slot`() {
        val player = newPlayer()
        val coins = Item(id = 2148, count = 100, type = gold)
        player.addItem(coins, Slots_t.CONST_SLOT_BACKPACK)

        assertThat(player.removeItem(coins, 30)).isEqualTo(ReturnValue.NOERROR)
        assertThat(coins.count).isEqualTo(70)

        assertThat(player.removeItem(coins)).isEqualTo(ReturnValue.NOERROR)
        assertThat(coins.count).isEqualTo(0)
        assertThat(player.getInventoryItem(Slots_t.CONST_SLOT_BACKPACK)).isNull()
    }

    @Test
    fun `removeItem reports an item that is not carried`() {
        val player = newPlayer()
        val stray = Item(id = 2160, type = ItemType(id = 2160, name = "crystal", weight = 1.0))

        assertThat(player.removeItem(stray)).isEqualTo(ReturnValue.NOTPOSSIBLE)
    }
}
