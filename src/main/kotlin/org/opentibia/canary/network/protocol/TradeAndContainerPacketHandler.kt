package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.items.Item
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.lua.runtime.LuaEventDispatcher
import org.opentibia.canary.map.Position

/**
 * Self-contained command produced by the player-to-player trade client opcodes,
 * mirroring the Canary `ProtocolGame` / `Game` handlers in
 * src/server/network/protocol/protocolgame.cpp.
 */
sealed interface TradeAndContainerCommand {
    val opcode: Int
}

/**
 * Trade request (client opcode `0x7D`), mirroring
 * `ProtocolGame::parseRequestTrade` (protocolgame.cpp:2830-2836): a `Position`,
 * the `uint16_t` item id, the stack position byte and the `uint32_t` id of the
 * player being invited to trade.
 */
data class RequestTradeCommand(
    val position: Position,
    val itemId: Int,
    val stackPosition: Int,
    val partnerPlayerId: Long,
) : TradeAndContainerCommand {
    override val opcode: Int get() = TradeAndContainerPacketHandler.OPCODE_REQUEST_TRADE
}

/**
 * Trade window inspection (client opcode `0x7E`), mirroring
 * `ProtocolGame::parseLookInTrade` (protocolgame.cpp:2838-2842): the first byte
 * is Canary's `counterOffer` flag (set only when it equals `0x01`) and the
 * second is the offer index.
 */
data class LookInTradeCommand(
    val counterOffer: Boolean,
    val index: Int,
) : TradeAndContainerCommand {
    override val opcode: Int get() = TradeAndContainerPacketHandler.OPCODE_LOOK_IN_TRADE
}

/**
 * Accept the current trade (client opcode `0x7F`). Canary reads no payload and
 * calls `Game::playerAcceptTrade` (protocolgame.cpp:1816).
 */
object AcceptTradeCommand : TradeAndContainerCommand {
    override val opcode: Int get() = TradeAndContainerPacketHandler.OPCODE_ACCEPT_TRADE
}

/**
 * Close/cancel the current trade (client opcode `0x80`). Canary reads no
 * payload and calls `Game::playerCloseTrade` (protocolgame.cpp:1819).
 */
object CloseTradeCommand : TradeAndContainerCommand {
    override val opcode: Int get() = TradeAndContainerPacketHandler.OPCODE_CLOSE_TRADE
}

/**
 * Container UI action (client opcode `0x75`), mirroring
 * `ProtocolGame::parseContainerAction` (protocolgame.cpp:3258-3268).
 *
 * Canary reads the leading `containerId` byte and the `action` byte and then
 * discards everything: three operand bytes for action 0, one for action 1 and
 * none for any other action. No game call is made at all.
 */
data class ContainerActionCommand(
    val containerId: Int,
    val action: Int,
    val operands: List<Int>,
) : TradeAndContainerCommand {
    override val opcode: Int get() = TradeAndContainerPacketHandler.OPCODE_CONTAINER_ACTION
}

/**
 * Close an open container (client opcode `0x87`), mirroring
 * `ProtocolGame::parseCloseContainer` (protocolgame.cpp:2584-2587) and
 * `Game::playerCloseContainer` (game.cpp:5028).
 */
data class CloseContainerCommand(
    val containerId: Int,
) : TradeAndContainerCommand {
    override val opcode: Int get() = TradeAndContainerPacketHandler.OPCODE_CLOSE_CONTAINER
}

/**
 * Move the view up one container level (client opcode `0x88`), mirroring
 * `ProtocolGame::parseUpArrowContainer` (protocolgame.cpp:2589-2592) and
 * `Game::playerMoveUpContainer` (game.cpp:5038).
 */
data class UpArrowContainerCommand(
    val containerId: Int,
) : TradeAndContainerCommand {
    override val opcode: Int get() = TradeAndContainerPacketHandler.OPCODE_UP_ARROW_CONTAINER
}

/**
 * Open a container found in the depot search (client opcode `0x95`), mirroring
 * `ProtocolGame::parseOpenParentContainer` (protocolgame.cpp:11052-11059):
 * the current profile reads a `Position`; the old profile reads nothing.
 */
