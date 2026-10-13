package org.opentibia.canary.systems.forge

import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.Slots_t
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemAttribute
import org.opentibia.canary.items.ItemTypeRegistry
import org.opentibia.canary.systems.rewardchest.ReturnValue
import java.util.IdentityHashMap

/**
 * [ForgePlayer] adapter backed by the port's Kotlin [Player].
 *
 * Every member resolves the same Canary call site the [ForgePlayer] contract
 * documents against the port's inventory:
 *
 *  - [getForgeItemFromId]: `Player::getForgeItemFromId` (player.cpp:6013) over
 *    the inventory slots and their containers. Canary passes
 *    `getAllInventoryItems(true)` (equipped items excluded) and skips items with
 *    imbuements; the port stores forge items directly in inventory slots and
 *    does not model imbuements, so all slots are searched.
 *  - [getItemsClassifications]: `Game::getItemsClassifications()` (game.hpp:591)
 *    through [itemClassifications]. The Lua-authored classification price tables
 *    (`data/scripts/systems/item_tiers.lua`) populate that store through
 *    `Game.createItemClassification`; the [itemClassificationOf] default resolves
 *    each item's classification from the injected `itemRegistry`
 *    (`Item::getClassification`, src/items/item.hpp:753), falling back to
 *    classification 0 for unknown types.
 *  - [getForgeSliversAndCores] / [hasItemCountById]: `Player::getForgeSliversAndCores`
 *    (player.cpp:1561) and `Player::hasItemCountById` (player.cpp:5335) over the
 *    inventory counts plus the injected stash seams (`Player::getStashItems()`).
 *  - [removeItemCountById]: `Player::removeItemCountById` (player.cpp:5361).
 *    Top-level items are removed through `Player::removeItem` (player.cpp:10251);
 *    container contents are decremented in place because the port's [Container]
 *    has no removal API.
 *  - [getFreeBackpackSlots]: `Player::getFreeBackpackSlots` (player.cpp:12578)
 *    over the backpack-slot container when one is equipped, falling back to free
 *    inventory slots because the port carries no backpack-container wiring and
 *    `Player::addItem` stores items directly in inventory slots.
 *  - [createItem] / [internalAddItem] / [internalRemoveItem]: `Item::CreateItem`
 *    plus `Game::internalPlayerAddItem` / `Game::internalRemoveItem`.
 *  - [getMoney] / [getBankBalance] / [removeMoney]: `Player::getMoney`
 *    (player.cpp:1532), `Player::getBankBalance` (player.cpp:8299) and
 *    `Game::removeMoney(..., useBalance = true)` (game.cpp:3299). The port's
 *    `Player` carries no money or bank balance yet, so these are injected seams;
 *    Canary's money-item worth and bank persistence are not modelled.
 *
 * `ForgeApplication` mutates the returned [ForgeItem] views (`tier`); [syncItemTiers]
 * writes those mutations back onto the backing [Item] after a command has been
 * applied on the WorldCommit lane.
 */
