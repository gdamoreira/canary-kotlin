package org.opentibia.canary.systems.imbuement

import io.github.oshai.kotlinlogging.KotlinLogging
import org.opentibia.canary.combat.condition.Skill
import org.opentibia.canary.combat.condition.Stat
import java.lang.ref.WeakReference
import java.util.IdentityHashMap

private val logger = KotlinLogging.logger {}

/** `ITEM_EMPTY_IMBUEMENT_SCROLL = 51442` (src/utils/utils_definitions.hpp:713). */
const val ITEM_EMPTY_IMBUEMENT_SCROLL: Int = 51442

/**
 * Mirror of `enum class ImbuementAction` (src/enums/imbuement.hpp:16), used by
 * `Player::openImbuementWindow` (player.cpp:2833).
 */
enum class ImbuementAction(val value: Int) {
    OPEN(0),
    PICK_ITEM(1),
    SCROLL(2),
}

/**
 * Injected view of the `Player` fields `Player::addItemImbuementStats` /
 * `removeItemImbuementStats` mutate (src/creatures/players/player.cpp:3289,3328):
 * `setVarSkill`, `setVarStats`, `changeSpeed`, `bonusCapacity`, `sendStats` and
 * `sendSkills`.
 */
interface ImbuementStatsTarget {
    fun getCapacity(): Int

    fun setVarSkill(skill: Skill, value: Int)

    fun setVarStats(stat: Stat, value: Int)

    fun changeSpeed(value: Int)

    fun setBonusCapacity(value: Int)

    fun sendStats()

    fun sendSkills()
}

/**
 * Dependency-injected replacement for the Player/Game calls the imbuement
 * handlers perform, so the engine owns no real inventory. Each member mirrors a
 * Canary call site:
 *
 *  - [getStorageValue]: `Player::getStorageValue`, used by `Imbuements::getImbuements`
 *    (imbuements.cpp:407).
 *  - [sendImbuementResult] / [sendTextMessage]: `Player::sendImbuementResult`
 *    (player.cpp:8360) and `Player::sendTextMessage`.
 *  - [removeMoney]: `Game::removeMoney(..., useBank = true)` (player.cpp:2628,2750,2815).
 *  - [getItemTypeCount] / [getStashItemCount] / [removeItemOfType] / [withdrawItem]:
 *    `Player::getItemTypeCount`, `Player::getStashItemCount`,
 *    `Player::removeItemOfType` and `Player::withdrawItem`
 *    (player.cpp:2613-2656, 2734-2780).
 *  - [getItemTypeName]: `Item::items[id].name` (`ItemType` lookup, player.cpp:2654).
 *  - [internalRemoveItem]: `g_game().internalRemoveItem` (player.cpp:2589).
 *  - [removeItemCountById]: `Player::removeItemCountById` (player.cpp:5361),
 *    used by `createScrollImbuement` (player.cpp:2659).
 *  - [internalAddItem]: `g_game().internalAddItem` (player.cpp:2666).
 *  - [openImbuementWindow]: `Player::openImbuementWindow` (player.cpp:2833):
 *    refreshes `updateImbuementTrackerStats()` and opens the window; the
 *    no-client early return is the caller's concern.
 *  - [updateImbuementTrackerStats]: `Player::updateImbuementTrackerStats`
 *    (player.cpp:3367).
 *  - [isInProtectionZone] / [hasConditionInFight]: `player->getTile()->hasFlag(TILESTATE_PROTECTIONZONE)`
 *    and `player->hasCondition(CONDITION_INFIGHT)` (imbuements.cpp:480-481).
 */
interface ImbuementPlayer : ImbuementStatsTarget {
    fun getStorageValue(key: Int): Int

    fun sendImbuementResult(message: String)

    fun sendTextMessage(message: String)

    fun removeMoney(amount: Long): Boolean

    fun getItemTypeCount(itemId: Int): Int

    fun getStashItemCount(itemId: Int): Int

    fun getItemTypeName(itemId: Int): String

    fun removeItemOfType(itemId: Int, count: Int, subType: Int, ignoreEquipped: Boolean): Boolean

    fun withdrawItem(itemId: Int, amount: Int)

    fun internalRemoveItem(item: ImbuementItem, count: Int): Boolean

    fun removeItemCountById(itemId: Int, itemAmount: Int, removeFromStash: Boolean): Boolean

    fun internalAddItem(item: ImbuementItem, count: Int): Boolean

