package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.CylinderFlags
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.items.Items
import org.opentibia.canary.items.containers.depot.DepotChest
import org.opentibia.canary.items.containers.depot.DepotLocker
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.systems.rewardchest.ReturnValue
import kotlin.math.min

/**
 * `Stash_Actions_t` (src/server/server_definitions.hpp:124-129) transcribed 1:1.
 */
enum class StashAction(val value: Int) {
    STOW_ITEM(0),
    STOW_CONTAINER(1),
    STOW_STACK(2),
    WITHDRAW(3),
}

/**
 * Self-contained commands produced by the stash (`0x28`) and depot-search
 * (`0x29`/`0x92`-`0x94`) client opcodes, mirroring the Canary `ProtocolGame`
 * handlers (src/server/network/protocol/protocolgame.cpp).
 *
 * `0x95` `ProtocolGame::parseOpenParentContainer` is decoded by
 * [TradeAndContainerPacketHandler] from inbound opcode batch 1.
 */
sealed interface DepotAndStashCommand {
    val opcode: Int
}

/**
 * Stow an item, container or stack into the stash (client opcode `0x28`,
 * actions `STOW_ITEM`/`STOW_CONTAINER`/`STOW_STACK`), mirroring
 * `ProtocolGame::parseStashWithdraw` (protocolgame.cpp:10891-10912): a
 * `Position`, the `uint16_t` item id and the stack position byte; only
 * `STOW_ITEM` carries a count byte, and only `STOW_STACK` stows the whole stack.
 */
data class StowItemCommand(
    val action: StashAction,
    val position: Position,
    val itemId: Int,
    val stackPosition: Int,
    val count: Int,
    val stowStack: Boolean,
) : DepotAndStashCommand {
    override val opcode: Int get() = DepotAndStashPacketHandler.OPCODE_STASH_WITHDRAW
}

/**
 * Withdraw an item from the stash (client opcode `0x28`, action `WITHDRAW`),
 * mirroring `ProtocolGame::parseStashWithdraw` (protocolgame.cpp:10913-10919):
 * the `uint16_t` item id, a `uint32_t` count and the stack position byte.
 */
data class StashWithdrawCommand(
    val itemId: Int,
    val count: Long,
    val stackPosition: Int,
) : DepotAndStashCommand {
    override val opcode: Int get() = DepotAndStashPacketHandler.OPCODE_STASH_WITHDRAW
}

/**
 * Unknown stash action (client opcode `0x28`): Canary logs the action and reads
 * nothing further (protocolgame.cpp:10920-10922).
 */
data class UnknownStashActionCommand(
    val action: Int,
) : DepotAndStashCommand {
    override val opcode: Int get() = DepotAndStashPacketHandler.OPCODE_STASH_WITHDRAW
}

/**
 * Open the depot search window (client opcode `0x92`). Canary reads no payload
 * and calls `Game::playerRequestDepotItems` (protocolgame.cpp:11007-11013).
 */
object OpenDepotSearchCommand : DepotAndStashCommand {
    override val opcode: Int get() = DepotAndStashPacketHandler.OPCODE_OPEN_DEPOT_SEARCH
}

/**
 * Close the depot search window (client opcode `0x93`). Canary reads no payload
 * and calls `Game::playerRequestCloseDepotSearch` (protocolgame.cpp:11015-11021).
 */
object CloseDepotSearchCommand : DepotAndStashCommand {
    override val opcode: Int get() = DepotAndStashPacketHandler.OPCODE_CLOSE_DEPOT_SEARCH
}

/**
 * Request a depot-search item detail (client opcode `0x94`), mirroring
 * `ProtocolGame::parseDepotSearchItemRequest` (protocolgame.cpp:11023-11035):
 * the `uint16_t` item id plus a tier byte only when the item carries an
 * `ItemType::upgradeClassification`.
 */
data class DepotSearchItemRequestCommand(
    val itemId: Int,
    val tier: Int,
) : DepotAndStashCommand {
    override val opcode: Int get() = DepotAndStashPacketHandler.OPCODE_DEPOT_SEARCH_ITEM_REQUEST
}

/**
 * Retrieve a depot-search item (client opcode `0x29`), mirroring
 * `ProtocolGame::parseRetrieveDepotSearch` (protocolgame.cpp:11037-11050): the
 * `uint16_t` item id, an optional tier byte for classified items and the
 * retrieve type byte.
 */
