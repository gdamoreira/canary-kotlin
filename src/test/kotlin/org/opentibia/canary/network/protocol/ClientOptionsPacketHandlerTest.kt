package org.opentibia.canary.network.protocol

import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Decode tests for `ProtocolGame::parseSetClientOptions` (client opcode `0x2E`,
 * protocolgame.cpp:3221-3228) and `ProtocolGame::parseClientCheck` (client
 * opcode `0x63`, protocolgame.cpp:3230-3248).
 */
class ClientOptionsPacketHandlerTest {

    @Test
    fun `parses the two client option bytes`() {
        val payload = Unpooled.buffer()
        payload.writeByte(1)
        payload.writeByte(0)

        val command = ClientOptionsPacketHandler.parse(ClientOptionsPacketHandler.OPCODE_SET_CLIENT_OPTIONS, payload)

        assertThat(command).isEqualTo(SetClientOptionsCommand(first = 1, second = 0))
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `rejects a truncated client options payload`() {
        val payload = Unpooled.buffer()
        payload.writeByte(1)

        assertThat(ClientOptionsPacketHandler.parse(ClientOptionsPacketHandler.OPCODE_SET_CLIENT_OPTIONS, payload))
            .isNull()
    }

    @Test
    fun `parses the client check payload size and raw bytes`() {
        val payload = Unpooled.buffer()
        payload.writeIntLE(2)
        payload.writeByte(1)
        payload.writeByte(1)

        val command = ClientOptionsPacketHandler.parse(ClientOptionsPacketHandler.OPCODE_CLIENT_CHECK, payload)

        assertThat(command).isInstanceOf(ClientCheckCommand::class.java)
        val clientCheck = command as ClientCheckCommand
        assertThat(clientCheck.payloadSize).isEqualTo(2)
        assertThat(clientCheck.payload).containsExactly(1, 1)
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `rejects a client check truncated inside the announced payload`() {
        val payload = Unpooled.buffer()
        payload.writeIntLE(3)
        payload.writeByte(1)
        payload.writeByte(1)

        assertThat(ClientOptionsPacketHandler.parse(ClientOptionsPacketHandler.OPCODE_CLIENT_CHECK, payload)).isNull()
    }

    @Test
    fun `rejects a client check missing the payload size`() {
        val payload = Unpooled.buffer()
        payload.writeByte(1)

        assertThat(ClientOptionsPacketHandler.parse(ClientOptionsPacketHandler.OPCODE_CLIENT_CHECK, payload)).isNull()
    }

    @Test
    fun `returns null for an unrelated opcode`() {
        assertThat(ClientOptionsPacketHandler.parse(0x2D, Unpooled.buffer())).isNull()
    }
}