    fun openImbuementWindow(action: ImbuementAction, item: ImbuementItem?)

    fun updateImbuementTrackerStats()

    fun isInProtectionZone(): Boolean

    fun hasConditionInFight(): Boolean

    companion object {
        /**
         * Null-object player used by `Imbuements.getImbuements` callers without
         * a wired inventory: no check blocks and no mutation is recorded.
         */
        val UNWIRED: ImbuementPlayer = object : ImbuementPlayer {
            override fun getStorageValue(key: Int): Int = 0

            override fun sendImbuementResult(message: String) = Unit

            override fun sendTextMessage(message: String) = Unit

            override fun removeMoney(amount: Long): Boolean = true

            override fun getItemTypeCount(itemId: Int): Int = 0

            override fun getStashItemCount(itemId: Int): Int = 0

            override fun getItemTypeName(itemId: Int): String = "item $itemId"

            override fun removeItemOfType(itemId: Int, count: Int, subType: Int, ignoreEquipped: Boolean): Boolean = true

            override fun withdrawItem(itemId: Int, amount: Int) = Unit

            override fun internalRemoveItem(item: ImbuementItem, count: Int): Boolean = true

            override fun removeItemCountById(itemId: Int, itemAmount: Int, removeFromStash: Boolean): Boolean = true

            override fun internalAddItem(item: ImbuementItem, count: Int): Boolean = true

            override fun openImbuementWindow(action: ImbuementAction, item: ImbuementItem?) = Unit

            override fun updateImbuementTrackerStats() = Unit

            override fun isInProtectionZone(): Boolean = false

            override fun hasConditionInFight(): Boolean = false

            override fun getCapacity(): Int = 0

            override fun setVarSkill(skill: Skill, value: Int) = Unit

            override fun setVarStats(stat: Stat, value: Int) = Unit

            override fun changeSpeed(value: Int) = Unit

            override fun setBonusCapacity(value: Int) = Unit

            override fun sendStats() = Unit

            override fun sendSkills() = Unit
        }
    }
}

/**
 * Injected item seam mirroring the `Item` imbuement surface the engine reads
 * and writes:
 *
 *  - [getImbuementSlot]: `Item::getImbuementSlot` (src/items/item.hpp:447).
 *  - [hasImbuementType]: `Item::hasImbuementType` (item.hpp:720), where
 *    `imbuementTypes` is the `ItemType::imbuementTypes` map set by
 *    `ItemType::setImbuementType` (src/items/items.cpp:123).
 *  - [setImbuement] / [decayImbuementTime] / [clearImbuement]: `Item::setImbuement`
 *    (item.cpp:485), `Item::decayImbuementTime` and `Item::clearImbuement`
 *    (item.hpp:713,716). The custom attribute key is `ITEM_IMBUEMENT_SLOT + slot`
 *    and the value is `(duration << 8) | imbuementId`, 0 for an empty slot
 *    (`#define ITEM_IMBUEMENT_SLOT 500`, item.cpp:33).
 *  - [parent]/[parentIsContainer]/[holdingPlayer]: `Item::getParent()->getContainer()`
 *    and `Item::getHoldingPlayer` (imbuements.cpp:474,488).
 */
class ImbuementItem(
    val id: Int,
    private val imbuementSlot: Int = 0,
    private val imbuementTypes: Map<ImbuementTypes_t, Int> = emptyMap()
) {
    private val customAttributes = mutableMapOf<Int, Long>()

    /** `Item::getParent()` (item.hpp:681); `=== player` means the item is carried directly. */
    var parent: Any? = null

    /** Whether [parent] is a container (`getParent()->getContainer()`), i.e. the item sits in a backpack. */
    var parentIsContainer: Boolean = false

    /** `Item::getHoldingPlayer` used by the decay tracker (`imbuements.cpp:474`). */
    var holdingPlayer: ImbuementPlayer? = null

    fun getImbuementSlot(): Int = imbuementSlot

    fun hasImbuementType(imbuementType: ImbuementTypes_t, imbuementTier: Int): Boolean {
        val maxTier = imbuementTypes[imbuementType] ?: return false
        return maxTier >= imbuementTier
    }

    fun hasImbuementAttribute(slot: Int): Boolean = customAttributes.containsKey(ITEM_IMBUEMENT_SLOT + slot)

    fun getImbuementAttribute(slot: Int): Long? = customAttributes[ITEM_IMBUEMENT_SLOT + slot]

    fun setImbuement(slot: Int, imbuementId: Int, duration: Int) {
        val packed = if (duration > 0) (duration shl 8) or imbuementId else 0
        customAttributes[ITEM_IMBUEMENT_SLOT + slot] = packed.toLong() and 0xFFFFFFFFL
    }

    fun decayImbuementTime(slot: Int, imbuementId: Int, duration: Int) = setImbuement(slot, imbuementId, duration)

    fun clearImbuement(slot: Int, imbuementId: Int) = setImbuement(slot, imbuementId, 0)

    companion object {
        /** `#define ITEM_IMBUEMENT_SLOT 500` (src/items/item.cpp:33). */
        const val ITEM_IMBUEMENT_SLOT = 500
    }
}

