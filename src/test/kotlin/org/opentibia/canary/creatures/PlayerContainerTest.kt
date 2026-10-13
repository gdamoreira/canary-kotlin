package org.opentibia.canary.creatures

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemAttribute
import org.opentibia.canary.map.Position

/**
 * Transcribes the Player container registry and the inventory container queries:
 *
 * - `Player::openContainers` / `OpenContainer` (player.hpp:118-121, 1671)
 * - `Player::addContainer` / `closeContainer` / `setContainerIndex`
 *   (player.cpp:1292-1327, 1346-1352)
 * - `Player::getContainerByID` / `getContainerID` / `getContainerIndex` /
 *   `getOpenContainers` (player.cpp:1354-1377, 3199-3201)
 * - `Player::getInventoryItemsFromId` (player.cpp:5714-5737),
 *   `Player::getAllInventoryItems` (player.cpp:5861-5890),
 *   `Player::getAllSlotItems` (player.cpp:1086-1098) and
 *   `Player::getBackpack` (player.cpp:9631-9639) over the depth-first
 *   `ContainerIterator` (container.cpp:1222-1298).
 */
class PlayerContainerTest {

    private fun newPlayer() = Player(
        id = 21,
        name = "Container Hero",
        maxHealth = 150,
        health = 150,
        position = Position(100, 100, 7),
    )

    private fun backpack(capacity: Int = 20) = Container(id = 2854, capacity = capacity)

    private fun item(id: Int, count: Int = 1, tier: Int = 0) = Item(id = id, count = count).apply {
        if (tier > 0) setAttribute(ItemAttribute.TIER, tier.toLong())
    }

    @Test
    fun `addContainer stores the container with a zero index`() {
        // Player::addContainer (player.cpp:1292-1312).
        val player = newPlayer()
        val bag = backpack()

        player.addContainer(3, bag)

        assertThat(player.getContainerByID(3)).isSameAs(bag)
        assertThat(player.getContainerIndex(3)).isEqualTo(0)
    }

    @Test
    fun `addContainer ignores ids above fifteen`() {
        // player.cpp:1293-1295: `cid > 0xF` returns without storing.
        val player = newPlayer()

        player.addContainer(16, backpack())

        assertThat(player.getContainerByID(16)).isNull()
        assertThat(player.getOpenContainers()).isEmpty()
    }

    @Test
    fun `addContainer ignores a missing container`() {
        // player.cpp:1297-1299.
        val player = newPlayer()

        player.addContainer(1, null)

        assertThat(player.getOpenContainers()).isEmpty()
    }

    @Test
    fun `addContainer replaces an existing id and resets the scroll index`() {
        // player.cpp:1301-1305: an existing OpenContainer gets the new container and index 0.
        val player = newPlayer()
        val first = backpack()
        val second = backpack()
        player.addContainer(1, first)
        player.setContainerIndex(1, 7)

        player.addContainer(1, second)

        assertThat(player.getContainerByID(1)).isSameAs(second)
        assertThat(player.getContainerIndex(1)).isEqualTo(0)
    }

    @Test
    fun `getContainerByID returns null for an unknown id`() {
        // player.cpp:1354-1360.
        val player = newPlayer()

        assertThat(player.getContainerByID(0)).isNull()
        assertThat(player.getContainerByID(15)).isNull()
    }

    @Test
    fun `getContainerID finds the open id and minus one otherwise`() {
        // Player::getContainerID (player.cpp:1362-1369) returns int8_t -1.
        val player = newPlayer()
        val bag = backpack()
        player.addContainer(5, bag)

        assertThat(player.getContainerID(bag)).isEqualTo(5)
        assertThat(player.getContainerID(backpack())).isEqualTo(-1)
    }

    @Test
    fun `setContainerIndex updates only an open container`() {
        // Player::setContainerIndex (player.cpp:1346-1352).
        val player = newPlayer()
        val bag = backpack()
        player.addContainer(2, bag)

        player.setContainerIndex(2, 42)
        player.setContainerIndex(9, 7)

        assertThat(player.getContainerIndex(2)).isEqualTo(42)
        assertThat(player.getContainerIndex(9)).isEqualTo(0)
    }

    @Test
    fun `closeContainer removes the open container`() {
        // Player::closeContainer (player.cpp:1314-1327).
        val player = newPlayer()
        player.addContainer(4, backpack())

        player.closeContainer(4)

        assertThat(player.getContainerByID(4)).isNull()
        assertThat(player.getOpenContainers()).isEmpty()
    }

    @Test
    fun `closeContainer ignores an unknown id`() {
        // player.cpp:1315-1318.
        val player = newPlayer()
        player.addContainer(1, backpack())

        player.closeContainer(2)

        assertThat(player.getContainerByID(1)).isNotNull()
    }

    @Test
    fun `getOpenContainers is ordered by the container id`() {
        // player.hpp:1671 keeps `std::map<uint8_t, OpenContainer>`.
        val player = newPlayer()
        player.addContainer(15, backpack())
        player.addContainer(1, backpack())

        assertThat(player.getOpenContainers().keys).containsExactly(1, 15)
    }

