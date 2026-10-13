package org.opentibia.canary.network.protocol

import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.network.message.writeTibiaString

/**
 * Decode tests for `ProtocolGame::parseFriendSystemAction` (client opcode
 * `0x81`, protocolgame.cpp:2975-3024).
 *
 * The parser targets the current payload (`ProtocolFeature::CurrentPayload`),
 * so the trailing operands are read for every state the switch names: a
 * `uint32_t` for `0x03`/`0x05`/`0x06`/`0x07`/`0x08`/`0x09`, a `uint32_t` and a
 * byte for `0x04`/`0x0A`/`0x0D`, a string for `0x0B`, a byte for
 * `0x0E` and four bytes for `0x10`.
 */
class FriendSystemPacketHandlerTest {

    @Test
    fun `parses the target id states`() {
        for (state in listOf(0x03, 0x05, 0x06, 0x07, 0x08, 0x09)) {
            val payload = Unpooled.buffer()
            payload.writeByte(state)
            payload.writeIntLE(77)

            val command = FriendSystemPacketHandler.parse(payload)

            assertThat(command).isEqualTo(FriendSystemTargetCommand(state = state, targetId = 77L))
            assertThat(payload.readableBytes()).isZero()
        }
    }

    @Test
    fun `parses the target id and byte states`() {
        for (state in listOf(0x04, 0x0A, 0x0D)) {
            val payload = Unpooled.buffer()
            payload.writeByte(state)
            payload.writeIntLE(77)
            payload.writeByte(3)

            val command = FriendSystemPacketHandler.parse(payload)

            assertThat(command).isEqualTo(
                FriendSystemTargetValueCommand(state = state, targetId = 77L, value = 3),
            )
            assertThat(payload.readableBytes()).isZero()
        }
    }

    @Test
    fun `parses the text state`() {
        val payload = Unpooled.buffer()
        payload.writeByte(0x0B)
        payload.writeTibiaString("hello")

        val command = FriendSystemPacketHandler.parse(payload)

        assertThat(command).isEqualTo(FriendSystemTextCommand(state = 0x0B, text = "hello"))
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `parses the title state`() {
        val payload = Unpooled.buffer()
        payload.writeByte(0x0E)
        payload.writeByte(5)

        val command = FriendSystemPacketHandler.parse(payload)

        assertThat(command).isEqualTo(FriendSystemTitleCommand(titleId = 5))
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `parses the four byte state`() {
        val payload = Unpooled.buffer()
        payload.writeByte(0x10)
        payload.writeByte(1)
        payload.writeByte(0)
        payload.writeByte(1)
        payload.writeByte(0)

        val command = FriendSystemPacketHandler.parse(payload)

        assertThat(command).isEqualTo(
            FriendSystemFourBytesCommand(state = 0x10, first = 1, second = 0, third = 1, fourth = 0),
        )
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `keeps an unnamed state with no operands`() {
        val payload = Unpooled.buffer()
        payload.writeByte(0x01)

        val command = FriendSystemPacketHandler.parse(payload)

        assertThat(command).isEqualTo(FriendSystemStateCommand(state = 0x01))
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `rejects a truncated target id state`() {
        val payload = Unpooled.buffer()
        payload.writeByte(0x03)
        payload.writeShortLE(77)

        assertThat(FriendSystemPacketHandler.parse(payload)).isNull()
    }

    @Test
    fun `rejects a truncated text state`() {
        val payload = Unpooled.buffer()
        payload.writeByte(0x0B)
        payload.writeShortLE(9)
        payload.writeByte(1)

        assertThat(FriendSystemPacketHandler.parse(payload)).isNull()
    }

    @Test
    fun `rejects an empty friend system payload`() {
        assertThat(FriendSystemPacketHandler.parse(Unpooled.buffer())).isNull()
    }
}
