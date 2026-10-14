package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.Slots_t
import org.opentibia.canary.creatures.WeaponType_t
import org.opentibia.canary.game.QuickLootFilter_t
import org.opentibia.canary.game.zones.TileFlags
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.ITEM_CRYSTAL_COIN
import org.opentibia.canary.items.ITEM_GOLD_COIN
import org.opentibia.canary.items.ITEM_PLATINUM_COIN
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemAttribute
import org.opentibia.canary.items.ItemProperty
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.items.ItemTypes_t
import org.opentibia.canary.items.ObjectCategory_t
import org.opentibia.canary.items.SLOTP_ARMOR
import org.opentibia.canary.items.SLOTP_BACKPACK
import org.opentibia.canary.items.SLOTP_FEET
import org.opentibia.canary.items.SLOTP_HAND
import org.opentibia.canary.items.SLOTP_HEAD
import org.opentibia.canary.items.SLOTP_LEGS
import org.opentibia.canary.items.SLOTP_NECKLACE
import org.opentibia.canary.items.SLOTP_RING
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.systems.rewardchest.ReturnValue

/**
 * Self-contained commands produced by the quick-loot client opcodes
 * (`0x8F`-`0x91`), mirroring the Canary `ProtocolGame` handlers
 * (src/server/network/protocol/protocolgame.cpp).
 *
 * Category bytes carry Canary's `ObjectCategory_t` (src/enums/object_category.hpp)
 * and filter bytes its `QuickLootFilter_t` (src/game/game_definitions.hpp:38);
 * neither is validated by Canary, so the raw protocol values are preserved.
 */
sealed interface QuickLootCommand {
    val opcode: Int
}

/**
 * Loot the corpse at a map position (client opcode `0x8F`, variants `0`/`1`),
 * mirroring `ProtocolGame::parseQuickLoot` (protocolgame.cpp:2645-2651): a
 * `Position`, the `uint16_t` item id and the stack position byte; variant `1`
 * sets Canary's `lootAllCorpses`.
 */
data class QuickLootCorpseCommand(
    val position: Position,
    val itemId: Int,
    val stackPosition: Int,
    val lootAllCorpses: Boolean,
) : QuickLootCommand {
    override val opcode: Int get() = QuickLootPacketHandler.OPCODE_QUICK_LOOT
}

/**
 * Loot every nearby corpse (client opcode `0x8F`, variant `2`), mirroring
 * `Game::playerLootNearby` (protocolgame.cpp:2635-2638).
 */
object QuickLootNearbyCommand : QuickLootCommand {
    override val opcode: Int get() = QuickLootPacketHandler.OPCODE_QUICK_LOOT
}

/**
 * Unsupported quick-loot variant (client opcode `0x8F`, variant `> 2`). Canary
 * logs the variant and consumes nothing further (protocolgame.cpp:2640-2643).
 */
data class QuickLootUnsupportedVariantCommand(
    val variant: Int,
) : QuickLootCommand {
    override val opcode: Int get() = QuickLootPacketHandler.OPCODE_QUICK_LOOT
}

/**
 * Assign a managed container (client opcode `0x90`, actions `0`/`4`), mirroring
 * `Game::playerSetManagedContainer` (game.cpp:6822): the category byte, a
 * `Position`, the `uint16_t` item id and the stack position byte; `isLootContainer`
 * is Canary's flag (`true` for actions `0`-`2`, `false` for `4`-`6`).
 */
data class SetManagedContainerCommand(
    val category: Int,
    val position: Position,
    val itemId: Int,
    val stackPosition: Int,
    val isLootContainer: Boolean,
) : QuickLootCommand {
    override val opcode: Int get() = QuickLootPacketHandler.OPCODE_LOOT_CONTAINER
}

/**
 * Clear a managed container (client opcode `0x90`, actions `1`/`5`), mirroring
 * `Game::playerClearManagedContainer` (game.cpp:6867): the category byte and
 * Canary's `isLootContainer` flag.
 */
data class ClearManagedContainerCommand(
    val category: Int,
    val isLootContainer: Boolean,
) : QuickLootCommand {
    override val opcode: Int get() = QuickLootPacketHandler.OPCODE_LOOT_CONTAINER
}

