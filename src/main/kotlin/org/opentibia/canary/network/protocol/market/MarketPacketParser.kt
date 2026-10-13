package org.opentibia.canary.network.protocol.market

import io.netty.buffer.ByteBuf
import org.opentibia.canary.economy.market.MarketOfferType

/**
 * Self-contained command DTOs for the Market client opcodes. Each command
 * carries the opcode so a caller can route on the decoded value without
 * re-reading the frame.
 *
 * Ported from Canary (Little-Endian wire order via `NetworkMessage::get<T>()`,
 * src/server/network/message/networkmessage.cpp):
 *  - 0xF4 `ProtocolGame::parseMarketLeave`         (protocolgame.cpp:4403)
 *  - 0xF5 `ProtocolGame::parseMarketBrowse`        (protocolgame.cpp:4411)
 *  - 0xF6 `ProtocolGame::parseMarketCreateOffer`   (protocolgame.cpp:4435)
 *  - 0xF7 `ProtocolGame::parseMarketCancelOffer`   (protocolgame.cpp:4455)
 *  - 0xF8 `ProtocolGame::parseMarketAcceptOffer`   (protocolgame.cpp:4469)
 */
sealed interface MarketCommand {
    val opcode: Int
}

/** 0xF4: leaves the market window; Canary reads no payload. */
object MarketLeaveCommand : MarketCommand {
    override val opcode: Int get() = MarketPacketParser.OPCODE_MARKET_LEAVE
}

/**
 * Browse target of the 0xF5 request. Canary selects it from the first field:
 * a single byte on the current profile (`MARKETREQUEST_OWN_HISTORY` /
 * `MARKETREQUEST_OWN_OFFERS`) or a `uint16_t` on the old profile
 * (`MARKETREQUEST_OWN_HISTORY_OLD` / `MARKETREQUEST_OWN_OFFERS_OLD`). Any other
 * value is treated as an item browse, reading the item id (and, on the current
 * profile, the tier when the item has an upgrade classification).
 */
sealed interface MarketBrowseRequest {
    object OwnOffers : MarketBrowseRequest
    object OwnHistory : MarketBrowseRequest
    data class Item(val itemId: Int, val tier: Int) : MarketBrowseRequest
}

/** 0xF5: a browse request for own offers, own history or a specific item. */
data class MarketBrowseCommand(val request: MarketBrowseRequest) : MarketCommand {
    override val opcode: Int get() = MarketPacketParser.OPCODE_MARKET_BROWSE
}

/**
 * 0xF6: create offer. Canary reads the action byte, the `uint16_t` item id, an
 * optional tier byte (current profile only, when the item has an upgrade
 * classification), the `uint16_t` amount, the price (`uint32_t` on the old
 * profile, `uint64_t` otherwise) and a byte-encoded anonymous flag.
 *
 * Canary only forwards the offer when `amount > 0 && price > 0`; that gate stays
 * with the caller.
 */
data class MarketCreateOfferCommand(
    val action: MarketOfferType,
    val itemId: Int,
    val tier: Int = 0,
    val amount: Int,
    val price: Long,
    val anonymous: Boolean,
) : MarketCommand {
    override val opcode: Int get() = MarketPacketParser.OPCODE_MARKET_CREATE_OFFER
}

/** 0xF7: cancel offer by `uint32_t` timestamp and `uint16_t` counter. */
data class MarketCancelOfferCommand(
    val timestamp: Long,
    val counter: Int,
) : MarketCommand {
    override val opcode: Int get() = MarketPacketParser.OPCODE_MARKET_CANCEL_OFFER
}

/** 0xF8: accept offer by `uint32_t` timestamp, `uint16_t` counter and `uint16_t` amount. */
data class MarketAcceptOfferCommand(
    val timestamp: Long,
    val counter: Int,
    val amount: Int,
) : MarketCommand {
    override val opcode: Int get() = MarketPacketParser.OPCODE_MARKET_ACCEPT_OFFER
}

/**
 * Decoder for the Market client opcodes. Every multi-byte field is
 * Little-Endian, matching `NetworkMessage::get<T>()` on the native wire order.
 *
 * The optional [upgradeClassification] callback supplies
 * `ItemType::upgradeClassification` for an item id; callers without an item
 * registry can leave it at the default `0`, which reproduces Canary's behavior
 * for items that carry no tier.
 */
object MarketPacketParser {

    const val OPCODE_MARKET_LEAVE: Int = 0xF4
    const val OPCODE_MARKET_BROWSE: Int = 0xF5
    const val OPCODE_MARKET_CREATE_OFFER: Int = 0xF6
    const val OPCODE_MARKET_CANCEL_OFFER: Int = 0xF7
    const val OPCODE_MARKET_ACCEPT_OFFER: Int = 0xF8

    const val MARKET_REQUEST_OWN_HISTORY: Int = 1
    const val MARKET_REQUEST_OWN_OFFERS: Int = 2
    const val MARKET_REQUEST_ITEM_BROWSE: Int = 3
    const val MARKET_REQUEST_OWN_OFFERS_OLD: Int = 0xFFFE
    const val MARKET_REQUEST_OWN_HISTORY_OLD: Int = 0xFFFF

