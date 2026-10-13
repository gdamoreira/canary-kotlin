package org.opentibia.canary.network.protocol.market

import io.netty.buffer.ByteBuf
import org.opentibia.canary.economy.market.MarketOfferType
import org.opentibia.canary.network.message.writeTibiaString

/**
 * Data required to serialize the market entry window (client opcode 0xF6,
 * `ProtocolGame::sendMarketEnter`, src/server/network/protocol/protocolgame.cpp:6738).
 *
 * [lockerItems] mirrors `Player::requestLockerItems`: itemId -> tier -> count;
 * `null` means the player has no depot locker, which Canary serializes as a zero
 * entry count (wire-wise identical to an empty map).
 */
data class MarketEnterWindow(
    val bankBalance: Long = 0,
    val playerOfferCount: Int = 0,
    val lockerItems: Map<Int, Map<Int, Int>>? = null,
)

/**
 * A market offer as carried by the browse frames, mirroring the Canary
 * `MarketOffer` struct (src/creatures/creatures_definitions.hpp:1660). The item
 * browse frame only writes [timestamp], [counter], [amount], [price] and
 * [playerName]; the own-offers frame writes [itemId] and [tier] instead of the
 * player name.
 */
data class MarketOffer(
    val timestamp: Long,
    val counter: Int,
    val amount: Int,
    val itemId: Int = 0,
    val tier: Int = 0,
    val price: Long = 0,
    val playerName: String = "",
)

/**
 * `MarketOfferState_t` (src/creatures/creatures_definitions.hpp:358). The
 * protocol writes the raw byte; [AcceptedEx] is normalized to [Accepted] by
 * `IOMarket::getOwnHistory` before it reaches the serializer.
 */
enum class MarketOfferState(val value: Int) {
    Active(0),
    Cancelled(1),
    Expired(2),
    Accepted(3),
    AcceptedEx(255),
}

/** A market history entry (`HistoryMarketOffer`, creatures_definitions.hpp:1695). */
data class HistoryMarketOffer(
    val timestamp: Long,
    val itemId: Int,
    val tier: Int = 0,
    val amount: Int,
    val price: Long,
    val state: MarketOfferState,
)

/**
 * A full offer used by the accept and cancel frames, mirroring the Canary
 * `MarketOfferEx` struct (creatures_definitions.hpp:1668).
 */
data class MarketOfferEx(
    val type: MarketOfferType,
    val itemId: Int,
    val tier: Int = 0,
    val timestamp: Long,
    val counter: Int,
    val amount: Int,
    val price: Long,
    val playerName: String = "",
)

/**
 * `MarketStatistics` (src/io/io_definitions.hpp:80) as written by the market
 * detail frame: transaction count plus total/highest/lowest prices.
 */
data class MarketStatistics(
    val numTransactions: Long,
    val totalPrice: Long,
    val highestPrice: Long,
    val lowestPrice: Long,
)

/**
 * Market item detail fields (`ProtocolGame::sendMarketDetail`,
 * protocolgame.cpp:7537). Canary derives every optional description from the
 * `ItemType` registry, so callers supply the already formatted strings in
 * Canary order; an empty string encodes as the `uint16_t(0x00)` placeholder
 * Canary writes for absent values.
 *
 * The `uint8_t tier` byte is written only when [upgradeClassification] is
 * greater than zero and the profile is not the old one.
 */
data class MarketDetail(
    val itemId: Int,
    val tier: Int = 0,
    val upgradeClassification: Int = 0,
    val armor: String = "",
    val attack: String = "",
    val containerCapacity: String = "",
    val defense: String = "",
    val description: String = "",
    val decay: String = "",
    val absorb: String = "",
    val minReqLevel: String = "",
    val minReqMagicLevel: String = "",
    val vocation: String = "",
    val runeSpellName: String = "",
    val skills: String = "",
    val charges: String = "",
    val weaponName: String = "",
    val weight: String = "",
    val augments: String = "",
    val imbuementSlot: String = "",
    val magicShieldCapacity: String = "",
    val cleave: String = "",
    val reflect: String = "",
    val perfectShot: String = "",
    val upgradeClassificationDescription: String = "",
    val elementalBond: String = "",
    val mantra: String = "",
    val imbuementEffect: String = "",
    val tierDescription: String = "",
    val purchaseStatistics: MarketStatistics? = null,
    val saleStatistics: MarketStatistics? = null,
)