/**
 * Open a managed container (client opcode `0x90`, actions `2`/`6`), mirroring
 * `Game::playerOpenManagedContainer` (game.cpp:6884): the category byte and
 * Canary's `isLootContainer` flag.
 */
data class OpenManagedContainerCommand(
    val category: Int,
    val isLootContainer: Boolean,
) : QuickLootCommand {
    override val opcode: Int get() = QuickLootPacketHandler.OPCODE_LOOT_CONTAINER
}

/**
 * Toggle the quick-loot fallback to the main container (client opcode `0x90`,
 * action `3`), mirroring `Game::playerSetQuickLootFallback` (game.cpp:6898):
 * one byte, true only when it equals `0x01`.
 */
data class SetQuickLootFallbackCommand(
    val useMainAsFallback: Boolean,
) : QuickLootCommand {
    override val opcode: Int get() = QuickLootPacketHandler.OPCODE_LOOT_CONTAINER
}

/**
 * Unknown loot-container action (client opcode `0x90`): Canary logs the action
 * and reads nothing further (protocolgame.cpp:2690).
 */
data class UnknownLootContainerActionCommand(
    val action: Int,
) : QuickLootCommand {
    override val opcode: Int get() = QuickLootPacketHandler.OPCODE_LOOT_CONTAINER
}

/**
 * Replace the quick-loot black/white list (client opcode `0x91`), mirroring
 * `ProtocolGame::parseQuickLootBlackWhitelist` (protocolgame.cpp:2693-2709):
 * the filter byte, a `uint16_t` item count and that many `uint16_t` item ids.
 */
data class QuickLootBlackWhitelistCommand(
    val filter: Int,
    val itemIds: List<Int>,
) : QuickLootCommand {
    override val opcode: Int get() = QuickLootPacketHandler.OPCODE_QUICK_LOOT_BLACK_WHITELIST
}

/**
 * Decoder for the quick-loot client opcodes. Multi-byte fields are Little-Endian,
 * matching `NetworkMessage::get<T>()` on the native wire order.
 *
 * Canary's `oldProtocol` early returns (protocolgame.cpp:2629-2631, 2655-2657,
 * 2694-2696) consume nothing; this port targets the current profile, which is
 * always the payload decoded here.
 *
 *  - `0x8F` `ProtocolGame::parseQuickLoot`                (protocolgame.cpp:2628)
 *  - `0x90` `ProtocolGame::parseLootContainer`            (protocolgame.cpp:2654)
 *  - `0x91` `ProtocolGame::parseQuickLootBlackWhitelist`  (protocolgame.cpp:2693)
 */
object QuickLootPacketHandler {

    const val OPCODE_QUICK_LOOT: Int = 0x8F
    const val OPCODE_LOOT_CONTAINER: Int = 0x90
    const val OPCODE_QUICK_LOOT_BLACK_WHITELIST: Int = 0x91

    fun parse(opcode: Int, buffer: ByteBuf): QuickLootCommand? = when (opcode) {
        OPCODE_QUICK_LOOT -> parseQuickLoot(buffer)
        OPCODE_LOOT_CONTAINER -> parseLootContainer(buffer)
        OPCODE_QUICK_LOOT_BLACK_WHITELIST -> parseQuickLootBlackWhitelist(buffer)
        else -> null
    }

    /**
     * 0x8F: reads the variant byte; `0`/`1` then read the loot target operands
     * and `2` decodes as a nearby-loot request.
     *
     * @return the decoded command, or `null` when the payload is truncated.
     */
    fun parseQuickLoot(buffer: ByteBuf): QuickLootCommand? {
        val variant = readByte(buffer) ?: return null

        if (variant == QUICK_LOOT_VARIANT_NEARBY) {
            return QuickLootNearbyCommand
        }
        if (variant > QUICK_LOOT_VARIANT_NEARBY) {
            return QuickLootUnsupportedVariantCommand(variant = variant)
        }

        val position = readPosition(buffer) ?: return null
        val itemId = readShort(buffer) ?: return null
        val stackPosition = readByte(buffer) ?: return null
        return QuickLootCorpseCommand(
            position = position,
            itemId = itemId,
            stackPosition = stackPosition,
            lootAllCorpses = variant == QUICK_LOOT_VARIANT_ALL_CORPSES,
        )
    }

