package org.opentibia.canary.network.protocol

import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.map.Position

class KeepaliveProtocolTest {

    @Test
    fun `responds to ping opcode 0x1E with pong packet 0x1E`() {
        val player = Player(1, "Hero", 100, 100, position = Position(100, 100, 7))

        val pong = KeepaliveHandler.handlePing(player)
        assertThat(pong).isNotNull
        assertThat(pong.readUnsignedByte().toInt()).isEqualTo(0x1E)
    }

    @Test
    fun `handles the ping back opcode without a payload`() {
        assertThat(KeepaliveHandler.handlePingBack()).isTrue()
    }

    @Test
    fun `allows logout when player is out of combat`() {
        val player = Player(1, "Hero", 100, 100, position = Position(100, 100, 7))
        player.inCombat = false

        val canLogout = KeepaliveHandler.canLogout(player)
        assertThat(canLogout).isTrue()
    }

    @Test
    fun `rejects logout when player is currently in combat`() {
        val player = Player(1, "Hero", 100, 100, position = Position(100, 100, 7))
        player.inCombat = true

        val canLogout = KeepaliveHandler.canLogout(player)
        assertThat(canLogout).isFalse()
    }
}
