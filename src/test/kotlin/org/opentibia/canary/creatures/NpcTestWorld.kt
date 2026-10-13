package org.opentibia.canary.creatures

import org.opentibia.canary.creatures.npcs.NpcWorld
import org.opentibia.canary.items.Item
import org.opentibia.canary.map.Position
import org.opentibia.canary.systems.rewardchest.ReturnValue

/**
 * In-memory [NpcWorld] double for the NPC runtime transcripts. Every knob maps
 * to the C++ call documented on the matching [NpcWorld] member.
 */
class NpcTestWorld : NpcWorld {
    val creaturesById = mutableMapOf<Long, Creature>()
    val playersByGUID = mutableMapOf<Long, Player>()
    val closedShopWindows = mutableListOf<Player>()
    val cancelMessages = mutableListOf<Pair<Player, ReturnValue>>()
    val textMessages = mutableListOf<Pair<Player, String>>()

    val walkablePositions = mutableSetOf<Position>()
    val floorChangeOrTeleportPositions = mutableSetOf<Position>()
    val heightPositions = mutableSetOf<Position>()

    var freeBackpackSlots = 0
    var backpackEquipped = false
    val backpackItemTypes = mutableSetOf<Int>()
    var playerHasTile = true
    var tileItemCount = 0
    var slotsNeeded: (Int, Int, Boolean) -> Double = { _, amount, _ -> amount.toDouble() }

    var money = 0L
    var bankBalance = 0L
    var autobank = false
    var addMoneyDeliversFully = true
    val customCurrency = mutableMapOf<Int, Long>()
    val bankBalancesSet = mutableListOf<Long>()
    val moneyAdded = mutableListOf<Long>()

    val inventoryItemsById = mutableMapOf<Int, MutableList<Item>>()
    var canRemove = true
    val removedItems = mutableListOf<Triple<Player, Item, Int>>()
    val addedItems = mutableListOf<Pair<Int, Long>>()
    val itemTypeNames = mutableMapOf<Int, String>()

    override fun getCreatureByID(playerId: Long): Creature? = creaturesById[playerId]

    override fun getPlayerByGUID(playerGuid: Long): Player? = playersByGUID[playerGuid]

    override fun closeShopWindow(player: Player) {
        closedShopWindows.add(player)
    }

    override fun canQueryAddNpc(npc: Npc, pos: Position): Boolean = pos in walkablePositions

    override fun isFloorChangeOrTeleportTile(pos: Position): Boolean = pos in floorChangeOrTeleportPositions

    override fun tileHasHeight(pos: Position): Boolean = pos in heightPositions

    override fun getFreeBackpackSlots(player: Player): Int = freeBackpackSlots

    override fun hasBackpackEquipped(player: Player): Boolean = backpackEquipped

    override fun itemTypeIsBackpack(itemId: Int): Boolean = itemId in backpackItemTypes

    override fun playerHasTile(player: Player): Boolean = playerHasTile

    override fun getTileItemCount(player: Player): Int = tileItemCount

    override fun calculateSlotsNeeded(itemId: Int, amount: Int, inBackpacks: Boolean): Double =
        slotsNeeded(itemId, amount, inBackpacks)

    override fun getMoney(player: Player): Long = money

    override fun getBankBalance(player: Player): Long = bankBalance

    override fun setBankBalance(player: Player, balance: Long) {
        bankBalance = balance
        bankBalancesSet.add(balance)
    }

    override fun getItemTypeCount(player: Player, itemId: Int): Long = customCurrency[itemId] ?: 0L

    override fun sendCancelMessage(player: Player, returnValue: ReturnValue) {
        cancelMessages.add(player to returnValue)
    }

    override fun sendTextMessage(player: Player, message: String) {
        textMessages.add(player to message)
    }

    override fun getAutoBank(): Boolean = autobank

    override fun addMoney(player: Player, amount: Long, check: Int): Pair<Long, ReturnValue> {
        val delivered = if (addMoneyDeliversFully) amount else 0L
        money += delivered
        moneyAdded.add(delivered)
        return delivered to ReturnValue.NOERROR
    }

    override fun getInventoryItemsFromId(player: Player, itemId: Int, ignore: Boolean): List<Item> =
        inventoryItemsById[itemId] ?: emptyList()

    override fun canRemoveFromInventory(player: Player, item: Item, count: Int): Boolean = canRemove

    override fun removeItem(player: Player, item: Item, count: Int): ReturnValue {
        removedItems.add(Triple(player, item, count))
        item.count -= count
        return ReturnValue.NOERROR
    }

    override fun addItemToPlayer(player: Player, itemId: Int, count: Long): Boolean {
        addedItems.add(itemId to count)
        return true
    }

    override fun getItemTypeName(itemId: Int): String = itemTypeNames[itemId] ?: ""
}