data class RetrieveDepotSearchCommand(
    val itemId: Int,
    val tier: Int,
    val type: Int,
) : DepotAndStashCommand {
    override val opcode: Int get() = DepotAndStashPacketHandler.OPCODE_RETRIEVE_DEPOT_SEARCH
}

/**
 * Decoder for the stash and depot-search client opcodes. Multi-byte fields are
 * Little-Endian, matching `NetworkMessage::get<T>()` on the native wire order.
 *
 * The optional [upgradeClassification] callback supplies
 * `ItemType::upgradeClassification` for an item id; callers without an item
 * registry can leave it at the default `0`, which reproduces Canary's behavior
 * for items that carry no tier.
 *
 *  - `0x28` `ProtocolGame::parseStashWithdraw`          (protocolgame.cpp:10874)
 *  - `0x29` `ProtocolGame::parseRetrieveDepotSearch`    (protocolgame.cpp:11037)
 *  - `0x92` `ProtocolGame::parseOpenDepotSearch`        (protocolgame.cpp:11007)
 *  - `0x93` `ProtocolGame::parseCloseDepotSearch`       (protocolgame.cpp:11015)
 *  - `0x94` `ProtocolGame::parseDepotSearchItemRequest` (protocolgame.cpp:11023)
 */
object DepotAndStashPacketHandler {

    const val OPCODE_STASH_WITHDRAW: Int = 0x28
    const val OPCODE_RETRIEVE_DEPOT_SEARCH: Int = 0x29
    const val OPCODE_OPEN_DEPOT_SEARCH: Int = 0x92
    const val OPCODE_CLOSE_DEPOT_SEARCH: Int = 0x93
    const val OPCODE_DEPOT_SEARCH_ITEM_REQUEST: Int = 0x94

    fun parse(
        opcode: Int,
        buffer: ByteBuf,
        upgradeClassification: (Int) -> Int = { 0 },
    ): DepotAndStashCommand? = when (opcode) {
        OPCODE_STASH_WITHDRAW -> parseStashWithdraw(buffer)
        OPCODE_RETRIEVE_DEPOT_SEARCH -> parseRetrieveDepotSearch(buffer, upgradeClassification)
        OPCODE_OPEN_DEPOT_SEARCH -> OpenDepotSearchCommand
        OPCODE_CLOSE_DEPOT_SEARCH -> CloseDepotSearchCommand
        OPCODE_DEPOT_SEARCH_ITEM_REQUEST -> parseDepotSearchItemRequest(buffer, upgradeClassification)
        else -> null
    }

    /**
     * 0x28: reads the action byte and the operands Canary consumes for it.
     *
     * @return the decoded command, or `null` when a consumed operand is missing.
     */
    fun parseStashWithdraw(buffer: ByteBuf): DepotAndStashCommand? {
        val actionByte = readByte(buffer) ?: return null
        val action = StashAction.entries.firstOrNull { it.value == actionByte }
            ?: return UnknownStashActionCommand(action = actionByte)

        return when (action) {
            StashAction.STOW_ITEM -> {
                val position = readPosition(buffer) ?: return null
                val itemId = readShort(buffer) ?: return null
                val stackPosition = readByte(buffer) ?: return null
                val count = readByte(buffer) ?: return null
                StowItemCommand(action, position, itemId, stackPosition, count, stowStack = false)
            }

            StashAction.STOW_CONTAINER -> {
                val position = readPosition(buffer) ?: return null
                val itemId = readShort(buffer) ?: return null
                val stackPosition = readByte(buffer) ?: return null
                StowItemCommand(action, position, itemId, stackPosition, count = 0, stowStack = false)
            }

            StashAction.STOW_STACK -> {
                val position = readPosition(buffer) ?: return null
                val itemId = readShort(buffer) ?: return null
                val stackPosition = readByte(buffer) ?: return null
                StowItemCommand(action, position, itemId, stackPosition, count = 0, stowStack = true)
            }

            StashAction.WITHDRAW -> {
                val itemId = readShort(buffer) ?: return null
                val count = readInt(buffer) ?: return null
                val stackPosition = readByte(buffer) ?: return null
                StashWithdrawCommand(itemId = itemId, count = count, stackPosition = stackPosition)
            }
        }
    }

