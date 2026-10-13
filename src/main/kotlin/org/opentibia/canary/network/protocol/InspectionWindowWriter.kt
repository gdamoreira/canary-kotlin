package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import org.opentibia.canary.items.Item
import org.opentibia.canary.network.message.writeTibiaString

/**
 * Outbound item inspection serialization, ported from Canary
 * src/server/network/protocol/protocolgame.cpp:
 *
 *  - `ProtocolGame::sendItemInspection` (0x76, protocolgame.cpp:2943-2973)
 *
 * This is the response to `parseInspectionObject` for
 * `INSPECT_NORMALOBJECT`/`INSPECT_NPCTRADE`/`INSPECT_CYCLOPEDIA`
 * (protocolgame.cpp:2913-2931); the cyclopedia character inspection
 * (`CYCLOPEDIA_CHARACTERINFO_INSPECTION`) lives in `CyclopediaCharacterWriter`.
 * `Item::getDescriptions` results and the item display name are passed in by
 * the caller; the `oldProtocol` guard stays with the caller. Every multi-byte
 * field is Little-Endian and strings are `uint16` length plus UTF-8.
 */
object InspectionWindowWriter {

    const val PACKET_ITEM_INSPECTION: Int = 0x76

    /** `INSPECT_CYCLOPEDIA` flag written as the second payload byte. */
    private const val CYCLOPEDIA_FLAG = 0x01

    /**
     * `sendItemInspection` (protocolgame.cpp:2943-2973).
     *
     * When [item] is null Canary resolves the `ItemType` from [itemId] and calls
     * `AddItem(msg, it.id, itemCount, 0)`; [itemInfo] carries the `ItemType`
     * subtype flags for that path. [descriptions] is the `Item::getDescriptions`
     * pair list in caller order.
     */
    fun writeItemInspection(
        out: ByteBuf,
        playerId: Long,
        cyclopedia: Boolean,
        itemId: Int,
        itemCount: Int,
        itemName: String,
        item: Item? = null,
        itemInfo: ItemWireInfo = ItemWireInfo.from(item?.type),
        descriptions: List<Pair<String, String>> = emptyList(),
    ) {
        out.writeByte(PACKET_ITEM_INSPECTION)
        out.writeByte(0x00)
        out.writeByte(if (cyclopedia) CYCLOPEDIA_FLAG else 0x00)
        out.writeIntLE(playerId.toInt())
        out.writeByte(0x01)

        out.writeTibiaString(itemName)
        if (item != null) {
            GameWindowWriter.writeItem(out, item, itemInfo)
        } else {
            GameWindowWriter.writeItem(out, Item(itemId, itemCount), itemInfo)
        }

        out.writeByte(0)
        out.writeByte(descriptions.size)
        for ((key, value) in descriptions) {
            out.writeTibiaString(key)
            out.writeTibiaString(value)
        }
    }
}