/**
 * Outbound market serialization, ported from Canary
 * src/server/network/protocol/protocolgame.cpp:
 *  - `ProtocolGame::sendMarketEnter`          (0xF6, protocolgame.cpp:6738)
 *  - `ProtocolGame::sendMarketLeave`          (0xF7, protocolgame.cpp:6855)
 *  - `ProtocolGame::sendMarketDetail`         (0xF8, protocolgame.cpp:7537)
 *  - `ProtocolGame::sendMarketBrowseItem`     (0xF9, protocolgame.cpp:6865)
 *  - `ProtocolGame::sendMarketAcceptOffer`    (0xF9, protocolgame.cpp:6908)
 *  - `ProtocolGame::sendMarketBrowseOwnOffers`(0xF9, protocolgame.cpp:6949)
 *  - `ProtocolGame::sendMarketCancelOffer`    (0xF9, protocolgame.cpp:6993)
 *  - `ProtocolGame::sendMarketBrowseOwnHistory`(0xF9, protocolgame.cpp:7037)
 *
 * The optional [upgradeClassification] callback supplies
 * `ItemType::upgradeClassification` for an item id; callers without an item
 * registry can leave it at the default `0`, which reproduces Canary's behavior
 * for items that carry no tier.
 */
object MarketWindowSerializer {

    const val PACKET_MARKET_ENTER: Int = 0xF6
    const val PACKET_MARKET_LEAVE: Int = 0xF7
    const val PACKET_MARKET_DETAIL: Int = 0xF8
    const val PACKET_MARKET_BROWSE: Int = 0xF9

    /** `sendMarketEnter` caps the serialized locker entry count at `uint16_t` max. */
    private const val MAX_ENTER_ENTRIES: Int = 0xFFFF

    /**
     * `sendMarketBrowseOwnHistory` allows each side to grow past 810 only while
     * the other side is under 810 (protocolgame.cpp:7040).
     */
    private const val HISTORY_SIDE_LIMIT: Int = 810

    /** protocolgame.cpp:6738-6807. Entries are emitted item id then tier, both ascending. */
    fun writeMarketEnter(
        out: ByteBuf,
        window: MarketEnterWindow,
        oldProtocol: Boolean = false,
        upgradeClassification: (Int) -> Int = { 0 },
    ) {
        out.writeByte(PACKET_MARKET_ENTER)
        if (oldProtocol) {
            out.writeLongLE(window.bankBalance)
        }
        out.writeByte(window.playerOfferCount.coerceIn(0, 0xFF))

        val lockerItems = window.lockerItems
        if (lockerItems == null) {
            out.writeShortLE(0)
            return
        }

        val entries = ArrayList<Triple<Int, Int, Int>>()
        entryLoop@ for ((itemId, tierCounts) in lockerItems.ascending()) {
            for ((tier, count) in tierCounts.ascending()) {
                if (entries.size >= MAX_ENTER_ENTRIES) {
                    break@entryLoop
                }
                entries += Triple(itemId, tier, count)
            }
        }

        // Canary backpatches the count after enumerating the locker.
        out.writeShortLE(entries.size)
        for ((itemId, tier, count) in entries) {
            out.writeShortLE(itemId)
            if (!oldProtocol && upgradeClassification(itemId) > 0) {
                out.writeByte(tier)
            }
            out.writeShortLE(count)
        }
    }

    /** protocolgame.cpp:6855-6863. A frame carrying only the opcode. */
    fun writeMarketLeave(out: ByteBuf) {
        out.writeByte(PACKET_MARKET_LEAVE)
    }

    /** protocolgame.cpp:6865-6906. Item browse offers never carry the item id or tier. */
    fun writeMarketBrowseItem(
        out: ByteBuf,
        itemId: Int,
        buyOffers: List<MarketOffer>,
        sellOffers: List<MarketOffer>,
        tier: Int = 0,
        oldProtocol: Boolean = false,
        upgradeClassification: (Int) -> Int = { 0 },
    ) {
        out.writeByte(PACKET_MARKET_BROWSE)
        if (!oldProtocol) {
            out.writeByte(MarketPacketParser.MARKET_REQUEST_ITEM_BROWSE)
        }
        out.writeShortLE(itemId)
        if (!oldProtocol && upgradeClassification(itemId) > 0) {
            out.writeByte(tier)
        }

        out.writeIntLE(buyOffers.size)
        for (offer in buyOffers) {
            out.writeBrowseOffer(offer, oldProtocol)
        }

        out.writeIntLE(sellOffers.size)
        for (offer in sellOffers) {
            out.writeBrowseOffer(offer, oldProtocol)
        }
    }