/**
 * `Item::getImbuementInfo` (src/items/item.cpp:472): resolves the registry
 * entry packed into the slot's custom attribute and returns null when the
 * attribute is missing, the imbuement id is unknown, or the duration is 0.
 */
internal fun resolveImbuementInfo(item: ImbuementItem, slot: Int, imbuements: Imbuements): ImbuementInfo? {
    if (!item.hasImbuementAttribute(slot)) {
        return null
    }
    val raw = (item.getImbuementAttribute(slot) ?: 0L).toInt()
    val imbuement = imbuements.getImbuement(raw and 0xFF) ?: return null
    val duration = raw ushr 8
    return if (duration > 0) ImbuementInfo(imbuement, duration) else null
}

/** `Item::hasImbuementCategoryId` (src/items/item.cpp:532). */
internal fun itemHasImbuementCategory(item: ImbuementItem, categoryId: Int, imbuements: Imbuements): Boolean {
    for (slotId in 0 until item.getImbuementSlot()) {
        val info = resolveImbuementInfo(item, slotId, imbuements) ?: continue
        val category = imbuements.getCategoryByID(info.imbuement.category)
        if (category?.id == categoryId) {
            return true
        }
    }
    return false
}

/**
 * `Player::addItemImbuementStats` (src/creatures/players/player.cpp:3289).
 * `bonusCapacity` is a percentage of the player's current capacity.
 */
internal fun addItemImbuementStatsTo(imbuement: Imbuement, player: ImbuementStatsTarget) {
    var requestUpdate = false
    for (skill in Skill.FIRST..Skill.LAST) {
        val bonus = imbuement.skills[skill]
        if (bonus != 0) {
            requestUpdate = true
            player.setVarSkill(Skill.ALL[skill], bonus)
        }
    }

    for (stat in Stat.FIRST..Stat.LAST) {
        val bonus = imbuement.stats[stat]
        if (bonus != 0) {
            requestUpdate = true
            player.setVarStats(Stat.ALL[stat], bonus)
        }
    }

    if (imbuement.speed != 0) {
        player.changeSpeed(imbuement.speed)
    }

    if (imbuement.capacity != 0) {
        requestUpdate = true
        player.setBonusCapacity(player.getCapacity() * imbuement.capacity / 100)
    }

    if (requestUpdate) {
        player.sendStats()
        player.sendSkills()
    }
}

/**
 * `Player::removeItemImbuementStats` (player.cpp:3328). Faithfully keeps the
 * C++ quirk of zeroing `bonusCapacity` instead of subtracting the share.
 */
internal fun removeItemImbuementStatsFrom(imbuement: Imbuement, player: ImbuementStatsTarget) {
    var requestUpdate = false
    for (skill in Skill.FIRST..Skill.LAST) {
        val bonus = imbuement.skills[skill]
        if (bonus != 0) {
            requestUpdate = true
            player.setVarSkill(Skill.ALL[skill], -bonus)
        }
    }

    for (stat in Stat.FIRST..Stat.LAST) {
        val bonus = imbuement.stats[stat]
        if (bonus != 0) {
            requestUpdate = true
            player.setVarStats(Stat.ALL[stat], -bonus)
        }
    }

    if (imbuement.speed != 0) {
        player.changeSpeed(-imbuement.speed)
    }

    if (imbuement.capacity != 0) {
        requestUpdate = true
        player.setBonusCapacity(0)
    }

    if (requestUpdate) {
        player.sendStats()
        player.sendSkills()
    }
}

