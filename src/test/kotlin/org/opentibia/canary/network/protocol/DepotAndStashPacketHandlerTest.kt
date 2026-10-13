package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.map.Position

/**
 * Decode tests for the stash and depot-search client opcodes
 * (`ProtocolGame::parseStashWithdraw`, `parseRetrieveDepotSearch`,
 * `parseOpenDepotSearch`, `parseCloseDepotSearch`,
 * `parseDepotSearchItemRequest`, protocolgame.cpp:10874-11035).
 *
 * The optional tier byte of `0x29`/`0x94` is read only when the requested item
 * carries an `ItemType::upgradeClassification` (see `parseDepotSearchItemRequest`,
 * protocolgame.cpp:11030).
 */
class DepotAndStashPacketHandlerTest {

    @Test
    fun `parses stash stow item 0x28 action with position item stack and count`() {
        val payload = Unpooled.buffer()
        payload.writeByte(StashAction.STOW_ITEM.value)
        writePosition(payload, Position(100, 100, 7))
        payload.writeShortLE(2160)
        payload.writeByte(3)
        payload.writeByte(10)

        val command = DepotAndStashPacketHandler.parse(DepotAndStashPacketHandler.OPCODE_STASH_WITHDRAW, payload)

        assertThat(command).isEqualTo(
            StowItemCommand(
                action = StashAction.STOW_ITEM,
                position = Position(100, 100, 7),
                itemId = 2160,
                stackPosition = 3,
                count = 10,
                stowStack = false,
            ),
        )
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `parses stash stow container 0x28 action without a count byte`() {
        val payload = Unpooled.buffer()
        payload.writeByte(StashAction.STOW_CONTAINER.value)
        writePosition(payload, Position(100, 100, 7))
        payload.writeShortLE(2160)
        payload.writeByte(3)

        val command = DepotAndStashPacketHandler.parse(DepotAndStashPacketHandler.OPCODE_STASH_WITHDRAW, payload)

        assertThat(command).isEqualTo(
            StowItemCommand(
                action = StashAction.STOW_CONTAINER,
                position = Position(100, 100, 7),
                itemId = 2160,
                stackPosition = 3,
                count = 0,
                stowStack = false,
            ),
        )
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `parses stash stow stack 0x28 action as a whole-stack stow`() {
        val payload = Unpooled.buffer()
        payload.writeByte(StashAction.STOW_STACK.value)
        writePosition(payload, Position(100, 100, 7))
        payload.writeShortLE(2160)
        payload.writeByte(3)

        val command = DepotAndStashPacketHandler.parse(DepotAndStashPacketHandler.OPCODE_STASH_WITHDRAW, payload)

        assertThat(command).isEqualTo(
            StowItemCommand(
                action = StashAction.STOW_STACK,
                position = Position(100, 100, 7),
                itemId = 2160,
                stackPosition = 3,
                count = 0,
                stowStack = true,
            ),
        )
    }

    @Test
    fun `parses stash withdraw 0x28 action with uint32 count`() {
        val payload = Unpooled.buffer()
        payload.writeByte(StashAction.WITHDRAW.value)
        payload.writeShortLE(2160)
        payload.writeIntLE(500)
        payload.writeByte(2)

        val command = DepotAndStashPacketHandler.parse(DepotAndStashPacketHandler.OPCODE_STASH_WITHDRAW, payload)

        assertThat(command).isEqualTo(
            StashWithdrawCommand(itemId = 2160, count = 500L, stackPosition = 2),
        )
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `decodes an unknown stash action without consuming more bytes`() {
        val payload = Unpooled.buffer()
        payload.writeByte(9)
        payload.writeByte(0xAB)

        val command = DepotAndStashPacketHandler.parse(DepotAndStashPacketHandler.OPCODE_STASH_WITHDRAW, payload)

        assertThat(command).isEqualTo(UnknownStashActionCommand(action = 9))
        assertThat(payload.readableBytes()).isEqualTo(1)
    }

    @Test
    fun `rejects a truncated stash stow payload`() {
        val payload = Unpooled.buffer()
        payload.writeByte(StashAction.STOW_ITEM.value)
        writePosition(payload, Position(100, 100, 7))
        payload.writeShortLE(2160)

        assertThat(DepotAndStashPacketHandler.parse(DepotAndStashPacketHandler.OPCODE_STASH_WITHDRAW, payload))
            .isNull()
    }

    @Test
    fun `parses open depot search 0x92 with no payload`() {
        val payload = Unpooled.buffer()

        val command = DepotAndStashPacketHandler.parse(DepotAndStashPacketHandler.OPCODE_OPEN_DEPOT_SEARCH, payload)

        assertThat(command).isEqualTo(OpenDepotSearchCommand)
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `parses close depot search 0x93 with no payload`() {
        val payload = Unpooled.buffer()

        val command = DepotAndStashPacketHandler.parse(DepotAndStashPacketHandler.OPCODE_CLOSE_DEPOT_SEARCH, payload)

        assertThat(command).isEqualTo(CloseDepotSearchCommand)
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `parses depot search item request 0x94 without a tier for unclassified items`() {
        val payload = Unpooled.buffer()
        payload.writeShortLE(2160)

        val command = DepotAndStashPacketHandler.parse(DepotAndStashPacketHandler.OPCODE_DEPOT_SEARCH_ITEM_REQUEST, payload)

        assertThat(command).isEqualTo(DepotSearchItemRequestCommand(itemId = 2160, tier = 0))
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `parses depot search item request 0x94 with a tier for classified items`() {
        val payload = Unpooled.buffer()
        payload.writeShortLE(2160)
        payload.writeByte(2)

        val command = DepotAndStashPacketHandler.parse(
            DepotAndStashPacketHandler.OPCODE_DEPOT_SEARCH_ITEM_REQUEST,
            payload,
        ) { itemId -> if (itemId == 2160) 1 else 0 }

        assertThat(command).isEqualTo(DepotSearchItemRequestCommand(itemId = 2160, tier = 2))
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `rejects a truncated depot search item request payload`() {
        val payload = Unpooled.buffer()
        payload.writeByte(0x08)

        assertThat(
            DepotAndStashPacketHandler.parse(DepotAndStashPacketHandler.OPCODE_DEPOT_SEARCH_ITEM_REQUEST, payload),
        ).isNull()
    }

    @Test
    fun `parses retrieve depot search 0x29 with tier and retrieve type`() {
        val payload = Unpooled.buffer()
        payload.writeShortLE(2160)
        payload.writeByte(2)
        payload.writeByte(1)

        val command = DepotAndStashPacketHandler.parse(
            DepotAndStashPacketHandler.OPCODE_RETRIEVE_DEPOT_SEARCH,
            payload,
        ) { itemId -> if (itemId == 2160) 1 else 0 }

        assertThat(command).isEqualTo(
            RetrieveDepotSearchCommand(itemId = 2160, tier = 2, type = 1),
        )
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `parses retrieve depot search 0x29 without a tier for unclassified items`() {
        val payload = Unpooled.buffer()
        payload.writeShortLE(2160)
        payload.writeByte(3)

        val command = DepotAndStashPacketHandler.parse(
            DepotAndStashPacketHandler.OPCODE_RETRIEVE_DEPOT_SEARCH,
            payload,
        )

        assertThat(command).isEqualTo(
            RetrieveDepotSearchCommand(itemId = 2160, tier = 0, type = 3),
        )
    }

    @Test
    fun `rejects a truncated retrieve depot search payload`() {
        val payload = Unpooled.buffer()
        payload.writeShortLE(2160)

        assertThat(
            DepotAndStashPacketHandler.parse(
                DepotAndStashPacketHandler.OPCODE_RETRIEVE_DEPOT_SEARCH,
                payload,
            ) { itemId -> if (itemId == 2160) 1 else 0 },
        ).isNull()
    }

    @Test
    fun `returns null for an unrelated opcode`() {
        assertThat(DepotAndStashPacketHandler.parse(0x95, Unpooled.buffer())).isNull()
    }

    @Test
    fun `decodes stash withdraw 0x28 via EmbeddedChannel inbound message`() {
        val channel = EmbeddedChannel()
        try {
            val bytes = Unpooled.buffer()
            bytes.writeByte(StashAction.WITHDRAW.value)
            bytes.writeShortLE(2160)
            bytes.writeIntLE(7)
            bytes.writeByte(1)
            channel.writeInbound(bytes)

            val payload = channel.readInbound<ByteBuf>()
            val command = DepotAndStashPacketHandler.parse(DepotAndStashPacketHandler.OPCODE_STASH_WITHDRAW, payload)

            assertThat(command).isEqualTo(
                StashWithdrawCommand(itemId = 2160, count = 7L, stackPosition = 1),
            )
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
