package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf

/**
 * Player typing report (client opcode `0x38`), mirroring
 * `ProtocolGame::parsePlayerTyping` (protocolgame.cpp:3670-3673): one
 * `uint8_t` typing state byte handed to `Game::playerSetTyping`.
 */
data class PlayerTypingCommand(
    val typing: Int,
)

/**
 * Decoder for `ProtocolGame::parsePlayerTyping` (protocolgame.cpp:3670).
 *
 * PARITY GAP: `Game::playerSetTyping` (game.cpp:12704-12714) only broadcasts
 * `ProtocolGame::sendPlayerTyping` to the spectators around the player; the
 * typing sender and the spectator pipeline are not ported, so the decoded
 * command is only the confinement boundary.
 */
object PlayerTypingPacketHandler {

    /** Client opcode for `ProtocolGame::parsePlayerTyping`. */
    const val OPCODE_PLAYER_TYPING: Int = 0x38

    /**
     * 0x38: reads the typing state byte.
     *
     * @return the decoded command, or `null` when the payload is truncated.
     */
    fun parse(buffer: ByteBuf): PlayerTypingCommand? {
        if (buffer.readableBytes() < 1) {
            return null
        }
        return PlayerTypingCommand(typing = buffer.readUnsignedByte().toInt())
    }
}