    /**
     * 0x90: reads the action byte and the operands Canary consumes for it.
     *
     * @return the decoded command, or `null` when a consumed operand is missing.
     */
    fun parseLootContainer(buffer: ByteBuf): QuickLootCommand? {
        val action = readByte(buffer) ?: return null
        return when (action) {
            0, 4 -> {
                val category = readByte(buffer) ?: return null
                val position = readPosition(buffer) ?: return null
                val itemId = readShort(buffer) ?: return null
                val stackPosition = readByte(buffer) ?: return null
                SetManagedContainerCommand(
                    category = category,
                    position = position,
                    itemId = itemId,
                    stackPosition = stackPosition,
                    isLootContainer = action == 0,
                )
            }

            1, 5 -> {
                val category = readByte(buffer) ?: return null
                ClearManagedContainerCommand(category = category, isLootContainer = action == 1)
            }

            2, 6 -> {
                val category = readByte(buffer) ?: return null
                OpenManagedContainerCommand(category = category, isLootContainer = action == 2)
            }

            3 -> {
                val useMainAsFallback = readByte(buffer) ?: return null
                SetQuickLootFallbackCommand(useMainAsFallback = useMainAsFallback == 0x01)
            }

            else -> UnknownLootContainerActionCommand(action = action)
        }
    }

    /**
     * 0x91: reads the filter byte, the `uint16_t` list size and that many item
     * ids.
     *
     * @return the decoded command, or `null` when the payload is truncated.
     */
    fun parseQuickLootBlackWhitelist(buffer: ByteBuf): QuickLootBlackWhitelistCommand? {
        val filter = readByte(buffer) ?: return null
        val size = readShort(buffer) ?: return null

        val itemIds = ArrayList<Int>(size)
        repeat(size) {
            itemIds.add(readShort(buffer) ?: return null)
        }
        return QuickLootBlackWhitelistCommand(filter = filter, itemIds = itemIds)
    }

    /**
     * Applies the managed-container actions of `0x90` on the WorldCommit lane:
     *
     *  - action `0`/`4` `Game::playerSetManagedContainer` (game.cpp:6822)
     *    registers the container through `Player::refreshManagedContainer`;
     *  - action `1`/`5` `Game::playerClearManagedContainer` (game.cpp:6867)
     *    clears the category slot;
     *  - action `2`/`6` `Game::playerOpenManagedContainer` (game.cpp:6884)
     *    re-sends the registered container through `Player::sendContainer`.
     *
     * The corpse/nearby loot paths and the `0x91` list stay with the caller.
     * PARITY GAPS: the `sendCancelMessage`/`sendTextMessage`/`sendLootContainers`
     * replies have no protocol sink in this context, the C++
     * `container->getHoldingPlayer() != player` gate has no ported holding-player
     * model, and the `TOGGLE_GOLD_POUCH_ALLOW_ANYTHING` /
     * `TOGGLE_GOLD_POUCH_QUICKLOOT_ONLY` config defaults are modelled as false
     * parameters.
     */
    fun apply(player: Player, command: QuickLootCommand) {
        when (command) {
            is SetManagedContainerCommand -> applySetManagedContainer(player, command)
            is ClearManagedContainerCommand -> applyClearManagedContainer(player, command)
            is OpenManagedContainerCommand -> applyOpenManagedContainer(player, command)
            else -> Unit
        }
    }