    /**
     * 0x29: reads the `uint16_t` item id, the tier byte when the item is
     * classified and the retrieve type byte.
     *
     * @return the decoded command, or `null` when the payload is truncated.
     */
    fun parseRetrieveDepotSearch(
        buffer: ByteBuf,
        upgradeClassification: (Int) -> Int = { 0 },
    ): RetrieveDepotSearchCommand? {
        val itemId = readShort(buffer) ?: return null
        val tier = readTier(buffer, itemId, upgradeClassification) ?: return null
        val type = readByte(buffer) ?: return null
        return RetrieveDepotSearchCommand(itemId = itemId, tier = tier, type = type)
    }

    /**
     * 0x94: reads the `uint16_t` item id and the tier byte when the item is
     * classified.
     *
     * @return the decoded command, or `null` when the payload is truncated.
     */
    fun parseDepotSearchItemRequest(
        buffer: ByteBuf,
        upgradeClassification: (Int) -> Int = { 0 },
    ): DepotSearchItemRequestCommand? {
        val itemId = readShort(buffer) ?: return null
        val tier = readTier(buffer, itemId, upgradeClassification) ?: return null
        return DepotSearchItemRequestCommand(itemId = itemId, tier = tier)
    }

    /**
     * Applies the state-transition part of the stash/depot-search handlers on
     * the WorldCommit lane:
     *
     *  - `0x92` `Game::playerRequestDepotItems` (game.cpp:6920): the
     *    `isDepotSearchAvailable` and `isUIExhausted(500)` gates, then
     *    `Player::requestDepotItems` (player.cpp:10707) and
     *    `Player::updateUIExhausted` (game.cpp:6930).
     *  - `0x94` `Game::playerRequestDepotSearchItem` (game.cpp:6945): the
     *    `isDepotSearchOpen` and `isUIExhausted(500)` gates, then
     *    `Player::requestDepotSearchItem` (player.cpp:10763) and
     *    `Player::updateUIExhausted` (game.cpp:6958).
     */
    fun apply(player: Player, command: DepotAndStashCommand) {
        when (command) {
            OpenDepotSearchCommand -> applyOpenDepotSearch(player)
            is DepotSearchItemRequestCommand -> applyDepotSearchItemRequest(player, command)
            else -> Unit
        }
    }

    /** `STASH_MANAGE_AMOUNT` config default (configmanager.cpp:143). */
    const val STASH_MANAGE_AMOUNT_DEFAULT: Int = 100000

    /** `ITEM_GOLD_POUCH` (src/utils/utils_definitions.hpp:644). */
    const val ITEM_GOLD_POUCH: Int = 23396

    /**
     * Applies `ProtocolGame::parseStashWithdraw`'s stash actions (`0x28`,
     * protocolgame.cpp:10874-10925) on the WorldCommit lane: the
     * `isAccessPlayer`/`isStashMenuAvailable` and `isUIExhausted(500)` gates,
     * the decoded stow/withdraw state change and the trailing
     * `Player::updateUIExhausted`.
     *
     * PARITY GAPS: the `sendCancelMessage`/`sendTextMessage` replies and the
     * `sendOpenStash` refresh have no protocol sink in this context; the
     * `PlayerFlags_t::CannotPickupItem` gate of `Game::playerStashWithdraw`
     * (game.cpp:5714) has no ported flag model; the insertion uses
     * `Player::addItem`'s single free inventory slot instead of the C++
     * backpack/stack-aware `internalAddItem`.
     */
    fun applyStashAction(
        player: Player,
        command: DepotAndStashCommand,
        map: GameMap,
        itemTypes: Items?,
        maxWithdrawLimit: Int = STASH_MANAGE_AMOUNT_DEFAULT,
    ) {
        if (!player.isAccessPlayer() && !player.isStashMenuAvailable()) {
            return
        }
        if (player.isUIExhausted(500)) {
            return
        }

        when (command) {
            is StowItemCommand -> applyStowItem(player, command, map)
            is StashWithdrawCommand -> applyStashWithdraw(player, command, itemTypes, maxWithdrawLimit)
            else -> Unit
        }

        player.updateUIExhausted()
    }

