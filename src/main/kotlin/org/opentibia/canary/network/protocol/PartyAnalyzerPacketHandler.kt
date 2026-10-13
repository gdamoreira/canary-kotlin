package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf

/**
 * Party analyzer action (client opcode `0x2B`), mirroring
 * `ProtocolGame::parsePartyAnalyzerAction` (protocolgame.cpp:3900-3926).
 *
 * [action] is Canary's `PartyAnalyzerAction_t`
 * (src/utils/utils_definitions.hpp:502-504: `RESET` 0, `PRICETYPE` 1,
 * `PRICEVALUE` 2). Only `PRICETYPE`/`PRICEVALUE` reach the analyzer, and only
 * `PRICEVALUE` carries [prices], the `uint16_t` entry pairs
 * `(itemId, price)` read before `Player::setItemCustomPrice` and
 * `Party::reloadPrices`.
 */
data class PartyAnalyzerActionCommand(
    val action: Int,
    val prices: List<PartyAnalyzerItemPrice>,
)

/** `(uint16_t itemId, uint64_t price)` pair of the `PRICEVALUE` payload. */
data class PartyAnalyzerItemPrice(
    val itemId: Int,
    val price: Long,
)

/**
 * Decoder for `ProtocolGame::parsePartyAnalyzerAction` (protocolgame.cpp:3900).
 *
 * Canary early-returns without reading when the player has no party or is not
 * the leader (protocolgame.cpp:3905-3908) and only reads [prices] for
 * `PARTYANALYZERACTION_PRICEVALUE` (protocolgame.cpp:3915-3921); this decoder
 * reads the full advertised payload for that action and the action byte alone
 * for every other action.
 *
 * PARITY GAP: `Party::resetAnalyzer`, `Party::switchAnalyzerPriceType`,
 * `Party::reloadPrices`, `Player::setItemCustomPrice` and the analyzer tracker
 * updates are not ported, so the decoded command is only the confinement
 * boundary.
 */
object PartyAnalyzerPacketHandler {

    /** Client opcode for `ProtocolGame::parsePartyAnalyzerAction`. */
    const val OPCODE_PARTY_ANALYZER_ACTION: Int = 0x2B

    /** `PARTYANALYZERACTION_RESET` (src/utils/utils_definitions.hpp:502). */
    const val ACTION_RESET: Int = 0

    /** `PARTYANALYZERACTION_PRICETYPE` (src/utils/utils_definitions.hpp:503). */
    const val ACTION_PRICE_TYPE: Int = 1

    /** `PARTYANALYZERACTION_PRICEVALUE` (src/utils/utils_definitions.hpp:504). */
    const val ACTION_PRICE_VALUE: Int = 2

    /**
     * 0x2B: reads the action byte and, for `PRICEVALUE`, the `uint16_t` count
     * followed by `count` `(uint16_t itemId, uint64_t price)` pairs.
     *
     * @return the decoded command, or `null` when the payload is truncated.
     */
    fun parse(buffer: ByteBuf): PartyAnalyzerActionCommand? {
        if (buffer.readableBytes() < 1) {
            return null
        }

        val action = buffer.readUnsignedByte().toInt()
        if (action != ACTION_PRICE_VALUE) {
            return PartyAnalyzerActionCommand(action = action, prices = emptyList())
        }

        if (buffer.readableBytes() < 2) {
            return null
        }
        val count = buffer.readUnsignedShortLE()
        val prices = ArrayList<PartyAnalyzerItemPrice>(count)
        repeat(count) {
            if (buffer.readableBytes() < 10) {
                return null
            }
            prices.add(
                PartyAnalyzerItemPrice(
                    itemId = buffer.readUnsignedShortLE(),
                    price = buffer.readLongLE(),
                ),
            )
        }
        return PartyAnalyzerActionCommand(action = action, prices = prices)
    }
}
