package org.opentibia.canary.network.protocol

import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.network.message.writeTibiaString

/**
 * Decode tests for `0xC1` `ProtocolGame::parseClientDetails`
 * (src/server/network/protocol/protocolgame.cpp:3313-3315): Canary reads and
 * discards the string.
 */
class ClientDetailsPacketHandlerTest {

    @Test
    fun `decodes the length prefixed client details string`() {
        val payload = Unpooled.buffer()
        payload.writeTibiaString("Tibia 15.25")

        val command = ClientDetailsPacketHandler.parse(payload)

        assertThat(command).isEqualTo(ClientDetailsCommand("Tibia 15.25"))
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `decodes an empty client details string`() {
        val payload = Unpooled.buffer()
        payload.writeTibiaString("")

        assertThat(ClientDetailsPacketHandler.parse(payload)).isEqualTo(ClientDetailsCommand(""))
    }

    @Test
    fun `returns null when the details payload is truncated`() {
        val payload = Unpooled.buffer()
        payload.writeShortLE(6)
        payload.writeByte('a'.code)

        assertThat(ClientDetailsPacketHandler.parse(payload)).isNull()
        assertThat(ClientDetailsPacketHandler.parse(Unpooled.buffer())).isNull()
    }
}
