package org.opentibia.canary.creatures.players

import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.Slots_t
import org.opentibia.canary.items.Cylinder
import org.opentibia.canary.items.CylinderLink
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.Thing

/**
 * The Player inventory-change notification producers
 * (src/creatures/players/player.cpp:8664-8760):
 *
 * - `Player::postAddNotification` (player.cpp:8664-8703)
 * - `Player::postRemoveNotification` (player.cpp:8705-8760)
 *
 * Both are the tail of every cylinder operation that touches the player
 * inventory: they run the `g_moveEvents().onPlayerEquip`/`onPlayerDeEquip`
 * scripts, refresh the inventory state (`updateInventoryWeight`,
 * `updateItemsLight`, `sendInventoryIds`, `sendStats`) for the `LINK_OWNER`
 * and `LINK_TOPPARENT` relations, re-send/close containers, and force the
 * deferred sale-list refresh into `Player::updateSaleShopList`
 * (player.cpp:6037-6054).
 *
 * Every dependency Canary resolves through a global or the unmodeled
 * `Player::client`/container state is a call-site seam per AGENTS.md 1.1;
 * `Game::internalAddItem`/`internalRemoveItem`/`internalMoveItem` bind the
 * shop/sale seams from their `Game::getShopOwner` and scheduled-sale state,
 * and the composition root injects the dispatcher behind `scheduleSaleUpdate`
 * (`g_dispatcher().addEvent`, player.cpp:6052).
 *
 * PARITY GAPS: the C++ `oldParent`/`newParent` identity comparison against
 * `getPlayer()` cannot match because the Kotlin [Player] is not a `Cylinder`
 * (`(oldParent as Any?) !== (this as Any?)` always holds, so
 * `requireListUpdate` can only be cleared through the
 * `Container::getHoldingPlayer` branch); `Player::updateItemsLight`
 * (player.cpp:6284-6305) has no composed [PlayerLightState] and defaults to a
 * no-op seam; `Player::checkLootContainers` (player.cpp:1670-1711) is not
 * ported and stays a no-op seam; and the batching `m_batching` guard reads
 * [Player.isBatching] (player.hpp:1973).
 */

/**
 * `Player::postAddNotification(thing, oldParent, index, link)`
 * (player.cpp:8664-8703): the equip script for `LINK_OWNER`, then the state
 * refresh for `LINK_OWNER`/`LINK_TOPPARENT`, the open-container re-send, the
 * close-out-of-range fanout for the player creature itself, and the shop
 * sale-list refresh gated by `requireListUpdate`.
 */
fun Player.postAddNotification(
    /** The `thing` argument (player.cpp:8664). */
    thing: Thing?,
    /** The `oldParent` argument (player.cpp:8664). */
    oldParent: Cylinder?,
    /** The `index` argument (player.cpp:8664); cast to [Slots_t] for the equip hook. */
    index: Int,
    /** The `link` argument (player.cpp:8664), defaulting to `LINK_OWNER`. */
    link: CylinderLink = CylinderLink.LINK_OWNER,
    /** `g_moveEvents().onPlayerEquip(player, item, slot, false)` (player.cpp:8667). */
    onPlayerEquip: (Player, Item, Slots_t, Boolean) -> Unit = { _, _, _, _ -> },
    /** `Player::updateInventoryWeight()` (player.cpp:1072-1084). */
    updateInventoryWeight: () -> Unit = { this.updateInventoryWeight() },
    /** `Player::updateItemsLight()` (player.cpp:6284-6305). */
    updateItemsLight: () -> Unit = {},
    /** `Player::sendInventoryIds()` (player.cpp:9287-9291). */
    sendInventoryIds: () -> Unit = { this.sendInventoryIds() },
    /** `Player::sendStats()` (player.cpp:2347-2352). */
    sendStats: () -> Unit = { this.sendStats() },
    /** `Player::onSendContainer(container)` (player.cpp:12503-12516). */
    onSendContainer: (Container) -> Unit = { container ->
        this.onSendContainer(
            container,
            hasClient = { this.hasClient() },
            sendContainer = { cid, sent, hasParent, firstIndex ->
                this.sendContainer(cid, sent, hasParent, firstIndex)
            },
        )
    },
    /** `Container::getHoldingPlayer()` (container.hpp). */
    getHoldingPlayer: (Container) -> Player? = { null },
    /** `Player::shopOwner != nullptr` (player.cpp:8697). */
    hasShopOwner: () -> Boolean = { false },
    /** `Player::scheduledSaleUpdate` (player.cpp:8697). */
    isSaleUpdateScheduled: () -> Boolean = { false },
    /** `Player::updateSaleShopList(item)` (player.cpp:6037-6049). */
    updateSaleShopList: (Item) -> Unit = {},
    /** `Player::closeContainersOutOfRange()` (player.cpp:9186-9209). */
    closeContainersOutOfRange: () -> Unit = {},
) {
    if (link == CylinderLink.LINK_OWNER) {
        val item = thing?.getItem()
        val slot = item?.let { Slots_t.fromValue(index) }
        if (item != null && slot != null) {
            onPlayerEquip(this, item, slot, false)
        }
    }

    if (isBatching()) {
        return
    }

    var requireListUpdate = true
    if (link == CylinderLink.LINK_OWNER || link == CylinderLink.LINK_TOPPARENT) {
        val item = oldParent?.getItem()
        val container = item?.getContainer()
        requireListUpdate = if (container != null) {
            getHoldingPlayer(container) !== this
        } else {
            (oldParent as Any?) !== (this as Any?)
        }

        updateInventoryWeight()
        updateItemsLight()
        sendInventoryIds()
        sendStats()
    }

    val item = thing?.getItem()
    if (item != null) {
        item.getContainer()?.let(onSendContainer)

        if (hasShopOwner() && !isSaleUpdateScheduled() && requireListUpdate) {
            updateSaleShopList(item)
        }
    } else {
        val creature = thing?.getCreature()
        if (creature === this) {
            closeContainersOutOfRange()
        }
    }
}