    /**
     * `Game::playerSetManagedContainer` (game.cpp:6822-6865).
     */
    fun applySetManagedContainer(
        player: Player,
        command: SetManagedContainerCommand,
        allowGoldPouchAnything: Boolean = false,
        allowGoldPouchQuickLootOnly: Boolean = false,
    ) {
        if (command.position.x != 0xFFFF) {
            return
        }

        val category = categoryOf(command.category) ?: return
        val item = resolveUseItem(player, command.position) ?: return
        val container = item.getContainer() ?: return

        val allowConfig = allowGoldPouchAnything || allowGoldPouchQuickLootOnly
        if (container.id == Container.ITEM_GOLD_POUCH &&
            category != ObjectCategory_t.OBJECTCATEGORY_GOLD &&
            !allowConfig
        ) {
            return
        }

        if (container.id == Container.ITEM_GOLD_POUCH && !command.isLootContainer) {
            return
        }

        player.refreshManagedContainer(category, container, command.isLootContainer)
    }

    /**
     * `Game::playerClearManagedContainer` (game.cpp:6867-6882).
     */
    fun applyClearManagedContainer(player: Player, command: ClearManagedContainerCommand) {
        val category = categoryOf(command.category) ?: return
        player.refreshManagedContainer(category, null, command.isLootContainer)
    }

    /**
     * `Game::playerOpenManagedContainer` (game.cpp:6884-6894): the container id
     * is sent as a single byte (`static_cast<uint8_t>(container->getID())`).
     */
    fun applyOpenManagedContainer(player: Player, command: OpenManagedContainerCommand) {
        val category = categoryOf(command.category) ?: return
        val container = player.getManagedContainer(category, command.isLootContainer) ?: return
        player.sendContainer(container.id and 0xFF, container, container.hasParent(), 0)
    }

    /** `QUICK_LOOT_MAX_CORPSES` config default (configmanager.cpp:301). */
    const val QUICK_LOOT_MAX_CORPSES_DEFAULT: Int = 30

    /** `AUTOBANK` config default (configmanager.cpp:85). */
    const val AUTOBANK_DEFAULT: Boolean = false

    /**
     * Applies the quick-loot loot paths of `0x8F` on the WorldCommit lane:
     *
     *  - [QuickLootCorpseCommand] `Game::playerQuickLoot` (game.cpp:6428): the
     *    range/premium gates, the `STACKPOS_FIND_THING` item resolution, the
     *    corpse resolution and either the managed-container collection
     *    (`pos.x == 0xFFFF`, `Game::internalCollectManagedItems`, game.cpp:3850)
     *    or `Game::playerQuickLootCorpse` (game.cpp:3596).
     *  - [QuickLootNearbyCommand] `Game::playerLootNearby` (game.cpp:6766): the
     *    3x3 neighbor corpse scan.
     *
     * PARITY GAPS: the C++ walk-to-corpse path, `Player::canDoAction`
     * (`PlayerActionTimers` is not composed into the port's `Player`),
     * reward corpses (`Player::getReward`), the
     * `Player::canOpenCorpse` party/guild check, `processLootItems`' nested
     * container/fallback search, every loot summary `sendTextMessage`,
     * `Player::sendLootStats` and `Container::clearLootHighlight`/
     * `sendUpdateToClient` have no ported equivalent.
     */
    fun applyQuickLoot(
        player: Player,
        command: QuickLootCommand,
        map: GameMap,
        autobank: Boolean = AUTOBANK_DEFAULT,
        maxCorpses: Int = QUICK_LOOT_MAX_CORPSES_DEFAULT,
        addBankBalance: (Player, Long) -> Unit = { _, _ -> },
    ) {
        when (command) {
            is QuickLootCorpseCommand -> applyQuickLootCorpse(player, command, map, autobank, maxCorpses, addBankBalance)
            QuickLootNearbyCommand -> applyLootNearby(player, map, autobank, maxCorpses, addBankBalance)
            else -> Unit
        }
    }

