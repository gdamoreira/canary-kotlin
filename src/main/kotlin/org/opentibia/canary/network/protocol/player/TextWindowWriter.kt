package org.opentibia.canary.network.protocol.player

import io.netty.buffer.ByteBuf
import java.nio.charset.StandardCharsets
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemAttribute
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.network.message.writeTibiaString
import org.opentibia.canary.network.protocol.GameWindowWriter
import org.opentibia.canary.network.protocol.ItemWireInfo
import org.opentibia.canary.utils.Tools

/**
 * Outbound editable-text window frames for the Canary `ProtocolProfileId::Current`
 * profile (`protocol_new` / 15.x: `oldProtocol == false`), ported from
 * src/server/network/protocol/protocolgame.cpp:
 *
 *  - `ProtocolGame::sendTextWindow(windowTextId, item, maxlen, canWrite)` (0x96, protocolgame.cpp:8965-8999)
 *  - `ProtocolGame::sendTextWindow(windowTextId, itemId, text)`          (0x96, protocolgame.cpp:9001-9016)
 *  - `ProtocolGame::AddItem(msg, id, count, tier)` static-type layout    (protocolgame.cpp:706-775)
 *
 * The read-only branch writes the TEXT length twice exactly like Canary
 * (`add<uint16_t>(text.size())` followed by `addString(text)`,
 * protocolgame.cpp:8980-8982). `formatDateShort` (tools.cpp:506-514) is shared
 * through [Tools]. The `!player`/old-protocol gates and the
 * `Player::sendTextWindow` window-id counter stay with the caller; the current
 * profile always emits the `Show (Traded)` byte.
 */
object TextWindowWriter {

    const val PACKET_TEXT_WINDOW: Int = 0x96

    /**
     * `sendTextWindow` item overload (protocolgame.cpp:8965-8999). `writer` and
     * `date` come from the item's `WRITER`/`DATE` attributes, and [canWrite]
     * selects the `maxlen`/TEXT layout.
     */
    fun writeTextWindow(
        out: ByteBuf,
        windowTextId: Long,
        item: Item,
        maxlen: Int,
        canWrite: Boolean,
        info: ItemWireInfo = ItemWireInfo.from(item.type),
    ) {
        out.writeByte(PACKET_TEXT_WINDOW)
        out.writeIntLE(windowTextId.toInt())
        GameWindowWriter.writeItem(out, item, info)

        val text = item.getAttribute(ItemAttribute.TEXT) as? String ?: ""
        if (canWrite) {
            out.writeShortLE(maxlen)
            out.writeTibiaString(text)
        } else {
            out.writeShortLE(text.toByteArray(StandardCharsets.UTF_8).size)
            out.writeTibiaString(text)
        }

        val writer = item.getAttribute(ItemAttribute.WRITER) as? String ?: ""
        if (writer.isNotEmpty()) {
            out.writeTibiaString(writer)
        } else {
            out.writeShortLE(0)
        }

        out.writeByte(0x00)

        val writtenDate = item.getAttribute(ItemAttribute.DATE) as? Long ?: 0L
        if (writtenDate != 0L) {
            out.writeTibiaString(Tools.formatDateShort(writtenDate))
        } else {
            out.writeShortLE(0)
        }
    }

    /**
     * `sendTextWindow` item-id overload (protocolgame.cpp:9001-9016). Canary
     * resolves `Item::items[itemId]`; the port receives that registry entry as
     * [itemType]. The item is serialized with `AddItem(msg, itemId, 1, 0)`, so
     * the tier byte is 0.
     */
    fun writeTextWindow(out: ByteBuf, windowTextId: Long, itemType: ItemType, text: String) {
        out.writeByte(PACKET_TEXT_WINDOW)
        out.writeIntLE(windowTextId.toInt())
        writeItemType(out, itemType, count = 1, tier = 0)
        out.writeShortLE(text.toByteArray(StandardCharsets.UTF_8).size)
        out.writeTibiaString(text)
        out.writeShortLE(0)
        out.writeByte(0x00)
        out.writeShortLE(0)
    }

    /**
     * `AddItem(NetworkMessage&, uint16_t id, uint8_t count, uint8_t tier)`
     * current-profile path (protocolgame.cpp:706-775): `oldProtocol` and
     * `isOTCR` are false, so only the static type flags reach the wire.
     */
    private fun writeItemType(out: ByteBuf, type: ItemType, count: Int, tier: Int) {
        out.writeShortLE(type.id)

        if (type.isStackable) {
            out.writeByte(count)
        }

        if (type.isSplash || type.isFluidContainer) {
            out.writeByte(count)
        }

        if (type.isContainer) {
            out.writeByte(0x00)
        }

        if (type.isPodium) {
            out.writeShortLE(0)
            out.writeShortLE(0)
            out.writeShortLE(0)
            out.writeByte(2)
            out.writeByte(0x01)
        }

        if (type.upgradeClassification > 0) {
            out.writeByte(tier)
        }

        if (type.expire || type.expireStop || type.clockExpire) {
            out.writeIntLE(type.decayTime.toInt())
            out.writeByte(0x01)
        }

        if (type.wearOut) {
            out.writeIntLE(type.charges)
            out.writeByte(0x01)
        }

        if (type.isWrapKit) {
            out.writeShortLE(0x00)
        }
    }
}
