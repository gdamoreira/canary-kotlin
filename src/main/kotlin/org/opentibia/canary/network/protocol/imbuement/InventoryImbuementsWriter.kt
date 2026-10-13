package org.opentibia.canary.network.protocol.imbuement

import io.netty.buffer.ByteBuf
import org.opentibia.canary.items.Item
import org.opentibia.canary.network.message.writeTibiaString
import org.opentibia.canary.network.protocol.GameWindowWriter

/**
 * Outbound inventory imbuements serialization, ported from Canary
 * src/server/network/protocol/protocolgame.cpp:
 *
 *  - `ProtocolGame::sendInventoryImbuements` (0x5D, protocolgame.cpp:10736-10799)
 *
 * The aggressive/protection-zone/fight-mode checks that gate the trailing
 * "active" byte (protocolgame.cpp:10774-10794) are resolved by the caller and
 * passed as [InventoryImbuementInfo.active]. Every multi-byte field is
 * Little-Endian and strings are `uint16` length plus UTF-8.
 */
object InventoryImbuementsWriter {

    const val PACKET_INVENTORY_IMBUEMENTS: Int = 0x5D

    /** One applied imbuement as `sendInventoryImbuements` serializes it. */
    data class InventoryImbuementInfo(
        val baseName: String,
        val name: String,
        val iconId: Int,
        val duration: Long,
        val active: Boolean,
    )

    /**
     * One inventory item. [imbuements] has one entry per `Item::getImbuementSlot`
     * (`null` for an empty slot), and [item] is serialized through
     * `GameWindowWriter.writeItem`.
     */
    data class InventoryImbuementEntry(
        val slot: Int,
        val item: Item,
        val imbuements: List<InventoryImbuementInfo?>,
    )

    /** `sendInventoryImbuements` (protocolgame.cpp:10741-10798). */
    fun writeInventoryImbuements(out: ByteBuf, entries: List<InventoryImbuementEntry>) {
        out.writeByte(PACKET_INVENTORY_IMBUEMENTS)
        out.writeByte(entries.size)

        for (entry in entries) {
            out.writeByte(entry.slot)
            GameWindowWriter.writeItem(out, entry.item)

            out.writeByte(entry.imbuements.size)
            for (imbuement in entry.imbuements) {
                if (imbuement == null) {
                    out.writeByte(0x00)
                    continue
                }

                out.writeByte(0x01)
                out.writeTibiaString("${imbuement.baseName} ${imbuement.name}")
                out.writeShortLE(imbuement.iconId)
                out.writeIntLE(imbuement.duration.toInt())
                out.writeByte(if (imbuement.active) 0x01 else 0x00)
            }
        }
    }
}