    /**
     * `Game::playerStowItem` (game.cpp:5665-5702): the premium, item identity,
     * owner and range gates, then `Player::stowItem` (player.cpp:10311) and the
     * open depot-search refresh.
     *
     * PARITY GAPS: every `sendCancelMessage` has no protocol sink in this
     * context.
     */
    fun applyStowItem(player: Player, command: StowItemCommand, map: GameMap) {
        if (!player.isPremium()) {
            return
        }

        val item = resolveStowItem(player, map, command.position, command.stackPosition) ?: return
        if (item.id != command.itemId || item.getItemCount() < command.count || item.isStoreItem()) {
            return
        }

        if (item.hasOwner() && !item.isOwner(player)) {
            return
        }

        if (command.position.x != 0xFFFF && !areInRange1x1(command.position, player.position)) {
            return
        }

        stowItem(player, item, command.count, command.stowStack)

        if (player.isDepotSearchOpenOnItem(command.itemId)) {
            player.requestDepotSearchItem(command.itemId, 0)
        }
    }

    /**
     * `Game::playerStashWithdraw` (game.cpp:5706-5755): the item id/count,
     * capacity and `STASH_MANAGE_AMOUNT` gates, then `Player::addItemFromStash`
     * (player.cpp:9364) with the C++ correction of a partial withdrawal, the
     * open depot-search refresh and the `sendOpenStash` refresh (PARITY GAP:
     * no protocol sink).
     */
    fun applyStashWithdraw(
        player: Player,
        command: StashWithdrawCommand,
        itemTypes: Items?,
        maxWithdrawLimit: Int = STASH_MANAGE_AMOUNT_DEFAULT,
    ) {
        val itemType = itemTypes?.get(command.itemId) ?: return
        if (itemType.id == 0 || command.count == 0L) {
            return
        }

        if (player.getFreeCapacity() < 100) {
            return
        }

        var count = command.count
        if (count > maxWithdrawLimit) {
            count = maxWithdrawLimit.toLong()
        }

        val previousStashCount = player.stash.getStashItemCount(command.itemId)
        val ret = addItemFromStash(player, command.itemId, count, itemType)
        if (ret != ReturnValue.NOERROR) {
            val currentStashCount = player.stash.getStashItemCount(command.itemId)
            if (currentStashCount < previousStashCount) {
                player.stash.addItemOnStash(command.itemId, previousStashCount - currentStashCount)
            }
        }

        if (player.isDepotSearchOpenOnItem(command.itemId)) {
            player.requestDepotSearchItem(command.itemId, 0)
        }
    }

    /**
     * `Game::playerRequestDepotSearchRetrieve` (game.cpp:6960-6973): the
     * `isDepotSearchOpenOnItem` and `isUIExhausted(500)` gates, then
     * `Player::retrieveAllItemsFromDepotSearch` (player.cpp:10813) and
     * `Player::updateUIExhausted` (game.cpp:6972).
     *
     * PARITY GAPS: `sendCancelMessage`, the batch-update batching and
     * `Player::addItemBatch`'s backpack placement have no ported equivalent;
     * the insertion uses `Player::addItem`'s single free inventory slot.
     */
    fun applyRetrieveDepotSearch(player: Player, command: RetrieveDepotSearchCommand, itemTypes: Items?) {
        if (!player.isDepotSearchOpenOnItem(command.itemId)) {
            return
        }
        if (player.isUIExhausted(500)) {
            return
        }

        retrieveAllItemsFromDepotSearch(player, command.itemId, command.tier, command.type == 1, itemTypes)
        player.updateUIExhausted()
    }