data class OpenParentContainerCommand(
    val position: Position,
) : TradeAndContainerCommand {
    override val opcode: Int get() = TradeAndContainerPacketHandler.OPCODE_OPEN_PARENT_CONTAINER
}

/**
 * Decoder for the trade (`0x7D`-`0x80`) and container (`0x75`/`0x87`/`0x88`/
 * `0x95`) client opcodes. Multi-byte fields are Little-Endian, matching
 * `NetworkMessage::get<T>()` on the native wire order.
 *
 *  - `0x75` `ProtocolGame::parseContainerAction`     (protocolgame.cpp:3258)
 *  - `0x7D` `ProtocolGame::parseRequestTrade`        (protocolgame.cpp:2830)
 *  - `0x7E` `ProtocolGame::parseLookInTrade`         (protocolgame.cpp:2838)
 *  - `0x7F` `Game::playerAcceptTrade`                (protocolgame.cpp:1816)
 *  - `0x80` `Game::playerCloseTrade`                 (protocolgame.cpp:1819)
 *  - `0x87` `ProtocolGame::parseCloseContainer`      (protocolgame.cpp:2584)
 *  - `0x88` `ProtocolGame::parseUpArrowContainer`    (protocolgame.cpp:2589)
 *  - `0x95` `ProtocolGame::parseOpenParentContainer` (protocolgame.cpp:11052)
 */
object TradeAndContainerPacketHandler {

    const val OPCODE_CONTAINER_ACTION: Int = 0x75
    const val OPCODE_REQUEST_TRADE: Int = 0x7D
    const val OPCODE_LOOK_IN_TRADE: Int = 0x7E
    const val OPCODE_ACCEPT_TRADE: Int = 0x7F
    const val OPCODE_CLOSE_TRADE: Int = 0x80
    const val OPCODE_CLOSE_CONTAINER: Int = 0x87
    const val OPCODE_UP_ARROW_CONTAINER: Int = 0x88
    const val OPCODE_OPEN_PARENT_CONTAINER: Int = 0x95

    fun parse(opcode: Int, buffer: ByteBuf): TradeAndContainerCommand? = when (opcode) {
        OPCODE_CONTAINER_ACTION -> parseContainerAction(buffer)
        OPCODE_REQUEST_TRADE -> parseRequestTrade(buffer)
        OPCODE_LOOK_IN_TRADE -> parseLookInTrade(buffer)
        OPCODE_ACCEPT_TRADE -> AcceptTradeCommand
        OPCODE_CLOSE_TRADE -> CloseTradeCommand
        OPCODE_CLOSE_CONTAINER -> parseCloseContainer(buffer)
        OPCODE_UP_ARROW_CONTAINER -> parseUpArrowContainer(buffer)
        OPCODE_OPEN_PARENT_CONTAINER -> parseOpenParentContainer(buffer)
        else -> null
    }

    /**
     * 0x7D: reads a `Position` (x/y `uint16_t`, z byte), the `uint16_t` item
     * id, the stack position byte and the `uint32_t` partner player id.
     *
     * @return the decoded command, or `null` when the payload is truncated.
     */
    fun parseRequestTrade(buffer: ByteBuf): RequestTradeCommand? {
        val position = readPosition(buffer) ?: return null
        val itemId = readShort(buffer) ?: return null
        val stackPosition = readByte(buffer) ?: return null
        val partnerPlayerId = readInt(buffer) ?: return null
        return RequestTradeCommand(
            position = position,
            itemId = itemId,
            stackPosition = stackPosition,
            partnerPlayerId = partnerPlayerId,
        )
    }

    /**
     * 0x7E: reads the counter-offer byte (true only when it equals `0x01`) and
     * the offer index byte.
     *
     * @return the decoded command, or `null` when the payload is truncated.
     */
    fun parseLookInTrade(buffer: ByteBuf): LookInTradeCommand? {
        val flag = readByte(buffer) ?: return null
        val index = readByte(buffer) ?: return null
        return LookInTradeCommand(counterOffer = flag == 0x01, index = index)
    }

