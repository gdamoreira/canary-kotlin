package org.opentibia.canary.creatures.npcs

import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Npc
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.items.Item
import org.opentibia.canary.map.Position
import org.opentibia.canary.systems.rewardchest.ReturnValue

/**
 * Injected view of the `g_game()`/`g_configManager()`/`Player` world operations
 * the NPC runtime performs. Canary reaches those globals directly; per AGENTS.md
 * 1.1 they are constructor-injected here, and every member cites the exact C++
 * symbol it stands in for.
 *
 * The default bodies are safe no-ops so tests and partially wired runtimes only
 * need to override the operations they exercise.
 */
interface NpcWorld {
    /** `g_game().getCreatureByID(playerId)` (`npc.cpp:1157`). */
    fun getCreatureByID(playerId: Long): Creature? = null

    /** `g_game().getPlayerByGUID(playerGuid)` (`npc.cpp:1259`). */
    fun getPlayerByGUID(playerGuid: Long): Player? = null

    /** `Player::closeShopWindow()` (`npc.cpp:1175`). */
    fun closeShopWindow(player: Player) = Unit

    /** `Tile::queryAdd(0, npc, 1, 0)` (`npc.cpp:1200`). */
    fun canQueryAddNpc(npc: Npc, pos: Position): Boolean = false

    /** `Tile::hasFlag(TILESTATE_FLOORCHANGE) || Tile::getTeleportItem()` (`npc.cpp:1204`). */
    fun isFloorChangeOrTeleportTile(pos: Position): Boolean = false

    /** `Tile::hasHeight(1)` (`npc.cpp:1208`). */
    fun tileHasHeight(pos: Position): Boolean = false

    /** `Player::getFreeBackpackSlots()` (`npc.cpp:33`). */
    fun getFreeBackpackSlots(player: Player): Int = 0

    /** `Player::getInventoryItem(CONST_SLOT_BACKPACK)` (`npc.cpp:37`). */
    fun hasBackpackEquipped(player: Player): Boolean = false

    /** `ItemType::isContainer() && (slotPosition & SLOTP_BACKPACK)` (`npc.cpp:41-42`). */
    fun itemTypeIsBackpack(itemId: Int): Boolean = false

    /** `Player::getTile()` non-null (`npc.cpp:59-62`). */
    fun playerHasTile(player: Player): Boolean = false

    /** `Tile::getItemList()->size()` (`npc.cpp:65-66`). */
    fun getTileItemCount(player: Player): Int = 0

    /** Anonymous-namespace `calculateSlotsNeeded` (`npc.cpp:45-52`). */
    fun calculateSlotsNeeded(itemId: Int, amount: Int, inBackpacks: Boolean): Double = amount.toDouble()

    /** `Player::getMoney()` (`npc.cpp:90`). */
    fun getMoney(player: Player): Long = 0L

    /** `Player::getBankBalance()` (`npc.cpp:90`). */
    fun getBankBalance(player: Player): Long = 0L

    /** `Player::setBankBalance(balance)` (`npc.cpp:271`). */
    fun setBankBalance(player: Player, balance: Long) = Unit

    /** `Cylinder::getItemTypeCount(currency)` (`npc.cpp:100`). */
    fun getItemTypeCount(player: Player, itemId: Int): Long = 0L

    /** `Player::sendCancelMessage(ret)` (`npc.cpp:746`). */
    fun sendCancelMessage(player: Player, returnValue: ReturnValue) = Unit

    /** `Player::sendTextMessage(type, message)` (`npc.cpp:904`). */
    fun sendTextMessage(player: Player, message: String) = Unit

    /** `g_configManager().getBoolean(AUTOBANK)` (`npc.cpp:269`). */
    fun getAutoBank(): Boolean = false

    /** `Game::addMoney(player, amount, check)` (`npc.cpp:279`). */
    fun addMoney(player: Player, amount: Long, check: Int = 0): Pair<Long, ReturnValue> =
        amount to ReturnValue.NOERROR

    /** `Player::getInventoryItemsFromId(itemId, ignore)` (`npc.cpp:180`). */
    fun getInventoryItemsFromId(player: Player, itemId: Int, ignore: Boolean): List<Item> = emptyList()

    /** `Cylinder::queryRemove(item, count, FLAG_IGNORENOTMOVABLE)` (`npc.cpp:198`). */
    fun canRemoveFromInventory(player: Player, item: Item, count: Int): Boolean = true

    /** `Player::removeItem(item, count)` (`npc.cpp:256`). */
    fun removeItem(player: Player, item: Item, count: Int): ReturnValue = ReturnValue.NOTPOSSIBLE

    /** `Item::CreateItem(currency, count)` + `Game::internalPlayerAddItem` (`npc.cpp:116-122`). */
    fun addItemToPlayer(player: Player, itemId: Int, count: Long): Boolean = false

    /** `Item::items[itemId].name` (`npc.cpp:1010`). */
    fun getItemTypeName(itemId: Int): String = ""
}
