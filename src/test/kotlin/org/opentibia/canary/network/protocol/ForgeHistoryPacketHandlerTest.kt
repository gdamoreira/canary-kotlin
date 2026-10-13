package org.opentibia.canary.network.protocol

import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Decode tests for `0xC0` `ProtocolGame::parseForgeBrowseHistory`
 * (src/server/network/protocol/protocolgame.cpp:7429-7443), on the current
 * protocol profile where the leading `uint16_t` field is consumed.
 */
class ForgeHistoryPacketHandlerTest {

    @Test
    fun `decodes the current payload u16 plus page byte`() {
        val payload = Unpooled.buffer()
        payload.writeShortLE(0xABCD)
        payload.writeByte(4)

        val command = ForgeHistoryPacketHandler.parse(payload)

        assertThat(command).isEqualTo(ForgeBrowseHistoryCommand(page = 4))
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `returns null when the current payload is truncated`() {
        val missingPage = Unpooled.buffer()
        missingPage.writeShortLE(0xABCD)
        assertThat(ForgeHistoryPacketHandler.parse(missingPage)).isNull()

        val missingU16 = Unpooled.buffer()
        missingU16.writeByte(4)
        assertThat(ForgeHistoryPacketHandler.parse(missingU16)).isNull()

        assertThat(ForgeHistoryPacketHandler.parse(Unpooled.buffer())).isNull()
    }
}
