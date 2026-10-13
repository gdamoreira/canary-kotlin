package org.opentibia.canary.creatures

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.npcs.NpcType
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemAttribute
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.protocol.npcshop.ShopBlock
import org.opentibia.canary.systems.rewardchest.ReturnValue

/**
 * Transcripts of the `Npc` shop runtime
 * (src/creatures/npcs/npc.cpp:512-521, 738-1036, 1242-1268). Capacity, funds,
 * inventory and bank mutations cross the injected [NpcWorld]; the price and
 * event-dispatch decisions stay in the engine.
 */
class NpcShopRuntimeTest {

    private val npcPos = Position(100, 100, 7)
    private val itemId = 2696
    private val customCurrencyId = 16112

    private class BuyCall(
        val npc: Npc,
        val player: Player,
        val itemId: Int,
        val subType: Int,
        val amount: Int,
        val ignore: Boolean,
        val inBackpacks: Boolean,
        val totalCost: Long,
    )

    private class SellCall(
        val npc: Npc,
        val player: Player,
        val itemId: Int,
        val subType: Int,
        val soldAmount: Int,
        val ignore: Boolean,
        val itemName: String,
        val totalCost: Long,
    )

    private fun npcWith(
        world: NpcTestWorld,
        configure: NpcType.() -> Unit = {},
    ): Npc {
        val npcType = NpcType("Shopkeeper")
        npcType.configure()
        return Npc(id = 1, name = "Shopkeeper", position = npcPos, npcType = npcType, world = world)
    }

    private fun player(id: Long = 7): Player =
        Player(id, "Aria", 100, 100, position = Position(100, 101, 7))

    @Test
    fun `addShopPlayer keeps the first vector and shop players are queryable and removable`() {
        val world = NpcTestWorld()
        val npc = npcWith(world)
        val first = listOf(ShopBlock(itemId = itemId, itemBuyPrice = 5))
        val second = listOf(ShopBlock(itemId = itemId, itemBuyPrice = 9))

        assertThat(npc.isShopPlayer(7)).isFalse()

        npc.addShopPlayer(7, first)
        npc.addShopPlayer(7, second)

        assertThat(npc.isShopPlayer(7)).isTrue()
        assertThat(npc.getShopItemVector(7)).isSameAs(first)

        npc.removeShopPlayer(7)
        assertThat(npc.isShopPlayer(7)).isFalse()
    }

    @Test
    fun `getShopItemVector prefers a non empty per player vector and falls back to the npc type vector`() {
        val world = NpcTestWorld()
        val typeBlock = ShopBlock(itemId = itemId, itemBuyPrice = 5)
        val playerBlock = ShopBlock(itemId = itemId, itemBuyPrice = 9)
        val npc = npcWith(world) { info.shopItemVector.add(typeBlock) }

        assertThat(npc.getShopItemVector(0)).containsExactly(typeBlock)
        assertThat(npc.getShopItemVector(7)).containsExactly(typeBlock)

        npc.addShopPlayer(7, listOf(playerBlock))
        npc.addShopPlayer(8, emptyList())

        assertThat(npc.getShopItemVector(7)).containsExactly(playerBlock)
        assertThat(npc.getShopItemVector(8)).containsExactly(typeBlock)
    }

    @Test
    fun `closeAllShopWindows closes every resolvable player window and clears the shop map`() {
        val world = NpcTestWorld()
        val npc = npcWith(world)
        val online = player(7)
        world.playersByGUID[7] = online

        npc.addShopPlayer(7, listOf(ShopBlock(itemId = itemId, itemBuyPrice = 5)))
        npc.addShopPlayer(9, listOf(ShopBlock(itemId = itemId, itemBuyPrice = 5)))

        npc.closeAllShopWindows()

        assertThat(world.closedShopWindows).containsExactly(online)
        assertThat(npc.isShopPlayer(7)).isFalse()
        assertThat(npc.isShopPlayer(9)).isFalse()

        npc.closeAllShopWindows()
        assertThat(world.closedShopWindows).containsExactly(online)
    }

    @Test
    fun `getCurrency defaults to gold and reads the npc type currency`() {
        val world = NpcTestWorld()
        val npc = npcWith(world)

        assertThat(npc.getCurrency()).isEqualTo(NpcType("x").info.currencyId)

        npc.setCurrency(customCurrencyId)
        assertThat(npc.getCurrency()).isEqualTo(customCurrencyId)
    }

