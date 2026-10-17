package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf

/**
 * Hotkey equip request (client opcode `0x77`), mirroring
 * `ProtocolGame::parseHotkeyEquip` (protocolgame.cpp:2129-2141): the
 * `uint16_t` item id and, only when the item type has
 * `upgradeClassification > 0`, the `uint8_t` tier byte.
 */
data class HotkeyEquipCommand(
    val itemId: Int,
    val hasTier: Boolean,
    val tier: Int,
)

/**
 * Decoder for `ProtocolGame::parseHotkeyEquip` (protocolgame.cpp:2129) over
 * `Game::playerEquipItem` (game.cpp:4159).
 */
object HotkeyEquipPacketHandler {

    /** Client opcode for `ProtocolGame::parseHotkeyEquip`. */
    const val OPCODE_HOTKEY_EQUIP: Int = 0x77

    /**
     * 0x77: reads the `uint16_t` item id and the conditional tier byte.
     *
     * @param upgradeClassification `Item::items[itemId].upgradeClassification`
     *   seam (protocolgame.cpp:2136); defaults to no classification.
     * @return the decoded command, or `null` when the payload is truncated.
     */
    fun parse(
        buffer: ByteBuf,
        upgradeClassification: (Int) -> Int = { 0 },
    ): HotkeyEquipCommand? {
        if (buffer.readableBytes() < 2) {
            return null
        }
        val itemId = buffer.readUnsignedShortLE()
        val hasTier = upgradeClassification(itemId) > 0
        if (!hasTier) {
            return HotkeyEquipCommand(itemId = itemId, hasTier = false, tier = 0)
        }
        if (buffer.readableBytes() < 1) {
            return null
        }
        return HotkeyEquipCommand(itemId = itemId, hasTier = true, tier = buffer.readUnsignedByte().toInt())
    }
}
