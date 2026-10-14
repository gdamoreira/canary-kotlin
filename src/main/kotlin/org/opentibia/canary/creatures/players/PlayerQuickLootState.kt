package org.opentibia.canary.creatures.players

import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.Slots_t
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ObjectCategory_t

/**
 * The auto-loot and managed-backpack hooks
 * (src/creatures/players/player.cpp:1670-1732, 5692-5708, 12864-12885):
 *
 * - `Player::checkLootContainers` (player.cpp:1670-1711)
 * - `Player::checkAutoLoot` (player.cpp:5692-5708)
 * - `Player::getLootPouch` (player.cpp:12864-12885)
 * - `Player::setMainBackpackUnassigned` (player.cpp:1713-1732)
 *
 * The port's [Player] owns the inventory and the managed containers
 * (`refreshManagedContainer`/`getManagedContainer`, player.cpp:1595-1668), so
 * these are extension functions. The config booleans, the VIP check, the
 * `kv()->scoped("features")->get("autoloot")` lookup, the parent-cylinder
 * probe and the `ProtocolGame::send*` calls are call-site seams per
 * AGENTS.md 1.1; unwired defaults keep the features inert.
 */

/**
 * `Player::checkLootContainers(container)` (player.cpp:1670-1711): every managed
 * loot/obtain registration whose container is the given container or holds it
 * is unregistered (the slot is cleared, the category entry erased once both
 * slots are empty) and `Player::sendLootContainers()` runs at most once.
 *
 * The C++ walks its private `m_managedContainers` map directly; the host owns
 * that map (Creature.kt), so the walk goes through the public
 * [Player.getManagedContainer] / [Player.refreshManagedContainer] accessors
 * over the `ObjectCategory_t` domain. Refreshing a category that has no entry
 * is a no-op, so the fallback resolution of `getManagedContainer` cannot clear
 * an unrelated registration.
 */
fun Player.checkLootContainers(
    /** The `std::shared_ptr<Container> &container` argument (player.cpp:1670). */
    container: Container?,
    /** `Player::sendLootContainers()` (player.cpp:1710). */
    sendLootContainers: () -> Unit = {},
) {
    if (container == null) {
        return
    }

    var shouldSend = false
    for (category in ObjectCategory_t.entries) {
        for (isLootContainer in LOOT_CONTAINER_SLOTS) {
            val managedContainer = getManagedContainer(category, isLootContainer) ?: continue
            if (container === managedContainer || container.isHoldingItem(managedContainer)) {
                shouldSend = true
                refreshManagedContainer(category, null, isLootContainer)
            }
        }
    }

    if (shouldSend) {
        sendLootContainers()
    }
}

/** `for (const bool isLootContainer : { true, false })` (player.cpp:1719). */
private val LOOT_CONTAINER_SLOTS = listOf(true, false)

/**
 * `Player::checkAutoLoot` (player.cpp:5692-5708): the `AUTOLOOT` toggle and
 * the VIP-only gate must pass, then the stored `features.autoloot` value
 * decides (2 = every corpse, 1 = non-boss corpses only).
 */
fun Player.checkAutoLoot(
    isBoss: Boolean,
    /** `g_configManager().getBoolean(AUTOLOOT)` (player.cpp:5693). */
    autoLootEnabled: Boolean = false,
    /** `g_configManager().getBoolean(VIP_SYSTEM_ENABLED)` (player.cpp:5696). */
    vipSystemEnabled: Boolean = false,
    /** `g_configManager().getBoolean(VIP_AUTOLOOT_VIP_ONLY)` (player.cpp:5696). */
    vipAutoLootVipOnly: Boolean = false,
    /** `Player::isVip()` (player.cpp:5696). */
    isVip: () -> Boolean = { false },
    /** `kv()->scoped("features")->get("autoloot")` (player.cpp:5699). */
    autolootFeature: () -> Long? = { null },
): Boolean {
    if (!autoLootEnabled) {
        return false
    }

    if (vipSystemEnabled && vipAutoLootVipOnly && !isVip()) {
        return false
    }

    val value = autolootFeature() ?: 0L
    return when (value) {
        2L -> true
        1L -> !isBoss
        else -> false
    }
}

/**
 * `Player::getLootPouch` (player.cpp:12864-12885): the gold pouch container
 * found through the inventory, unless a regular player-group character sits
 * inside a non-store-inbox parent item (in which case the pouch is
 * unavailable).
 */
fun Player.getLootPouch(
    /** `Player::isPlayerGroup()` (player.cpp:12867). */
    isPlayerGroup: () -> Boolean = { false },
    /** `getParent() ? getParent()->getItem() : nullptr` (player.cpp:12866). */
    parentItem: () -> Item? = { null },
): Container? {
    val parent = parentItem()
    if (isPlayerGroup() && parent != null && parent.id != Container.ITEM_STORE_INBOX) {
        return null
    }

    val containerItem = getInventoryItemsFromId(Container.ITEM_GOLD_POUCH).firstOrNull() ?: return null
    return containerItem.getContainer()
}

/**
 * `Player::setMainBackpackUnassigned` (player.cpp:1713-1732): registers the
 * given container as the default-category loot/obtain container for every
 * still-unmanaged kind, then pushes the backpack inventory update and the
 * loot-container list once.
 */
fun Player.setMainBackpackUnassigned(
    container: Container?,
    /** `Player::sendInventoryItem(CONST_SLOT_BACKPACK, container)` (player.cpp:1729). */
    sendInventoryItem: (Slots_t, Container) -> Unit = { _, _ -> },
    /** `Player::sendLootContainers()` (player.cpp:1730). */
    sendLootContainers: () -> Unit = {},
) {
    if (container == null) {
        return
    }

    var toSendInventoryUpdate = false
    for (isLootContainer in listOf(true, false)) {
        val managedContainer = getManagedContainer(ObjectCategory_t.OBJECTCATEGORY_DEFAULT, isLootContainer)
        if (managedContainer == null) {
            refreshManagedContainer(ObjectCategory_t.OBJECTCATEGORY_DEFAULT, container, isLootContainer)
            toSendInventoryUpdate = true
        }
    }

    if (toSendInventoryUpdate) {
        sendInventoryItem(Slots_t.CONST_SLOT_BACKPACK, container)
        sendLootContainers()
    }
}
