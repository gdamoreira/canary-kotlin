package org.opentibia.canary.network.protocol.player

import io.netty.buffer.ByteBuf
import org.opentibia.canary.chat.SpeakClasses
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.message.writeTibiaString

/**
 * Outbound chat frames for the Canary `ProtocolProfileId::Current` profile
 * (`protocol_new` / 15.x: `oldProtocol == false`), ported from
 * src/server/network/protocol/protocolgame.cpp:
 *
 *  - `ProtocolGame::sendCreatureSay`     (0xAA, protocolgame.cpp:8006-8046)
 *  - `ProtocolGame::sendToChannel`       (0xAA, protocolgame.cpp:8048-8094)
 *  - `ProtocolGame::sendPrivateMessage`  (0xAA, protocolgame.cpp:8096-8128)
 *  - `ProtocolGame::sendChannelMessage`  (0xAA, protocolgame.cpp:6237-6255)
 *
 * Every multi-byte field is Little-Endian and strings are `uint16` length plus
 * UTF-8. Canary keeps a function-local `static uint32_t statementId` per sender
 * and pre-increments it on every call; the port passes the caller-resolved
 * [statementId] in, mirroring how the other writers receive engine state. The
 * `isCipsoft860Profile` and `oldProtocol` `clientType` translations do not apply
 * to the current profile.
 */
object ChatWindowWriter {

    /** `sendCreatureSay`/`sendToChannel`/`sendPrivateMessage`/`sendChannelMessage` share opcode `0xAA`. */
    const val PACKET_CREATURE_SAY: Int = 0xAA

    /**
     * `sendCreatureSay` current-profile layout (protocolgame.cpp:8006-8046):
     * statement id, speaker name, the always-present `Show (Traded)` byte, the
     * speaker level (0 for non-player creatures), client talk type, position and
     * text.
     */
    fun writeCreatureSay(
        out: ByteBuf,
        statementId: Long,
        speakerName: String,
        speakerLevel: Int,
        type: SpeakClasses,
        position: Position,
        text: String,
    ) {
        out.writeByte(PACKET_CREATURE_SAY)
        out.writeIntLE(statementId.toInt())
        out.writeTibiaString(speakerName)
        out.writeByte(0x00)
        out.writeShortLE(speakerLevel)
        out.writeByte(type.value)
        out.writePosition(position)
        out.writeTibiaString(text)
    }

    /**
     * `sendToChannel` current-profile layout (protocolgame.cpp:8048-8094).
     *
     * A `null` [speakerName] models Canary's `!creature` branch (zero speaker id
     * plus the traded byte). `TALKTYPE_CHANNEL_R2` with a speaker downgrades to
     * `TALKTYPE_CHANNEL_R1` and also zeroes the speaker id. The traded byte is
     * omitted only when [statementId] wrapped to 0.
     */
    fun writeToChannel(
        out: ByteBuf,
        statementId: Long,
        speakerName: String?,
        speakerLevel: Int,
        type: SpeakClasses,
        text: String,
        channelId: Int,
    ) {
        out.writeByte(PACKET_CREATURE_SAY)
        out.writeIntLE(statementId.toInt())

        var clientType = type
        if (speakerName == null) {
            out.writeIntLE(0)
            if (statementId != 0L) {
                out.writeByte(0x00)
            }
        } else if (type == SpeakClasses.TALKTYPE_CHANNEL_R2) {
            out.writeIntLE(0)
            if (statementId != 0L) {
                out.writeByte(0x00)
            }
            clientType = SpeakClasses.TALKTYPE_CHANNEL_R1
        } else {
            out.writeTibiaString(speakerName)
            if (statementId != 0L) {
                out.writeByte(0x00)
            }
            out.writeShortLE(speakerLevel)
        }

        out.writeByte(clientType.value)
        out.writeShortLE(channelId)
        out.writeTibiaString(text)
    }

    /**
     * `sendPrivateMessage` current-profile layout (protocolgame.cpp:8096-8128):
     * a `null` [speakerName] models Canary's `!speaker` branch (zero speaker id).
     * The traded byte is omitted only when [statementId] wrapped to 0.
     */
    fun writePrivateMessage(
        out: ByteBuf,
        statementId: Long,
        speakerName: String?,
        speakerLevel: Int,
        type: SpeakClasses,
        text: String,
    ) {
        out.writeByte(PACKET_CREATURE_SAY)
        out.writeIntLE(statementId.toInt())

        if (speakerName != null) {
            out.writeTibiaString(speakerName)
            if (statementId != 0L) {
                out.writeByte(0x00)
            }
            out.writeShortLE(speakerLevel)
        } else {
            out.writeIntLE(0)
            if (statementId != 0L) {
                out.writeByte(0x00)
            }
        }

        out.writeByte(type.value)
        out.writeTibiaString(text)
    }

    /** `sendChannelMessage` (protocolgame.cpp:6237-6255): zero statement id, author, zero level. */
    fun writeChannelMessage(out: ByteBuf, author: String, type: SpeakClasses, text: String, channelId: Int) {
        out.writeByte(PACKET_CREATURE_SAY)
        out.writeIntLE(0)
        out.writeTibiaString(author)
        out.writeShortLE(0)
        out.writeByte(type.value)
        out.writeShortLE(channelId)
        out.writeTibiaString(text)
    }

    private fun ByteBuf.writePosition(pos: Position) {
        writeShortLE(pos.x)
        writeShortLE(pos.y)
        writeByte(pos.z)
    }
}