    /**
     * `Player::retrieveAllItemsFromDepotSearch` (player.cpp:10813-10899): the
     * depot-locker inbox/depot scan filtered by the open search item and tier,
     * the weight cap, the inventory insertion, the removal from the source
     * containers and the trailing `requestDepotSearchItem` refresh.
     */
    private fun retrieveAllItemsFromDepotSearch(
        player: Player,
        itemId: Int,
        tier: Int,
        isDepot: Boolean,
        itemTypes: Items?,
    ) {
        val depotLocker = player.getDepotLocker(player.getLastDepotId())
        val itemsVector = mutableListOf<Item>()
        for (locker in depotLocker.getItemList()) {
            val container = locker.getContainer() ?: continue
            if (container.empty() ||
                (container.isInbox() && isDepot) ||
                (!container.isInbox() && !isDepot)
            ) {
                continue
            }

            val iterator = container.iterator()
            while (iterator.hasNext()) {
                val item = iterator.current()
                iterator.advance()
                if (item == null) {
                    continue
                }
                if (item.id == itemId && item.getTier() == player.getDepotSearchItemTier()) {
                    itemsVector.add(item)
                }
            }
        }

        val totalCount = itemsVector.sumOf { it.getItemCount() }
        val itemType = itemTypes?.get(itemId) ?: return
        val unitWeight = itemType.weight
        val maxByWeight = if (unitWeight > 0.0) {
            (player.getFreeCapacity() / unitWeight).toInt()
        } else {
            totalCount
        }
        val retrievableCount = min(totalCount, maxByWeight)

        var actuallyRetrieved = 0
        var remaining = retrievableCount
        val stackSize = if (itemType.isStackable) DEFAULT_STACK_SIZE else 1
        while (remaining > 0) {
            val chunk = min(stackSize, remaining)
            if (player.addItem(Item(id = itemId, count = chunk, type = itemType)) != ReturnValue.NOERROR) {
                break
            }
            actuallyRetrieved += chunk
            remaining -= chunk
        }

        if (actuallyRetrieved == 0) {
            return
        }

        var totalRemoved = 0
        for (item in itemsVector) {
            if (totalRemoved >= actuallyRetrieved) {
                break
            }
            val amountToRemove = min(actuallyRetrieved - totalRemoved, item.getItemCount())
            if (removeItem(item, amountToRemove) == ReturnValue.NOERROR) {
                totalRemoved += amountToRemove
            }
        }

        player.requestDepotSearchItem(itemId, tier)
    }

    /** `Player::removeItem` (player.cpp:10251-10281) over the item's parent cylinder. */
    private fun removeItem(item: Item, count: Int): ReturnValue {
        val cylinder = item.getParent() ?: return ReturnValue.NOTPOSSIBLE
        val removeCount = if (count == 0 || count > item.getItemCount()) item.getItemCount() else count
        val ret = cylinder.queryRemove(item, removeCount, CylinderFlags.FLAG_IGNORENOTMOVABLE)
        if (ret != ReturnValue.NOERROR) {
            return ret
        }
        cylinder.removeThing(item, removeCount)
        return ReturnValue.NOERROR
    }

    /**
     * `Player::addItemFromStash` (player.cpp:9364-9530). The retrievable count
     * follows the weight cap and the C++ `canAddItems` free-slot/stack-space
     * precheck; the actual insertion goes through [Player.addItem] and the
     * stash withdrawal only removes what was delivered.
     *
     * PARITY GAP: the C++ managed obtain container/backpack placement and the
     * per-stack `addItemBatch` split are not modeled; the port's inventory
     * insertion places one item per free inventory slot.
     */
    private fun addItemFromStash(player: Player, itemId: Int, itemCount: Long, itemType: ItemType): ReturnValue {
        val itemWeight = itemType.weight
        val availableCapacity = player.getFreeCapacity()
        val maxRetrievableByWeight = if (itemWeight > 0.0) {
            (availableCapacity / itemWeight).toInt()
        } else {
            itemCount.toInt()
        }
        val retrievableCount = min(maxRetrievableByWeight, itemCount.toInt())
        if (retrievableCount <= 0) {
            return ReturnValue.NOTENOUGHROOM
        }

        val stackSize = if (itemType.isStackable) DEFAULT_STACK_SIZE else 1
        val freeSlots = freeInventorySlots(player)
        val maxBySlots = if (itemType.isStackable) {
            freeStackSpace(player, itemId, stackSize) + freeSlots * stackSize
        } else {
            freeSlots
        }
        val finalRetrievable = min(maxBySlots, retrievableCount)
        if (finalRetrievable <= 0) {
            return ReturnValue.NOTENOUGHROOM
        }

        if (!player.stash.withdrawItem(itemId, finalRetrievable)) {
            return ReturnValue.NOTPOSSIBLE
        }

        var remaining = finalRetrievable
        var added = 0
        while (remaining > 0) {
            val chunk = min(stackSize, remaining)
            if (player.addItem(Item(id = itemId, count = chunk, type = itemType)) != ReturnValue.NOERROR) {
                break
            }
            added += chunk
            remaining -= chunk
        }

        if (added < finalRetrievable) {
            player.stash.addItemOnStash(itemId, finalRetrievable - added)
        }
        return ReturnValue.NOERROR
    }