/**
 * Mirror of Canary's `ImbuementDecay` (src/creatures/players/imbuements/imbuements.hpp:140).
 *
 * The C++ class schedules its own 1-second dispatcher event; this port is
 * caller-driven: the game dispatcher/timer must call [checkImbuementDecay] on
 * the same cadence. Tracked items are held through [WeakReference] so a decay
 * tick never extends an item's lifetime, matching the C++ `std::weak_ptr`
 * tracker and the deferred-callback lifetime policy.
 */
class ImbuementDecay(
    private val imbuements: Imbuements,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private class TrackedImbuementItem(val item: WeakReference<ImbuementItem>, var lastUpdate: Long)

    private val itemsToDecay = IdentityHashMap<ImbuementItem, TrackedImbuementItem>()

    /** Number of items currently tracked for decay (diagnostics/tests). */
    internal val trackedItemCount: Int get() = itemsToDecay.size

    /** `ImbuementDecay::startImbuementDecay` (imbuements.cpp:499). */
    fun startImbuementDecay(item: ImbuementItem) {
        if (!hasImbuements(item)) {
            return
        }

        val existing = itemsToDecay[item]
        if (existing != null) {
            if (existing.item.get() != null) {
                return
            }
            itemsToDecay.remove(item)
        }

        itemsToDecay[item] = TrackedImbuementItem(WeakReference(item), clock())
    }

    /** `ImbuementDecay::stopImbuementDecay` (imbuements.cpp:532). */
    fun stopImbuementDecay(item: ImbuementItem) {
        itemsToDecay.remove(item)
    }

    /**
     * `ImbuementDecay::canDecayImbuement` (imbuements.cpp:473). The
     * `TOGGLE_IMBUEMENT_NON_AGGRESSIVE_FIGHT_ONLY` config flag is a parameter
     * defaulting to its configmanager default (configmanager.cpp:155).
     */
    fun canDecayImbuement(
        item: ImbuementItem,
        imbuementInfo: ImbuementInfo,
        nonAggressiveFightOnly: Boolean = false
    ): Boolean {
        val player = item.holdingPlayer ?: return false
        val isInProtectionZone = player.isInProtectionZone()
        val isInFightMode = player.hasConditionInFight()
        val category = imbuements.getCategoryByID(imbuementInfo.imbuement.category)
        val parent = item.parent
        val isInBackpack = parent != null && item.parentIsContainer

        if (category != null && (category.agressive || nonAggressiveFightOnly) &&
            (isInProtectionZone || !isInFightMode || isInBackpack)
        ) {
            return false
        }

        if (category != null && !category.agressive && parent != null && parent !== player) {
            return false
        }

        return true
    }

    /** `ImbuementDecay::checkImbuementDecay` (imbuements.cpp:555). */
    fun checkImbuementDecay(nonAggressiveFightOnly: Boolean = false) {
        val currentTime = clock()
        val iterator = itemsToDecay.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val item = entry.value.item.get()
            if (item == null) {
                iterator.remove()
                continue
            }

            val player = item.holdingPlayer
            if (player == null) {
                iterator.remove()
                continue
            }

            var hasImbuements = false
            val lastUpdate = if (entry.value.lastUpdate == 0L) currentTime else entry.value.lastUpdate
            val elapsedTime = maxOf(0L, currentTime - lastUpdate)
            val elapsedSeconds = (elapsedTime / 1000).toInt()

            for (slotId in 0 until item.getImbuementSlot()) {
                val imbuementInfo = resolveImbuementInfo(item, slotId, imbuements) ?: continue
                hasImbuements = true

                if (!canDecayImbuement(item, imbuementInfo, nonAggressiveFightOnly)) {
                    continue
                }

                val imbuement = imbuementInfo.imbuement

                // Unreachable through resolveImbuementInfo (duration 0 returns
                // null); transcribed from the C++ branch for parity.
                if (imbuementInfo.duration == 0) {
                    removeItemImbuementStatsFrom(imbuement, player)
                    player.updateImbuementTrackerStats()
                    continue
                }

                val duration = if (imbuementInfo.duration > elapsedSeconds) {
                    imbuementInfo.duration - elapsedSeconds
                } else {
                    0
                }
                item.decayImbuementTime(slotId, imbuement.id, duration)

                if (duration == 0) {
                    removeItemImbuementStatsFrom(imbuement, player)
                    player.updateImbuementTrackerStats()
                }
            }

            if (!hasImbuements) {
                iterator.remove()
                continue
            }

            entry.value.lastUpdate = currentTime
        }
    }

    private fun hasImbuements(item: ImbuementItem): Boolean =
        (0 until item.getImbuementSlot()).any { resolveImbuementInfo(item, it, imbuements) != null }
}

