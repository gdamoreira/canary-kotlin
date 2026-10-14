package org.opentibia.canary.network.protocol.depot

import io.netty.buffer.ByteBuf
import org.opentibia.canary.items.Item
import org.opentibia.canary.network.protocol.GameWindowWriter

/**
 * Outbound depot-search serialization, ported from Canary
 * src/server/network/protocol/protocolgame.cpp:
 *
 *  - `ProtocolGame::sendDepotItems`             (0x94, protocolgame.cpp:10940-10962)
 *  - `ProtocolGame::sendCloseDepotSearch`       (0x9A, protocolgame.cpp:10964-10972)
 *  - `ProtocolGame::sendDepotSearchResultDetail`(0x99, protocolgame.cpp:10974-11005)
 *
 * The `!player`/`oldProtocol` guards stay with the caller. [writeDepotItems]
 * receives the `ItemsTierCountList` rows in the caller's ascending `std::map`
 * order and writes the tier byte only for tiers above 0 (as `tier - 1`). The
 * detail frame writes the tier byte only for an item with
 * `upgradeClassification > 0` and closes with the optional stash block. Every
 * multi-byte field is Little-Endian and the item payload is the shared
 * [GameWindowWriter.writeItem] body.
 */
object DepotSearchWriter {

    const val PACKET_DEPOT_ITEMS: Int = 0x94
    const val PACKET_DEPOT_SEARCH_RESULT_DETAIL: Int = 0x99
    const val PACKET_CLOSE_DEPOT_SEARCH: Int = 0x9A

    /** One `ItemsTierCountList` row (`std::pair<uint16_t itemId, uint8_t tier>`, `uint32_t count`). */
    data class DepotItemCount(
        val itemId: Int,
        val tier: Int,
        val count: Long,
    )

    /** `sendDepotItems` (protocolgame.cpp:10940-10962). */
    fun writeDepotItems(out: ByteBuf, entries: List<DepotItemCount>, count: Int) {
        out.writeByte(PACKET_DEPOT_ITEMS)
        out.writeShortLE(count)
        for (entry in entries) {
            out.writeShortLE(entry.itemId)
            if (entry.tier > 0) {
                out.writeByte(entry.tier - 1)
            }
            out.writeShortLE(entry.count.toInt() and 0xFFFF)
        }
    }

    /** `sendCloseDepotSearch` (protocolgame.cpp:10964-10972). */
    fun writeCloseDepotSearch(out: ByteBuf) {
        out.writeByte(PACKET_CLOSE_DEPOT_SEARCH)
    }

    /** `sendDepotSearchResultDetail` (protocolgame.cpp:10974-11005). */
    fun writeDepotSearchResultDetail(
        out: ByteBuf,
        itemId: Int,
        tier: Int,
        upgradeClassification: Int,
        depotCount: Long,
        depotItems: List<Item>,
        inboxCount: Long,
        inboxItems: List<Item>,
        stashCount: Long,
    ) {
        out.writeByte(PACKET_DEPOT_SEARCH_RESULT_DETAIL)
        out.writeShortLE(itemId)
        if (upgradeClassification > 0) {
            out.writeByte(tier)
        }

        out.writeIntLE(depotCount.toInt())
        out.writeByte(depotItems.size)
        for (item in depotItems) {
            GameWindowWriter.writeItem(out, item)
        }

        out.writeIntLE(inboxCount.toInt())
        out.writeByte(inboxItems.size)
        for (item in inboxItems) {
            GameWindowWriter.writeItem(out, item)
        }

        out.writeByte(if (stashCount > 0) 0x01 else 0x00)
        if (stashCount > 0) {
            out.writeShortLE(itemId)
            out.writeIntLE(stashCount.toInt())
        }
    }
}
