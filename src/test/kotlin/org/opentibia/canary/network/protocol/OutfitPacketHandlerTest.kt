package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.condition.Outfit
import org.opentibia.canary.map.Position

/**
 * Decode tests for the outfit/mount client opcodes
 * (src/server/network/protocol/protocolgame.cpp):
 *  - 0xD3 `ProtocolGame::parseSetOutfit`  (protocolgame.cpp:2471-2531)
 *  - 0xD4 `ProtocolGame::parseToggleMount` (protocolgame.cpp:2533-2536)
 *
 * The tests pin the current-profile (`oldProtocol == false`) read order that
 * the port targets; the `isOTCR` wing/aura/effect/shader fields are not
 * modelled because Canary reads them only for OTC Redux clients.
 */
class OutfitPacketHandlerTest {

    @Test
    fun `parses set outfit 0xD3 type zero with mount and familiar fields`() {
        val payload = Unpooled.buffer()
        payload.writeByte(0)
        writeOutfitHeader(payload, lookType = 136, head = 10, body = 20, legs = 30, feet = 40, addons = 3)
        payload.writeShortLE(5) // lookMount
        payload.writeByte(1) // lookMountHead
        payload.writeByte(2) // lookMountBody
        payload.writeByte(3) // lookMountLegs
        payload.writeByte(4) // lookMountFeet
        payload.writeByte(0x01) // setMount
        payload.writeShortLE(7) // lookFamiliarsType
        payload.writeByte(0x01) // isMountRandomized

        val command = OutfitPacketHandler.parseSetOutfit(payload)

        assertThat(command).isEqualTo(
            SetOutfitCommand(
                outfitType = 0,
                outfit = Outfit(
                    lookType = 136,
                    lookHead = 10,
                    lookBody = 20,
                    lookLegs = 30,
                    lookFeet = 40,
                    lookAddons = 3,
                    lookMount = 5,
                    lookMountHead = 1,
                    lookMountBody = 2,
                    lookMountLegs = 3,
                    lookMountFeet = 4,
                    lookFamiliarsType = 7,
                ),
                setMount = true,
                isMountRandomized = 1,
            )
        )
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `clamps set outfit 0xD3 colors above 132`() {
        val payload = Unpooled.buffer()
        payload.writeByte(0)
        writeOutfitHeader(payload, lookType = 136, head = 200, body = 201, legs = 202, feet = 203, addons = 0)
        payload.writeShortLE(0)
        payload.writeByte(240) // lookMountHead
        payload.writeByte(241) // lookMountBody
        payload.writeByte(242) // lookMountLegs
        payload.writeByte(243) // lookMountFeet
        payload.writeByte(0)
        payload.writeShortLE(0)
        payload.writeByte(0)

        val command = OutfitPacketHandler.parseSetOutfit(payload)

        assertThat(command!!.outfit.lookHead).isEqualTo(132)
        assertThat(command.outfit.lookBody).isEqualTo(132)
        assertThat(command.outfit.lookLegs).isEqualTo(132)
        assertThat(command.outfit.lookFeet).isEqualTo(132)
        assertThat(command.outfit.lookMountHead).isEqualTo(132)
        assertThat(command.outfit.lookMountBody).isEqualTo(132)
        assertThat(command.outfit.lookMountLegs).isEqualTo(132)
        assertThat(command.outfit.lookMountFeet).isEqualTo(132)
    }

    @Test
    fun `parses set outfit 0xD3 try outfit type one and discards the trailing uint32`() {
        val payload = Unpooled.buffer()
        payload.writeByte(1)
        writeOutfitHeader(payload, lookType = 136, head = 1, body = 2, legs = 3, feet = 4, addons = 5)
        payload.writeIntLE(0xAABBCCDD.toInt())

        val command = OutfitPacketHandler.parseSetOutfit(payload)

        assertThat(command).isEqualTo(
            SetOutfitCommand(
                outfitType = 1,
                outfit = Outfit(lookType = 136, lookHead = 1, lookBody = 2, lookLegs = 3, lookFeet = 4, lookAddons = 5),
            )
        )
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `parses set outfit 0xD3 show off socket type two`() {
        val payload = Unpooled.buffer()
        payload.writeByte(2)
        writeOutfitHeader(payload, lookType = 136, head = 1, body = 2, legs = 3, feet = 4, addons = 5)
        payload.writeShortLE(32000) // x
        payload.writeShortLE(32100) // y
        payload.writeByte(7) // z
        payload.writeShortLE(2160) // itemId
        payload.writeByte(2) // stackpos
        payload.writeShortLE(9) // lookMount
        payload.writeByte(11) // lookMountHead
        payload.writeByte(12) // lookMountBody
        payload.writeByte(13) // lookMountLegs
        payload.writeByte(14) // lookMountFeet
        payload.writeByte(200) // direction, clamped to DIRECTION_WEST (3)
        payload.writeByte(0x01) // podiumVisible

        val command = OutfitPacketHandler.parseSetOutfit(payload)

        assertThat(command).isEqualTo(
            SetOutfitCommand(
                outfitType = 2,
                outfit = Outfit(
                    lookType = 136,
                    lookHead = 1,
                    lookBody = 2,
                    lookLegs = 3,
                    lookFeet = 4,
                    lookAddons = 5,
                    lookMount = 9,
                    lookMountHead = 11,
                    lookMountBody = 12,
                    lookMountLegs = 13,
                    lookMountFeet = 14,
                ),
                showOffSocket = ShowOffSocket(
                    position = Position(32000, 32100, 7),
                    itemId = 2160,
                    stackPosition = 2,
                    direction = 3,
                    podiumVisible = true,
                ),
            )
        )
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `parses set outfit 0xD3 unknown type consuming only the common header`() {
        val payload = Unpooled.buffer()
        payload.writeByte(3)
        writeOutfitHeader(payload, lookType = 136, head = 1, body = 2, legs = 3, feet = 4, addons = 5)
        payload.writeByte(0xCC)

        val command = OutfitPacketHandler.parseSetOutfit(payload)

        assertThat(command).isEqualTo(
            SetOutfitCommand(
                outfitType = 3,
                outfit = Outfit(lookType = 136, lookHead = 1, lookBody = 2, lookLegs = 3, lookFeet = 4, lookAddons = 5),
            )
        )
        assertThat(payload.readableBytes()).isEqualTo(1)
        assertThat(payload.readUnsignedByte().toInt()).isEqualTo(0xCC)
    }

    @Test
    fun `rejects a truncated set outfit 0xD3 payload`() {
        val payload = Unpooled.buffer()
        payload.writeByte(0)
        payload.writeShortLE(136)
        payload.writeByte(1) // remaining common header bytes missing

        assertThat(OutfitPacketHandler.parseSetOutfit(payload)).isNull()
    }

    @Test
    fun `parses toggle mount 0xD4 as true for a nonzero byte`() {
        val payload = Unpooled.buffer()
        payload.writeByte(0x01)

        val command = OutfitPacketHandler.parseToggleMount(payload)

        assertThat(command).isEqualTo(ToggleMountCommand(mount = true))
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `parses toggle mount 0xD4 as false for a zero byte`() {
        val payload = Unpooled.buffer()
        payload.writeByte(0x00)

        assertThat(OutfitPacketHandler.parseToggleMount(payload)).isEqualTo(ToggleMountCommand(mount = false))
    }

    @Test
    fun `rejects a truncated toggle mount 0xD4 payload`() {
        assertThat(OutfitPacketHandler.parseToggleMount(Unpooled.buffer())).isNull()
    }

    @Test
    fun `returns null for an unrelated opcode`() {
        assertThat(OutfitPacketHandler.parse(0xD2, Unpooled.buffer())).isNull()
    }

    @Test
    fun `decodes toggle mount 0xD4 via EmbeddedChannel inbound message`() {
        val channel = EmbeddedChannel()
        try {
            val bytes = Unpooled.buffer()
            bytes.writeByte(0x01)
            channel.writeInbound(bytes)

            val payload = channel.readInbound<ByteBuf>()
            val command = OutfitPacketHandler.parse(
                OutfitPacketHandler.OPCODE_TOGGLE_MOUNT,
                payload,
            )

            assertThat(command).isEqualTo(ToggleMountCommand(mount = true))
        } finally {
            channel.finishAndReleaseAll()
        }
    }

    private fun writeOutfitHeader(
        payload: ByteBuf,
        lookType: Int,
        head: Int,
        body: Int,
        legs: Int,
        feet: Int,
        addons: Int,
    ) {
        payload.writeShortLE(lookType)
        payload.writeByte(head)
        payload.writeByte(body)
        payload.writeByte(legs)
        payload.writeByte(feet)
        payload.writeByte(addons)
    }
}