/**
 * Application service over the imbuement state. It transcribes:
 *
 *  - `Item::getImbuementInfo` / `getFreeImbuementSlot` / `hasImbuements` /
 *    `hasImbuementCategoryId` / `canAddImbuement`
 *    (src/items/item.cpp:457-543);
 *  - `Player::applyScrollImbuement` / `onApplyImbuement` / `onClearImbuement` /
 *    `clearAllImbuements` (src/creatures/players/player.cpp:2564-2831);
 *  - `Player::addItemImbuementStats` / `removeItemImbuementStats`
 *    (player.cpp:3289,3328).
 *
 * The engine owns no state of its own; the registry and decay tracker are
 * injected.
 */
class ImbuementEngine(
    private val imbuements: Imbuements,
    val decay: ImbuementDecay = ImbuementDecay(imbuements)
) {

    /** `Item::getImbuementInfo` (item.cpp:472). */
    fun getImbuementInfo(item: ImbuementItem, slot: Int): ImbuementInfo? =
        resolveImbuementInfo(item, slot, imbuements)

    /** `Item::getFreeImbuementSlot` (item.cpp:457): -1 when full or slot-less. */
    fun getFreeImbuementSlot(item: ImbuementItem): Int {
        if (item.getImbuementSlot() == 0) {
            return -1
        }
        for (slotId in 0 until item.getImbuementSlot()) {
            if (resolveImbuementInfo(item, slotId, imbuements) == null) {
                return slotId
            }
        }
        return -1
    }

    /** `Item::hasImbuements` (item.hpp:730). */
    fun hasImbuements(item: ImbuementItem): Boolean =
        (0 until item.getImbuementSlot()).any { resolveImbuementInfo(item, it, imbuements) != null }

    /** `Item::hasImbuementCategoryId` (item.cpp:532). */
    fun hasImbuementCategoryId(item: ImbuementItem, categoryId: Int): Boolean =
        itemHasImbuementCategory(item, categoryId, imbuements)

    /**
     * `Item::canAddImbuement` (item.cpp:490): slot bounds, known category, the
     * item's `imbuementTypes` tier (`hasImbuementType`) and the duplicate
     * category guard, with Canary's exact result messages.
     */
    fun canAddImbuement(item: ImbuementItem, slot: Int, imbuement: Imbuement, player: ImbuementPlayer): Boolean {
        val itemSlots = item.getImbuementSlot()
        if (itemSlots == 0 || slot >= itemSlots) {
            logger.error { "Player attempted to apply imbuement in an invalid slot ($slot)" }
            player.sendImbuementResult("Invalid slot selection.")
            return false
        }

        val category = imbuements.getCategoryByID(imbuement.category)
        if (category == null) {
            logger.error { "Invalid imbuement category '${imbuement.category}' for item id ${item.id}" }
            player.sendImbuementResult("This object cannot be imbued with that scroll.")
            return false
        }

        val imbuementType = ImbuementTypes_t.fromValue(category.id) ?: ImbuementTypes_t.IMBUEMENT_NONE
        if (!item.hasImbuementType(imbuementType, imbuement.baseId)) {
            player.sendImbuementResult("This object cannot be imbued with that scroll.")
            return false
        }

        if (hasImbuementCategoryId(item, category.id)) {
            logger.error { "Player tried to apply imbuement to an item that already contains the same category" }
            player.sendImbuementResult("This item already has this imbuement type.")
            return false
        }

        return true
    }

    /** `Player::applyScrollImbuement` (player.cpp:2564). */
    fun applyScrollImbuement(item: ImbuementItem, scrollItem: ImbuementItem, player: ImbuementPlayer) {
        val freeImbuementSlot = getFreeImbuementSlot(item)
        if (freeImbuementSlot < 0) {
            return
        }

        val imbuement = imbuements.getImbuementByScrollID(scrollItem.id) ?: return
        val baseImbuement = imbuements.getBaseByID(imbuement.baseId) ?: return

        if (!canAddImbuement(item, freeImbuementSlot, imbuement, player)) {
            return
        }

        if (!player.internalRemoveItem(scrollItem, 1)) {
            logger.error { "Failed to remove scroll item ${scrollItem.id}" }
            return
        }

        item.setImbuement(freeImbuementSlot, imbuement.id, baseImbuement.duration)
        decay.startImbuementDecay(item)

        if (item.parent === player) {
            addItemImbuementStats(imbuement, player)
        }
    }

    /**
     * `Player::createScrollImbuement` (player.cpp:2602): the shrine scroll
     * crafting path. Requires every crafting material (inventory or stash), an
     * empty imbuement scroll ([ITEM_EMPTY_IMBUEMENT_SCROLL]) and the base price;
     * consumes the materials and the empty scroll, then delivers
     * `Imbuement::getScrollItemID()` and reopens the scroll window.
     *
     * The `g_metrics().addCounter("balance_decrease", ...)` call has no metrics
     * seam in this port (see PARITY GAPS).
     */
    fun createScrollImbuement(imbuement: Imbuement, player: ImbuementPlayer) {
        val baseImbuement = imbuements.getBaseByID(imbuement.baseId) ?: return

        val items = imbuement.items
        for ((itemId, amount) in items) {
            if (player.getItemTypeCount(itemId) + player.getStashItemCount(itemId) < amount) {
                player.sendImbuementResult("You don't have all necessary items.")
                return
            }
        }

        val emptyScrollsCount =
            player.getItemTypeCount(ITEM_EMPTY_IMBUEMENT_SCROLL) +
                player.getStashItemCount(ITEM_EMPTY_IMBUEMENT_SCROLL)
        if (emptyScrollsCount == 0) {
            player.sendImbuementResult("You don't have all necessary items.")
            return
        }

        val price = baseImbuement.price
        if (!player.removeMoney(price.toLong())) {
            logger.error { "Player do not have money to create imbuement scroll" }
            player.sendImbuementResult("You don't have $price gold coins.")
            return
        }

        for ((itemId, amount) in items) {
            val inventoryItemCount = player.getItemTypeCount(itemId)
            if (inventoryItemCount >= amount) {
                if (!player.removeItemOfType(itemId, amount, -1, true)) {
                    logger.error { "Failed to remove ${amount}x item $itemId" }
                }
                continue
            }

            var stashItemCount = amount
            if (inventoryItemCount > 0 && player.removeItemOfType(itemId, inventoryItemCount, -1, false)) {
                stashItemCount -= inventoryItemCount
            }

            player.withdrawItem(itemId, stashItemCount)
            player.sendTextMessage(
                "Using ${stashItemCount}x ${player.getItemTypeName(itemId)} from your stash."
            )
        }

        if (!player.removeItemCountById(ITEM_EMPTY_IMBUEMENT_SCROLL, 1, true)) {
            logger.error { "Failed to remove empty imbuement scroll" }
            return
        }

        val imbuementScrollId = imbuement.getScrollItemID()
        val imbuementScroll = ImbuementItem(id = imbuementScrollId)
        if (!player.internalAddItem(imbuementScroll, 1)) {
            logger.error { "Failed to add imbuement scroll id '$imbuementScrollId'" }
            player.sendImbuementResult("Failed to add imbuement scroll. Please contact administration.")
            return
        }

        player.openImbuementWindow(ImbuementAction.SCROLL, null)
    }

    /** `Player::onApplyImbuement` (player.cpp:2711). */
    fun onApplyImbuement(imbuement: Imbuement, item: ImbuementItem, slot: Int, player: ImbuementPlayer) {
        if (!canAddImbuement(item, slot, imbuement, player)) {
            return
        }

        if (resolveImbuementInfo(item, slot, imbuements) != null) {
            logger.error { "Player tried to apply an imbuement to a slot that already contains one" }
            player.sendImbuementResult("An error occurred, please reopen imbuement window.")
            return
        }

        if (hasMatchingImbuementInOtherSlot(item, slot, imbuement)) {
            logger.error { "Player attempted to apply the same imbuement in multiple slots" }
            player.sendImbuementResult("You cannot apply the same imbuement in multiple slots.")
            return
        }

        val items = imbuement.items
        for ((itemId, amount) in items) {
            if (player.getItemTypeCount(itemId) + player.getStashItemCount(itemId) < amount) {
                player.sendImbuementResult("You don't have all necessary items.")
                return
            }
        }

        val baseImbuement = imbuements.getBaseByID(imbuement.baseId) ?: return
        val price = baseImbuement.price
        if (!player.removeMoney(price.toLong())) {
            logger.error { "Player do not have money to apply imbuement" }
            player.sendImbuementResult("You don't have $price gold coins.")
            return
        }

        for ((itemId, amount) in items) {
            val inventoryItemCount = player.getItemTypeCount(itemId)
            if (inventoryItemCount >= amount) {
                if (!player.removeItemOfType(itemId, amount, -1, true)) {
                    logger.error { "Failed to remove ${amount}x item $itemId" }
                }
                continue
            }

            var stashItemCount = amount
            if (inventoryItemCount > 0 && player.removeItemOfType(itemId, inventoryItemCount, -1, false)) {
                stashItemCount -= inventoryItemCount
            }

            player.withdrawItem(itemId, stashItemCount)
            player.sendTextMessage(
                "Using ${stashItemCount}x ${player.getItemTypeName(itemId)} from your stash. "
            )
        }

        if (item.parent === player) {
            resolveImbuementInfo(item, slot, imbuements)?.let { removeItemImbuementStats(it.imbuement, player) }
            addItemImbuementStats(imbuement, player)
        }
        item.setImbuement(slot, imbuement.id, baseImbuement.duration)
        decay.startImbuementDecay(item)

        player.openImbuementWindow(ImbuementAction.PICK_ITEM, item)
    }

    /** `Player::onClearImbuement` (player.cpp:2798). */
    fun onClearImbuement(item: ImbuementItem, slot: Int, player: ImbuementPlayer) {
        val imbuementInfo = resolveImbuementInfo(item, slot, imbuements)
        if (imbuementInfo == null) {
            logger.error { "Player tried to clear an imbuement from an item that does not contain one" }
            player.sendImbuementResult("An error occurred, please reopen imbuement window.")
            return
        }

        val baseImbuement = imbuements.getBaseByID(imbuementInfo.imbuement.baseId) ?: return
        val removeCost = baseImbuement.removeCost

        if (!player.removeMoney(removeCost.toLong())) {
            logger.error { "Player do not have money to clear imbuement" }
            player.sendImbuementResult("You don't have $removeCost gold coins.")
            player.openImbuementWindow(ImbuementAction.PICK_ITEM, item)
            return
        }

        if (item.parent === player) {
            removeItemImbuementStats(imbuementInfo.imbuement, player)
        }

        item.clearImbuement(slot, imbuementInfo.imbuement.id)
        player.openImbuementWindow(ImbuementAction.PICK_ITEM, item)
    }

    /** `Player::clearAllImbuements` (player.cpp:2676). */
    fun clearAllImbuements(item: ImbuementItem, player: ImbuementPlayer): Boolean {
        val itemSlots = item.getImbuementSlot()
        if (itemSlots == 0) {
            player.sendTextMessage("Sorry, not possible.")
            return false
        }

        val imbuementsToRemove = mutableListOf<Pair<Int, ImbuementInfo>>()
        for (slot in 0 until itemSlots) {
            val imbuementInfo = resolveImbuementInfo(item, slot, imbuements)
            if (imbuementInfo != null) {
                imbuementsToRemove += slot to imbuementInfo
            }
        }

        if (imbuementsToRemove.isEmpty()) {
            player.sendTextMessage("Sorry, not possible.")
            return false
        }

        for ((slot, imbuementInfo) in imbuementsToRemove) {
            if (item.parent === player) {
                removeItemImbuementStats(imbuementInfo.imbuement, player)
            }
            item.clearImbuement(slot, imbuementInfo.imbuement.id)
        }

        return true
    }

    /** `Player::addItemImbuementStats` (player.cpp:3289). */
    fun addItemImbuementStats(imbuement: Imbuement, player: ImbuementStatsTarget) =
        addItemImbuementStatsTo(imbuement, player)

    /** `Player::removeItemImbuementStats` (player.cpp:3328). */
    fun removeItemImbuementStats(imbuement: Imbuement, player: ImbuementStatsTarget) =
        removeItemImbuementStatsFrom(imbuement, player)

    private fun hasMatchingImbuementInOtherSlot(item: ImbuementItem, ignoredSlot: Int, imbuement: Imbuement): Boolean {
        for (slot in 0 until item.getImbuementSlot()) {
            if (slot == ignoredSlot) {
                continue
            }

            val existing = resolveImbuementInfo(item, slot, imbuements) ?: continue
            if (existing.imbuement.name == imbuement.name) {
                return true
            }
        }
        return false
    }
}