class PlayerForgeAdapter(
    private val player: Player,
    private val itemClassifications: ItemClassifications = ItemClassifications(),
    itemRegistry: ItemTypeRegistry? = null,
    private val itemClassificationOf: (Int) -> Int =
        { itemId -> itemRegistry?.get(itemId)?.upgradeClassification ?: 0 },
    private val stashSlivers: () -> Long = { 0L },
    private val stashCores: () -> Long = { 0L },
    private val money: () -> Long = { 0L },
    private val bankBalance: () -> Long = { 0L },
    private val moneyRemover: (Long) -> Boolean = { amount -> amount == 0L },
    private val stashItemRemover: (Int, Int) -> Boolean = { _, _ -> false },
) : ForgePlayer {

    private val backingItems = IdentityHashMap<ForgeItem, Item>()

    override fun getFreeBackpackSlots(): Int {
        val backpack = player.getInventoryItem(Slots_t.CONST_SLOT_BACKPACK) as? Container
        if (backpack != null) {
            return (backpack.capacity - backpack.items.size).coerceAtLeast(0)
        }
        return inventorySlots().count { player.getInventoryItem(it) == null }
    }

    override fun getForgeItemFromId(itemId: Int, tier: Int, exclude: ForgeItem?): ForgeItem? {
        val excluded = exclude?.let { backingItems[it] }
        val item = inventoryItems().firstOrNull {
            it !== excluded && it.id == itemId && tierOf(it) == tier
        } ?: return null
        return wrap(item)
    }

    override fun getItemsClassifications(): List<ItemClassification> = itemClassifications.getAll()

    override fun getForgeSliversAndCores(): Pair<Long, Long> =
        (player.getItemCount(ForgeItemIds.ITEM_FORGE_SLIVER) + stashSlivers()) to
            (player.getItemCount(ForgeItemIds.ITEM_FORGE_CORE) + stashCores())

    override fun hasItemCountById(itemId: Int, count: Int, checkStash: Boolean): Boolean {
        var total = player.getItemCount(itemId)
        if (checkStash) {
            total += stashCount(itemId)
        }
        return total >= count
    }

    override fun removeItemCountById(itemId: Int, count: Int): Boolean {
        if (!hasItemCountById(itemId, count, true)) {
            return false
        }

        var remaining = count
        for (item in inventoryItems()) {
            if (remaining == 0) {
                break
            }
            if (item.id != itemId) {
                continue
            }
            val removed = minOf(remaining, item.count)
            remaining -= removed
            if (!removeFromInventory(item, removed)) {
                return false
            }
        }

        return remaining == 0 || stashItemRemover(itemId, remaining)
    }

    override fun getMoney(): Long = money()

    override fun getBankBalance(): Long = bankBalance()

    override fun removeMoney(amount: Long): Boolean = moneyRemover(amount)

    override fun createItem(itemId: Int, count: Int): ForgeItem = wrap(Item.create(itemId, count))

    override fun internalAddItem(item: ForgeItem): Boolean {
        val backing = backingItems[item] ?: Item.create(item.id, item.count)
        backingItems[item] = backing
        backing.setAttribute(ItemAttribute.TIER, item.tier.toLong())
        return player.addItem(backing) == ReturnValue.NOERROR
    }

    override fun internalRemoveItem(item: ForgeItem, count: Int): Boolean {
        val backing = backingItems[item] ?: return false
        return removeFromInventory(backing, count)
    }

    /**
     * Writes each [ForgeItem] view's `tier` back onto its backing [Item]
     * (`Item::setTier`). [ForgeApplication] mutates the views after
     * [internalAddItem] (promotions, retained second-item tiers), so the router
     * calls this once per applied command before leaving the WorldCommit lane.
     */
    fun syncItemTiers() {
        for ((view, item) in backingItems) {
            item.setAttribute(ItemAttribute.TIER, view.tier.toLong())
        }
    }

    private fun inventorySlots(): List<Slots_t> =
        Slots_t.entries.filter { it.value in Slots_t.FIRST..Slots_t.LAST }

    private fun inventoryItems(): List<Item> {
        val items = mutableListOf<Item>()
        for (slot in inventorySlots()) {
            player.getInventoryItem(slot)?.let { collectItems(it, items) }
        }
        return items
    }

    private fun collectItems(item: Item, out: MutableList<Item>) {
        out += item
        if (item is Container) {
            item.items.forEach { collectItems(it, out) }
        }
    }

    private fun removeFromInventory(item: Item, count: Int): Boolean {
        val slot = inventorySlots().firstOrNull { player.getInventoryItem(it) === item }
        if (slot != null) {
            return player.removeItem(item, count) == ReturnValue.NOERROR
        }
        if (count > item.count) {
            return false
        }
        item.count -= count
        return true
    }

    private fun wrap(item: Item): ForgeItem {
        val view = ForgeItem(
            id = item.id,
            tier = tierOf(item),
            count = item.count,
            classification = itemClassificationOf(item.id),
        )
        backingItems[view] = item
        return view
    }

    private fun tierOf(item: Item): Int = (item.getAttribute(ItemAttribute.TIER) as? Long ?: 0L).toInt()

    private fun stashCount(itemId: Int): Long = when (itemId) {
        ForgeItemIds.ITEM_FORGE_SLIVER -> stashSlivers()
        ForgeItemIds.ITEM_FORGE_CORE -> stashCores()
        else -> 0L
    }
}
