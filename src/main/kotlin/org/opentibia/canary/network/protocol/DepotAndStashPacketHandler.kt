package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.map.Position

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
     *
     * `0x28` (`Game::playerStowItem`/`playerStashWithdraw`), `0x29`
     * (`Game::playerRequestDepotSearchRetrieve`) and `0x95`
     * (`Game::playerRequestOpenContainerFromDepotSearch`) need the item-move
     * engines (`Player::addItemFromStash`, `stowItem`,
     * `retrieveAllItemsFromDepotSearch`, `openContainerFromDepotSearch`) and the
     * depot-search senders, which are not ported (PARITY GAP).
     */
    fun apply(player: Player, command: DepotAndStashCommand) {
        when (command) {
            OpenDepotSearchCommand -> applyOpenDepotSearch(player)
            is DepotSearchItemRequestCommand -> applyDepotSearchItemRequest(player, command)
            else -> Unit
        }
    }

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
