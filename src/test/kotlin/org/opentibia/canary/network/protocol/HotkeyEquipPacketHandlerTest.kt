package org.opentibia.canary.network.protocol

import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Decode tests for `ProtocolGame::parseHotkeyEquip` (client opcode `0x77`,
 * protocolgame.cpp:2129-2141): the `uint16_t` item id and, only when the item
 * type has `upgradeClassification > 0`, the `uint8_t` tier.
 */
class HotkeyEquipPacketHandlerTest {

    @Test
    fun `parses an item id without a tier byte`() {
        val payload = Unpooled.buffer()
        payload.writeShortLE(2160)

        val command = HotkeyEquipPacketHandler.parse(payload)

        assertThat(command).isEqualTo(HotkeyEquipCommand(itemId = 2160, hasTier = false, tier = 0))
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `parses an item id with its tier byte when the item upgrades`() {
        val payload = Unpooled.buffer()
        payload.writeShortLE(2160)
        payload.writeByte(2)

        val command = HotkeyEquipPacketHandler.parse(payload) { if (it == 2160) 1 else 0 }

        assertThat(command).isEqualTo(HotkeyEquipCommand(itemId = 2160, hasTier = true, tier = 2))
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `rejects a truncated item id`() {
        val payload = Unpooled.buffer()
        payload.writeByte(1)

        assertThat(HotkeyEquipPacketHandler.parse(payload)).isNull()
    }

    @Test
    fun `rejects a missing tier byte for an upgradeable item`() {
        val payload = Unpooled.buffer()
        payload.writeShortLE(2160)

        assertThat(HotkeyEquipPacketHandler.parse(payload) { 5 }).isNull()
    }
}
