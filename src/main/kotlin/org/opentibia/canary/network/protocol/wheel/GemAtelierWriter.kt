package org.opentibia.canary.network.protocol.wheel

import io.netty.buffer.ByteBuf

/**
 * Outbound gem atelier serialization, ported from Canary
 * src/server/network/protocol/protocolgame.cpp:
 *
 *  - `ProtocolGame::sendGemAtelierGemRevealed` (0xC5, protocolgame.cpp:11697-11706)
 *
 * The `!player`/`oldProtocol`/`ProtocolFeature::CurrentPayload` guards stay
 * with the caller. The frame is the opcode plus the revealed gem index, written
 * Little-Endian.
 */
object GemAtelierWriter {

    const val PACKET_GEM_ATELIER_GEM_REVEALED: Int = 0xC5

    /** `sendGemAtelierGemRevealed` (protocolgame.cpp:11697-11706). */
    fun writeGemAtelierGemRevealed(out: ByteBuf, gemIndex: Int) {
        out.writeByte(PACKET_GEM_ATELIER_GEM_REVEALED)
        out.writeShortLE(gemIndex)
    }
}
