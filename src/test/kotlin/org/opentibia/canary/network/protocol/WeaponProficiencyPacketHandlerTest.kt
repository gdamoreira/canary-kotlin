package org.opentibia.canary.network.protocol

import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Decode tests for `0xB3` `ProtocolGame::parseWeaponProficiency`
 * (src/server/network/protocol/protocolgame.cpp:3130-3202), on the current
 * protocol profile (`CurrentPayload` + `OfficialWeaponProficiencyPayload`).
 */
class WeaponProficiencyPacketHandlerTest {

    @Test
    fun `decodes action 1 as the tracked weapons window request without more fields`() {
        val payload = Unpooled.buffer()
        payload.writeByte(0x01)
        payload.writeByte(0xAA)

        val command = WeaponProficiencyPacketHandler.parse(payload)

        assertThat(command).isEqualTo(WeaponProficiencyWindowsCommand)
        assertThat(payload.readableBytes()).isEqualTo(1)
    }

    @Test
    fun `decodes action 0 with the weapon id only`() {
        val payload = Unpooled.buffer()
        payload.writeByte(0x00)
        payload.writeShortLE(0x1234)

        val command = WeaponProficiencyPacketHandler.parse(payload)

        assertThat(command).isEqualTo(
            WeaponProficiencyActionCommand(action = 0x00, weaponId = 0x1234, selectedPerks = emptyList()),
        )
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `decodes action 2 with the weapon id only`() {
        val payload = Unpooled.buffer()
        payload.writeByte(0x02)
        payload.writeShortLE(0x00FF)

        val command = WeaponProficiencyPacketHandler.parse(payload)

        assertThat(command).isEqualTo(
            WeaponProficiencyActionCommand(action = 0x02, weaponId = 0x00FF, selectedPerks = emptyList()),
        )
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `decodes action 3 selected perks as level and perk index pairs`() {
        val payload = Unpooled.buffer()
        payload.writeByte(0x03)
        payload.writeShortLE(0x0102)
        payload.writeByte(2) // slot count
        payload.writeByte(1)
        payload.writeByte(10)
        payload.writeByte(3)
        payload.writeByte(11)

        val command = WeaponProficiencyPacketHandler.parse(payload)

        assertThat(command).isEqualTo(
            WeaponProficiencyActionCommand(
                action = 0x03,
                weaponId = 0x0102,
                selectedPerks = listOf(
                    SelectedPerkInput(level = 1, perkIndex = 10),
                    SelectedPerkInput(level = 3, perkIndex = 11),
                ),
            ),
        )
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `decodes action 4 consuming the two official payload bytes`() {
        val payload = Unpooled.buffer()
        payload.writeByte(0x04)
        payload.writeShortLE(0x0001)
        payload.writeByte(0xAB)
        payload.writeByte(0xCD)

        val command = WeaponProficiencyPacketHandler.parse(payload)

        assertThat(command).isEqualTo(
            WeaponProficiencyActionCommand(action = 0x04, weaponId = 0x0001, selectedPerks = emptyList()),
        )
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `decodes action 8 consuming the three official payload bytes`() {
        val payload = Unpooled.buffer()
        payload.writeByte(0x08)
        payload.writeShortLE(0x0002)
        payload.writeByte(0x01)
        payload.writeByte(0x02)
        payload.writeByte(0x03)

        val command = WeaponProficiencyPacketHandler.parse(payload)

        assertThat(command).isEqualTo(
            WeaponProficiencyActionCommand(action = 0x08, weaponId = 0x0002, selectedPerks = emptyList()),
        )
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `consumes only the available official payload bytes for action 9`() {
        val payload = Unpooled.buffer()
        payload.writeByte(0x09)
        payload.writeShortLE(0x0003)
        payload.writeByte(0xAA)

        val command = WeaponProficiencyPacketHandler.parse(payload)

        assertThat(command).isEqualTo(
            WeaponProficiencyActionCommand(action = 0x09, weaponId = 0x0003, selectedPerks = emptyList()),
        )
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `returns null when the weapon id is truncated`() {
        val payload = Unpooled.buffer()
        payload.writeByte(0x00)
        payload.writeByte(0x12)

        assertThat(WeaponProficiencyPacketHandler.parse(payload)).isNull()
        assertThat(WeaponProficiencyPacketHandler.parse(Unpooled.buffer())).isNull()
    }

    @Test
    fun `returns null when a selected perk pair is truncated`() {
        val payload = Unpooled.buffer()
        payload.writeByte(0x03)
        payload.writeShortLE(0x0102)
        payload.writeByte(2)
        payload.writeByte(1)
        payload.writeByte(10)
        payload.writeByte(3)

        assertThat(WeaponProficiencyPacketHandler.parse(payload)).isNull()
    }
}