    @Test
    fun `getBackpack returns the container equipped in the backpack slot`() {
        // Player::getBackpack (player.cpp:9631-9639).
        val player = newPlayer()
        assertThat(player.getBackpack()).isNull()

        val bag = backpack()
        player.addItem(bag, Slots_t.CONST_SLOT_BACKPACK)

        assertThat(player.getBackpack()).isSameAs(bag)
    }

    @Test
    fun `getBackpack ignores a non container backpack item`() {
        // player.cpp:9637-9638: the slot item must expose a container.
        val player = newPlayer()
        player.addItem(item(2854), Slots_t.CONST_SLOT_BACKPACK)

        assertThat(player.getBackpack()).isNull()
    }

    @Test
    fun `getAllSlotItems maps every occupied slot`() {
        // Player::getAllSlotItems (player.cpp:1086-1098).
        val player = newPlayer()
        val sword = item(2376)
        val bag = backpack()
        player.addItem(sword, Slots_t.CONST_SLOT_LEFT)
        player.addItem(bag, Slots_t.CONST_SLOT_BACKPACK)

        assertThat(player.getAllSlotItems()).containsOnlyKeys(
            Slots_t.CONST_SLOT_LEFT,
            Slots_t.CONST_SLOT_BACKPACK,
        )
        assertThat(player.getAllSlotItems()[Slots_t.CONST_SLOT_LEFT]).isSameAs(sword)
        assertThat(player.getAllSlotItems()[Slots_t.CONST_SLOT_BACKPACK]).isSameAs(bag)
    }

    @Test
    fun `getInventoryItemsFromId finds the equipped item and nested container items`() {
        // player.cpp:5714-5737: slots first, then the depth-first container iterator.
        val player = newPlayer()
        val sword = item(2376)
        val bag = backpack()
        val gold = item(2148, count = 100)
        val sub = backpack()
        val nestedGold = item(2148, count = 50)
        val nestedSword = item(2376)
        sub.addItem(nestedGold)
        sub.addItem(nestedSword)
        bag.addItem(gold)
        bag.addItem(sub)
        player.addItem(sword, Slots_t.CONST_SLOT_LEFT)
        player.addItem(bag, Slots_t.CONST_SLOT_BACKPACK)

        // Slots ascend (BACKPACK 3 before LEFT 6), so the nested items come first.
        assertThat(player.getInventoryItemsFromId(2376, ignore = false))
            .containsExactly(nestedSword, sword)
        assertThat(player.getInventoryItemsFromId(2148)).containsExactly(gold, nestedGold)
        assertThat(player.getInventoryItemsFromId(9999)).isEmpty()
    }

    @Test
    fun `getInventoryItemsFromId can skip the equipped item itself`() {
        // player.cpp:5722-5724 matches the slot item only when `ignore` is false.
        val player = newPlayer()
        val sword = item(2376)
        player.addItem(sword, Slots_t.CONST_SLOT_LEFT)

        assertThat(player.getInventoryItemsFromId(2376)).isEmpty()
        assertThat(player.getInventoryItemsFromId(2376, ignore = false)).containsExactly(sword)
    }

    @Test
    fun `getAllInventoryItems walks slots and nested containers`() {
        // player.cpp:5861-5890: slot items first, then every contained item.
        val player = newPlayer()
        val sword = item(2376)
        val bag = backpack()
        val gold = item(2148, count = 100)
        val sub = backpack()
        val nestedGold = item(2148, count = 50)
        sub.addItem(nestedGold)
        bag.addItem(gold)
        bag.addItem(sub)
        player.addItem(sword, Slots_t.CONST_SLOT_LEFT)
        player.addItem(bag, Slots_t.CONST_SLOT_BACKPACK)

        // Slots ascend (BACKPACK 3 before LEFT 6).
        assertThat(player.getAllInventoryItems())
            .containsExactly(bag, gold, sub, nestedGold, sword)
        assertThat(player.getAllInventoryItems(ignoreEquiped = true))
            .containsExactly(gold, sub, nestedGold)
    }

    @Test
    fun `getAllInventoryItems can skip tiered container items`() {
        // player.cpp:5880-5882: `ignoreItemWithTier` filters contained tier > 0 items only.
        val player = newPlayer()
        val bag = backpack()
        val tiered = item(2148, tier = 3)
        val plain = item(2148)
        bag.addItem(tiered)
        bag.addItem(plain)
        player.addItem(bag, Slots_t.CONST_SLOT_BACKPACK)

        assertThat(player.getAllInventoryItems(ignoreItemWithTier = true))
            .containsExactly(bag, plain)
    }

    @Test
    fun `inventory container iteration terminates on a self referencing container`() {
        // ContainerIterator cycle detection (container.cpp:1288-1292).
        val player = newPlayer()
        val recursive = backpack()
        recursive.addItem(recursive)
        player.addItem(recursive, Slots_t.CONST_SLOT_BACKPACK)

        assertThat(player.getAllInventoryItems()).containsExactly(recursive, recursive)
    }
}