    private fun freeInventorySlots(player: Player): Int {
        var free = 0
        for (slot in org.opentibia.canary.creatures.Slots_t.FIRST..org.opentibia.canary.creatures.Slots_t.LAST) {
            val inventorySlot = org.opentibia.canary.creatures.Slots_t.fromValue(slot) ?: continue
            if (player.getInventoryItem(inventorySlot) == null) {
                free++
            }
        }
        return free
    }

    private fun freeStackSpace(player: Player, itemId: Int, stackSize: Int): Int {
        var space = 0
        for (container in playerContainers(player)) {
            val iterator = container.iterator()
            while (iterator.hasNext()) {
                val item = iterator.current()
                iterator.advance()
                if (item == null) {
                    continue
                }
                if (item.id == itemId && item.isStackable()) {
                    space += maxOf(0, stackSize - item.getItemCount())
                }
            }
        }
        return space
    }

    private fun playerContainers(player: Player): List<Container> {
        val containers = mutableListOf<Container>()
        for (slot in org.opentibia.canary.creatures.Slots_t.FIRST..org.opentibia.canary.creatures.Slots_t.LAST) {
            val inventorySlot = org.opentibia.canary.creatures.Slots_t.fromValue(slot) ?: continue
            val container = player.getInventoryItem(inventorySlot)?.getContainer() ?: continue
            containers.add(container)
        }
        return containers
    }

    /**
     * `Player::stowItem` (player.cpp:10311-10392) for the decoded stash
     * actions: the single-item/`STOW_ITEM` path, the container
     * `getStowableItems` path, and the `STOW_STACK` backpack/loot-pouch/depot
     * locker scan.
     *
     * PARITY GAPS: `sendCancelMessage`/`sendTextMessage` have no protocol sink,
     * `g_moveEvents().onPlayerDeEquip` stays with the Lua move-event layer, and
     * the C++ `ItemType::maxItems` container-vs-type guard of
     * `requestLockerItems` is not modeled.
     */
    private fun stowItem(player: Player, item: Item, count: Int, allItems: Boolean) {
        if (!item.isItemStorable() && item.id != ITEM_GOLD_POUCH) {
            return
        }

        val itemDict = mutableListOf<Pair<Item, Int>>()
        var totalItemsToStow = 0
        val maxItemsToStow = STASH_MANAGE_AMOUNT_DEFAULT

        if (allItems) {
            if (item.getContainer() != null) {
                return
            }

            if (!isInsideDepot(item, true)) {
                val backpack = player.getBackpack()
                if (backpack != null) {
                    totalItemsToStow += collectStowItems(item, backpack, itemDict, totalItemsToStow, maxItemsToStow)
                }

                val parentItem = item.getParent()?.getItem()
                if (parentItem != null && parentItem.id == ITEM_GOLD_POUCH) {
                    totalItemsToStow += collectStowItems(item, parentItem, itemDict, totalItemsToStow, maxItemsToStow)
                }
            }

            val depotLocker = player.getDepotLocker(player.getLastDepotId())
            for (lockerItem in requestLockerItems(depotLocker, 0)) {
                if (isInsideDepot(item, true)) {
                    totalItemsToStow += collectStowItems(item, lockerItem, itemDict, totalItemsToStow, maxItemsToStow)
                }
            }
        } else {
            val container = item.getContainer()
            if (container != null) {
                for ((stowableItem, stowableCount) in getStowableItems(container)) {
                    if (totalItemsToStow >= maxItemsToStow) {
                        break
                    }
                    val stowableToAdd = min(stowableCount, maxItemsToStow - totalItemsToStow)
                    itemDict.add(stowableItem to stowableToAdd)
                    totalItemsToStow += stowableToAdd
                }
            } else {
                val stowableToAdd = min(count, maxItemsToStow - totalItemsToStow)
                if (stowableToAdd > 0) {
                    itemDict.add(item to stowableToAdd)
                    totalItemsToStow += stowableToAdd
                }
            }
        }

        if (itemDict.isEmpty()) {
            return
        }

        stashContainer(player, itemDict)
    }

