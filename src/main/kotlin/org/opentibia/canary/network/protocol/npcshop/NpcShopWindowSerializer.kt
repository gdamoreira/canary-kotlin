package org.opentibia.canary.network.protocol.npcshop

import io.netty.buffer.ByteBuf
import org.opentibia.canary.network.message.writeTibiaString

/**
 * One NPC shop entry, mirroring the Canary `ShopBlock`
 * (src/creatures/creatures_definitions.hpp:1804).
 *
 * Prices are `uint32_t` on the wire; the all-ones value `0xFFFFFFFF` means
 * "no price" and is serialized as zero (`AddShopItem`, protocolgame.cpp:10669).
 * When [itemName] is empty the serializer falls back to the item type name.
 */
data class ShopBlock(
    val itemId: Int,
    val itemName: String = "",
    val itemSubType: Int = 0,
    val itemBuyPrice: Long = 0,
    val itemSellPrice: Long = 0,
    val itemStorageKey: Int = 0,
    val itemStorageValue: Int = 0,
)

/**
 * Outbound NPC shop serialization, ported from Canary
 * src/server/network/protocol/protocolgame.cpp:
 *  - `ProtocolGame::sendShop`          (0x7A, protocolgame.cpp:6532)
 *  - `ProtocolGame::sendSaleItemList`  (0x7B, protocolgame.cpp:6669)
 *  - `ProtocolGame::sendCloseShop`     (0x7C, protocolgame.cpp:6580)
 *  - `ProtocolGame::AddShopItem`       (protocolgame.cpp:10647)
 *  - `ProtocolGame::AddHiddenShopItem` (protocolgame.cpp:10637)
 *
 * The item-type fields Canary reads from `Item::items` (name fallback, weight,
 * splash/fluid subtype flag and the player's storage gate) are supplied as
 * provider callbacks so callers without an item registry can use the
 * zero-value defaults.
 *
 * Parity note: `sendSaleItemList` also emits leading `0xEE` resource-balance
 * frames through `ProtocolGame::sendResourceBalance` (protocolgame.cpp:6628);
 * those are a separate outbound packet keyed on the protocol profile and are
 * not part of the `0x7B` shop frame serialized here.
 */
object NpcShopWindowSerializer {

    const val PACKET_SHOP: Int = 0x7A
    const val PACKET_SALE_ITEM_LIST: Int = 0x7B
    const val PACKET_CLOSE_SHOP: Int = 0x7C

    /** `ITEM_GOLD_COIN` (src/utils/utils_definitions.hpp:608); `Npc::currencyId` default. */
    const val ITEM_GOLD_COIN: Int = 3031

    /** `std::numeric_limits<uint32_t>::max()` price sentinel written as zero. */
    private const val HIDDEN_PRICE: Long = 0xFFFFFFFFL

    /** `sendShop` caps the item count at the byte maximum on the Cipsoft 8.60 layout. */
    private const val CIPSOFT860_ITEM_LIMIT: Int = 0xFF

    /** `sendShop` caps the item count at the `uint16_t` maximum otherwise. */
    private const val SHOP_ITEM_LIMIT: Int = 0xFFFF

    /** `sendSaleItemList` caps the entry count at the byte maximum on the old profile. */
    private const val OLD_SALE_ITEM_LIMIT: Int = 0xFF

    /** `sendSaleItemList` caps the entry count at the `uint16_t` maximum otherwise. */
    private const val SALE_ITEM_LIMIT: Int = 0xFFFF

