package org.opentibia.canary.network.protocol.wheel

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.systems.wheel.WheelFragmentType
import org.opentibia.canary.systems.wheel.WheelGemAffinity
import org.opentibia.canary.systems.wheel.WheelGemQuality
import org.opentibia.canary.systems.wheel.WheelSlots

class WheelPacketParserTest {

    @Test
    fun `parses open wheel packet 0x61 owner id as little endian uint32`() {
        val payload = Unpooled.buffer()
        payload.writeIntLE(0x01020304)

        val command = WheelPacketParser.parseOpenWheel(payload)

        assertThat(command).isEqualTo(OpenWheelCommand(0x01020304L))
        assertThat(command?.opcode).isEqualTo(0x61)
    }

    @Test
    fun `parses save wheel packet 0x62 slot points in canary slot order`() {
        val payload = Unpooled.buffer()
        for (i in WheelSlots.entries.indices) {
            payload.writeShortLE(1000 + i)
        }
        writeVessel(payload, hasGem = false, gemIndex = 0)
        writeVessel(payload, hasGem = false, gemIndex = 0)
        writeVessel(payload, hasGem = false, gemIndex = 0)
        writeVessel(payload, hasGem = false, gemIndex = 0)

        val command = WheelPacketParser.parseSaveWheel(payload)

        assertThat(command).isNotNull
        assertThat(command!!.opcode).isEqualTo(0x62)
        assertThat(command.slotPoints).hasSize(WheelSlots.entries.size)
        assertThat(command.slotPoints.first().slot).isEqualTo(WheelSlots.SLOT_GREEN_200)
        assertThat(command.slotPoints.first().points).isEqualTo(1000)
        assertThat(command.slotPoints.last().slot).isEqualTo(WheelSlots.SLOT_PURPLE_200)
        assertThat(command.slotPoints.last().points).isEqualTo(1000 + WheelSlots.entries.size - 1)
    }

    @Test
    fun `parses save wheel packet 0x62 gem vessels as has-gem byte plus little endian uint16 index`() {
        val payload = Unpooled.buffer()
        repeat(WheelSlots.entries.size) { payload.writeShortLE(0) }
        writeVessel(payload, hasGem = true, gemIndex = 0x1234)
        writeVessel(payload, hasGem = false, gemIndex = 0)
        writeVessel(payload, hasGem = true, gemIndex = 0xABCD)
        writeVessel(payload, hasGem = false, gemIndex = 0)

        val command = WheelPacketParser.parseSaveWheel(payload)

        assertThat(command!!.gemVessels).containsExactly(
            WheelGemVessel(WheelGemAffinity.Green, 0x1234),
            WheelGemVessel(WheelGemAffinity.Red, null),
            WheelGemVessel(WheelGemAffinity.Blue, 0xABCD),
            WheelGemVessel(WheelGemAffinity.Purple, null)
        )
    }

    @Test
    fun `parses destroy gem action 0xE7 modern payload with uint16 index`() {
        val payload = Unpooled.buffer()
        payload.writeByte(0) // WheelGemAction_t::Destroy
        payload.writeShortLE(300)

        val command = WheelPacketParser.parseWheelGemAction(payload)

        assertThat(command).isEqualTo(DestroyGemCommand(300))
        assertThat(command?.opcode).isEqualTo(0xE7)
        assertThat(command?.action).isEqualTo(org.opentibia.canary.systems.wheel.WheelGemAction.Destroy)
    }

    @Test
    fun `parses reveal gem action 0xE7 modern payload with quality byte`() {
        val payload = Unpooled.buffer()
        payload.writeByte(1) // WheelGemAction_t::Reveal
        payload.writeByte(2) // WheelGemQuality_t::Greater

        val command = WheelPacketParser.parseWheelGemAction(payload)

        assertThat(command).isEqualTo(RevealGemCommand(WheelGemQuality.Greater))
    }