    /** protocolgame.cpp:6908-6947. */
    fun writeMarketAcceptOffer(
        out: ByteBuf,
        offer: MarketOfferEx,
        oldProtocol: Boolean = false,
        upgradeClassification: (Int) -> Int = { 0 },
    ) {
        out.writeByte(PACKET_MARKET_BROWSE)
        if (!oldProtocol) {
            out.writeByte(MarketPacketParser.MARKET_REQUEST_ITEM_BROWSE)
        }
        out.writeShortLE(offer.itemId)
        if (!oldProtocol && upgradeClassification(offer.itemId) > 0) {
            out.writeByte(offer.tier)
        }

        if (offer.type == MarketOfferType.Buy) {
            out.writeIntLE(1)
            out.writeMarketOfferBody(offer, oldProtocol)
            out.writeIntLE(0)
        } else {
            out.writeIntLE(0)
            out.writeIntLE(1)
            out.writeMarketOfferBody(offer, oldProtocol)
        }
    }

    /** protocolgame.cpp:6949-6991. */
    fun writeMarketBrowseOwnOffers(
        out: ByteBuf,
        buyOffers: List<MarketOffer>,
        sellOffers: List<MarketOffer>,
        oldProtocol: Boolean = false,
        upgradeClassification: (Int) -> Int = { 0 },
    ) {
        out.writeByte(PACKET_MARKET_BROWSE)
        out.writeOwnOffersMarker(oldProtocol)

        out.writeIntLE(buyOffers.size)
        for (offer in buyOffers) {
            out.writeOwnOffer(offer, oldProtocol, upgradeClassification)
        }

        out.writeIntLE(sellOffers.size)
        for (offer in sellOffers) {
            out.writeOwnOffer(offer, oldProtocol, upgradeClassification)
        }
    }

    /**
     * protocolgame.cpp:6993-7035.
     *
     * A buy cancellation appends a trailing empty sell count; a sell
     * cancellation has no trailing field.
     */
    fun writeMarketCancelOffer(
        out: ByteBuf,
        offer: MarketOfferEx,
        oldProtocol: Boolean = false,
        upgradeClassification: (Int) -> Int = { 0 },
    ) {
        out.writeByte(PACKET_MARKET_BROWSE)
        out.writeOwnOffersMarker(oldProtocol)

        if (offer.type == MarketOfferType.Buy) {
            out.writeIntLE(1)
            out.writeOwnOfferBody(offer, oldProtocol, upgradeClassification)
            out.writeIntLE(0)
        } else {
            out.writeIntLE(0)
            out.writeIntLE(1)
            out.writeOwnOfferBody(offer, oldProtocol, upgradeClassification)
        }
    }

    /**
     * protocolgame.cpp:7037-7091.
     *
     * Each side is capped independently as
     * `min(size, 810 + max(0, 810 - otherSize))`. Counter values restart at zero
     * per timestamp in list order.
     *
     * Canary quirks kept byte-for-byte: the sale loop writes the tier byte even
     * on the old profile, and always writes a `uint64_t` price (the buy loop
     * gates both on `!oldProtocol`).
     */
    fun writeMarketBrowseOwnHistory(
        out: ByteBuf,
        buyOffers: List<HistoryMarketOffer>,
        sellOffers: List<HistoryMarketOffer>,
        oldProtocol: Boolean = false,
        upgradeClassification: (Int) -> Int = { 0 },
    ) {
        val buyLimit = HISTORY_SIDE_LIMIT + maxOf(0, HISTORY_SIDE_LIMIT - sellOffers.size)
        val sellLimit = HISTORY_SIDE_LIMIT + maxOf(0, HISTORY_SIDE_LIMIT - buyOffers.size)
        val buyCount = minOf(buyOffers.size, buyLimit)
        val sellCount = minOf(sellOffers.size, sellLimit)

        out.writeByte(PACKET_MARKET_BROWSE)
        if (oldProtocol) {
            out.writeShortLE(MarketPacketParser.MARKET_REQUEST_OWN_HISTORY_OLD)
        } else {
            out.writeByte(MarketPacketParser.MARKET_REQUEST_OWN_HISTORY)
        }

        out.writeIntLE(buyCount)
        val counters = HashMap<Long, Int>()
        for (offer in buyOffers.take(buyCount)) {
            out.writeIntLE(offer.timestamp.toInt())
            out.writeShortLE(nextCounter(counters, offer.timestamp))
            out.writeShortLE(offer.itemId)
            if (!oldProtocol && upgradeClassification(offer.itemId) > 0) {
                out.writeByte(offer.tier)
            }
            out.writeShortLE(offer.amount)
            out.writeMarketPrice(offer.price, oldProtocol)
            out.writeByte(offer.state.value)
        }

        counters.clear()
        out.writeIntLE(sellCount)
        for (offer in sellOffers.take(sellCount)) {
            out.writeIntLE(offer.timestamp.toInt())
            out.writeShortLE(nextCounter(counters, offer.timestamp))
            out.writeShortLE(offer.itemId)
            if (upgradeClassification(offer.itemId) > 0) {
                out.writeByte(offer.tier)
            }
            out.writeShortLE(offer.amount)
            out.writeLongLE(offer.price)
            out.writeByte(offer.state.value)
        }
    }