    /**
     * `Game::playerQuickLoot` (game.cpp:6428-6570) for the decoded
     * [QuickLootCorpseCommand]: the position gates, the corpse resolution and
     * the loot dispatch.
     */
    fun applyQuickLootCorpse(
        player: Player,
        command: QuickLootCorpseCommand,
        map: GameMap,
        autobank: Boolean = AUTOBANK_DEFAULT,
        maxCorpses: Int = QUICK_LOOT_MAX_CORPSES_DEFAULT,
        addBankBalance: (Player, Long) -> Unit = { _, _ -> },
    ) {
        if (command.position.x != 0xFFFF) {
            if (!areInRange1x1(command.position, player.position)) {
                return
            }
        } else if (!player.isPremium()) {
            return
        }

        val item = if (command.position.x == 0xFFFF) {
            resolveUseItem(player, command.position) ?: return
        } else {
            findThing(player, map, command.position, command.stackPosition) ?: return
        }

        if (item.getParent() == null) {
            return
        }

        var browseField = false
        val corpse = if (command.position.x == 0xFFFF) {
            val parentContainer = item.getParent()?.getContainer() ?: return
            if (parentContainer.id == Container.ITEM_BROWSEFIELD) {
                browseField = true
                item.getContainer() ?: return
            } else {
                parentContainer
            }
        } else {
            item.getContainer() ?: return
        }

        if (corpse.hasAttribute(ItemAttribute.UNIQUEID) || corpse.hasAttribute(ItemAttribute.ACTIONID)) {
            return
        }

        if (!isLootableCorpseOwner(player, corpse)) {
            return
        }

        if (command.position.x == 0xFFFF && !browseField) {
            internalCollectManagedItems(player, item, getObjectCategory(item), autobank, addBankBalance)
        } else if (!command.lootAllCorpses) {
            playerQuickLootCorpse(player, corpse, autobank, addBankBalance)
        } else {
            playerLootAllCorpses(player, map, command.position, maxCorpses, autobank, addBankBalance)
        }
    }

    /**
     * `Game::playerLootAllCorpses` (game.cpp:6572-6620): every lootable corpse
     * on the target tile, capped by `QUICK_LOOT_MAX_CORPSES`.
     */
    fun playerLootAllCorpses(
        player: Player,
        map: GameMap,
        position: Position,
        maxCorpses: Int = QUICK_LOOT_MAX_CORPSES_DEFAULT,
        autobank: Boolean = AUTOBANK_DEFAULT,
        addBankBalance: (Player, Long) -> Unit = { _, _ -> },
    ) {
        val tile = map.getTile(position) ?: return
        var corpses = 0
        for (tileItem in tile.items) {
            val tileCorpse = tileItem.getContainer() ?: continue
            if (!tileCorpse.isCorpse() ||
                tileCorpse.hasAttribute(ItemAttribute.UNIQUEID) ||
                tileCorpse.hasAttribute(ItemAttribute.ACTIONID)
            ) {
                continue
            }
            if (!isLootableCorpseOwner(player, tileCorpse)) {
                continue
            }

            corpses++
            playerQuickLootCorpse(player, tileCorpse, autobank, addBankBalance)
            if (corpses >= maxCorpses) {
                break
            }
        }
    }

    /**
     * `Game::playerLootNearby` (game.cpp:6766-6820): the 3x3 tile scan around
     * the player, looting each lootable corpse up to the
     * `QUICK_LOOT_MAX_CORPSES` cap.
     *
     * PARITY GAP: the C++ loot snapshot comparison that distinguishes a corpse
     * that was found from one that was looted is approximated by counting every
     * corpse the collection moved items out of.
     */
    fun applyLootNearby(
        player: Player,
        map: GameMap,
        autobank: Boolean = AUTOBANK_DEFAULT,
        maxCorpses: Int = QUICK_LOOT_MAX_CORPSES_DEFAULT,
        addBankBalance: (Player, Long) -> Unit = { _, _ -> },
    ) {
        val playerPosition = player.position
        var looted = 0
        for (x in -1..1) {
            for (y in -1..1) {
                val position = Position(playerPosition.x + x, playerPosition.y + y, playerPosition.z)
                val tile = map.getTile(position) ?: continue
                for (tileItem in tile.items) {
                    val corpse = tileItem.getContainer() ?: continue
                    if (!corpse.isCorpse() ||
                        corpse.hasAttribute(ItemAttribute.UNIQUEID) ||
                        corpse.hasAttribute(ItemAttribute.ACTIONID)
                    ) {
                        continue
                    }
                    if (!isLootableCorpseOwner(player, corpse)) {
                        continue
                    }

                    if (playerQuickLootCorpse(player, corpse, autobank, addBankBalance) > 0) {
                        looted++
                    }
                    if (looted >= maxCorpses) {
                        return
                    }
                }
            }
        }
    }