    @Test
    fun `parses switch domain and toggle lock gem actions 0xE7 modern payload with uint16 index`() {
        val switchPayload = Unpooled.buffer()
        switchPayload.writeByte(2) // SwitchDomain
        switchPayload.writeShortLE(0x0102)
        assertThat(WheelPacketParser.parseWheelGemAction(switchPayload))
            .isEqualTo(SwitchGemDomainCommand(0x0102))

        val lockPayload = Unpooled.buffer()
        lockPayload.writeByte(3) // ToggleLock
        lockPayload.writeShortLE(0x00FF)
        assertThat(WheelPacketParser.parseWheelGemAction(lockPayload))
            .isEqualTo(ToggleGemLockCommand(0x00FF))
    }

    @Test
    fun `parses improve grade gem action 0xE7 mapping nonzero fragment to lesser`() {
        val lesserPayload = Unpooled.buffer()
        lesserPayload.writeByte(4) // ImproveGrade
        lesserPayload.writeByte(1) // nonzero fragment -> Lesser(1)
        lesserPayload.writeByte(3) // position

        assertThat(WheelPacketParser.parseWheelGemAction(lesserPayload))
            .isEqualTo(ImproveGemGradeCommand(WheelFragmentType.Lesser, 3))

        val greaterPayload = Unpooled.buffer()
        greaterPayload.writeByte(4)
        greaterPayload.writeByte(0) // zero fragment -> Greater(0)
        greaterPayload.writeByte(7)

        assertThat(WheelPacketParser.parseWheelGemAction(greaterPayload))
            .isEqualTo(ImproveGemGradeCommand(WheelFragmentType.Greater, 7))
    }

    @Test
    fun `parses legacy gem action 0xE7 layout with byte parameters`() {
        val destroyPayload = Unpooled.buffer()
        destroyPayload.writeByte(0) // Destroy
        destroyPayload.writeByte(5) // param byte
        assertThat(WheelPacketParser.parseWheelGemAction(destroyPayload, currentPayload = false))
            .isEqualTo(DestroyGemCommand(5))

        val improvePayload = Unpooled.buffer()
        improvePayload.writeByte(4) // ImproveGrade
        improvePayload.writeByte(1) // fragment param -> Lesser
        improvePayload.writeByte(2) // position
        assertThat(WheelPacketParser.parseWheelGemAction(improvePayload, currentPayload = false))
            .isEqualTo(ImproveGemGradeCommand(WheelFragmentType.Lesser, 2))
    }

    @Test
    fun `returns null for invalid wheel gem action`() {
        val payload = Unpooled.buffer()
        payload.writeByte(9)

        assertThat(WheelPacketParser.parseWheelGemAction(payload)).isNull()
    }

    @Test
    fun `returns null when the payload is truncated`() {
        val openPayload = Unpooled.buffer()
        openPayload.writeByte(1)
        assertThat(WheelPacketParser.parseOpenWheel(openPayload)).isNull()

        val savePayload = Unpooled.buffer()
        savePayload.writeShortLE(1)
        assertThat(WheelPacketParser.parseSaveWheel(savePayload)).isNull()

        val gemPayload = Unpooled.buffer()
        gemPayload.writeByte(0)
        gemPayload.writeByte(1)
        assertThat(WheelPacketParser.parseWheelGemAction(gemPayload)).isNull()
    }

    @Test
    fun `parse dispatches by opcode and rejects unknown opcodes`() {
        val payload = Unpooled.buffer()
        payload.writeIntLE(42)

        assertThat(WheelPacketParser.parse(0x61, payload)).isEqualTo(OpenWheelCommand(42L))
        assertThat(WheelPacketParser.parse(0x99, Unpooled.buffer())).isNull()
    }

    @Test
    fun `decodes open wheel frame delivered through an embedded channel`() {
        val channel = EmbeddedChannel()
        try {
            val frame = Unpooled.buffer()
            frame.writeIntLE(77)
            assertThat(channel.writeInbound(frame)).isTrue()

            val inbound = channel.readInbound<ByteBuf>()
            assertThat(WheelPacketParser.parseOpenWheel(inbound)).isEqualTo(OpenWheelCommand(77L))
        } finally {
            channel.finishAndReleaseAll()
        }
    }

    private fun writeVessel(buffer: ByteBuf, hasGem: Boolean, gemIndex: Int) {
        buffer.writeByte(if (hasGem) 1 else 0)
        if (hasGem) {
            buffer.writeShortLE(gemIndex)
        }
    }
}
