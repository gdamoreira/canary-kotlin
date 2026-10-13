package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import org.opentibia.canary.map.Position

/**
 * Self-contained commands produced by the item-interaction client opcodes,
 * mirroring the Canary `ProtocolGame` / `Game` handlers in
 * src/server/network/protocol/protocolgame.cpp.
 */
sealed interface ItemInteractionCommand {
    val opcode: Int
}

/**
 * Use an item with a creature (client opcode `0x84`), mirroring
 * `ProtocolGame::parseUseWithCreature` (protocolgame.cpp:2576-2582): a
 * `Position`, the `uint16_t` item id, the source stack position byte and the
 * `uint32_t` target creature id handed to `Game::playerUseWithCreature`
 * (game.cpp:4853).
 */
data class UseWithCreatureCommand(
    val position: Position,
    val itemId: Int,
    val fromStackPosition: Int,
    val creatureId: Long,
) : ItemInteractionCommand {
    override val opcode: Int get() = ItemInteractionPacketHandler.OPCODE_USE_WITH_CREATURE
}

/**
 * Rotate an item (client opcode `0x85`), mirroring
 * `ProtocolGame::parseRotateItem` (protocolgame.cpp:2894-2904): a `Position`,
 * the `uint16_t` item id and the stack position byte.
 *
 * Canary picks `Game::playerRotatePodium` (game.cpp:11395) when
 * `Item::items[itemId].isPodium` and `Game::playerRotateItem` (game.cpp:5104)
 * otherwise; neither handler is ported, so the decode keeps the shared operands
 * only.
 */
data class RotateItemCommand(
    val position: Position,
    val itemId: Int,
    val stackPosition: Int,
) : ItemInteractionCommand {
    override val opcode: Int get() = ItemInteractionPacketHandler.OPCODE_ROTATE_ITEM
}

/**
 * Configure a show-off socket (client opcode `0x86`), mirroring
 * `ProtocolGame::parseConfigureShowOffSocket` (protocolgame.cpp:3445-3454): a
 * `Position`, the `uint16_t` item id and the stack position byte handed to
 * `Game::playerConfigureShowOffSocket` (game.cpp:5161).
 */
data class ConfigureShowOffSocketCommand(
    val position: Position,
    val itemId: Int,
    val stackPosition: Int,
) : ItemInteractionCommand {
    override val opcode: Int get() = ItemInteractionPacketHandler.OPCODE_CONFIGURE_SHOW_OFF_SOCKET
}

/**
 * Wrap a wrapable item (client opcode `0x8B`), mirroring
 * `ProtocolGame::parseWrapableItem` (protocolgame.cpp:2906-2911): a `Position`,
 * the `uint16_t` item id and the stack position byte handed to
 * `Game::playerWrapableItem` (game.cpp:5361).
 */
data class WrapableItemCommand(
    val position: Position,
    val itemId: Int,
    val stackPosition: Int,
) : ItemInteractionCommand {
    override val opcode: Int get() = ItemInteractionPacketHandler.OPCODE_WRAPABLE_ITEM
}

/**
 * Set the monster displayed on a podium (client opcode `0x9F`), mirroring
 * `ProtocolGame::parseSetMonsterPodium` (protocolgame.cpp:11491-11506): the
 * `uint32_t` monster race id (Canary casts it to `uint16_t`), a `Position`, the
 * `uint16_t` item id, the stack position byte, the direction byte and the raw
 * podium/monster visibility bytes, handed to `Game::playerSetMonsterPodium`
 * (game.cpp:11283).
 */
data class SetMonsterPodiumCommand(
    val monsterRaceId: Int,
    val position: Position,
    val itemId: Int,
    val stackPosition: Int,
    val direction: Int,
    val podiumVisible: Int,
    val monsterVisible: Int,
) : ItemInteractionCommand {
    override val opcode: Int get() = ItemInteractionPacketHandler.OPCODE_SET_MONSTER_PODIUM
}

/**
 * Decoder for the item-interaction client opcodes. Multi-byte fields are
 * Little-Endian, matching `NetworkMessage::get<T>()` on the native wire order.
 *
 * The current-profile read order is targeted; the `oldProtocol` early returns of
 * `parseConfigureShowOffSocket` (protocolgame.cpp:3446-3448) and
 * `parseSetMonsterPodium` (protocolgame.cpp:11492-11494) do not apply.
 *
 *  - `0x84` `ProtocolGame::parseUseWithCreature`        (protocolgame.cpp:2576)
 *  - `0x85` `ProtocolGame::parseRotateItem`             (protocolgame.cpp:2894)
 *  - `0x86` `ProtocolGame::parseConfigureShowOffSocket` (protocolgame.cpp:3445)
 *  - `0x8B` `ProtocolGame::parseWrapableItem`           (protocolgame.cpp:2906)
 *  - `0x9F` `ProtocolGame::parseSetMonsterPodium`       (protocolgame.cpp:11491)
 */