    @Test
    fun `onPlayerBuyItem computes the buy price from the shop vector and dispatches the total cost`() {
        val world = NpcTestWorld()
        val npc = npcWith(world) {
            info.shopItemVector.add(ShopBlock(itemId = itemId, itemBuyPrice = 5, itemSellPrice = 2))
        }
        val buyer = player()
        val calls = mutableListOf<BuyCall>()
        npc.registerOnPlayerBuyHandler { engine, p, id, subType, amount, ignore, inBackpacks, cost ->
            calls.add(BuyCall(engine, p, id, subType, amount, ignore, inBackpacks, cost))
        }
        world.money = 100
        world.freeBackpackSlots = 20

        npc.onPlayerBuyItem(buyer, itemId = itemId, subType = 1, amount = 3, ignore = false, inBackpacks = false)

        assertThat(calls).hasSize(1)
        val call = calls.single()
        assertThat(call.npc).isSameAs(npc)
        assertThat(call.player).isSameAs(buyer)
        assertThat(call.itemId).isEqualTo(itemId)
        assertThat(call.subType).isEqualTo(1)
        assertThat(call.amount).isEqualTo(3)
        assertThat(call.ignore).isFalse()
        assertThat(call.inBackpacks).isFalse()
        assertThat(call.totalCost).isEqualTo(15)
    }

    @Test
    fun `onPlayerBuyItem refuses the sale when gold and bank can not cover the price`() {
        val world = NpcTestWorld()
        val npc = npcWith(world) {
            info.shopItemVector.add(ShopBlock(itemId = itemId, itemBuyPrice = 5))
        }
        val buyer = player()
        var dispatched = 0
        npc.registerOnPlayerBuyHandler { _, _, _, _, _, _, _, _ -> dispatched++ }

        world.freeBackpackSlots = 20
        world.money = 10
        world.bankBalance = 4
        npc.onPlayerBuyItem(buyer, itemId, subType = 0, amount = 3, ignore = false, inBackpacks = false)
        assertThat(dispatched).isZero()

        world.bankBalance = 5
        npc.onPlayerBuyItem(buyer, itemId, subType = 0, amount = 3, ignore = false, inBackpacks = false)
        assertThat(dispatched).isEqualTo(1)
    }

    @Test
    fun `onPlayerBuyItem charges the custom currency when the npc does not trade in gold`() {
        val world = NpcTestWorld()
        val npc = npcWith(world) {
            info.currencyId = customCurrencyId
            info.shopItemVector.add(ShopBlock(itemId = itemId, itemBuyPrice = 5))
        }
        val buyer = player()
        var dispatched = 0
        npc.registerOnPlayerBuyHandler { _, _, _, _, _, _, _, _ -> dispatched++ }

        world.freeBackpackSlots = 20
        world.customCurrency[customCurrencyId] = 14
        npc.onPlayerBuyItem(buyer, itemId, subType = 0, amount = 3, ignore = false, inBackpacks = false)
        assertThat(dispatched).isZero()

        world.customCurrency[customCurrencyId] = 15
        npc.onPlayerBuyItem(buyer, itemId, subType = 0, amount = 3, ignore = false, inBackpacks = false)
        assertThat(dispatched).isEqualTo(1)
    }

    @Test
    fun `onPlayerBuyItem cancels a full backpack purchase that can not go into the backpack`() {
        val world = NpcTestWorld()
        val npc = npcWith(world) {
            info.shopItemVector.add(ShopBlock(itemId = itemId, itemBuyPrice = 5))
        }
        val buyer = player()
        var dispatched = 0
        npc.registerOnPlayerBuyHandler { _, _, _, _, _, _, _, _ -> dispatched++ }
        world.money = 100
        world.freeBackpackSlots = 0
        world.backpackEquipped = true

        npc.onPlayerBuyItem(buyer, itemId, subType = 0, amount = 1, ignore = false, inBackpacks = false)

        assertThat(dispatched).isZero()
        assertThat(world.cancelMessages).containsExactly(buyer to ReturnValue.NOTENOUGHROOM)

        world.freeBackpackSlots = 1
        npc.onPlayerBuyItem(buyer, itemId, subType = 0, amount = 1, ignore = false, inBackpacks = false)
        assertThat(dispatched).isEqualTo(1)

        world.freeBackpackSlots = 0
        world.backpackEquipped = false
        world.backpackItemTypes.add(itemId)
        npc.onPlayerBuyItem(buyer, itemId, subType = 0, amount = 1, ignore = false, inBackpacks = false)
        assertThat(dispatched).isEqualTo(2)

        npc.onPlayerBuyItem(buyer, itemId, subType = 0, amount = 1, ignore = true, inBackpacks = false)
        assertThat(dispatched).isEqualTo(3)
    }

