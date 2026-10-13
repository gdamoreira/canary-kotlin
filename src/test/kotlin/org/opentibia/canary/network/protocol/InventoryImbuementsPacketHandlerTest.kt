package org.opentibia.canary.network.protocol

import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Decode tests for `ProtocolGame::parseInventoryImbuements` (client opcode
 * `0x60`, protocolgame.cpp:10727-10734): one byte stored into the player
 * `imbuementTrackerWindowOpen` flag (`Game::playerRequestInventoryImbuements`,
 * game.cpp:11487-11517).
 */
class InventoryImbuementsPacketHandlerTest {

    @Test
    fun `parses the open tracker byte`() {
        val payload = Unpooled.buffer()
        payload.writeByte(1)

        assertThat(InventoryImbuementsPacketHandler.parse(payload))
            .isEqualTo(InventoryImbuementsCommand(isTrackerOpen = true))
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `parses the closed tracker byte`() {
        val payload = Unpooled.buffer()
        payload.writeByte(0)

        assertThat(InventoryImbuementsPacketHandler.parse(payload))
            .isEqualTo(InventoryImbuementsCommand(isTrackerOpen = false))
    }

    @Test
    fun `matches the C plus plus boolean conversion for non zero bytes`() {
        val payload = Unpooled.buffer()
        payload.writeByte(7)

        assertThat(InventoryImbuementsPacketHandler.parse(payload))
            .isEqualTo(InventoryImbuementsCommand(isTrackerOpen = true))
    }

    @Test
    fun `rejects an empty tracker payload`() {
        assertThat(InventoryImbuementsPacketHandler.parse(Unpooled.buffer())).isNull()
    }
}