object ItemInteractionPacketHandler {

    const val OPCODE_USE_WITH_CREATURE: Int = 0x84
    const val OPCODE_ROTATE_ITEM: Int = 0x85
    const val OPCODE_CONFIGURE_SHOW_OFF_SOCKET: Int = 0x86
    const val OPCODE_WRAPABLE_ITEM: Int = 0x8B
    const val OPCODE_SET_MONSTER_PODIUM: Int = 0x9F

    fun parse(opcode: Int, buffer: ByteBuf): ItemInteractionCommand? = when (opcode) {
        OPCODE_USE_WITH_CREATURE -> parseUseWithCreature(buffer)
        OPCODE_ROTATE_ITEM -> parseRotateItem(buffer)
        OPCODE_CONFIGURE_SHOW_OFF_SOCKET -> parseConfigureShowOffSocket(buffer)
        OPCODE_WRAPABLE_ITEM -> parseWrapableItem(buffer)
        OPCODE_SET_MONSTER_PODIUM -> parseSetMonsterPodium(buffer)
        else -> null
    }

    /**
     * 0x84: reads the `Position`, the `uint16_t` item id, the source stack
     * position byte and the `uint32_t` creature id.
     *
     * @return the decoded command, or `null` when the payload is truncated.
     */
    fun parseUseWithCreature(buffer: ByteBuf): UseWithCreatureCommand? {
        val position = readPosition(buffer) ?: return null
        val itemId = readShort(buffer) ?: return null
        val fromStackPosition = readByte(buffer) ?: return null
        val creatureId = readInt(buffer) ?: return null
        return UseWithCreatureCommand(
            position = position,
            itemId = itemId,
            fromStackPosition = fromStackPosition,
            creatureId = creatureId,
        )
    }

    /**
     * 0x85: reads the `Position`, the `uint16_t` item id and the stack position
     * byte.
     *
     * @return the decoded command, or `null` when the payload is truncated.
     */
    fun parseRotateItem(buffer: ByteBuf): RotateItemCommand? {
        val position = readPosition(buffer) ?: return null
        val itemId = readShort(buffer) ?: return null
        val stackPosition = readByte(buffer) ?: return null
        return RotateItemCommand(position = position, itemId = itemId, stackPosition = stackPosition)
    }

    /**
     * 0x86: reads the `Position`, the `uint16_t` item id and the stack position
     * byte.
     *
     * @return the decoded command, or `null` when the payload is truncated.
     */
    fun parseConfigureShowOffSocket(buffer: ByteBuf): ConfigureShowOffSocketCommand? {
        val position = readPosition(buffer) ?: return null
        val itemId = readShort(buffer) ?: return null
        val stackPosition = readByte(buffer) ?: return null
        return ConfigureShowOffSocketCommand(
            position = position,
            itemId = itemId,
            stackPosition = stackPosition,
        )
    }

    /**
     * 0x8B: reads the `Position`, the `uint16_t` item id and the stack position
     * byte.
     *
     * @return the decoded command, or `null` when the payload is truncated.
     */
    fun parseWrapableItem(buffer: ByteBuf): WrapableItemCommand? {
        val position = readPosition(buffer) ?: return null
        val itemId = readShort(buffer) ?: return null
        val stackPosition = readByte(buffer) ?: return null
        return WrapableItemCommand(position = position, itemId = itemId, stackPosition = stackPosition)
    }

    /**
     * 0x9F: reads the `uint32_t` monster race id truncated to `uint16_t` (as
     * Canary's `static_cast<uint16_t>` does), the `Position`, the `uint16_t`
     * item id, the stack position byte, the direction byte and the raw podium
     * and monster visibility bytes.
     *
     * @return the decoded command, or `null` when the payload is truncated.
     */
    fun parseSetMonsterPodium(buffer: ByteBuf): SetMonsterPodiumCommand? {
        val monsterRaceId = (readInt(buffer) ?: return null).toInt() and 0xFFFF
        val position = readPosition(buffer) ?: return null
        val itemId = readShort(buffer) ?: return null
        val stackPosition = readByte(buffer) ?: return null
        val direction = readByte(buffer) ?: return null
        val podiumVisible = readByte(buffer) ?: return null
        val monsterVisible = readByte(buffer) ?: return null
        return SetMonsterPodiumCommand(
            monsterRaceId = monsterRaceId,
            position = position,
            itemId = itemId,
            stackPosition = stackPosition,
            direction = direction,
            podiumVisible = podiumVisible,
            monsterVisible = monsterVisible,
        )
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