    fun parse(
        opcode: Int,
        buffer: ByteBuf,
        oldProtocol: Boolean = false,
        upgradeClassification: (Int) -> Int = { 0 },
    ): MarketCommand? = when (opcode) {
        OPCODE_MARKET_LEAVE -> parseMarketLeave()
        OPCODE_MARKET_BROWSE -> parseMarketBrowse(buffer, oldProtocol, upgradeClassification)
        OPCODE_MARKET_CREATE_OFFER -> parseMarketCreateOffer(buffer, oldProtocol, upgradeClassification)
        OPCODE_MARKET_CANCEL_OFFER -> parseMarketCancelOffer(buffer)
        OPCODE_MARKET_ACCEPT_OFFER -> parseMarketAcceptOffer(buffer)
        else -> null
    }

    /** 0xF4: no payload is read. */
    fun parseMarketLeave(): MarketLeaveCommand = MarketLeaveCommand

    /**
     * 0xF5: reads the request marker and, for an item browse, the `uint16_t`
     * item id plus the current-profile tier byte when the item is classified.
     */
    fun parseMarketBrowse(
        buffer: ByteBuf,
        oldProtocol: Boolean = false,
        upgradeClassification: (Int) -> Int = { 0 },
    ): MarketBrowseCommand? {
        val request = if (oldProtocol) {
            parseOldBrowseRequest(buffer)
        } else {
            parseCurrentBrowseRequest(buffer, upgradeClassification)
        } ?: return null
        return MarketBrowseCommand(request)
    }

    private fun parseCurrentBrowseRequest(
        buffer: ByteBuf,
        upgradeClassification: (Int) -> Int,
    ): MarketBrowseRequest? {
        val browseId = readByte(buffer) ?: return null
        return when (browseId) {
            MARKET_REQUEST_OWN_HISTORY -> MarketBrowseRequest.OwnHistory
            MARKET_REQUEST_OWN_OFFERS -> MarketBrowseRequest.OwnOffers
            else -> readItemBrowse(buffer, upgradeClassification)
        }
    }

    private fun parseOldBrowseRequest(
        buffer: ByteBuf,
    ): MarketBrowseRequest? {
        val browseId = readShort(buffer) ?: return null
        return when (browseId) {
            MARKET_REQUEST_OWN_HISTORY_OLD -> MarketBrowseRequest.OwnHistory
            MARKET_REQUEST_OWN_OFFERS_OLD -> MarketBrowseRequest.OwnOffers
            else -> MarketBrowseRequest.Item(browseId, 0)
        }
    }

    private fun readItemBrowse(
        buffer: ByteBuf,
        upgradeClassification: (Int) -> Int,
    ): MarketBrowseRequest.Item? {
        val itemId = if (buffer.readableBytes() >= 2) buffer.readUnsignedShortLE() else return null
        val tier = if (upgradeClassification(itemId) > 0) {
            readByte(buffer) ?: return null
        } else {
            0
        }
        return MarketBrowseRequest.Item(itemId, tier)
    }

    /**
     * 0xF6: reads the action byte, item id, optional tier, amount, price and the
     * anonymous flag. Returns `null` when the action is not a
     * [MarketOfferType] member (`Game::checkCanInitCreateMarketOffer` rejects it,
     * protocolgame.cpp:10681) or the payload is truncated.
     */
    fun parseMarketCreateOffer(
        buffer: ByteBuf,
        oldProtocol: Boolean = false,
        upgradeClassification: (Int) -> Int = { 0 },
    ): MarketCreateOfferCommand? {
        val typeByte = readByte(buffer) ?: return null
        val action = MarketOfferType.entries.getOrNull(typeByte) ?: return null
        val itemId = readShort(buffer) ?: return null
        val tier = if (!oldProtocol && upgradeClassification(itemId) > 0) {
            readByte(buffer) ?: return null
        } else {
            0
        }
        val amount = readShort(buffer) ?: return null
        val price = if (oldProtocol) readInt(buffer) ?: return null else readLong(buffer) ?: return null
        val anonymous = (readByte(buffer) ?: return null) != 0
        return MarketCreateOfferCommand(
            action = action,
            itemId = itemId,
            tier = tier,
            amount = amount,
            price = price,
            anonymous = anonymous,
        )
    }

    /**
     * 0xF7: reads the `uint32_t` timestamp and `uint16_t` counter. Canary only
     * forwards the cancel when `counter > 0`; that gate stays with the caller.
     */
    fun parseMarketCancelOffer(buffer: ByteBuf): MarketCancelOfferCommand? {
        val timestamp = readInt(buffer) ?: return null
        val counter = readShort(buffer) ?: return null
        return MarketCancelOfferCommand(timestamp = timestamp, counter = counter)
    }

    /**
     * 0xF8: reads the `uint32_t` timestamp, `uint16_t` counter and `uint16_t`
     * amount. Canary only forwards the accept when `amount > 0 && counter > 0`;
     * that gate stays with the caller.
     */
    fun parseMarketAcceptOffer(buffer: ByteBuf): MarketAcceptOfferCommand? {
        val timestamp = readInt(buffer) ?: return null
        val counter = readShort(buffer) ?: return null
        val amount = readShort(buffer) ?: return null
        return MarketAcceptOfferCommand(timestamp = timestamp, counter = counter, amount = amount)
    }

    private fun readByte(buffer: ByteBuf): Int? =
        if (buffer.readableBytes() < 1) null else buffer.readUnsignedByte().toInt()

    private fun readShort(buffer: ByteBuf): Int? =
        if (buffer.readableBytes() < 2) null else buffer.readUnsignedShortLE()

    private fun readInt(buffer: ByteBuf): Long? =
        if (buffer.readableBytes() < 4) null else buffer.readUnsignedIntLE()

    private fun readLong(buffer: ByteBuf): Long? =
        if (buffer.readableBytes() < 8) null else buffer.readLongLE()
}
