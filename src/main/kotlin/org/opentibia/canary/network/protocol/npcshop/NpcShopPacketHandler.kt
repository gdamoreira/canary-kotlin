package org.opentibia.canary.network.protocol.npcshop

import io.netty.buffer.ByteBuf

/**
 * Self-contained command produced by the NPC shop client opcodes, mirroring the
 * Canary `ProtocolGame` handlers (src/server/network/protocol/protocolgame.cpp).
 */
sealed interface NpcShopCommand {
    val opcode: Int
}

/**
 * Buy request (client opcode `0x7A`), mirroring
 * `ProtocolGame::parsePlayerBuyOnShop` (protocolgame.cpp:2812).
 *
 * `amount` is a `uint16_t` on the current profile and a single byte on the old
 * profile; `count` is the trade-window stack index, not a clamp candidate.
 */
data class PlayerBuyOnShopCommand(
    val itemId: Int,
    val count: Int,
    val amount: Int,
    val ignoreCap: Boolean,
    val inBackpacks: Boolean,
) : NpcShopCommand {
    override val opcode: Int get() = NpcShopPacketHandler.OPCODE_PLAYER_BUY_ON_SHOP
}

/**
 * Sell request (client opcode `0x7B`), mirroring
 * `ProtocolGame::parsePlayerSellOnShop` (protocolgame.cpp:2821).
 *
 * Canary clamps `count` with `std::max(msg.getByte(), 1)`, so a zero byte always
 * decodes as one.
 */
data class PlayerSellOnShopCommand(
    val itemId: Int,
    val count: Int,
    val amount: Int,
    val ignoreEquipped: Boolean,
) : NpcShopCommand {
    override val opcode: Int get() = NpcShopPacketHandler.OPCODE_PLAYER_SELL_ON_SHOP
}

/**
 * Close request (client opcode `0x7C`). Canary reads no payload and calls
 * `Game::playerCloseShop` (protocolgame.cpp:1806-1808, game.cpp:6319), which
 * closes the shop window through `Player::closeShopWindow`.
 */
object PlayerCloseShopCommand : NpcShopCommand {
    override val opcode: Int get() = NpcShopPacketHandler.OPCODE_PLAYER_CLOSE_SHOP
}

/**
 * Decoder for the NPC shop client opcodes. Multi-byte fields are Little-Endian,
 * matching `NetworkMessage::get<T>()` on the native wire order.
 *
 *  - `0x7A` `ProtocolGame::parsePlayerBuyOnShop`  (protocolgame.cpp:2812)
 *  - `0x7B` `ProtocolGame::parsePlayerSellOnShop` (protocolgame.cpp:2821)
 *  - `0x7C` `Game::playerCloseShop`               (protocolgame.cpp:1806)
 */
object NpcShopPacketHandler {

    const val OPCODE_PLAYER_BUY_ON_SHOP: Int = 0x7A
    const val OPCODE_PLAYER_SELL_ON_SHOP: Int = 0x7B
    const val OPCODE_PLAYER_CLOSE_SHOP: Int = 0x7C

    fun parse(opcode: Int, buffer: ByteBuf, oldProtocol: Boolean = false): NpcShopCommand? = when (opcode) {
        OPCODE_PLAYER_BUY_ON_SHOP -> parsePlayerBuyOnShop(buffer, oldProtocol)
        OPCODE_PLAYER_SELL_ON_SHOP -> parsePlayerSellOnShop(buffer, oldProtocol)
        OPCODE_PLAYER_CLOSE_SHOP -> parsePlayerCloseShop()
        else -> null
    }

    /**
     * 0x7A: reads the `uint16_t` item id, the byte `count`, the amount
     * (`uint16_t`, or one byte on the old profile) and the two flag bytes.
     *
     * @return the decoded command, or `null` when the payload is truncated.
     */
    fun parsePlayerBuyOnShop(buffer: ByteBuf, oldProtocol: Boolean = false): PlayerBuyOnShopCommand? {
        val itemId = readShort(buffer) ?: return null
        val count = readByte(buffer) ?: return null
        val amount = if (oldProtocol) readByte(buffer) ?: return null else readShort(buffer) ?: return null
        val ignoreCap = readBoolean(buffer) ?: return null
        val inBackpacks = readBoolean(buffer) ?: return null
        return PlayerBuyOnShopCommand(
            itemId = itemId,
            count = count,
            amount = amount,
            ignoreCap = ignoreCap,
            inBackpacks = inBackpacks,
        )
    }

    /**
     * 0x7B: reads the `uint16_t` item id, the byte `count` (clamped to at least
     * one, as Canary's `std::max` does), the amount (`uint16_t`, or one byte on
     * the old profile) and the ignore-equipped flag.
     *
     * @return the decoded command, or `null` when the payload is truncated.
     */
    fun parsePlayerSellOnShop(buffer: ByteBuf, oldProtocol: Boolean = false): PlayerSellOnShopCommand? {
        val itemId = readShort(buffer) ?: return null
        val count = maxOf(readByte(buffer) ?: return null, 1)
        val amount = if (oldProtocol) readByte(buffer) ?: return null else readShort(buffer) ?: return null
        val ignoreEquipped = readBoolean(buffer) ?: return null
        return PlayerSellOnShopCommand(
            itemId = itemId,
            count = count,
            amount = amount,
            ignoreEquipped = ignoreEquipped,
        )
    }

    /** 0x7C: no payload is read. */
    fun parsePlayerCloseShop(): PlayerCloseShopCommand = PlayerCloseShopCommand

    private fun readByte(buffer: ByteBuf): Int? =
        if (buffer.readableBytes() < 1) null else buffer.readUnsignedByte().toInt()

    private fun readShort(buffer: ByteBuf): Int? =
        if (buffer.readableBytes() < 2) null else buffer.readUnsignedShortLE()

    private fun readBoolean(buffer: ByteBuf): Boolean? =
        if (buffer.readableBytes() < 1) null else buffer.readUnsignedByte().toInt() != 0
}