    /**
     * protocolgame.cpp:6532-6578.
     *
     * The current profile writes the NPC currency (and an always-empty currency
     * name) before the item count; the Cipsoft 8.60 layout writes the item count
     * as a single byte. Entries beyond the layout limit are truncated, and each
     * hidden entry still occupies a full empty block
     * ([writeHiddenShopItem]).
     */
    fun writeShop(
        out: ByteBuf,
        npcName: String,
        shopBlocks: List<ShopBlock>,
        oldProtocol: Boolean = false,
        cipsoft860ShopLayout: Boolean = false,
        currency: Int = ITEM_GOLD_COIN,
        hideSellItemsNotInInventory: Boolean = false,
        inventoryMap: Map<Int, Int> = emptyMap(),
        storageValue: (Int) -> Int = { 0 },
        itemTypeName: (Int) -> String = { "" },
        itemTypeWeight: (Int) -> Long = { 0 },
        itemTypeIsSplashOrFluid: (Int) -> Boolean = { false },
    ) {
        out.writeByte(PACKET_SHOP)
        out.writeTibiaString(npcName)

        if (!oldProtocol) {
            out.writeShortLE(currency)
            out.writeTibiaString("")
        }

        val itemsToSend = minOf(shopBlocks.size, if (cipsoft860ShopLayout) CIPSOFT860_ITEM_LIMIT else SHOP_ITEM_LIMIT)
        if (cipsoft860ShopLayout) {
            out.writeByte(itemsToSend)
        } else {
            out.writeShortLE(itemsToSend)
        }

        for (block in shopBlocks.take(itemsToSend)) {
            if (hideSellItemsNotInInventory &&
                !inventoryMap.containsKey(block.itemId) &&
                block.itemSellPrice > 0 &&
                block.itemBuyPrice == 0L
            ) {
                out.writeHiddenShopItem()
                continue
            }

            out.writeShopBlock(block, storageValue, itemTypeName, itemTypeWeight, itemTypeIsSplashOrFluid)
        }
    }

    /**
     * protocolgame.cpp:6669-6736.
     *
     * Only entries with a non-zero sell price that the player owns are written,
     * as `itemId` plus the inventory count clamped to the profile's count width.
     * The entry count is backpatched after enumeration, exactly like Canary's
     * `setBufferPosition` round trip.
     */
    fun writeSaleItemList(
        out: ByteBuf,
        shopBlocks: List<ShopBlock>,
        inventoryMap: Map<Int, Int>,
        oldProtocol: Boolean = false,
        money: Long = 0,
        bankBalance: Long = 0,
    ) {
        out.writeByte(PACKET_SALE_ITEM_LIST)
        if (oldProtocol) {
            out.writeLongLE(money + bankBalance)
        }

        val countIndex = out.writerIndex()
        if (oldProtocol) {
            out.writeByte(0)
        } else {
            out.writeShortLE(0)
        }

        val limit = if (oldProtocol) OLD_SALE_ITEM_LIMIT else SALE_ITEM_LIMIT
        var itemsToSend = 0
        for (block in shopBlocks) {
            if (block.itemSellPrice == 0L) {
                continue
            }

            val inventoryCount = inventoryMap[block.itemId] ?: continue

            out.writeShortLE(block.itemId)
            if (oldProtocol) {
                out.writeByte(minOf(inventoryCount, 0xFF))
            } else {
                out.writeShortLE(minOf(inventoryCount, 0xFFFF))
            }

            itemsToSend++
            if (itemsToSend >= limit) {
                break
            }
        }

        if (oldProtocol) {
            out.setByte(countIndex, itemsToSend)
        } else {
            out.setShortLE(countIndex, itemsToSend)
        }
    }

    /** protocolgame.cpp:6580-6584. A frame carrying only the opcode. */
    fun writeCloseShop(out: ByteBuf) {
        out.writeByte(PACKET_CLOSE_SHOP)
    }

    /** protocolgame.cpp:10647-10671. */
    private fun ByteBuf.writeShopBlock(
        block: ShopBlock,
        storageValue: (Int) -> Int,
        itemTypeName: (Int) -> String,
        itemTypeWeight: (Int) -> Long,
        itemTypeIsSplashOrFluid: (Int) -> Boolean,
    ) {
        if (block.itemStorageKey != 0 && storageValue(block.itemStorageKey) < block.itemStorageValue) {
            writeHiddenShopItem()
            return
        }

        writeShortLE(block.itemId)
        writeByte(if (itemTypeIsSplashOrFluid(block.itemId)) block.itemSubType else 0)
        writeTibiaString(block.itemName.ifEmpty { itemTypeName(block.itemId) })
        writeIntLE(itemTypeWeight(block.itemId).toInt())
        writeIntLE(priceOrZero(block.itemBuyPrice))
        writeIntLE(priceOrZero(block.itemSellPrice))
    }

    /** protocolgame.cpp:10637-10645. */
    private fun ByteBuf.writeHiddenShopItem() {
        writeShortLE(0)
        writeByte(0)
        writeTibiaString("")
        writeIntLE(0)
        writeIntLE(0)
        writeIntLE(0)
    }

    private fun priceOrZero(price: Long): Int =
        if (price == HIDDEN_PRICE) 0 else price.toInt()
}