    /**
     * `Game::playerQuickLootCorpse` (game.cpp:3596-3755): the quick-loot list
     * filter, the per-item managed-container collection and the corpse loot
     * highlight reset.
     *
     * @return the number of items the collection moved.
     */
    fun playerQuickLootCorpse(
        player: Player,
        corpse: Container,
        autobank: Boolean = AUTOBANK_DEFAULT,
        addBankBalance: (Player, Long) -> Unit = { _, _ -> },
    ): Int {
        val itemList = mutableListOf<Item>()
        val ignoreListItems = player.quickLootFilter == QuickLootFilter_t.QUICKLOOTFILTER_SKIPPEDLOOT
        val iterator = corpse.iterator()
        while (iterator.hasNext()) {
            val item = iterator.current()
            iterator.advance()
            if (item == null) {
                continue
            }
            val listed = player.quickLootListItemIds.contains(item.id)
            if ((listed && ignoreListItems) || (!listed && !ignoreListItems)) {
                continue
            }
            itemList.add(item)
        }

        var moved = 0
        for (item in itemList) {
            if (internalCollectManagedItems(player, item, getObjectCategory(item), autobank, addBankBalance) == ReturnValue.NOERROR) {
                moved++
            }
        }

        // Container::clearLootHighlight (container.cpp) has no ported client sink.
        return moved
    }

    /**
     * `Game::internalCollectManagedItems` (game.cpp:3850-3893): the AUTOBANK
     * coin deposit, the quick-loot list filter, the managed loot container
     * resolution and the item move.
     */
    fun internalCollectManagedItems(
        player: Player,
        item: Item,
        category: ObjectCategory_t,
        autobank: Boolean = AUTOBANK_DEFAULT,
        addBankBalance: (Player, Long) -> Unit = { _, _ -> },
    ): ReturnValue {
        if (autobank &&
            (item.id == ITEM_GOLD_COIN || item.id == ITEM_PLATINUM_COIN || item.id == ITEM_CRYSTAL_COIN)
        ) {
            val money = when (item.id) {
                ITEM_PLATINUM_COIN -> item.getItemCount().toLong() * 100
                ITEM_CRYSTAL_COIN -> item.getItemCount().toLong() * 10000
                else -> item.getItemCount().toLong()
            }
            val parent = item.getParent() ?: return ReturnValue.NOTPOSSIBLE
            parent.removeThing(item, item.getItemCount())
            addBankBalance(player, money)
            return ReturnValue.NOERROR
        }

        if (player.quickLootListItemIds.isNotEmpty()) {
            val isInList = player.quickLootListItemIds.contains(item.id)
            if (player.quickLootFilter == QuickLootFilter_t.QUICKLOOTFILTER_ACCEPTEDLOOT && !isInList) {
                return ReturnValue.NOTPOSSIBLE
            }
            if (player.quickLootFilter == QuickLootFilter_t.QUICKLOOTFILTER_SKIPPEDLOOT && isInList) {
                return ReturnValue.NOTPOSSIBLE
            }
        }

        val lootContainer = findManagedContainer(player, category, true) ?: return ReturnValue.NOTPOSSIBLE
        return processLootItems(player, lootContainer, item)
    }

    /**
     * `Game::findManagedContainer` (game.cpp:3757-3774): the category's managed
     * container, falling back to the main backpack when the quick-loot fallback
     * is enabled. The `sendInventoryItem` refresh has no protocol sink here.
     */
    private fun findManagedContainer(
        player: Player,
        category: ObjectCategory_t,
        isLootContainer: Boolean,
    ): Container? {
        player.getManagedContainer(category, isLootContainer)?.let { return it }
        if (!player.quickLootFallbackToMainContainer) {
            return null
        }

        val mainBackpack = player.getBackpack() ?: return null
        player.refreshManagedContainer(ObjectCategory_t.OBJECTCATEGORY_DEFAULT, mainBackpack, isLootContainer)
        return mainBackpack
    }

