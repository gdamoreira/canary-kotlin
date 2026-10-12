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

    fun canLogout(player: Player): Boolean {
        return !player.inCombat
    }
}
