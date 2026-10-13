package org.opentibia.canary.network.protocol

import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Decode tests for `ProtocolGame::parseCharacterTradeConfigurationAction`
 * (client opcode `0x76`, protocolgame.cpp:3270-3297).
 *
 * Actions 2 and 3 carry two discarded `uint32_t` values, a source item list, a
 * target item list and a store item list. Every item id is followed by one
 * extra byte when Canary's `itemTypeHasSubtype` (protocolgame.cpp:166-168) is
 * true for it.
 */
class CharacterTradeConfigurationPacketHandlerTest {

    @Test
    fun `parses an action without item lists`() {
        val payload = Unpooled.buffer()
        payload.writeByte(1)

        val command = CharacterTradeConfigurationPacketHandler.parse(payload)

        assertThat(command).isEqualTo(
            CharacterTradeConfigurationCommand(
                action = 1,
                sourceItemIds = emptyList(),
                targetItemIds = emptyList(),
                storeItemIds = emptyList(),
            ),
        )
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `parses the action two item lists and consumes subtype bytes`() {
        val payload = Unpooled.buffer()
        payload.writeByte(2)
        payload.writeIntLE(10)
        payload.writeIntLE(20)
        payload.writeByte(2)
        payload.writeShortLE(2160)
        payload.writeByte(3) // subtype byte for 2160
        payload.writeShortLE(2400)
        payload.writeByte(1)
        payload.writeShortLE(100)
        payload.writeByte(0)

        val command = CharacterTradeConfigurationPacketHandler.parse(payload) { it == 2160 }

        assertThat(command).isEqualTo(
            CharacterTradeConfigurationCommand(
                action = 2,
                sourceItemIds = listOf(2160, 2400),
                targetItemIds = listOf(100),
                storeItemIds = emptyList(),
            ),
        )
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `parses the action three lists with store items`() {
        val payload = Unpooled.buffer()
        payload.writeByte(3)
        payload.writeIntLE(1)
        payload.writeIntLE(2)
        payload.writeByte(0)
        payload.writeByte(0)
        payload.writeByte(2)
        payload.writeShortLE(300)
        payload.writeShortLE(400)

        val command = CharacterTradeConfigurationPacketHandler.parse(payload)

        assertThat(command).isEqualTo(
            CharacterTradeConfigurationCommand(
                action = 3,
                sourceItemIds = emptyList(),
                targetItemIds = emptyList(),
                storeItemIds = listOf(300, 400),
            ),
        )
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `rejects a truncated item id`() {
        val payload = Unpooled.buffer()
        payload.writeByte(2)
        payload.writeIntLE(1)
        payload.writeIntLE(2)
        payload.writeByte(1)
        payload.writeByte(7)

        assertThat(CharacterTradeConfigurationPacketHandler.parse(payload)).isNull()
    }

    @Test
    fun `rejects a missing subtype byte`() {
        val payload = Unpooled.buffer()
        payload.writeByte(2)
        payload.writeIntLE(1)
        payload.writeIntLE(2)
        payload.writeByte(1)
        payload.writeShortLE(2160)

        assertThat(CharacterTradeConfigurationPacketHandler.parse(payload) { it == 2160 }).isNull()
    }

    @Test
    fun `rejects an empty action payload`() {
        assertThat(CharacterTradeConfigurationPacketHandler.parse(Unpooled.buffer())).isNull()
    }
}