    /**
     * `Game::processLootItems` (game.cpp:3820-3848) reduced to the target
     * container move; the C++ `findNextAvailableContainer` nested-container
     * search and `handleFallbackLogic` stay unported (PARITY GAP).
     */
    private fun processLootItems(player: Player, lootContainer: Container, item: Item): ReturnValue {
        if (lootContainer.capacity <= lootContainer.size()) {
            return ReturnValue.CONTAINERNOTENOUGHROOM
        }

        val parent = item.getParent() ?: return ReturnValue.NOTPOSSIBLE
        parent.removeThing(item, item.getItemCount())
        lootContainer.addItem(item)
        return ReturnValue.NOERROR
    }

    /**
     * `Game::getObjectCategory` (game.cpp:3946-4025): a coin's worth makes the
     * item gold; every other item maps through `ItemType::weaponType`,
     * `ItemType::slotPosition` and `ItemType::type`.
     */
    fun getObjectCategory(item: Item): ObjectCategory_t {
        if (item.getWorth() != 0L) {
            return ObjectCategory_t.OBJECTCATEGORY_GOLD
        }
        val itemType = item.type ?: return ObjectCategory_t.OBJECTCATEGORY_OTHERS
        return getObjectCategory(itemType)
    }

    /** `Game::getObjectCategory(const ItemType &)` (game.cpp:3962-4025). */
    fun getObjectCategory(itemType: ItemType): ObjectCategory_t {
        if (itemType.weaponType != WeaponType_t.WEAPON_NONE) {
            return when (itemType.weaponType) {
                WeaponType_t.WEAPON_FIST -> ObjectCategory_t.OBJECTCATEGORY_FISTWEAPONS
                WeaponType_t.WEAPON_SWORD -> ObjectCategory_t.OBJECTCATEGORY_SWORDS
                WeaponType_t.WEAPON_CLUB -> ObjectCategory_t.OBJECTCATEGORY_CLUBS
                WeaponType_t.WEAPON_AXE -> ObjectCategory_t.OBJECTCATEGORY_AXES
                WeaponType_t.WEAPON_SHIELD -> ObjectCategory_t.OBJECTCATEGORY_SHIELDS
                WeaponType_t.WEAPON_MISSILE, WeaponType_t.WEAPON_DISTANCE -> ObjectCategory_t.OBJECTCATEGORY_DISTANCEWEAPONS
                WeaponType_t.WEAPON_WAND -> ObjectCategory_t.OBJECTCATEGORY_WANDS
                WeaponType_t.WEAPON_AMMO -> ObjectCategory_t.OBJECTCATEGORY_AMMO
                else -> ObjectCategory_t.OBJECTCATEGORY_DEFAULT
            }
        }

        if (itemType.slotPosition != SLOTP_HAND) {
            return when {
                itemType.slotPosition and SLOTP_HEAD != 0 -> ObjectCategory_t.OBJECTCATEGORY_HELMETS
                itemType.slotPosition and SLOTP_NECKLACE != 0 -> ObjectCategory_t.OBJECTCATEGORY_NECKLACES
                itemType.slotPosition and SLOTP_BACKPACK != 0 -> ObjectCategory_t.OBJECTCATEGORY_CONTAINERS
                itemType.slotPosition and SLOTP_ARMOR != 0 -> ObjectCategory_t.OBJECTCATEGORY_ARMORS
                itemType.slotPosition and SLOTP_LEGS != 0 -> ObjectCategory_t.OBJECTCATEGORY_LEGS
                itemType.slotPosition and SLOTP_FEET != 0 -> ObjectCategory_t.OBJECTCATEGORY_BOOTS
                itemType.slotPosition and SLOTP_RING != 0 -> ObjectCategory_t.OBJECTCATEGORY_RINGS
                else -> ObjectCategory_t.OBJECTCATEGORY_OTHERS
            }
        }

        return when (itemType.type) {
            ItemTypes_t.ITEM_TYPE_RUNE -> ObjectCategory_t.OBJECTCATEGORY_RUNES
            ItemTypes_t.ITEM_TYPE_CREATUREPRODUCT -> ObjectCategory_t.OBJECTCATEGORY_CREATUREPRODUCTS
            ItemTypes_t.ITEM_TYPE_FOOD -> ObjectCategory_t.OBJECTCATEGORY_FOOD
            ItemTypes_t.ITEM_TYPE_VALUABLE -> ObjectCategory_t.OBJECTCATEGORY_VALUABLES
            ItemTypes_t.ITEM_TYPE_POTION -> ObjectCategory_t.OBJECTCATEGORY_POTIONS
            else -> ObjectCategory_t.OBJECTCATEGORY_OTHERS
        }
    }