    @Test
    fun `onPlayerBuyItem enforces the tile item limit only when the ignore capacity flag is set`() {
        val world = NpcTestWorld()
        val npc = npcWith(world) {
            info.shopItemVector.add(ShopBlock(itemId = itemId, itemBuyPrice = 5))
        }
        val buyer = player()
        var dispatched = 0
        npc.registerOnPlayerBuyHandler { _, _, _, _, _, _, _, _ -> dispatched++ }
        world.money = 100
        world.playerHasTile = true
        world.freeBackpackSlots = 0
        world.backpackItemTypes.add(itemId)
        world.tileItemCount = 30

        world.slotsNeeded = { _, _, _ -> 1.0 }
        npc.onPlayerBuyItem(buyer, itemId, subType = 0, amount = 1, ignore = false, inBackpacks = false)
        assertThat(dispatched).isEqualTo(1)

        npc.onPlayerBuyItem(buyer, itemId, subType = 0, amount = 1, ignore = true, inBackpacks = false)
        assertThat(dispatched).isEqualTo(1)
        assertThat(world.cancelMessages).containsExactly(buyer to ReturnValue.NOTENOUGHROOM)

        world.playerHasTile = false
        npc.onPlayerBuyItem(buyer, itemId, subType = 0, amount = 1, ignore = true, inBackpacks = false)
        assertThat(dispatched).isEqualTo(2)
    }

    @Test
    fun `onPlayerSellItem pays the sell price for gold and dispatches the sold amount`() {
        val world = NpcTestWorld()
        val npc = npcWith(world) {
            info.shopItemVector.add(ShopBlock(itemId = itemId, itemBuyPrice = 8, itemSellPrice = 5))
        }
        val seller = player()
        val item = Item(itemId, count = 2)
        world.inventoryItemsById[itemId] = mutableListOf(item)
        world.itemTypeNames[itemId] = "cheese"
        val calls = mutableListOf<SellCall>()
        npc.registerOnPlayerSellHandler { engine, p, id, subType, sold, ignore, name, cost ->
            calls.add(SellCall(engine, p, id, subType, sold, ignore, name, cost))
        }

        npc.onPlayerSellItem(seller, itemId = itemId, subType = 0, amount = 2, ignore = false)

        assertThat(world.removedItems).containsExactly(Triple(seller, item, 2))
        assertThat(world.moneyAdded).containsExactly(10)
        assertThat(calls).hasSize(1)
        val call = calls.single()
        assertThat(call.npc).isSameAs(npc)
        assertThat(call.soldAmount).isEqualTo(2)
        assertThat(call.itemName).isEqualTo("cheese")
        assertThat(call.totalCost).isEqualTo(10)
    }

    @Test
    fun `onPlayerSellItem reports that the player has nothing to sell`() {
        val world = NpcTestWorld()
        val npc = npcWith(world) {
            info.shopItemVector.add(ShopBlock(itemId = itemId, itemSellPrice = 5))
        }
        val seller = player()
        var dispatched = 0
        npc.registerOnPlayerSellHandler { _, _, _, _, _, _, _, _ -> dispatched++ }

        npc.onPlayerSellItem(seller, itemId, subType = 0, amount = 1, ignore = false)

        assertThat(dispatched).isZero()
        assertThat(world.moneyAdded).isEmpty()
        assertThat(world.textMessages).containsExactly(seller to "You have no items to sell.")
    }

    @Test
    fun `onPlayerSellItem ignores items without a sell price`() {
        val world = NpcTestWorld()
        val npc = npcWith(world) {
            info.shopItemVector.add(ShopBlock(itemId = itemId, itemBuyPrice = 8))
        }
        val seller = player()
        var dispatched = 0
        npc.registerOnPlayerSellHandler { _, _, _, _, _, _, _, _ -> dispatched++ }
        world.inventoryItemsById[itemId] = mutableListOf(Item(itemId, count = 1))

        npc.onPlayerSellItem(seller, itemId, subType = 0, amount = 1, ignore = false)

        assertThat(dispatched).isZero()
        assertThat(world.removedItems).isEmpty()
        assertThat(world.textMessages).isEmpty()
    }

    @Test
    fun `onPlayerSellItem deposits the proceeds into the bank when autobank is enabled`() {
        val world = NpcTestWorld()
        val npc = npcWith(world) {
            info.shopItemVector.add(ShopBlock(itemId = itemId, itemSellPrice = 5))
        }
        val seller = player()
        world.inventoryItemsById[itemId] = mutableListOf(Item(itemId, count = 2))
        world.autobank = true
        world.bankBalance = 4

        npc.onPlayerSellItem(seller, itemId, subType = 0, amount = 2, ignore = false)

        assertThat(world.money).isZero()
        assertThat(world.moneyAdded).isEmpty()
        assertThat(world.bankBalance).isEqualTo(14)
        assertThat(world.bankBalancesSet).containsExactly(14)
    }

