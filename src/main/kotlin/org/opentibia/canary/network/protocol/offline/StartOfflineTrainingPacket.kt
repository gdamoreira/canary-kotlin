package org.opentibia.canary.network.protocol.offline

import io.netty.buffer.ByteBuf

/**
 * Self-contained command produced by the client opcode `0x74`
 * (`ProtocolGame::parseStartOfflineTraining`).
 *
 * Canary reads a single unsigned byte and currently discards it
 * (src/server/network/protocol/protocolgame.cpp:3254):
 *
 *     void ProtocolGame::parseStartOfflineTraining(NetworkMessage &msg) {
 *         msg.getByte(true);
 *     }
 *
 * [action] exposes that byte so callers can route it to the offline-training
 * engine core at [org.opentibia.canary.systems.offlinetraining.OfflineTraining]
 * without re-reading the buffer.
 */
data class StartOfflineTrainingCommand(
    val action: Int,
)

/**
 * Decoder for the offline-training start request (client opcode `0x74`).
 *
 * Mirrors Canary's `NetworkMessage::getByte(true)` read: exactly one unsigned
 * byte, no Little-Endian multi-byte fields.
 */
object StartOfflineTrainingPacketParser {

    /** Canary reads one byte unconditionally; too-short payloads cannot advance. */
    private const val BYTES = 1

    /**
     * Decodes [payload] into a [StartOfflineTrainingCommand].
     *
     * @return the decoded command, or `null` when the buffer holds fewer than
     *   [BYTES] readable bytes.
     */
    fun parse(payload: ByteBuf): StartOfflineTrainingCommand? {
        if (payload.readableBytes() < BYTES) {
            return null
        }

        val action = payload.readUnsignedByte().toInt()
        return StartOfflineTrainingCommand(action = action)
    }
}
