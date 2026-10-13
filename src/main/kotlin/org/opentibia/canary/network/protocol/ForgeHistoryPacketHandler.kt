package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf

/**
 * `ProtocolGame::parseForgeBrowseHistory` request (client opcode `0xC0`,
 * src/server/network/protocol/protocolgame.cpp:7429-7443), handed to
 * `Game::playerBrowseForgeHistory` (src/game/game.cpp:11241).
 */
data class ForgeBrowseHistoryCommand(
    val page: Int,
)

/**
 * Decoder for `0xC0` on the current protocol profile: the discarded leading
 * `uint16_t` followed by the page byte (protocolgame.cpp:7435-7440). The legacy
 * single-byte profile is not modeled (PARITY GAP).
 */
object ForgeHistoryPacketHandler {

    /** protocolgame.cpp:1977-1979 dispatches this opcode to `parseForgeBrowseHistory`. */
    const val OPCODE_FORGE_BROWSE_HISTORY: Int = 0xC0

    /**
     * @return the decoded command, or `null` when the payload is truncated.
     */
    fun parse(buffer: ByteBuf): ForgeBrowseHistoryCommand? {
        if (buffer.readableBytes() < 3) return null
        buffer.skipBytes(2) // unknown/current-payload field, discarded by Canary
        return ForgeBrowseHistoryCommand(page = buffer.readUnsignedByte().toInt())
    }
}
