package org.opentibia.canary.creatures.players

import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.Slots_t
import org.opentibia.canary.game.movement.areInRange
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.ItemAttribute
import org.opentibia.canary.items.containers.depot.DepotChest
import org.opentibia.canary.systems.rewardchest.RewardChest

/**
 * Player container/reward UI helpers
 * (src/creatures/players/player.cpp:1329-1344, 9032-9054, 9186-9240,
 * 9586-9629, 12023-12060, 12491-12516, 12518-12541, 12578-12587).
 *
 * The open-container registry already lives on the host
 * (`Player::addContainer`, `getOpenContainers`, `closeContainer`, ...), so the
 * transcriptions are extension functions over it and keep the exact `Player::`
 * names of their C++ symbols. Every external dependency is a call-site seam per
 * AGENTS.md 1.1: `Player::checkLootContainers` (player.cpp:1670) becomes a
 * callback, `Container::getHoldingPlayer` (container.hpp) its owning-player
 * lookup, the depot-chest map and `Player::isNearDepotBox` (player.cpp:1981)
 * are injected, and the `client->sendCloseContainer`/`sendContainer` calls are
 * callbacks guarded by a `hasClient` predicate.
 *
 * PARITY GAP: `closeContainersOutOfRange`'s debug log
 * (`g_logger().debug`, player.cpp:9204) is not reproduced.
 */

/**
 * `Player::shouldCloseContainer(container)` (player.cpp:9211-9240): removed or
 * unparented containers close, a container on the player's own tile (or whose
 * top-parent item is within one tile) stays open, a depot chest defers to
 * `Player::isNearDepotBox`, and anything else must be within one tile without
 * a z difference.
 */
fun Player.shouldCloseContainer(
    /** The `std::shared_ptr<Container> &` argument (player.cpp:9211). */
    container: Container?,
    /** `Player::depotChests` (player.hpp:1674) for the `getDepotChest()` check. */
    depotChests: () -> Collection<Container> = { emptyList() },
    /** `Player::isNearDepotBox()` (player.cpp:1981-1996). */
    isNearDepotBox: () -> Boolean = { false },
): Boolean {
    if (container == null || container.isRemoved()) {
        return true
    }

    val topParent = container.getTopParent() ?: return true
    // C++ `topParent == getPlayer()`: the port's `Player` is not a `Cylinder`,
    // so the identity comparison is written against `Any` and can only match a
    // future host that implements the cylinder contract (PARITY GAP).
    if ((topParent as Any?) === (this as Any?)) {
        return false
    }

    val topItem = topParent.getItem()
    if (topItem != null && areInRange(position, topItem.position, 1, 1, 0)) {
        return false
    }

    if (topParent is DepotChest) {
        for (chest in depotChests()) {
            if (chest === topParent) {
                return !isNearDepotBox()
            }
        }
    }

    return !areInRange(position, container.position, 1, 1, 0)
}

/**
 * `Player::closeContainersOutOfRange()` (player.cpp:9186-9209): every open
 * container that [shouldCloseContainer] rejects is loot-checked, closed and
 * reported to the client.
 */
fun Player.closeContainersOutOfRange(
    /** `Player::client != nullptr` (player.cpp:9187). */
    hasClient: () -> Boolean = { false },
    /** `Player::depotChests` (player.hpp:1674). */
    depotChests: () -> Collection<Container> = { emptyList() },
    /** `Player::isNearDepotBox()` (player.cpp:1981-1996). */
    isNearDepotBox: () -> Boolean = { false },
    /** `Player::checkLootContainers(container)` (player.cpp:1670-1711). */
    checkLootContainers: (Container) -> Unit = {},
    /** `ProtocolGame::sendCloseContainer(containerId)` (player.cpp:9207). */
    sendCloseContainer: (Int) -> Unit = {},
) {
    if (!hasClient()) {
        return
    }

    val containersToClose = mutableListOf<Int>()
    for ((containerId, containerInfo) in getOpenContainers()) {
        val container = containerInfo.container ?: continue
        if (shouldCloseContainer(container, depotChests, isNearDepotBox)) {
            checkLootContainers(container)
            containersToClose.add(containerId)
        }
    }

    for (containerId in containersToClose) {
        closeContainer(containerId)
        sendCloseContainer(containerId)
    }
}

