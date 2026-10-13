package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf

/**
 * Blessing-window request (client opcode `0xCF`), mirroring
 * `ProtocolGame::sendBlessingWindow` (protocolgame.cpp:5899-5962): Canary reads
 * no payload and immediately serializes the window from the player's blessing
 * state. The sender is not ported (PARITY GAP), so the decoded command only
 * marks the confinement boundary.
 */
object BlessingWindowCommand

/**
 * Decoder for the blessing-window client opcode.
 *
 *  - `0xCF` `ProtocolGame::sendBlessingWindow` (protocolgame.cpp:5899)
 */
object BlessingWindowPacketHandler {

    const val OPCODE_BLESSING_WINDOW: Int = 0xCF

    fun parse(opcode: Int, buffer: ByteBuf): BlessingWindowCommand? {
        if (opcode != OPCODE_BLESSING_WINDOW) {
            return null
        }
        return BlessingWindowCommand
    }
}
