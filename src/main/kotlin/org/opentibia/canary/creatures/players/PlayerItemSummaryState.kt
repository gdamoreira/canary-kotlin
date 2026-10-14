package org.opentibia.canary.creatures.players

import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.Slots_t
import org.opentibia.canary.creatures.skills_t
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemAttribute
import org.opentibia.canary.network.protocol.cyclopedia.CyclopediaCharacterWriter.ItemSummaryEntry
import org.opentibia.canary.network.protocol.cyclopedia.SkillsEquipment
import java.util.TreeMap

/**
 * Player item enumeration
 * (src/creatures/players/player.cpp:5488-5511, 5803-5859, 5972-6011,
 * 13214-13238).
 *
 * The host already owns the inventory slots and the inbox, so the
 * transcriptions are extension functions that keep the exact `Player::` names of
 * their C++ symbols. `Player::depotChests` (player.hpp:1674) is not modelled on
 * the port's `Player`, so [getDepotChestItemsId] takes it as a call-site seam
 * per AGENTS.md 1.1.
 *
 * The C++ `ItemsTierCountList` (`std::map<std::pair<uint16_t, uint8_t>,
 * uint32_t>`, player.hpp:1639) is surfaced as the port's protocol-ready
 * [ItemSummaryEntry] list instead of a nested map: the `tier` column is null
 * exactly when `Item::items[itemId].upgradeClassification == 0`, the condition
 * under which `ProtocolGame::sendCyclopediaCharacterItemSummary`
 * (protocolgame.cpp:4967) skips the tier byte. The rows stay in the C++
 * `std::map` ascending `(itemId, tier)` order.
 */

/** One `ItemsTierCountList` accumulator row key: `{ item->getID(), item->getTier() }`. */
private class ItemTierAccumulator {
    private val keyOrder = compareBy<Pair<Int, Int>>({ it.first }, { it.second })
    private val counts = TreeMap<Pair<Int, Int>, Long>(keyOrder)
    private val classifiedIds = mutableSetOf<Int>()

    fun add(item: Item) {
        val itemId = item.id
        if ((item.type?.upgradeClassification ?: 0) > 0) {
            classifiedIds.add(itemId)
        }
        val key = itemId to item.getTier()
        counts[key] = (counts[key] ?: 0L) + item.getItemAmount()
    }

    fun addAll(items: Collection<Item>) {
        for (item in items) {
            add(item)
        }
    }

    fun toEntries(): List<ItemSummaryEntry> = counts.map { (key, count) ->
        ItemSummaryEntry(
            itemId = key.first,
            tier = if (key.first in classifiedIds) key.second else null,
            count = count,
        )
    }
}

/**
 * `Player::getInventoryItemsId(ignoreStoreInbox)` (player.cpp:5488-5511): every
 * inventory slot's item plus its recursive container contents, keyed by
 * `(id, tier)`; a `ITEM_STORE_INBOX` slotted item itself is never counted and
 * its contents are skipped when [ignoreStoreInbox] is set.
 */
fun Player.getInventoryItemsId(ignoreStoreInbox: Boolean = false): List<ItemSummaryEntry> {
    val cache = ItemTierAccumulator()

    for (slot in Slots_t.FIRST..Slots_t.LAST) {
        val item = Slots_t.fromValue(slot)?.let { getInventoryItem(it) } ?: continue
        val isStoreInbox = item.id == Container.ITEM_STORE_INBOX

        if (!isStoreInbox) {
            cache.add(item)
        }

        val container = item.getContainer()
        if (container != null && (!isStoreInbox || !ignoreStoreInbox)) {
            cache.addAll(container.getItems(recursive = true))
        }
    }

    return cache.toEntries()
}

/**
 * `Player::getStoreInboxItemsId()` (player.cpp:5803-5819): the store-inbox
 * container's recursive contents. `getStoreInbox()` (player.cpp:12887-12894)
 * reads `getThing(CONST_SLOT_STORE_INBOX)->getContainer()`.
 */
fun Player.getStoreInboxItemsId(): List<ItemSummaryEntry> {
    val container = getInventoryItem(Slots_t.CONST_SLOT_STORE_INBOX)?.getContainer() ?: return emptyList()
    val cache = ItemTierAccumulator()
    cache.addAll(container.getItems(recursive = true))
    return cache.toEntries()
}

/**
 * `Player::getDepotChestItemsId()` (player.cpp:5821-5841): the recursive
 * contents of every `Player::depotChests` entry's container. The map is the
 * [depotChests] seam because the port's depot state is host-owned.
 */
fun Player.getDepotChestItemsId(
    /** `Player::depotChests` (player.hpp:1674). */
    depotChests: () -> Collection<Item> = { emptyList() },
): List<ItemSummaryEntry> {
    val cache = ItemTierAccumulator()

    for (depot in depotChests()) {
        val container = depot.getContainer() ?: continue
        cache.addAll(container.getItems(recursive = true))
    }

    return cache.toEntries()
}