    /**
     * protocolgame.cpp:7537-7946.
     *
     * [clientVersion] gates the 15.10 `getMarketDetailImbuementEffect` string;
     * pass `0` for profiles older than 15.10. The trailing tier description and
     * statistics are the only fields whose content depends on the item/offer
     * state Canary resolves before serializing.
     */
    fun writeMarketDetail(
        out: ByteBuf,
        detail: MarketDetail,
        oldProtocol: Boolean = false,
        clientVersion: Int = 0,
    ) {
        out.writeByte(PACKET_MARKET_DETAIL)
        out.writeShortLE(detail.itemId)
        if (!oldProtocol && detail.upgradeClassification > 0) {
            out.writeByte(detail.tier)
        }

        out.writeTibiaString(detail.armor)
        out.writeTibiaString(detail.attack)
        out.writeTibiaString(detail.containerCapacity)
        out.writeTibiaString(detail.defense)
        // Canary drops a single trailing period from the item description.
        out.writeTibiaString(detail.description.removeSuffix("."))
        out.writeTibiaString(detail.decay)
        out.writeTibiaString(detail.absorb)
        out.writeTibiaString(detail.minReqLevel)
        out.writeTibiaString(detail.minReqMagicLevel)
        out.writeTibiaString(detail.vocation)
        out.writeTibiaString(detail.runeSpellName)
        out.writeTibiaString(detail.skills)
        out.writeTibiaString(detail.charges)
        out.writeTibiaString(detail.weaponName)
        out.writeTibiaString(detail.weight)

        if (!oldProtocol) {
            out.writeTibiaString(detail.augments)
        }

        out.writeTibiaString(detail.imbuementSlot)

        if (!oldProtocol) {
            out.writeTibiaString(detail.magicShieldCapacity)
            out.writeTibiaString(detail.cleave)
            out.writeTibiaString(detail.reflect)
            out.writeTibiaString(detail.perfectShot)
            out.writeTibiaString(detail.upgradeClassificationDescription)
            out.writeTibiaString(detail.elementalBond)
            out.writeTibiaString(detail.mantra)
            if (clientVersion >= 1510) {
                out.writeTibiaString(detail.imbuementEffect)
            }
            out.writeTibiaString(detail.tierDescription)
        }

        out.writePurchaseStatistics(detail.purchaseStatistics, oldProtocol)
        out.writeSaleStatistics(detail.saleStatistics, oldProtocol)
    }

    /** protocolgame.cpp:6867-6889: timestamp, counter, amount, price, player name. */
    private fun ByteBuf.writeBrowseOffer(offer: MarketOffer, oldProtocol: Boolean) {
        writeIntLE(offer.timestamp.toInt())
        writeShortLE(offer.counter)
        writeShortLE(offer.amount)
        writeMarketPrice(offer.price, oldProtocol)
        writeTibiaString(offer.playerName)
    }

    /** protocolgame.cpp:6954-6984: timestamp, counter, item id, [tier], amount, price. */
    private fun ByteBuf.writeOwnOffer(
        offer: MarketOffer,
        oldProtocol: Boolean,
        upgradeClassification: (Int) -> Int,
    ) {
        writeIntLE(offer.timestamp.toInt())
        writeShortLE(offer.counter)
        writeShortLE(offer.itemId)
        if (!oldProtocol && upgradeClassification(offer.itemId) > 0) {
            writeByte(offer.tier)
        }
        writeShortLE(offer.amount)
        writeMarketPrice(offer.price, oldProtocol)
    }

