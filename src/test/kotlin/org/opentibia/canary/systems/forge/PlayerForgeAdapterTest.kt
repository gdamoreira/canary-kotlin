package org.opentibia.canary.systems.forge

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.Slots_t
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemAttribute
import org.opentibia.canary.map.Position

/**
 * Transcript tests for [PlayerForgeAdapter] against the real Kotlin `Player`
 * inventory.
 *
 * Source of truth: /Users/damoreira/.projects/canary
 *  - src/creatures/players/player.cpp:6013 `getForgeItemFromId`
 *  - src/creatures/players/player.cpp:1561 `getForgeSliversAndCores`
 *  - src/creatures/players/player.cpp:5335,5361 `hasItemCountById` / `removeItemCountById`
 *  - src/creatures/players/player.cpp:12578 `getFreeBackpackSlots`
 *  - src/creatures/players/player.cpp:1532,8299 `getMoney` / `getBankBalance`
 */
class PlayerForgeAdapterTest {

    private fun hero(): Player = Player(1, "Hero", 100, 100, position = Position(100, 100, 7))

    private fun tieredItem(itemId: Int, tier: Int, count: Int = 1): Item =
        Item.create(itemId, count).apply { setAttribute(ItemAttribute.TIER, tier.toLong()) }

    @Test
    fun `getForgeItemFromId matches an inventory item by id and tier and skips the excluded one`() {
        val player = hero()
        player.addItem(tieredItem(100, 2))
        player.addItem(tieredItem(100, 2))
        player.addItem(tieredItem(100, 1))
        val adapter = PlayerForgeAdapter(player)

        val first = adapter.getForgeItemFromId(100, 2)
        val second = adapter.getForgeItemFromId(100, 2, first)

        assertThat(first).isNotNull()
        assertThat(first!!.tier).isEqualTo(2)
        assertThat(second).isNotNull()
        assertThat(second).isNotSameAs(first)
        assertThat(adapter.getForgeItemFromId(100, 3)).isNull()
    }

    @Test
    fun `internalAddItem stores the item tier and syncItemTiers persists later promotions`() {
        val player = hero()
        val adapter = PlayerForgeAdapter(player)

        val view = adapter.createItem(100, 1)
        view.tier = 3
        assertThat(adapter.internalAddItem(view)).isTrue()

        val stored = player.getInventoryItem(Slots_t.CONST_SLOT_HEAD)
        assertThat(stored).isNotNull()
        assertThat(stored!!.getAttribute(ItemAttribute.TIER)).isEqualTo(3L)

        view.tier = 4
        adapter.syncItemTiers()
        assertThat(stored.getAttribute(ItemAttribute.TIER)).isEqualTo(4L)
    }

    @Test
    fun `hasItemCountById and removeItemCountById use real inventory counts`() {
        val player = hero()
        player.addItem(Item.create(ForgeItemIds.ITEM_FORGE_SLIVER, 50))
        val adapter = PlayerForgeAdapter(player)

        assertThat(adapter.hasItemCountById(ForgeItemIds.ITEM_FORGE_SLIVER, 50, true)).isTrue()
        assertThat(adapter.hasItemCountById(ForgeItemIds.ITEM_FORGE_SLIVER, 51, true)).isFalse()
        assertThat(adapter.removeItemCountById(ForgeItemIds.ITEM_FORGE_SLIVER, 50)).isTrue()
        assertThat(player.getItemCount(ForgeItemIds.ITEM_FORGE_SLIVER)).isEqualTo(0)
        assertThat(adapter.removeItemCountById(ForgeItemIds.ITEM_FORGE_SLIVER, 1)).isFalse()
    }

    @Test
    fun `removeItemCountById removes stackable slivers nested in a container`() {
        val player = hero()
        val backpack = Container(1988, capacity = 20)
        val slivers = Item.create(ForgeItemIds.ITEM_FORGE_SLIVER, 50)
        backpack.addItem(slivers)
        player.addItem(backpack, Slots_t.CONST_SLOT_BACKPACK)
        val adapter = PlayerForgeAdapter(player)

        assertThat(adapter.removeItemCountById(ForgeItemIds.ITEM_FORGE_SLIVER, 20)).isTrue()
        assertThat(slivers.count).isEqualTo(30)
        assertThat(player.getItemCount(ForgeItemIds.ITEM_FORGE_SLIVER)).isEqualTo(30)
    }

    @Test
    fun `getForgeSliversAndCores adds the injected stash counts`() {
        val player = hero()
        player.addItem(Item.create(ForgeItemIds.ITEM_FORGE_SLIVER, 5))
        player.addItem(Item.create(ForgeItemIds.ITEM_FORGE_CORE, 1))
        val adapter = PlayerForgeAdapter(
            player = player,
            stashSlivers = { 10L },
            stashCores = { 2L },
        )

        assertThat(adapter.getForgeSliversAndCores()).isEqualTo(15L to 3L)
    }

    @Test
    fun `getFreeBackpackSlots falls back to free inventory slots and uses the equipped backpack`() {
        val player = hero()
        val adapter = PlayerForgeAdapter(player)
        assertThat(adapter.getFreeBackpackSlots()).isEqualTo(Slots_t.LAST - Slots_t.FIRST + 1)

        val backpack = Container(1988, capacity = 20)
        repeat(3) { backpack.addItem(Item.create(2148)) }
        player.addItem(backpack, Slots_t.CONST_SLOT_BACKPACK)
        assertThat(adapter.getFreeBackpackSlots()).isEqualTo(17)
        assertThat(player.getInventoryItem(Slots_t.CONST_SLOT_BACKPACK)).isSameAs(backpack)
    }

    @Test
    fun `money seams default to an empty wallet and only a zero charge succeeds`() {
        val adapter = PlayerForgeAdapter(hero())

        assertThat(adapter.getMoney()).isEqualTo(0)
        assertThat(adapter.getBankBalance()).isEqualTo(0)
        assertThat(adapter.removeMoney(0)).isTrue()
        assertThat(adapter.removeMoney(5)).isFalse()
    }

    @Test
    fun `item classification seam drives the returned classification`() {
        val player = hero()
        player.addItem(tieredItem(100, 0))
        val adapter = PlayerForgeAdapter(player, itemClassificationOf = { itemId -> if (itemId == 100) 4 else 0 })

        assertThat(adapter.getForgeItemFromId(100, 0)!!.classification).isEqualTo(4)
    }
}
