package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf

/**
 * Store offer description request (client opcode `0xE8`), mirroring
 * `ProtocolGame::parseOfferDescription` (protocolgame.cpp:4258-4261): a
 * `uint32_t` offer id that Canary only logs (`g_logger().debug`).
 */
data class OfferDescriptionCommand(
    val offerId: Long,
)

/**
 * Decoder for the store offer description client opcode.
 *
 *  - `0xE8` `ProtocolGame::parseOfferDescription` (protocolgame.cpp:4258)
 */
object OfferDescriptionPacketHandler {

    const val OPCODE_OFFER_DESCRIPTION: Int = 0xE8

    fun parse(opcode: Int, buffer: ByteBuf): OfferDescriptionCommand? {
        if (opcode != OPCODE_OFFER_DESCRIPTION) {
            return null
        }
        if (buffer.readableBytes() < 4) {
            return null
        }
        return OfferDescriptionCommand(offerId = buffer.readUnsignedIntLE())
    }
}
