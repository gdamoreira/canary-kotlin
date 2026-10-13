package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.map.Position

/**
 * Decode tests for the item-interaction client opcodes
 * (src/server/network/protocol/protocolgame.cpp):
 *  - 0x84 `ProtocolGame::parseUseWithCreature`       (protocolgame.cpp:2576-2582)
 *  - 0x85 `ProtocolGame::parseRotateItem`            (protocolgame.cpp:2894-2904)
 *  - 0x86 `ProtocolGame::parseConfigureShowOffSocket` (protocolgame.cpp:3445-3454)
 *  - 0x8B `ProtocolGame::parseWrapableItem`          (protocolgame.cpp:2906-2911)
 *  - 0x9F `ProtocolGame::parseSetMonsterPodium`      (protocolgame.cpp:11491-11506)
 *
 * The tests pin the current-profile (`oldProtocol == false`) read order that the
 * port targets; `0x86` and `0x9F` return early for old-protocol clients in
 * Canary, which the current-profile port always skips.
 */
class ItemInteractionPacketHandlerTest {

    @Test
    fun `parses use with creature 0x84 position item stack and creature id`() {
        val payload = Unpooled.buffer()
        writePosition(payload, Position(32000, 32100, 7))
        payload.writeShortLE(2160)
        payload.writeByte(3)
        payload.writeIntLE(0x01020304)

        val command = ItemInteractionPacketHandler.parse(
            ItemInteractionPacketHandler.OPCODE_USE_WITH_CREATURE,
            payload,
        )

        assertThat(command).isEqualTo(
            UseWithCreatureCommand(
                position = Position(32000, 32100, 7),
                itemId = 2160,
                fromStackPosition = 3,
                creatureId = 0x01020304L,
            ),
        )
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `rejects a truncated use with creature 0x84 payload`() {
        val payload = Unpooled.buffer()
        writePosition(payload, Position(100, 100, 7))
        payload.writeShortLE(2160)
        payload.writeByte(3)
        payload.writeShortLE(1) // creature id needs 4 bytes

        assertThat(ItemInteractionPacketHandler.parseUseWithCreature(payload)).isNull()
    }

    @Test
    fun `parses rotate item 0x85 position item and stack`() {
        val payload = Unpooled.buffer()
        writePosition(payload, Position(100, 100, 7))
        payload.writeShortLE(2160)
        payload.writeByte(2)

        val command = ItemInteractionPacketHandler.parse(
            ItemInteractionPacketHandler.OPCODE_ROTATE_ITEM,
            payload,
        )

        assertThat(command).isEqualTo(
            RotateItemCommand(
                position = Position(100, 100, 7),
                itemId = 2160,
                stackPosition = 2,
            ),
        )
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `rejects a truncated rotate item 0x85 payload`() {
        val payload = Unpooled.buffer()
        writePosition(payload, Position(100, 100, 7))
        payload.writeByte(0x00) // item id needs 2 bytes

        assertThat(ItemInteractionPacketHandler.parseRotateItem(payload)).isNull()
    }

    @Test
    fun `parses configure show off socket 0x86 position item and stack`() {
        val payload = Unpooled.buffer()
        writePosition(payload, Position(32300, 32200, 6))
        payload.writeShortLE(2578)
        payload.writeByte(1)

        val command = ItemInteractionPacketHandler.parse(
            ItemInteractionPacketHandler.OPCODE_CONFIGURE_SHOW_OFF_SOCKET,
            payload,
        )

        assertThat(command).isEqualTo(
            ConfigureShowOffSocketCommand(
                position = Position(32300, 32200, 6),
                itemId = 2578,
                stackPosition = 1,
            ),
        )
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `rejects a truncated configure show off socket 0x86 payload`() {
        val payload = Unpooled.buffer()
        writePosition(payload, Position(100, 100, 7))
        payload.writeShortLE(2578)

        assertThat(ItemInteractionPacketHandler.parseConfigureShowOffSocket(payload)).isNull()
    }

    @Test
    fun `parses wrapable item 0x8B position item and stack`() {
        val payload = Unpooled.buffer()
        writePosition(payload, Position(100, 100, 7))
        payload.writeShortLE(2348)
        payload.writeByte(4)

        val command = ItemInteractionPacketHandler.parse(
            ItemInteractionPacketHandler.OPCODE_WRAPABLE_ITEM,
            payload,
        )

        assertThat(command).isEqualTo(
            WrapableItemCommand(
                position = Position(100, 100, 7),
                itemId = 2348,
                stackPosition = 4,
            ),
        )
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `rejects a truncated wrapable item 0x8B payload`() {
        val payload = Unpooled.buffer()
        writePosition(payload, Position(100, 100, 7))
        payload.writeShortLE(2348)

        assertThat(ItemInteractionPacketHandler.parseWrapableItem(payload)).isNull()
    }

    @Test
    fun `parses set monster podium 0x9F and truncates the race id to uint16`() {
        val payload = Unpooled.buffer()
        payload.writeIntLE(0x1234ABCD.toInt()) // Canary casts the uint32 to uint16
        writePosition(payload, Position(100, 100, 7))
        payload.writeShortLE(2578)
        payload.writeByte(2)
        payload.writeByte(3)
        payload.writeByte(0x01)
        payload.writeByte(0x00)

        val command = ItemInteractionPacketHandler.parse(
            ItemInteractionPacketHandler.OPCODE_SET_MONSTER_PODIUM,
            payload,
        )

        assertThat(command).isEqualTo(
            SetMonsterPodiumCommand(
                monsterRaceId = 0xABCD,
                position = Position(100, 100, 7),
                itemId = 2578,
                stackPosition = 2,
                direction = 3,
                podiumVisible = 1,
                monsterVisible = 0,
            ),
        )
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `rejects a truncated set monster podium 0x9F payload`() {
        val payload = Unpooled.buffer()
        payload.writeIntLE(0x0000ABCD)
        writePosition(payload, Position(100, 100, 7))
        payload.writeShortLE(2578)
        payload.writeByte(2)
        payload.writeByte(3) // direction and visibility bytes missing

        assertThat(ItemInteractionPacketHandler.parseSetMonsterPodium(payload)).isNull()
    }

    @Test
    fun `returns null for an unrelated opcode`() {
        assertThat(ItemInteractionPacketHandler.parse(0x83, Unpooled.buffer())).isNull()
    }

    @Test
    fun `decodes use with creature 0x84 via EmbeddedChannel inbound message`() {
        val channel = EmbeddedChannel()
        try {
            val bytes = Unpooled.buffer()
            writePosition(bytes, Position(100, 100, 7))
            bytes.writeShortLE(2160)
            bytes.writeByte(1)
            bytes.writeIntLE(77)
            channel.writeInbound(bytes)

            val payload = channel.readInbound<ByteBuf>()
            val command = ItemInteractionPacketHandler.parse(
                ItemInteractionPacketHandler.OPCODE_USE_WITH_CREATURE,
                payload,
            )

            assertThat(command).isEqualTo(
                UseWithCreatureCommand(
                    position = Position(100, 100, 7),
                    itemId = 2160,
                    fromStackPosition = 1,
                    creatureId = 77L,
                ),
            )
        } finally {
            channel.finishAndReleaseAll()
        }
    }

    private fun writePosition(payload: ByteBuf, position: Position) {
        payload.writeShortLE(position.x)
        payload.writeShortLE(position.y)
        payload.writeByte(position.z)
    }
}