/**
 * `Player::closeAllExternalContainers()` (player.cpp:9032-9054): every open
 * container not held by this player is auto-closed (with its children).
 */
fun Player.closeAllExternalContainers(
    /** `Player::client != nullptr` (player.cpp:9032 -> autoCloseContainers). */
    hasClient: () -> Boolean = { false },
    /** `Container::getHoldingPlayer()` (container.hpp) — null when unheld. */
    getHoldingPlayer: (Container) -> Player? = { null },
    /** `ProtocolGame::sendCloseContainer(containerId)` (player.cpp:12537). */
    sendCloseContainer: (Int) -> Unit = {},
) {
    if (getOpenContainers().isEmpty()) {
        return
    }

    val containersToClose = mutableListOf<Container>()
    for ((_, containerInfo) in getOpenContainers()) {
        val container = containerInfo.container ?: continue
        if (getHoldingPlayer(container) !== this) {
            containersToClose.add(container)
        }
    }

    for (container in containersToClose) {
        autoCloseContainers(container, hasClient, sendCloseContainer)
    }
}

/**
 * `Player::autoCloseContainers(container)` (player.cpp:12518-12541): closes
 * every open container whose parent chain reaches the given container (or that
 * is already removed) and notifies the client for each closed id.
 */
fun Player.autoCloseContainers(
    /** The `container` argument (player.cpp:12518). */
    container: Container?,
    /** `Player::client != nullptr` (player.cpp:12535). */
    hasClient: () -> Boolean = { false },
    /** `ProtocolGame::sendCloseContainer(containerId)` (player.cpp:12537). */
    sendCloseContainer: (Int) -> Unit = {},
) {
    val closeList = mutableListOf<Int>()
    for ((containerId, containerInfo) in getOpenContainers()) {
        var tmpContainer = containerInfo.container
        while (tmpContainer != null) {
            if (tmpContainer.isRemoved() || tmpContainer === container) {
                closeList.add(containerId)
                break
            }

            tmpContainer = tmpContainer.getParent() as? Container
        }
    }

    for (containerId in closeList) {
        closeContainer(containerId)
        if (hasClient()) {
            sendCloseContainer(containerId)
        }
    }
}

/**
 * `Player::onCloseContainer(container)` (player.cpp:12491-12501): every open
 * id holding the container is sent a close frame.
 */
fun Player.onCloseContainer(
    /** The `container` argument (player.cpp:12491). */
    container: Container?,
    /** `Player::client != nullptr` (player.cpp:12492). */
    hasClient: () -> Boolean = { false },
    /** `ProtocolGame::sendCloseContainer(containerId)` (player.cpp:12498). */
    sendCloseContainer: (Int) -> Unit = {},
) {
    if (!hasClient()) {
        return
    }

    for ((containerId, containerInfo) in getOpenContainers()) {
        if (containerInfo.container === container) {
            sendCloseContainer(containerId)
        }
    }
}

/**
 * `Player::onSendContainer(container)` (player.cpp:12503-12516): re-sends the
 * container description for every open id holding it, with its parent flag and
 * stored index.
 */
fun Player.onSendContainer(
    /** The `container` argument (player.cpp:12503). */
    container: Container?,
    /** `Player::client != nullptr` (player.cpp:12504). */
    hasClient: () -> Boolean = { false },
    /** `ProtocolGame::sendContainer(containerId, container, hasParent, index)` (player.cpp:12512). */
    sendContainer: (Int, Container, Boolean, Int) -> Unit = { _, _, _, _ -> },
) {
    if (!hasClient() || container == null) {
        return
    }

    val hasParent = container.hasParent()
    for ((containerId, containerInfo) in getOpenContainers()) {
        if (containerInfo.container === container) {
            sendContainer(containerId, container, hasParent, containerInfo.index)
        }
    }
}

