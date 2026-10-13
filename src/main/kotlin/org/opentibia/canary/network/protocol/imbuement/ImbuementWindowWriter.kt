package org.opentibia.canary.network.protocol.imbuement

import io.netty.buffer.ByteBuf
import org.opentibia.canary.network.message.writeTibiaString
import org.opentibia.canary.network.protocol.ImbuementPacketHandler

/**
 * Outbound imbuement window serialization, ported from Canary
 * src/server/network/protocol/protocolgame.cpp:
 *
 *  - `ProtocolGame::openImbuementWindow`              (0xEB, protocolgame.cpp:10162-10235)
 *  - `ProtocolGame::addImbuementInfo`                 (protocolgame.cpp:10078-10105)
 *  - `ProtocolGame::addAvailableImbuementsInfo`       (protocolgame.cpp:10135-10160)
 *
 * The `ProtocolProfileId::Current` profile enables
 * `ProtocolFeature::ImbuementWindow` (protocol_profile.cpp:202), so the frame is
 * always emitted; the Tibia 11.00 (`openTibia1100ImbuementWindow`) and
 * `oldProtocol` layouts are not modeled (PARITY GAPS). Every multi-byte field
 * is Little-Endian and strings are `uint16` length plus UTF-8.
 *
 * `neededItems` is a `phmap::flat_hash_map` in C++, so its iteration order is
 * unspecified; this serializer preserves the caller-provided order. The
 * `sendResourceBalance` frames Canary queues right after the window stay with
 * the caller, so each call here writes exactly one frame.
 */
object ImbuementWindowWriter {

    const val PACKET_IMBUEMENT_WINDOW: Int = 0xEB

    /** `ITEM_EMPTY_IMBUEMENT_SCROLL` (`src/utils/utils_definitions.hpp:713`). */
    const val ITEM_EMPTY_IMBUEMENT_SCROLL: Int = 51442

    /** One `Imbuement::getItems()` entry plus the resolved `ItemType::name`. */
    data class ImbuementCostItem(
        val id: Int,
        val name: String,
        val amount: Int,
    )

    /**
     * The `addImbuementInfo` payload. [baseName]/[name] reproduce Canary's
     * `fmt::format("{} {}", baseImbuement->name, imbuement->getName())` and
     * [baseId] is serialized as `baseId - 1`.
     */
    data class ImbuementInfoEntry(
        val id: Long,
        val baseName: String,
        val name: String,
        val description: String,
        val baseId: Int,
        val iconId: Int,
        val duration: Long,
        val items: List<ImbuementCostItem> = emptyList(),
        val price: Long = 0L,
    )

    /** One applied imbuement slot: the info block, remaining duration and remove cost. */
    data class ImbuementAppliedEntry(
        val info: ImbuementInfoEntry,
        val duration: Long,
        val removeCost: Long,
    )

    /**
     * The full 0xEB payload.
     *
     * @param action `ImbuementAction` (open/pick item/scroll).
     * @param hasEmptyScrolls `getItemTypeCount(51442) + getStashItemCount(51442) > 0`.
     * @param itemId `PickItem` only: the picked item id.
     * @param itemTier `PickItem` only: written when the item classification is
     *   greater than 0, `null` otherwise.
     * @param itemSlots `PickItem` only: `Item::getImbuementSlot()`.
     * @param applied `PickItem` only: one entry per slot, `null` for an empty slot.
     * @param hasFreeBackpackSlots `Scroll` only: `Player::getFreeBackpackSlots() > 0`.
     * @param available `getImbuements` result; [scrollAction] appends the empty
     *   scroll to every info item list like Canary does.
     * @param emptyScrollItemName `Item::items[51442].name` used for the appended
     *   scroll cost item.
     * @param neededItems item id to owned amount, in caller order.
     */
    data class ImbuementWindowFrame(
        val action: Int,
        val hasEmptyScrolls: Boolean,
        val itemId: Int = 0,
        val itemTier: Int? = null,
        val itemSlots: Int = 0,
        val applied: List<ImbuementAppliedEntry?> = emptyList(),
        val hasFreeBackpackSlots: Boolean = false,
        val available: List<ImbuementInfoEntry> = emptyList(),
        val scrollAction: Boolean = false,
        val emptyScrollItemName: String = "",
        val neededItems: Map<Int, Int> = emptyMap(),
    )

    /** `openImbuementWindow` current-profile branch (protocolgame.cpp:10182-10234). */
    fun writeImbuementWindow(out: ByteBuf, frame: ImbuementWindowFrame) {
        out.writeByte(PACKET_IMBUEMENT_WINDOW)
        out.writeByte(frame.action)
        out.writeByte(if (frame.hasEmptyScrolls) 0x01 else 0x00)

        when (frame.action) {
            ImbuementPacketHandler.IMBUEMENT_ACTION_OPEN -> out.writeShortLE(0)

            ImbuementPacketHandler.IMBUEMENT_ACTION_PICK_ITEM -> {
                out.writeShortLE(frame.itemId)
                frame.itemTier?.let { out.writeByte(it) }
                out.writeByte(frame.itemSlots)

                for (slot in 0 until frame.itemSlots) {
                    val applied = frame.applied.getOrNull(slot)
                    if (applied == null) {
                        out.writeByte(0x00)
                        continue
                    }

                    out.writeByte(0x01)
                    writeImbuementInfo(out, applied.info, scrollAction = false, frame.emptyScrollItemName)
                    out.writeIntLE(applied.duration.toInt())
                    out.writeIntLE(applied.removeCost.toInt())
                }

                writeAvailableImbuements(out, frame.available, scrollAction = false, frame.emptyScrollItemName)
            }

            ImbuementPacketHandler.IMBUEMENT_ACTION_SCROLL -> {
                out.writeByte(if (frame.hasFreeBackpackSlots) 0x01 else 0x00)
                out.writeByte(0)
                writeAvailableImbuements(out, frame.available, frame.scrollAction, frame.emptyScrollItemName)
            }
        }

        out.writeIntLE(frame.neededItems.size)
        for ((itemId, amount) in frame.neededItems) {
            out.writeShortLE(itemId)
            out.writeShortLE(amount)
        }
    }

    /** `addAvailableImbuementsInfo` (protocolgame.cpp:10135-10160). */
    private fun writeAvailableImbuements(
        out: ByteBuf,
        available: List<ImbuementInfoEntry>,
        scrollAction: Boolean,
        emptyScrollItemName: String,
    ) {
        out.writeShortLE(available.size)
        for (entry in available) {
            writeImbuementInfo(out, entry, scrollAction, emptyScrollItemName)
        }
    }

    /** `addImbuementInfo` (protocolgame.cpp:10078-10105). */
    private fun writeImbuementInfo(
        out: ByteBuf,
        entry: ImbuementInfoEntry,
        scrollAction: Boolean,
        emptyScrollItemName: String,
    ) {
        out.writeIntLE(entry.id.toInt())
        out.writeTibiaString("${entry.baseName} ${entry.name}")
        out.writeTibiaString(entry.description)
        out.writeByte(entry.baseId - 1)
        out.writeShortLE(entry.iconId)
        out.writeIntLE(entry.duration.toInt())

        val items = if (scrollAction) {
            entry.items + ImbuementCostItem(ITEM_EMPTY_IMBUEMENT_SCROLL, emptyScrollItemName, 1)
        } else {
            entry.items
        }
        out.writeByte(items.size)
        for (item in items) {
            out.writeShortLE(item.id)
            out.writeTibiaString(item.name)
            out.writeShortLE(item.amount)
        }

        out.writeIntLE(entry.price.toInt())
    }
}
