package org.opentibia.canary.network.protocol.vip

import io.netty.buffer.ByteBuf
import org.opentibia.canary.creatures.players.vip.VipStatus

/**
 * Outbound VIP status serialization, ported from Canary
 * src/server/network/protocol/protocolgame.cpp:
 *
 *  - `ProtocolGame::sendUpdatedVIPStatus` (0xD3, protocolgame.cpp:9355-9372)
 *
 * `sendVIP` (0xD2) and `sendVIPGroups` (0xD4) already live in
 * `SocialWindowWriter`; this file only adds the status-update frame. Every
 * multi-byte field is Little-Endian. The `oldProtocol && version < 980`
 * branch (`VipStatus_t::Offline` selects 0xD4 and no status byte is written)
 * and the `oldProtocol && Training` suppression are reproduced exactly; the
 * `ProtocolProfileId::Current` caller uses the default arguments.
 */
object VipWindowWriter {

    const val PACKET_VIP_STATUS: Int = 0xD3
    const val PACKET_VIP_STATUS_OLD_OFFLINE: Int = 0xD4

    /** Client protocol version at which the 0xD3 status byte was introduced. */
    private const val VIP_STATUS_BYTE_VERSION = 980

    /** The `ProtocolProfileId::Current` protocol version (15.x). */
    private const val CURRENT_VERSION = 1500

    /**
     * `sendUpdatedVIPStatus` (protocolgame.cpp:9355-9372).
     *
     * [oldProtocol] is `ProtocolGame::oldProtocol` and [version] the client
     * protocol version; both default to the current profile so the status byte
     * is always emitted.
     */
    fun writeUpdatedVipStatus(
        out: ByteBuf,
        guid: Long,
        newStatus: VipStatus,
        oldProtocol: Boolean = false,
        version: Int = CURRENT_VERSION,
    ) {
        if (oldProtocol && newStatus == VipStatus.Training) {
            return
        }

        if (oldProtocol && version < VIP_STATUS_BYTE_VERSION) {
            out.writeByte(if (newStatus == VipStatus.Offline) PACKET_VIP_STATUS_OLD_OFFLINE else PACKET_VIP_STATUS)
            out.writeIntLE(guid.toInt())
            return
        }

        out.writeByte(PACKET_VIP_STATUS)
        out.writeIntLE(guid.toInt())
        out.writeByte(newStatus.value)
    }
}
