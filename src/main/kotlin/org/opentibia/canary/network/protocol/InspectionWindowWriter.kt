package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.items.Items
import org.opentibia.canary.items.ObjectCategory_t
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
 * The `oldProtocol` guard stays with the caller. Every multi-byte field is
 * Little-Endian and strings are `uint16` length plus UTF-8.
 */
object InspectionWindowWriter {

    const val PACKET_ITEM_INSPECTION: Int = 0x76

    /** `INSPECT_CYCLOPEDIA` flag written as the second payload byte. */
    private const val CYCLOPEDIA_FLAG = 0x01

    /**
     * `sendItemInspection` (protocolgame.cpp:2943-2973), including the
     * `Item::getDescriptions(it, item)` key/value list (src/items/item.cpp:1658-2298).
     *
     * `it` is the `Item::items[itemId]` registry entry; a `null` registry or an
     * unknown id behaves like the absent global lookup (the empty `ItemType`).
     * The item display name is `item->getName()` for an item instance and
     * `it.name` otherwise; [objectCategory] is the `g_game().getObjectCategory(item)`
     * seam behind the `Tier` description entry. The `Item::getDescriptions`
     * pairs are written in their resolved list order.
     */
    fun writeItemInspection(
        out: ByteBuf,
        playerId: Long,
        cyclopedia: Boolean,
        itemId: Int,
        itemCount: Int,
        item: Item? = null,
        items: Items? = null,
        objectCategory: ((Item) -> ObjectCategory_t)? = null,
    ) {
        out.writeByte(PACKET_ITEM_INSPECTION)
        out.writeByte(0x00)
        out.writeByte(if (cyclopedia) CYCLOPEDIA_FLAG else 0x00)
        out.writeIntLE(playerId.toInt())
        out.writeByte(0x01)

        val it = items?.get(itemId) ?: ItemType(id = 0, name = "")

        if (item != null) {
            out.writeTibiaString(item.getName())
            GameWindowWriter.writeItem(out, item, ItemWireInfo.from(item.type ?: it))
        } else {
            out.writeTibiaString(it.name)
            GameWindowWriter.writeItem(out, Item(itemId, itemCount), ItemWireInfo.from(it))
        }

        out.writeByte(0)

        val descriptions = Item.getDescriptions(it, item, items, objectCategory)
        out.writeByte(descriptions.size)
        for ((key, value) in descriptions) {
            out.writeTibiaString(key)
            out.writeTibiaString(value)
        }
    }
}