    /**
     * The `Player::canOpenCorpse(corpseOwner)` gate of `playerQuickLootCorpse`
     * (player.cpp) reduced to the owner identity check; the C++ party/guild
     * access (`Player::isPartner`) is not reachable from this context
     * (PARITY GAP).
     */
    private fun isLootableCorpseOwner(player: Player, corpse: Container): Boolean {
        val owner = (corpse.getAttribute(ItemAttribute.CORPSEOWNER) as? Number)?.toLong() ?: 0L
        return owner == 0L || owner == player.id
    }

    /**
     * `Game::internalGetThing` `STACKPOS_FIND_THING` branch (game.cpp:1170-1184)
     * plus the support-hangable veto (game.cpp:1191-1203): the use item first,
     * then the door item, then the top-down item.
     */
    private fun findThing(player: Player, map: GameMap, position: Position, index: Int): Item? {
        val tile = map.getTile(position) ?: return null
        var item = tile.getUseItem(index)?.getItem()
        if (item == null) {
            item = tile.getDoorItem()
        }
        if (item == null) {
            item = tile.getTopDownItem()
        }

        if (item != null && tile.hasFlag(TileFlags.TILESTATE_SUPPORTS_HANGABLE.value)) {
            if (tile.hasProperty(ItemProperty.CONST_PROP_ISVERTICAL)) {
                if (player.position.x + 1 == position.x && item.isHangable()) {
                    return null
                }
            } else if (player.position.y + 1 == position.y && item.isHangable()) {
                return null
            }
        }
        return item
    }

    /** `Position::areInRange<1, 1, 0>` (src/game/movement/position.hpp). */
    private fun areInRange1x1(a: Position, b: Position): Boolean =
        kotlin.math.abs(a.x - b.x) <= 1 && kotlin.math.abs(a.y - b.y) <= 1 && a.z == b.z

    /**
     * `Game::internalGetThing` `STACKPOS_USEITEM` branch for `pos.x == 0xFFFF`
     * (game.cpp:1205-1263): an open-container position (`pos.y & 0x40`) resolves
     * `container->getItemByIndex(player->getContainerIndex(cid) + pos.z)`, the
     * `0x20`/`0x21` depot-search positions need the unported depot-search item
     * lookup, and every other `pos.y` is an inventory slot.
     */
    private fun resolveUseItem(player: Player, position: Position): Item? {
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

        val slot = Slots_t.fromValue(position.y) ?: return null
        return player.getInventoryItem(slot)
    }

    private fun categoryOf(value: Int): ObjectCategory_t? =
        ObjectCategory_t.entries.firstOrNull { it.value == value }

    /** `parseQuickLoot` variant `2` (`Game::playerLootNearby`). */
    private const val QUICK_LOOT_VARIANT_NEARBY = 2

    /** `parseQuickLoot` variant `1` (`lootAllCorpses`). */
    private const val QUICK_LOOT_VARIANT_ALL_CORPSES = 1

    private fun readByte(buffer: ByteBuf): Int? =
        if (buffer.readableBytes() < 1) null else buffer.readUnsignedByte().toInt()

    private fun readShort(buffer: ByteBuf): Int? =
        if (buffer.readableBytes() < 2) null else buffer.readUnsignedShortLE()

    private fun readPosition(buffer: ByteBuf): Position? {
        if (buffer.readableBytes() < 5) return null
        val x = buffer.readUnsignedShortLE()
        val y = buffer.readUnsignedShortLE()
        val z = buffer.readUnsignedByte().toInt()
        return Position(x, y, z)
    }
}
