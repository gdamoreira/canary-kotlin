package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.map.Position

/**
 * Decode tests for the imbuement client opcodes `0xB2` and `0xD5`-`0xD7` on the
 * current protocol payload (`ProtocolFeature::CurrentPayload`, the profile this
 * port targets; protocol_profile.cpp:202).
 *
 *  - `0xB2` `ProtocolGame::parseImbuementAction`     (protocolgame.cpp:3065)
 *  - `0xD5` `ProtocolGame::parseApplyImbuement`      (protocolgame.cpp:2538)
 *  - `0xD6` `ProtocolGame::parseClearImbuement`      (protocolgame.cpp:2549)
 *  - `0xD7` `ProtocolGame::parseCloseImbuementWindow` (protocolgame.cpp:2554)
 */
class ImbuementPacketHandlerTest {

    @Test
    fun `parses imbuement action 0xB2 pick item`() {
        val payload = Unpooled.buffer()
        payload.writeByte(ImbuementPacketHandler.IMBUEMENT_ACTION_PICK_ITEM)
        writePosition(payload, Position(100, 100, 7))
        payload.writeShortLE(2160)
        payload.writeByte(3)

        val command = ImbuementPacketHandler.parse(ImbuementPacketHandler.OPCODE_IMBUEMENT_ACTION, payload)

        assertThat(command).isEqualTo(
            ImbuementActionCommand(
                action = ImbuementPacketHandler.IMBUEMENT_ACTION_PICK_ITEM,
                position = Position(100, 100, 7),
                itemId = 2160,
                stackPosition = 3,
            ),
        )
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `parses imbuement action 0xB2 scroll without an item payload`() {
        val payload = Unpooled.buffer()
        payload.writeByte(ImbuementPacketHandler.IMBUEMENT_ACTION_SCROLL)

        val command = ImbuementPacketHandler.parse(ImbuementPacketHandler.OPCODE_IMBUEMENT_ACTION, payload)

        assertThat(command).isEqualTo(
            ImbuementActionCommand(
                action = ImbuementPacketHandler.IMBUEMENT_ACTION_SCROLL,
                position = null,
                itemId = null,
                stackPosition = null,
            ),
        )
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `parses imbuement action 0xB2 open without an item payload`() {
        val payload = Unpooled.buffer()
        payload.writeByte(ImbuementPacketHandler.IMBUEMENT_ACTION_OPEN)

        val command = ImbuementPacketHandler.parse(ImbuementPacketHandler.OPCODE_IMBUEMENT_ACTION, payload)

        assertThat(command).isEqualTo(
            ImbuementActionCommand(
                action = ImbuementPacketHandler.IMBUEMENT_ACTION_OPEN,
                position = null,
                itemId = null,
                stackPosition = null,
            ),
        )
    }

    @Test
    fun `rejects a truncated pick item imbuement action payload`() {
        val payload = Unpooled.buffer()
        payload.writeByte(ImbuementPacketHandler.IMBUEMENT_ACTION_PICK_ITEM)
        writePosition(payload, Position(100, 100, 7))

        assertThat(ImbuementPacketHandler.parse(ImbuementPacketHandler.OPCODE_IMBUEMENT_ACTION, payload)).isNull()
    }

    @Test
    fun `parses apply imbuement 0xD5 slot and uint32 id`() {
        val payload = Unpooled.buffer()
        payload.writeByte(5)
        payload.writeIntLE(1)

        val command = ImbuementPacketHandler.parse(ImbuementPacketHandler.OPCODE_APPLY_IMBUEMENT, payload)

        assertThat(command).isEqualTo(ApplyImbuementCommand(slot = 5, imbuementId = 1L))
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `decodes the full uint32 imbuement id before Canary's uint16 gate`() {
        val payload = Unpooled.buffer()
        payload.writeByte(0)
        payload.writeIntLE(65536)

        val command = ImbuementPacketHandler.parse(ImbuementPacketHandler.OPCODE_APPLY_IMBUEMENT, payload)

        assertThat(command).isEqualTo(ApplyImbuementCommand(slot = 0, imbuementId = 65536L))
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `rejects a truncated apply imbuement payload`() {
        val payload = Unpooled.buffer()
        payload.writeByte(5)
        payload.writeByte(0)
        payload.writeByte(0)
        payload.writeByte(0)

        assertThat(ImbuementPacketHandler.parse(ImbuementPacketHandler.OPCODE_APPLY_IMBUEMENT, payload)).isNull()
    }

    @Test
    fun `parses clear imbuement 0xD6 slot`() {
        val payload = Unpooled.buffer()
        payload.writeByte(4)

        val command = ImbuementPacketHandler.parse(ImbuementPacketHandler.OPCODE_CLEAR_IMBUEMENT, payload)

        assertThat(command).isEqualTo(ClearImbuementCommand(slot = 4))
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `rejects a truncated clear imbuement payload`() {
        assertThat(ImbuementPacketHandler.parse(ImbuementPacketHandler.OPCODE_CLEAR_IMBUEMENT, Unpooled.buffer()))
            .isNull()
    }

    @Test
    fun `decodes close imbuement window 0xD7 with no payload`() {
        val payload = Unpooled.buffer()

        val command = ImbuementPacketHandler.parse(ImbuementPacketHandler.OPCODE_CLOSE_IMBUEMENT_WINDOW, payload)

        assertThat(command).isEqualTo(CloseImbuementWindowCommand)
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `returns null for an unrelated opcode`() {
        assertThat(ImbuementPacketHandler.parse(0xD4, Unpooled.buffer())).isNull()
    }

    @Test
    fun `decodes apply imbuement 0xD5 via EmbeddedChannel inbound message`() {
        val channel = EmbeddedChannel()
        try {
            val bytes = Unpooled.buffer()
            bytes.writeByte(2)
            bytes.writeIntLE(31)
            channel.writeInbound(bytes)

            val payload = channel.readInbound<ByteBuf>()
            val command = ImbuementPacketHandler.parse(ImbuementPacketHandler.OPCODE_APPLY_IMBUEMENT, payload)

            assertThat(command).isEqualTo(ApplyImbuementCommand(slot = 2, imbuementId = 31L))
        } finally {
            channel.finishAndReleaseAll()
        }
    }

    private fun writePosition(buffer: ByteBuf, position: Position) {
        buffer.writeShortLE(position.x)
        buffer.writeShortLE(position.y)
        buffer.writeByte(position.z)
    }
}