    /** `Player::sendStowItems` (player.cpp:10283-10309). */
    private fun collectStowItems(
        item: Item,
        stowItem: Item,
        itemDict: MutableList<Pair<Item, Int>>,
        totalItemsToStow: Int,
        maxItemsToStow: Int,
    ): Int {
        var itemsAdded = 0
        if (stowItem.id == item.id) {
            val stowableToAdd = min(stowItem.getItemCount(), maxItemsToStow - totalItemsToStow)
            itemDict.add(stowItem to stowableToAdd)
            itemsAdded += stowableToAdd
        }

        val container = stowItem.getContainer() ?: return itemsAdded
        for ((stowableItem, stowableCount) in getStowableItems(container)) {
            if (totalItemsToStow + itemsAdded >= maxItemsToStow) {
                break
            }
            if (stowableItem.id != item.id) {
                continue
            }
            val stowableToAdd = min(stowableCount, maxItemsToStow - (totalItemsToStow + itemsAdded))
            itemDict.add(stowableItem to stowableToAdd)
            itemsAdded += stowableToAdd
        }
        return itemsAdded
    }

    /** `Container::getStowableItems` (container.cpp:162-176). */
    private fun getStowableItems(container: Container): List<Pair<Item, Int>> {
        val stowable = mutableListOf<Pair<Item, Int>>()
        val iterator = container.iterator()
        while (iterator.hasNext()) {
            val item = iterator.current()
            iterator.advance()
            if (item == null) {
                continue
            }
            val itemType = item.type ?: continue
            if (item.isItemStorable() && !itemType.isContainer) {
                stowable.add(item to item.getItemCount())
            }
        }
        return stowable
    }

    /**
     * `Player::stashContainer` (player.cpp:5238-5284): every collected entry is
     * moved through [processStashItem] and the stowed total drives the status
     * message (PARITY GAP: no protocol sink).
     */
    private fun stashContainer(player: Player, itemDict: List<Pair<Item, Int>>) {
        var totalStowed = 0
        for ((item, itemCount) in itemDict) {
            if (processStashItem(player, item, itemCount)) {
                totalStowed += itemCount
            }
        }
        if (totalStowed == 0) {
            return
        }
    }

    /**
     * `Player::processStashItem` (player.cpp:5181-5236): the storable guard,
     * the removal from the item's parent cylinder and the stash increment.
     *
     * PARITY GAP: the C++ `g_moveEvents().onPlayerDeEquip` veto for equipped
     * items stays with the Lua move-event layer.
     */
    private fun processStashItem(player: Player, item: Item, itemCount: Int): Boolean {
        if (!item.isItemStorable()) {
            return false
        }

        val parent = item.getParent()
        val success = if (parent == null) {
            // The Kotlin `Player` is not a `Cylinder`, so an inventory item has
            // no parent; `Player::removeItem` (player.cpp:10251) is the C++
            // `item->getHoldingPlayer() == selfPlayer` branch.
            player.removeItem(item, itemCount) == ReturnValue.NOERROR
        } else {
            parent.removeThing(item, itemCount)
            true
        }

        if (!success) {
            return false
        }

        player.stash.addItemOnStash(item.id, itemCount)
        if (player.isDepotSearchOpenOnItem(item.id)) {
            player.requestDepotSearchItem(item.id, 0)
        }
        return true
    }

    /**
     * `Player::requestLockerItems` (player.cpp:10933-10990) reduced to its
     * `itemVector` output: the breadth-first locker container walk with the
     * store-item/ware-id/market-attribute/tier filters.
     *
     * PARITY GAPS: the `lockerItems` ware-id map and the `sendToClient`
     * parameter are not modeled, and the
     * `ItemType::maxItems` container-vs-type guard is skipped.
     */
    private fun requestLockerItems(depotLocker: DepotLocker, tier: Int): List<Item> {
        val itemVector = mutableListOf<Item>()
        val containers = ArrayDeque<Container>()
        containers.addLast(depotLocker)

        while (containers.isNotEmpty()) {
            val container = containers.removeFirst()
            for (item in container.getItemList()) {
                val lockerContainer = item.getContainer()
                if (lockerContainer != null && lockerContainer.items.isNotEmpty()) {
                    containers.addLast(lockerContainer)
                    continue
                }

                val itemType = item.type ?: continue
                if (item.isStoreItem() || itemType.wareId == 0) {
                    continue
                }
                if (!item.hasMarketAttributes() || item.getTier() != tier) {
                    continue
                }
                itemVector.add(item)
            }
        }
        return itemVector
    }

