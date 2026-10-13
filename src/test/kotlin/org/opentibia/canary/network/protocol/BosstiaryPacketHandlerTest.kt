package org.opentibia.canary.network.protocol

import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Decode tests for `0xAE` `ProtocolGame::parseSendBosstiary` (protocolgame.cpp:11206),
 * `0xAF` `ProtocolGame::parseSendBosstiarySlots` (protocolgame.cpp:11248) and `0xB0`
 * `ProtocolGame::parseBosstiarySlot` (protocolgame.cpp:11383).
 */
class BosstiaryPacketHandlerTest {

    @Test
    fun `decodes send bosstiary 0xAE without consuming bytes`() {
        val payload = Unpooled.buffer()

        val command = BosstiaryPacketHandler.parse(BosstiaryPacketHandler.OPCODE_SEND_BOSSTIARY, payload)

        assertThat(command).isEqualTo(SendBosstiaryCommand)
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `decodes send bosstiary slots 0xAF without consuming bytes`() {
        val payload = Unpooled.buffer()

        val command = BosstiaryPacketHandler.parse(BosstiaryPacketHandler.OPCODE_SEND_BOSSTIARY_SLOTS, payload)

        assertThat(command).isEqualTo(SendBosstiarySlotsCommand)
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `decodes bosstiary slot 0xB0 as slot byte plus u32 boss id`() {
        val payload = Unpooled.buffer()
        payload.writeByte(2)
        payload.writeIntLE(0x0A0B0C0D)

        val command = BosstiaryPacketHandler.parse(BosstiaryPacketHandler.OPCODE_BOSSTIARY_SLOT, payload)

        assertThat(command).isEqualTo(BosstiarySlotCommand(slot = 2, selectedBossId = 0x0A0B0C0DL))
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `returns null for an unknown bosstiary opcode`() {
        assertThat(BosstiaryPacketHandler.parse(0x1D, Unpooled.buffer())).isNull()
    }

    @Test
    fun `returns null when the bosstiary slot payload is truncated`() {
        val payload = Unpooled.buffer()
        payload.writeByte(1)
        payload.writeByte(0xAB)

        assertThat(BosstiaryPacketHandler.parse(BosstiaryPacketHandler.OPCODE_BOSSTIARY_SLOT, payload)).isNull()

        assertThat(BosstiaryPacketHandler.parse(BosstiaryPacketHandler.OPCODE_BOSSTIARY_SLOT, Unpooled.buffer()))
            .isNull()
    }
}