/**
 * `Player::postRemoveNotification(thing, newParent, index, link)`
 * (player.cpp:8705-8760): the de-equip script for `LINK_OWNER` (a zero return
 * aborts the notification), then the state refresh for
 * `LINK_OWNER`/`LINK_TOPPARENT`, the loot-check/close re-send of the removed
 * container, the forced sale refresh for a tiered item, and the shop
 * sale-list refresh gated by `requireListUpdate`.
 */
fun Player.postRemoveNotification(
    /** The `thing` argument (player.cpp:8705). */
    thing: Thing?,
    /** The `newParent` argument (player.cpp:8705). */
    newParent: Cylinder?,
    /** The `index` argument (player.cpp:8705); cast to [Slots_t] for the de-equip hook. */
    index: Int,
    /** The `link` argument (player.cpp:8705), defaulting to `LINK_OWNER`. */
    link: CylinderLink = CylinderLink.LINK_OWNER,
    /**
     * `g_moveEvents().onPlayerDeEquip(player, item, slot)` (player.cpp:8717);
     * `0` aborts the notification, the unloaded move-event layer returns `1`.
     */
    onPlayerDeEquip: (Player, Item, Slots_t) -> Int = { _, _, _ -> 1 },
    /** `Player::updateInventoryWeight()` (player.cpp:1072-1084). */
    updateInventoryWeight: () -> Unit = { this.updateInventoryWeight() },
    /** `Player::updateItemsLight()` (player.cpp:6284-6305). */
    updateItemsLight: () -> Unit = {},
    /** `Player::sendInventoryIds()` (player.cpp:9287-9291). */
    sendInventoryIds: () -> Unit = { this.sendInventoryIds() },
    /** `Player::sendStats()` (player.cpp:2347-2352). */
    sendStats: () -> Unit = { this.sendStats() },
    /** `Container::getHoldingPlayer()` (container.hpp). */
    getHoldingPlayer: (Container) -> Player? = { null },
    /** `Player::checkLootContainers(container)` (player.cpp:1670-1711), unported. */
    checkLootContainers: (Container) -> Unit = {},
    /** `Player::shouldCloseContainer(container)` (player.cpp:9211-9240). */
    shouldCloseContainer: (Container) -> Boolean = { container -> this.shouldCloseContainer(container) },
    /** `Player::autoCloseContainers(container)` (player.cpp:12518-12541). */
    autoCloseContainers: (Container) -> Unit = { container ->
        this.autoCloseContainers(
            container,
            hasClient = { this.hasClient() },
            sendCloseContainer = { cid -> this.sendCloseContainer(cid) },
        )
    },
    /** `Player::onSendContainer(container)` (player.cpp:12503-12516). */
    onSendContainer: (Container) -> Unit = { container ->
        this.onSendContainer(
            container,
            hasClient = { this.hasClient() },
            sendContainer = { cid, sent, hasParent, firstIndex ->
                this.sendContainer(cid, sent, hasParent, firstIndex)
            },
        )
    },
    /** `Player::shopOwner != nullptr` (player.cpp:8753). */
    hasShopOwner: () -> Boolean = { false },
    /** `Player::scheduledSaleUpdate` (player.cpp:8753). */
    isSaleUpdateScheduled: () -> Boolean = { false },
    /** `Player::updateSaleShopList(item)` (player.cpp:6037-6049). */
    updateSaleShopList: (Item) -> Unit = {},
) {
    if (thing == null) {
        return
    }

    if (link == CylinderLink.LINK_OWNER) {
        val item = thing.getItem()
        val slot = item?.let { Slots_t.fromValue(index) }
        if (item != null && slot != null && onPlayerDeEquip(this, item, slot) == 0) {
            return
        }
    }

    if (isBatching()) {
        return
    }

    var requireListUpdate = true
    if (link == CylinderLink.LINK_OWNER || link == CylinderLink.LINK_TOPPARENT) {
        val item = newParent?.getItem()
        val container = item?.getContainer()
        requireListUpdate = if (container != null) {
            getHoldingPlayer(container) !== this
        } else {
            (newParent as Any?) !== (this as Any?)
        }

        updateInventoryWeight()
        updateItemsLight()
        sendInventoryIds()
        sendStats()
    }

    val item = thing.getItem()
    if (item != null) {
        val container = item.getContainer()
        if (container != null) {
            if ((container.getTopParent() as Any?) !== (this as Any?)) {
                checkLootContainers(container)
            }

            if (shouldCloseContainer(container)) {
                autoCloseContainers(container)
            } else {
                onSendContainer(container)
            }
        }

        // force list update if item exists tier
        if (item.getTier() > 0 && !requireListUpdate) {
            requireListUpdate = true
        }

        if (hasShopOwner() && !isSaleUpdateScheduled() && requireListUpdate) {
            updateSaleShopList(item)
        }
    }
}
