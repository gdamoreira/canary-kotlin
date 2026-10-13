package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf

/**
 * Resource balance request (client opcode `0xED`), mirroring
 * `ProtocolGame::parseSendResourceBalance(NetworkMessage &)` (protocolgame.cpp:4305-4334):
 * under the current payload profile Canary reads one requested-resource byte
 * and answers with the matching balance; the balance senders are not ported
 * (PARITY GAP).
 *
 * `Resource_t` values live in `src/server/server_definitions.hpp:57-75`
 * (`RESOURCE_BOUNTY_POINTS` 0x56, `RESOURCE_SOULSEALS` 0x57).
 */
data class ResourceBalanceCommand(
    val requestedResource: Int,
)

/**
 * Decoder for the resource balance client opcode.
 *
 *  - `0xED` `ProtocolGame::parseSendResourceBalance` (protocolgame.cpp:4305)
 */
object ResourceBalancePacketHandler {

    const val OPCODE_SEND_RESOURCE_BALANCE: Int = 0xED

    fun parse(opcode: Int, buffer: ByteBuf): ResourceBalanceCommand? {
        if (opcode != OPCODE_SEND_RESOURCE_BALANCE) {
            return null
        }
        if (buffer.readableBytes() < 1) {
            return null
        }
        return ResourceBalanceCommand(requestedResource = buffer.readUnsignedByte().toInt())
    }
}
