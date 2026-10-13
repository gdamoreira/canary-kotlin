package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf

/**
 * Follow request (client opcode `0xA2`), mirroring
 * `ProtocolGame::parseFollow` (protocolgame.cpp:2787-2791): a single
 * `uint32_t` creature id handed to `Game::playerFollowCreature`
 * (game.cpp:7033), which clears the attacked creature and stores the follow
 * target.
 */
data class FollowCommand(
    val creatureId: Long,
)

/**
 * Decoder for the follow client opcode.
 *
 *  - `0xA2` `ProtocolGame::parseFollow` (protocolgame.cpp:2787)
 */
object FollowPacketHandler {

    const val OPCODE_FOLLOW: Int = 0xA2

    fun parse(opcode: Int, buffer: ByteBuf): FollowCommand? = when (opcode) {
        OPCODE_FOLLOW -> parseFollow(buffer)
        else -> null
    }

    /**
     * 0xA2: reads the `uint32_t` creature id. Canary's source also carries a
     * commented-out duplicate read (protocolgame.cpp:2789); it is not executed.
     *
     * @return the decoded command, or `null` when the payload is truncated.
     */
    fun parseFollow(buffer: ByteBuf): FollowCommand? {
        if (buffer.readableBytes() < 4) return null
        return FollowCommand(creatureId = buffer.readUnsignedIntLE())
    }
}