    /** protocolgame.cpp:6995-7009 and 7012-7026: timestamp, counter, item id, [tier], amount, price. */
    private fun ByteBuf.writeOwnOfferBody(
        offer: MarketOfferEx,
        oldProtocol: Boolean,
        upgradeClassification: (Int) -> Int,
    ) {
        writeIntLE(offer.timestamp.toInt())
        writeShortLE(offer.counter)
        writeShortLE(offer.itemId)
        if (!oldProtocol && upgradeClassification(offer.itemId) > 0) {
            writeByte(offer.tier)
        }
        writeShortLE(offer.amount)
        writeMarketPrice(offer.price, oldProtocol)
    }

    /** protocolgame.cpp:6914-6923: timestamp, counter, amount, price, player name. */
    private fun ByteBuf.writeMarketOfferBody(offer: MarketOfferEx, oldProtocol: Boolean) {
        writeIntLE(offer.timestamp.toInt())
        writeShortLE(offer.counter)
        writeShortLE(offer.amount)
        writeMarketPrice(offer.price, oldProtocol)
        writeTibiaString(offer.playerName)
    }

    private fun ByteBuf.writeOwnOffersMarker(oldProtocol: Boolean) {
        if (oldProtocol) {
            writeShortLE(MarketPacketParser.MARKET_REQUEST_OWN_OFFERS_OLD)
        } else {
            writeByte(MarketPacketParser.MARKET_REQUEST_OWN_OFFERS)
        }
    }

    /**
     * Canary uses `msg.add<uint32_t>(offer.price)` on the old profile, which
     * truncates the `uint64_t` price to its low 32 bits.
     */
    private fun ByteBuf.writeMarketPrice(price: Long, oldProtocol: Boolean) {
        if (oldProtocol) {
            writeIntLE(price.toInt())
        } else {
            writeLongLE(price)
        }
    }

    /** protocolgame.cpp:7900-7922: purchase statistics clamp all three prices on the old profile. */
    private fun ByteBuf.writePurchaseStatistics(statistics: MarketStatistics?, oldProtocol: Boolean) {
        if (statistics == null) {
            writeByte(0x00)
            return
        }

        writeByte(0x01)
        writeIntLE(statistics.numTransactions.toInt())
        if (oldProtocol) {
            writeIntLE(clampToUint32(statistics.totalPrice))
            writeIntLE(clampToUint32(statistics.highestPrice))
            writeIntLE(clampToUint32(statistics.lowestPrice))
        } else {
            writeLongLE(statistics.totalPrice)
            writeLongLE(statistics.highestPrice)
            writeLongLE(statistics.lowestPrice)
        }
    }

    /**
     * protocolgame.cpp:7925-7945.
     *
     * Canary quirk kept byte-for-byte: the old profile clamps all three sale
     * prices, while the current profile clamps only `totalPrice`.
     */
    private fun ByteBuf.writeSaleStatistics(statistics: MarketStatistics?, oldProtocol: Boolean) {
        if (statistics == null) {
            writeByte(0x00)
            return
        }

        writeByte(0x01)
        writeIntLE(statistics.numTransactions.toInt())
        if (oldProtocol) {
            writeIntLE(clampToUint32(statistics.totalPrice))
            writeIntLE(clampToUint32(statistics.highestPrice))
            writeIntLE(clampToUint32(statistics.lowestPrice))
        } else {
            writeLongLE(minOf(statistics.totalPrice, 0xFFFFFFFFL))
            writeLongLE(statistics.highestPrice)
            writeLongLE(statistics.lowestPrice)
        }
    }

    private fun clampToUint32(value: Long): Int =
        minOf(value, 0xFFFFFFFFL).toInt()

    private fun nextCounter(counters: MutableMap<Long, Int>, timestamp: Long): Int {
        val counter = counters.getOrDefault(timestamp, 0)
        counters[timestamp] = counter + 1
        return counter and 0xFFFF
    }

    private fun <K : Comparable<K>, V> Map<K, V>.ascending(): List<Map.Entry<K, V>> =
        entries.sortedBy { it.key }
}