/**
 * `Player::getAllContainers(onlyFromMainBackpack)` (player.cpp:9586-9629): the
 * main backpack (or every inventory slot container, slots `FIRST..AMMO`) plus
 * all nested containers in insertion order.
 */
fun Player.getAllContainers(onlyFromMainBackpack: Boolean = false): List<Container> {
    val containersCache = mutableListOf<Container>()

    if (onlyFromMainBackpack) {
        getBackpack()?.let { containersCache.add(it) }
    }

    for (slot in Slots_t.FIRST..Slots_t.CONST_SLOT_AMMO.value) {
        if (onlyFromMainBackpack) {
            break
        }

        val slotItem = Slots_t.fromValue(slot)?.let { getInventoryItem(it) } ?: continue
        (slotItem as? Container)?.let { containersCache.add(it) }
    }

    var index = 0
    while (index < containersCache.size) {
        for (item in containersCache[index].items) {
            (item as? Container)?.let { containersCache.add(it) }
        }
        index++
    }

    return containersCache
}

/**
 * `Player::openPlayerContainers()` (player.cpp:12023-12060): every inventory
 * container carrying the `OPENCONTAINER` attribute is reopened (id minus one)
 * in ascending client-id order and re-sent.
 */
fun Player.openPlayerContainers(
    /** `Player::client != nullptr` (player.cpp:12504 via onSendContainer). */
    hasClient: () -> Boolean = { false },
    /** `ProtocolGame::sendContainer(containerId, container, hasParent, index)` (player.cpp:12512). */
    sendContainer: (Int, Container, Boolean, Int) -> Unit = { _, _, _, _ -> },
) {
    val openContainersList = mutableListOf<Pair<Int, Container>>()

    for (slot in Slots_t.FIRST..Slots_t.CONST_SLOT_STORE_INBOX.value) {
        val item = Slots_t.fromValue(slot)?.let { getInventoryItem(it) } ?: continue
        val itemContainer = item.getContainer() ?: continue

        val cid = item.getAttribute(ItemAttribute.OPENCONTAINER) as? Long ?: 0L
        if (cid > 0) {
            openContainersList.add(cid.toInt() to itemContainer)
        }

        for (subItem in itemContainer.getItems(recursive = true)) {
            val subContainer = subItem.getContainer() ?: continue
            val subCid = subItem.getAttribute(ItemAttribute.OPENCONTAINER) as? Long ?: 0L
            if (subCid > 0) {
                openContainersList.add(subCid.toInt() to subContainer)
            }
        }
    }

    openContainersList.sortBy { it.first }

    for ((containerId, container) in openContainersList) {
        addContainer(containerId - 1, container)
        onSendContainer(container, hasClient, sendContainer)
    }
}

/**
 * `Player::getFreeBackpackSlots()` (player.cpp:12578-12587): the backpack's
 * free slots, or zero without a backpack.
 */
fun Player.getFreeBackpackSlots(): Int = maxOf(0, getBackpack()?.getFreeSlots() ?: 0)

/**
 * `Player::removeEmptyRewards()` (player.cpp:1329-1338): drops every empty
 * reward proxy from the reward chest and its map. The chest owns the C++
 * `Player::rewardMap`, so the method lives on [RewardChest] and keeps the
 * `Player::` name.
 */
fun Player.removeEmptyRewards(rewardChest: RewardChest) {
    for (rewardId in rewardChest.getRewardList()) {
        val reward = rewardChest.getReward(rewardId, autoCreate = false) ?: continue
        if (reward.empty) {
            rewardChest.removeItem(reward)
            rewardChest.removeReward(rewardId)
        }
    }
}

/**
 * `Player::hasOtherRewardContainerOpen(container)` (player.cpp:1340-1344):
 * another open container (not the given one) is a reward chest/container.
 * The C++ body dereferences a possibly-null stored container; the port skips
 * null entries instead.
 */
fun Player.hasOtherRewardContainerOpen(container: Container): Boolean =
    getOpenContainers().values.any { containerInfo ->
        val open = containerInfo.container
        open != null && open !== container && open.isAnyKindOfRewardContainer()
    }
