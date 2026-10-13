package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import org.opentibia.canary.chat.ChannelEvent
import org.opentibia.canary.network.message.writeTibiaString

/**
 * Outbound channel frames for the Canary `ProtocolProfileId::Current` profile,
 * ported from src/server/network/protocol/protocolgame.cpp:
 *
 *  - `ProtocolGame::sendOpenPrivateChannel` (0xAD, protocolgame.cpp:4519-4524)
 *  - `ProtocolGame::sendChannelEvent`       (0xF3, protocolgame.cpp:4538-4545)
 *
 * The remaining channel senders (`sendChannelsDialog` 0xAB, `sendChannel`
 * 0xAC, `sendCreatePrivateChannel` 0xB2, `sendClosePrivate` 0xB3) live in
 * `SocialWindowWriter`. Every multi-byte field is Little-Endian.
 */
object ChannelWindowWriter {

    const val PACKET_OPEN_PRIVATE_CHANNEL: Int = 0xAD
    const val PACKET_CHANNEL_EVENT: Int = 0xF3

    /** `sendOpenPrivateChannel` (protocolgame.cpp:4519-4524). */
    fun writeOpenPrivateChannel(out: ByteBuf, receiver: String) {
        out.writeByte(PACKET_OPEN_PRIVATE_CHANNEL)
        out.writeTibiaString(receiver)
    }

    /**
     * `sendChannelEvent` (protocolgame.cpp:4538-4545). [channelEvent] is
     * `ChannelEvent_t` and is written as its raw byte value.
     */
    fun writeChannelEvent(out: ByteBuf, channelId: Int, playerName: String, channelEvent: ChannelEvent) {
        out.writeByte(PACKET_CHANNEL_EVENT)
        out.writeShortLE(channelId)
        out.writeTibiaString(playerName)
        out.writeByte(channelEvent.value)
    }
}