    /**
     * 0x75: reads the container id byte, the action byte and then the operands
     * Canary consumes for that action (three bytes for action 0, one byte for
     * action 1, none otherwise).
     *
     * @return the decoded command, or `null` when a consumed operand is missing.
     */
    fun parseContainerAction(buffer: ByteBuf): ContainerActionCommand? {
        val containerId = readByte(buffer) ?: return null
        val action = readByte(buffer) ?: return null
        val operandCount = when (action) {
            0 -> 3
            1 -> 1
            else -> 0
        }
        val operands = ArrayList<Int>(operandCount)
        repeat(operandCount) {
            operands.add(readByte(buffer) ?: return null)
        }
        return ContainerActionCommand(containerId = containerId, action = action, operands = operands)
    }

    /**
     * 0x87: reads the `uint8_t` container id.
     *
     * @return the decoded command, or `null` when the payload is truncated.
     */
    fun parseCloseContainer(buffer: ByteBuf): CloseContainerCommand? {
        val containerId = readByte(buffer) ?: return null
        return CloseContainerCommand(containerId = containerId)
    }

    /**
     * 0x88: reads the `uint8_t` container id.
     *
     * @return the decoded command, or `null` when the payload is truncated.
     */
    fun parseUpArrowContainer(buffer: ByteBuf): UpArrowContainerCommand? {
        val containerId = readByte(buffer) ?: return null
        return UpArrowContainerCommand(containerId = containerId)
    }

    /**
     * 0x95: reads a `Position` on the current profile. Canary's old-profile
     * early return (protocolgame.cpp:11053-11055) consumes nothing; the modern
     * profiles this port targets always carry the position.
     *
     * @return the decoded command, or `null` when the payload is truncated.
     */
    fun parseOpenParentContainer(buffer: ByteBuf): OpenParentContainerCommand? {
        val position = readPosition(buffer) ?: return null
        return OpenParentContainerCommand(position = position)
    }

    /**
     * `Game::playerRequestOpenContainerFromDepotSearch` (game.cpp:6975-6987):
     * the open-search and `isUIExhausted(500)` gates, then
     * `Player::openContainerFromDepotSearch` (player.cpp:10901) and
     * `Player::updateUIExhausted`.
     *
     * `Player::openContainerFromDepotSearch` resolves the searched item's
     * parent container and dispatches the container use through the Lua action
     * layer (`g_actions().useItem`, actions.cpp), matching
     * [ItemPacketHandler.handleUse]. PARITY GAPS: the `sendCancelMessage`
     * replies have no protocol sink in this context.
     */
    fun applyOpenParentContainer(
        player: Player,
        command: OpenParentContainerCommand,
        luaEnvironment: LuaEnvironment,
    ) {
        if (!player.isDepotSearchOpen()) {
            return
        }
        if (player.isUIExhausted(500)) {
            return
        }

        openContainerFromDepotSearch(player, command.position, luaEnvironment)
        player.updateUIExhausted()
    }

    /**
     * `Player::openContainerFromDepotSearch` (player.cpp:10901-10918).
     */
    private fun openContainerFromDepotSearch(player: Player, position: Position, luaEnvironment: LuaEnvironment) {
        val item = getItemFromDepotSearch(player, position) ?: return
        val container = item.getParent()?.getContainer() ?: return
        LuaEventDispatcher.actionOnUse(
            luaEnvironment,
            container.id,
            player,
            container,
            position,
            null,
            position,
            false,
        )
    }

    /**
     * `Player::getItemFromDepotSearch` (player.cpp:10920-10950): the locker
     * scan over the inbox (`pos.y == 0x21`) or depot (`pos.y == 0x20`)
     * containers, indexed by the matching items of the open search item and
     * tier.
     */
    fun getItemFromDepotSearch(player: Player, position: Position): Item? {
        val depotLocker = player.getDepotLocker(player.getLastDepotId())
        val itemId = player.getDepotSearchItemId()
        val tier = player.getDepotSearchItemTier()

        var index = 0
        for (locker in depotLocker.getItemList()) {
            val container = locker.getContainer() ?: continue
            if (container.empty() ||
                (container.isInbox() && position.y != 0x21) ||
                (!container.isInbox() && position.y != 0x20)
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
                if (item.id != itemId || item.getTier() != tier) {
                    continue
                }
                if (position.z == index) {
                    return item
                }
                index++
            }
        }
        return null
    }

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
