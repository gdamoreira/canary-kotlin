package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import org.opentibia.canary.creatures.Player

object KeepaliveHandler {

    const val PACKET_PING: Byte = 0x1E

    fun handlePing(player: Player): ByteBuf {
        val out = Unpooled.buffer(1)
        out.writeByte(PACKET_PING.toInt())
        return out
    }

    /**
     * Handles the ping-back request (client opcode `0x1D`), mirroring
     * `Game::playerReceivePingBack` (game.cpp:4463-4470), which calls
     * `Player::sendPingBack` (player.cpp:2341-2345).
     *
     * PARITY GAP: `sendPingBack` is a pure protocol send (`client->sendPingBack()`)
     * and the outbound frame is not ported, so the packet only marks the
     * confinement/handled boundary.
     */
    fun handlePingBack(): Boolean = true

    fun canLogout(player: Player): Boolean {
        return !player.inCombat
    }
}