/**
 * `Player::getDepotInboxItemsId()` (player.cpp:5843-5859): the inbox
 * container's recursive contents (`Player::getInbox()`, player.cpp:905).
 */
fun Player.getDepotInboxItemsId(): List<ItemSummaryEntry> {
    val cache = ItemTierAccumulator()
    cache.addAll(inbox.getItems(recursive = true))
    return cache.toEntries()
}

/**
 * `Player::getAllItemTypeCount(countMap)` (player.cpp:5972-5979): folds
 * `Item::countByType(item, -1)` into the caller's map keyed by item id.
 */
fun Player.getAllItemTypeCount(countMap: MutableMap<Int, Int>): MutableMap<Int, Int> {
    for (item in getAllInventoryItems()) {
        countMap[item.id] = (countMap[item.id] ?: 0) + Item.countByType(item, -1)
    }
    return countMap
}

/**
 * `Player::getAllSaleItemIdAndCount(countMap)` (player.cpp:5982-6000): the
 * sale-list fold. Every enumerated item other than a gold pouch must carry
 * market attributes and must not be a non-empty container; the gold pouch is
 * always counted. `getAllInventoryItems(false, true)` skips contained items
 * with a tier.
 */
fun Player.getAllSaleItemIdAndCount(countMap: MutableMap<Int, Int>): MutableMap<Int, Int> {
    for (item in getAllInventoryItems(ignoreEquiped = false, ignoreItemWithTier = true)) {
        if (item.id != Container.ITEM_GOLD_POUCH) {
            if (!item.hasMarketAttributes()) {
                continue
            }

            val container = item.getContainer()
            if (container != null && container.items.isNotEmpty()) {
                continue
            }
        }

        countMap[item.id] = (countMap[item.id] ?: 0) + item.getItemCount()
    }
    return countMap
}

/**
 * `Player::getAllItemTypeCountAndSubtype(countMap)` (player.cpp:6002-6011):
 * fluid containers are keyed `itemId | (FLUIDTYPE << 16)`, everything else by
 * item id.
 */
fun Player.getAllItemTypeCountAndSubtype(countMap: MutableMap<Int, Int>): MutableMap<Int, Int> {
    for (item in getAllInventoryItems()) {
        val itemId = item.id
        if (item.type?.isFluidContainer == true) {
            val fluidType = (item.getAttribute(ItemAttribute.FLUIDTYPE) as? Number)?.toInt() ?: 0
            val key = itemId or (fluidType shl 16)
            countMap[key] = (countMap[key] ?: 0) + item.getItemCount()
        } else {
            countMap[itemId] = (countMap[itemId] ?: 0) + item.getItemCount()
        }
    }
    return countMap
}

/**
 * `Player::getItemImbuementSkillEquipment(item, skill)` (player.cpp:73-85): the
 * sum of every active imbuement slot's `skills[skill] / 10000.0`, reading
 * `Item::getImbuementInfo` (item.cpp:472-483) — a non-zero duration and a
 * registry-resolved imbuement id.
 */
fun getItemImbuementSkillEquipment(item: Item, skill: Int): Double {
    var imbuementSkill = 0.0
    val registry = item.imbuementRegistry

    for (slotId in 0 until item.getImbuementSlot()) {
        val packed = item.getImbuementAttribute(slotId) ?: continue
        val duration = packed ushr 8
        if (duration == 0L) {
            continue
        }
        val imbuement = registry?.getImbuement((packed and 0xFF).toInt()) ?: continue
        if (skill in imbuement.skills.indices) {
            imbuementSkill += imbuement.skills[skill] / 10000.0
        }
    }

    return imbuementSkill
}

/**
 * `Player::getSkillsEquipment()` (player.cpp:13214-13238): one
 * [SkillsEquipment] per `skills_t`, folding every item-type ability skill and
 * imbuement skill over the `CONST_SLOT_FIRST..CONST_SLOT_AMMO` slots.
 */
fun Player.getSkillsEquipment(
    /** `Player::getItemImbuementSkillEquipment(item, skill)` (player.cpp:73-85). */
    imbuementSkillEquipment: (Item, Int) -> Double = { item, skill ->
        getItemImbuementSkillEquipment(item, skill)
    },
): List<SkillsEquipment> {
    val skillsEquipment = MutableList(skills_t.LAST + 1) { SkillsEquipment() }

    for (skill in skills_t.FIRST..skills_t.LAST) {
        var equipment = 0.0
        var imbuement = 0.0

        for (slot in Slots_t.FIRST..Slots_t.CONST_SLOT_AMMO.value) {
            val item = Slots_t.fromValue(slot)?.let { getInventoryItem(it) } ?: continue

            val abilities = item.type?.abilities
            if (abilities != null) {
                equipment += abilities.skills[skill] / 10000.0
            }

            imbuement += imbuementSkillEquipment(item, skill)
        }

        skillsEquipment[skill] = SkillsEquipment(equipment, imbuement)
    }

    return skillsEquipment
}
