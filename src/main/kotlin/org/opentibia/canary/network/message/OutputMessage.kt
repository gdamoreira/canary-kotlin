package org.opentibia.canary.network.message

/**
 * Minimal port of Canary's `OutputMessage` (src/server/network/message/outputmessage.hpp)
 * for the connection write queue: a framed outbound message identified by its
 * encoded payload.
 *
 * PARITY GAPS: the header staging helpers (`writeMessageLength`, `writeChecksum`,
 * `addCryptoHeader`, ...) and the shared `OutputMessagePool` are owned by the
 * Netty encoders in this project, so only the queue-visible surface is ported.
 */
class OutputMessage(
    private val payload: ByteArray = ByteArray(0),
) {
    fun getLength(): Int = payload.size

    fun getOutputBuffer(): ByteArray = payload
}
