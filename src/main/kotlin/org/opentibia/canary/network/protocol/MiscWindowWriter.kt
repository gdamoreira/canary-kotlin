package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import org.opentibia.canary.network.message.writeTibiaString

/**
 * Outbound miscellaneous protocol frames, ported from Canary
 * src/server/network/protocol/protocolgame.cpp:
 *
 *  - `ProtocolGame::sendPlayerTyping`        (0x38, protocolgame.cpp:11856-11866)
 *  - `ProtocolGame::sendMessageDialog`       (0xED/0x14, protocolgame.cpp:10289-10295)
 *  - `ProtocolGame::sendImbuementResult`     (0xED/0x01, protocolgame.cpp:10297-10303)
 *  - `ProtocolGame::closeImbuementWindow`    (0xEC, protocolgame.cpp:10305-10309)
 *  - `ProtocolGame::closeForgeWindow`        (0x89, protocolgame.cpp:7531-7535)
 *  - `ProtocolGame::sendExperienceTracker`   (0xAF, protocolgame.cpp:4526-4536)
 *
 * The `isOTCR` guard on `sendPlayerTyping`, the `!player`/`oldProtocol` guards
 * and the `sendForgeError` composition (`sendMessageDialog` + `closeForgeWindow`)
 * stay with the caller. Every multi-byte field is Little-Endian and strings are
 * `uint16` length plus UTF-8.
 */
object MiscWindowWriter {

    const val PACKET_PLAYER_TYPING: Int = 0x38
    const val PACKET_MESSAGE_DIALOG: Int = 0xED
    const val PACKET_CLOSE_IMBUEMENT_WINDOW: Int = 0xEC
    const val PACKET_CLOSE_FORGE_WINDOW: Int = 0x89
    const val PACKET_EXPERIENCE_TRACKER: Int = 0xAF

    /** `sendMessageDialog`'s unknown dialog type (protocolgame.cpp:10292). */
    const val MESSAGE_DIALOG_TYPE: Int = 0x14

    /** `sendImbuementResult`'s result type (protocolgame.cpp:10300). */
    const val IMBUEMENT_RESULT_TYPE: Int = 0x01

    /** `sendPlayerTyping` (protocolgame.cpp:11856-11866). */
    fun writePlayerTyping(out: ByteBuf, creatureId: Long, typing: Int) {
        out.writeByte(PACKET_PLAYER_TYPING)
        out.writeIntLE(creatureId.toInt())
        out.writeByte(typing)
    }

    /** `sendMessageDialog` (protocolgame.cpp:10289-10295). */
    fun writeMessageDialog(out: ByteBuf, message: String) {
        out.writeByte(PACKET_MESSAGE_DIALOG)
        out.writeByte(MESSAGE_DIALOG_TYPE)
        out.writeTibiaString(message)
    }

    /** `sendImbuementResult` (protocolgame.cpp:10297-10303). */
    fun writeImbuementResult(out: ByteBuf, message: String) {
        out.writeByte(PACKET_MESSAGE_DIALOG)
        out.writeByte(IMBUEMENT_RESULT_TYPE)
        out.writeTibiaString(message)
    }

    /** `closeImbuementWindow` (protocolgame.cpp:10305-10309). */
    fun writeCloseImbuementWindow(out: ByteBuf) {
        out.writeByte(PACKET_CLOSE_IMBUEMENT_WINDOW)
    }

    /** `closeForgeWindow` (protocolgame.cpp:7531-7535). */
    fun writeCloseForgeWindow(out: ByteBuf) {
        out.writeByte(PACKET_CLOSE_FORGE_WINDOW)
    }

    /** `sendExperienceTracker` (protocolgame.cpp:4526-4536). */
    fun writeExperienceTracker(out: ByteBuf, rawExp: Long, finalExp: Long) {
        out.writeByte(PACKET_EXPERIENCE_TRACKER)
        out.writeLongLE(rawExp)
        out.writeLongLE(finalExp)
    }
}
