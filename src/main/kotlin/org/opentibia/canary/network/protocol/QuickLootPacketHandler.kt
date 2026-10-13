package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.Slots_t
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ObjectCategory_t
import org.opentibia.canary.map.Position

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