    @Test
    fun `onPlayerSellItem refunds undelivered gold into the bank`() {
        val world = NpcTestWorld()
        val npc = npcWith(world) {
            info.shopItemVector.add(ShopBlock(itemId = itemId, itemSellPrice = 5))
        }
        val seller = player()
        world.inventoryItemsById[itemId] = mutableListOf(Item(itemId, count = 2))
        world.addMoneyDeliversFully = false
        world.bankBalance = 4

        npc.onPlayerSellItem(seller, itemId, subType = 0, amount = 2, ignore = false)

        assertThat(world.money).isZero()
        assertThat(world.bankBalance).isEqualTo(14)
        assertThat(world.bankBalancesSet).containsExactly(14)
    }

    @Test
    fun `onPlayerSellItem pays custom currency and removes the counted items`() {
        val world = NpcTestWorld()
        val npc = npcWith(world) {
            info.currencyId = customCurrencyId
            info.shopItemVector.add(ShopBlock(itemId = itemId, itemSellPrice = 5))
        }
        val seller = player()
        val item = Item(itemId, count = 3)
        world.inventoryItemsById[itemId] = mutableListOf(item)
        var dispatched = 0
        npc.registerOnPlayerSellHandler { _, _, _, _, _, _, _, _ -> dispatched++ }

        npc.onPlayerSellItem(seller, itemId, subType = 0, amount = 3, ignore = false)

        assertThat(world.addedItems).containsExactly(customCurrencyId to 15L)
        assertThat(world.removedItems).containsExactly(Triple(seller, item, 3))
        assertThat(dispatched).isEqualTo(1)
    }

    @Test
    fun `onPlayerSellItem skips tiered items and items the player can not lose`() {
        val world = NpcTestWorld()
        val npc = npcWith(world) {
            info.shopItemVector.add(ShopBlock(itemId = itemId, itemSellPrice = 5))
        }
        val seller = player()
        val tiered = Item(itemId, count = 1).apply { setAttribute(ItemAttribute.TIER, 1L) }
        val normal = Item(itemId, count = 3)
        world.inventoryItemsById[itemId] = mutableListOf(tiered, normal)
        var dispatched = 0
        npc.registerOnPlayerSellHandler { _, _, _, _, _, _, _, _ -> dispatched++ }

        npc.onPlayerSellItem(seller, itemId, subType = 0, amount = 3, ignore = false)

        assertThat(world.removedItems).containsExactly(Triple(seller, normal, 3))
        assertThat(dispatched).isEqualTo(1)

        world.inventoryItemsById[itemId] = mutableListOf(Item(itemId, count = 3))
        world.canRemove = false
        world.addedItems.clear()
        world.removedItems.clear()
        dispatched = 0
        npc.setCurrency(customCurrencyId)

        npc.onPlayerSellItem(seller, itemId, subType = 0, amount = 3, ignore = false)

        assertThat(world.addedItems).isEmpty()
        assertThat(world.removedItems).isEmpty()
        assertThat(dispatched).isZero()
    }

    @Test
    fun `onPlayerCheckItem dispatches the look event`() {
        val world = NpcTestWorld()
        val npc = npcWith(world)
        val buyer = player()
        val seen = mutableListOf<Triple<Player, Int, Int>>()
        npc.registerOnPlayerCheckItemHandler { _, p, id, subType -> seen.add(Triple(p, id, subType)) }

        npc.onPlayerCheckItem(buyer, itemId = itemId, subType = 2)
        npc.onPlayerCheckItem(null, itemId = itemId, subType = 2)

        assertThat(seen).containsExactly(Triple(buyer, itemId, 2))
    }

    @Test
    fun `onPlayerCloseChannel dispatches then removes the player interaction`() {
        val world = NpcTestWorld()
        val npc = npcWith(world)
        val buyer = player()
        world.creaturesById[buyer.id] = buyer
        val seen = mutableListOf<Player>()
        npc.registerOnPlayerCloseChannelHandler { _, p -> seen.add(p) }
        npc.setPlayerInteraction(buyer.id, topicId = 1)

        npc.onPlayerCloseChannel(buyer as Creature)

        assertThat(seen).containsExactly(buyer)
        assertThat(npc.isInteractingWithPlayer(buyer.id)).isFalse()
        assertThat(world.closedShopWindows).containsExactly(buyer)

        val monster = Monster(9, "Rat", 10, 10, position = Position(100, 101, 7))
        npc.onPlayerCloseChannel(monster)
        assertThat(seen).containsExactly(buyer)
    }

    @Test
    fun `onPlayerBuyItem ignores a null player`() {
        val world = NpcTestWorld()
        val npc = npcWith(world)
        var dispatched = 0
        npc.registerOnPlayerBuyHandler { _, _, _, _, _, _, _, _ -> dispatched++ }

        npc.onPlayerBuyItem(null, itemId, subType = 0, amount = 1, ignore = false, inBackpacks = false)

        assertThat(dispatched).isZero()
    }
}
