package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf

/**
 * Transaction-details request (client opcode `0xC9`), mirroring
 * `ProtocolGame::parseGetTransactionDetails` (protocolgame.cpp:4336-4340):
 * under the current payload profile Canary reads one `uint32_t` and discards it
 * (`static_cast<void>(msg.get<uint32_t>())`); the transaction-details response
 * sender is not ported (PARITY GAP).
 */
data class TransactionDetailsCommand(
    val transactionId: Long,
)

/**
 * Decoder for the transaction-details client opcode.
 *
 *  - `0xC9` `ProtocolGame::parseGetTransactionDetails` (protocolgame.cpp:4336)
 */
object TransactionDetailsPacketHandler {

    const val OPCODE_GET_TRANSACTION_DETAILS: Int = 0xC9

    fun parse(opcode: Int, buffer: ByteBuf): TransactionDetailsCommand? {
        if (opcode != OPCODE_GET_TRANSACTION_DETAILS) {
            return null
        }
        if (buffer.readableBytes() < 4) {
            return null
        }
        return TransactionDetailsCommand(transactionId = buffer.readUnsignedIntLE())
    }
}