    /**
     * `Item::isInsideDepot` (item.cpp:3561-3585): the item's own container or
     * its parent-container chain reaches a depot locker, depot chest or (when
     * [includeInbox]) an inbox.
     */
    private fun isInsideDepot(item: Item, includeInbox: Boolean): Boolean {
        val ownContainer = item.getContainer()
        if (ownContainer != null && isDepotContainer(ownContainer, includeInbox)) {
            return true
        }

        var container = item.getParent()?.getContainer() ?: return false
        while (true) {
            if (isDepotContainer(container, includeInbox)) {
                return true
            }
            container = container.getParent()?.getContainer() ?: return false
        }
    }

    private fun isDepotContainer(container: Container, includeInbox: Boolean): Boolean =
        container is DepotLocker || container is DepotChest || (includeInbox && container.isInbox())

    /**
     * `Game::internalGetThing` `STACKPOS_TOPDOWN_ITEM` branch (game.cpp:1164-1167)
     * plus the container/inventory positions (game.cpp:1197-1257): the tile
     * top-down item, an open container's indexed item, a depot-search item or
     * an inventory slot.
     */
    private fun resolveStowItem(player: Player, map: GameMap, position: Position, stackPosition: Int): Item? {
        if (position.x != 0xFFFF) {
            return map.getTile(position)?.getTopDownItem()
        }

        if ((position.y and 0x40) != 0) {
            val fromCid = position.y and 0x0F
            val parentContainer = player.getContainerByID(fromCid) ?: return null
            val containerIndex = player.getContainerIndex(fromCid) + position.z
            return parentContainer.getItemByIndex(containerIndex)
        }

        if (position.y == 0x20 || position.y == 0x21) {
            return null
        }

        if (position.y == 0 && position.z == 0) {
            return null
        }

        val slot = org.opentibia.canary.creatures.Slots_t.fromValue(position.y) ?: return null
        return player.getInventoryItem(slot)
    }

    /** `Position::areInRange<1, 1, 0>` (src/game/movement/position.hpp). */
    private fun areInRange1x1(a: Position, b: Position): Boolean =
        kotlin.math.abs(a.x - b.x) <= 1 && kotlin.math.abs(a.y - b.y) <= 1 && a.z == b.z

    /**
     * `ItemType::stackSize` default (`DEFAULT_STACK_SIZE`, src/items/items.hpp).
     */
    private const val DEFAULT_STACK_SIZE = 100

    /**
     * `Game::playerRequestDepotItems` (game.cpp:6920-6931). The
     * `sendCancelMessage(RETURNVALUE_YOUAREEXHAUSTED)` reply has no protocol
     * sink in this context (PARITY GAP).
     */
    fun applyOpenDepotSearch(player: Player) {
        if (!player.isDepotSearchAvailable()) {
            return
        }
        if (player.isUIExhausted(500)) {
            return
        }

        player.requestDepotItems()
        player.updateUIExhausted()
    }

    /**
     * `Game::playerRequestDepotSearchItem` (game.cpp:6945-6958). The
     * `sendCancelMessage(RETURNVALUE_YOUAREEXHAUSTED)` reply has no protocol
     * sink in this context (PARITY GAP).
     */
    fun applyDepotSearchItemRequest(player: Player, command: DepotSearchItemRequestCommand) {
        if (!player.isDepotSearchOpen()) {
            return
        }
        if (player.isUIExhausted(500)) {
            return
        }

        player.requestDepotSearchItem(command.itemId, command.tier)
        player.updateUIExhausted()
    }

    private fun readTier(
        buffer: ByteBuf,
        itemId: Int,
        upgradeClassification: (Int) -> Int,
    ): Int? = if (upgradeClassification(itemId) > 0) readByte(buffer) else 0

    private fun readByte(buffer: ByteBuf): Int? =
        if (buffer.readableBytes() < 1) null else buffer.readUnsignedByte().toInt()

    private fun readShort(buffer: ByteBuf): Int? =
        if (buffer.readableBytes() < 2) null else buffer.readUnsignedShortLE()

    private fun readInt(buffer: ByteBuf): Long? =
        if (buffer.readableBytes() < 4) null else buffer.readUnsignedIntLE()

    private fun readPosition(buffer: ByteBuf): Position? {
        if (buffer.readableBytes() < 5) return null
        val x = buffer.readUnsignedShortLE()
        val y = buffer.readUnsignedShortLE()
        val z = buffer.readUnsignedByte().toInt()
        return Position(x, y, z)
    }
}
