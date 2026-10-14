package org.opentibia.canary.network.protocol.container

import io.netty.buffer.ByteBuf
import org.opentibia.canary.items.Item
import org.opentibia.canary.network.protocol.GameWindowWriter

/**
 * Outbound container item serialization, ported from Canary
 * src/server/network/protocol/protocolgame.cpp:
 *
 *  - `ProtocolGame::sendAddContainerItem`    (0x70, protocolgame.cpp:8934-8941)
 *  - `ProtocolGame::sendUpdateContainerItem` (0x71, protocolgame.cpp:8943-8950)
 *  - `ProtocolGame::sendRemoveContainerItem` (0x72, protocolgame.cpp:8952-8963)
 *
 * The three frames share the `cid` + `uint16_t slot` header and the shared
 * `AddItem` payload ([GameWindowWriter.writeItem]); the remove frame falls back
 * to a `uint16_t 0x00` when there is no last item. Every multi-byte field is
 * Little-Endian.
 */
object ContainerItemWriter {

    const val PACKET_ADD_CONTAINER_ITEM: Int = 0x70
    const val PACKET_UPDATE_CONTAINER_ITEM: Int = 0x71
    const val PACKET_REMOVE_CONTAINER_ITEM: Int = 0x72

    /** `sendAddContainerItem` (protocolgame.cpp:8934-8941). */
    fun writeAddContainerItem(out: ByteBuf, cid: Int, slot: Int, item: Item) {
        out.writeByte(PACKET_ADD_CONTAINER_ITEM)
        out.writeByte(cid)
        out.writeShortLE(slot)
        GameWindowWriter.writeItem(out, item)
    }

    /** `sendUpdateContainerItem` (protocolgame.cpp:8943-8950). */
    fun writeUpdateContainerItem(out: ByteBuf, cid: Int, slot: Int, item: Item) {
        out.writeByte(PACKET_UPDATE_CONTAINER_ITEM)
        out.writeByte(cid)
        out.writeShortLE(slot)
        GameWindowWriter.writeItem(out, item)
    }

    /** `sendRemoveContainerItem` (protocolgame.cpp:8952-8963). */
    fun writeRemoveContainerItem(out: ByteBuf, cid: Int, slot: Int, lastItem: Item?) {
        out.writeByte(PACKET_REMOVE_CONTAINER_ITEM)
        out.writeByte(cid)
        out.writeShortLE(slot)
        if (lastItem != null) {
            GameWindowWriter.writeItem(out, lastItem)
        } else {
            out.writeShortLE(0x00)
        }
    }
}
