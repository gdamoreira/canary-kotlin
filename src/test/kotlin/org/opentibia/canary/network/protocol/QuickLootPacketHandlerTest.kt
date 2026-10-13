package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.map.Position

/**
 * Decode tests for the quick-loot client opcodes `0x8F`-`0x91`
 * (`ProtocolGame::parseQuickLoot`, `parseLootContainer`,
 * `parseQuickLootBlackWhitelist`, protocolgame.cpp:2628-2709) on the current
 * protocol payload, which is the profile this port targets.
 */
class QuickLootPacketHandlerTest {

    @Test
    fun `parses quick loot 0x8F variant 0 as a single-corpse loot`() {
        val payload = Unpooled.buffer()
        payload.writeByte(0)
        writePosition(payload, Position(100, 100, 7))
        payload.writeShortLE(2160)
        payload.writeByte(3)

        val command = QuickLootPacketHandler.parse(QuickLootPacketHandler.OPCODE_QUICK_LOOT, payload)

        assertThat(command).isEqualTo(
            QuickLootCorpseCommand(
                position = Position(100, 100, 7),
                itemId = 2160,
                stackPosition = 3,
                lootAllCorpses = false,
            ),
        )
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `parses quick loot 0x8F variant 1 as a loot-all-corpses request`() {
        val payload = Unpooled.buffer()
        payload.writeByte(1)
        writePosition(payload, Position(100, 100, 7))
        payload.writeShortLE(2160)
        payload.writeByte(3)

        val command = QuickLootPacketHandler.parse(QuickLootPacketHandler.OPCODE_QUICK_LOOT, payload)

        assertThat(command).isEqualTo(
            QuickLootCorpseCommand(
                position = Position(100, 100, 7),
                itemId = 2160,
                stackPosition = 3,
                lootAllCorpses = true,
            ),
        )
    }

    @Test
    fun `parses quick loot 0x8F variant 2 as a nearby loot request`() {
        val payload = Unpooled.buffer()
        payload.writeByte(2)

        val command = QuickLootPacketHandler.parse(QuickLootPacketHandler.OPCODE_QUICK_LOOT, payload)

        assertThat(command).isEqualTo(QuickLootNearbyCommand)
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `decodes an unsupported quick loot 0x8F variant without consuming more bytes`() {
        val payload = Unpooled.buffer()
        payload.writeByte(9)
        payload.writeByte(0xAB)

        val command = QuickLootPacketHandler.parse(QuickLootPacketHandler.OPCODE_QUICK_LOOT, payload)

        assertThat(command).isEqualTo(QuickLootUnsupportedVariantCommand(variant = 9))
        assertThat(payload.readableBytes()).isEqualTo(1)
    }

    @Test
    fun `rejects a truncated quick loot 0x8F payload`() {
        val payload = Unpooled.buffer()
        payload.writeByte(0)
        writePosition(payload, Position(100, 100, 7))

        assertThat(QuickLootPacketHandler.parse(QuickLootPacketHandler.OPCODE_QUICK_LOOT, payload)).isNull()
    }

    @Test
    fun `parses loot container 0x90 action 0 as a loot-container assignment`() {
        val payload = Unpooled.buffer()
        payload.writeByte(0)
        payload.writeByte(4)
        writePosition(payload, Position(100, 100, 7))
        payload.writeShortLE(2160)
        payload.writeByte(3)

        val command = QuickLootPacketHandler.parse(QuickLootPacketHandler.OPCODE_LOOT_CONTAINER, payload)

        assertThat(command).isEqualTo(
            SetManagedContainerCommand(
                category = 4,
                position = Position(100, 100, 7),
                itemId = 2160,
                stackPosition = 3,
                isLootContainer = true,
            ),
        )
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `parses loot container 0x90 action 3 as a quick loot fallback toggle`() {
        val payload = Unpooled.buffer()
        payload.writeByte(3)
        payload.writeByte(1)

        val command = QuickLootPacketHandler.parse(QuickLootPacketHandler.OPCODE_LOOT_CONTAINER, payload)

        assertThat(command).isEqualTo(SetQuickLootFallbackCommand(useMainAsFallback = true))
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `parses loot container 0x90 action 4 as an obtain-container assignment`() {
        val payload = Unpooled.buffer()
        payload.writeByte(4)
        payload.writeByte(4)
        writePosition(payload, Position(100, 100, 7))
        payload.writeShortLE(2160)
        payload.writeByte(3)

        val command = QuickLootPacketHandler.parse(QuickLootPacketHandler.OPCODE_LOOT_CONTAINER, payload)

        assertThat(command).isEqualTo(
            SetManagedContainerCommand(
                category = 4,
                position = Position(100, 100, 7),
                itemId = 2160,
                stackPosition = 3,
                isLootContainer = false,
            ),
        )
    }

    @Test
    fun `parses loot container 0x90 action 5 as an obtain-container clear`() {
        val payload = Unpooled.buffer()
        payload.writeByte(5)
        payload.writeByte(7)

        val command = QuickLootPacketHandler.parse(QuickLootPacketHandler.OPCODE_LOOT_CONTAINER, payload)

        assertThat(command).isEqualTo(ClearManagedContainerCommand(category = 7, isLootContainer = false))
    }

    @Test
    fun `parses loot container 0x90 action 6 as an obtain-container open`() {
        val payload = Unpooled.buffer()
        payload.writeByte(6)
        payload.writeByte(7)

        val command = QuickLootPacketHandler.parse(QuickLootPacketHandler.OPCODE_LOOT_CONTAINER, payload)

        assertThat(command).isEqualTo(OpenManagedContainerCommand(category = 7, isLootContainer = false))
    }

    @Test
    fun `decodes an unknown loot container 0x90 action without consuming more bytes`() {
        val payload = Unpooled.buffer()
        payload.writeByte(7)
        payload.writeByte(0xAB)

        val command = QuickLootPacketHandler.parse(QuickLootPacketHandler.OPCODE_LOOT_CONTAINER, payload)

        assertThat(command).isEqualTo(UnknownLootContainerActionCommand(action = 7))
        assertThat(payload.readableBytes()).isEqualTo(1)
    }

    @Test
    fun `rejects a truncated loot container assignment payload`() {
        val payload = Unpooled.buffer()
        payload.writeByte(0)
        payload.writeByte(4)
        writePosition(payload, Position(100, 100, 7))

        assertThat(QuickLootPacketHandler.parse(QuickLootPacketHandler.OPCODE_LOOT_CONTAINER, payload)).isNull()
    }

    @Test
    fun `parses quick loot black whitelist 0x91 filter and item list`() {
        val payload = Unpooled.buffer()
        payload.writeByte(1)
        payload.writeShortLE(2)
        payload.writeShortLE(2160)
        payload.writeShortLE(2152)

        val command = QuickLootPacketHandler.parse(QuickLootPacketHandler.OPCODE_QUICK_LOOT_BLACK_WHITELIST, payload)

        assertThat(command).isEqualTo(
            QuickLootBlackWhitelistCommand(filter = 1, itemIds = listOf(2160, 2152)),
        )
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `parses an empty quick loot black whitelist`() {
        val payload = Unpooled.buffer()
        payload.writeByte(0)
        payload.writeShortLE(0)

        val command = QuickLootPacketHandler.parse(QuickLootPacketHandler.OPCODE_QUICK_LOOT_BLACK_WHITELIST, payload)

        assertThat(command).isEqualTo(QuickLootBlackWhitelistCommand(filter = 0, itemIds = emptyList()))
    }

    @Test
    fun `rejects a truncated quick loot black whitelist item list`() {
        val payload = Unpooled.buffer()
        payload.writeByte(1)
        payload.writeShortLE(2)
        payload.writeShortLE(2160)

        assertThat(
            QuickLootPacketHandler.parse(QuickLootPacketHandler.OPCODE_QUICK_LOOT_BLACK_WHITELIST, payload),
        ).isNull()
    }

    @Test
    fun `returns null for an unrelated opcode`() {
        assertThat(QuickLootPacketHandler.parse(0x8E, Unpooled.buffer())).isNull()
    }

    @Test
    fun `decodes quick loot 0x8F via EmbeddedChannel inbound message`() {
        val channel = EmbeddedChannel()
        try {
            val bytes = Unpooled.buffer()
            bytes.writeByte(2)
            channel.writeInbound(bytes)

            val payload = channel.readInbound<ByteBuf>()
            val command = QuickLootPacketHandler.parse(QuickLootPacketHandler.OPCODE_QUICK_LOOT, payload)

            assertThat(command).isEqualTo(QuickLootNearbyCommand)
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
