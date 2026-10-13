package org.opentibia.canary.network.protocol

import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Decode tests for `0xBE` `Game::playerCancelAttackAndFollow`
 * (src/game/game.cpp:6990-6997; protocolgame.cpp:1971-1973).
 */
class CancelAttackAndFollowPacketHandlerTest {

    @Test
    fun `decodes cancel attack and follow 0xBE without consuming bytes`() {
        val payload = Unpooled.buffer()

        val command = CancelAttackAndFollowPacketHandler.parse()

        assertThat(command).isEqualTo(CancelAttackAndFollowCommand)
        assertThat(payload.readableBytes()).isZero()
    }
}
