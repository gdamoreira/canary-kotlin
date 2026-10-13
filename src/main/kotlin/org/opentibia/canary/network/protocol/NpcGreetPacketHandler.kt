package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf

/**
 * NPC greet request (client opcode `0xEE`), mirroring
 * `ProtocolGame::parseGreet` (protocolgame.cpp:4253-4256): the `uint32_t` NPC
 * id handed to `Game::playerNpcGreet` (game.cpp:10236), which drives the NPC's
 * `onGreet` Lua callback. The NPC registry and the Lua greet dispatch are not
 * ported (PARITY GAP), so the decoded command only marks the confinement
 * boundary.
 */
data class NpcGreetCommand(
    val npcId: Long,
)

/**
 * Decoder for the NPC greet client opcode.
 *
 *  - `0xEE` `ProtocolGame::parseGreet` (protocolgame.cpp:4253)
 */
object NpcGreetPacketHandler {

    const val OPCODE_GREET: Int = 0xEE

    fun parse(opcode: Int, buffer: ByteBuf): NpcGreetCommand? {
        if (opcode != OPCODE_GREET) {
            return null
        }
        if (buffer.readableBytes() < 4) {
            return null
        }
        return NpcGreetCommand(npcId = buffer.readUnsignedIntLE())
    }
}
