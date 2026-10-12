package org.opentibia.canary.economy.market

enum class MarketOfferType {
    Buy,
    Sell
}

data class MarketOffer(
    val id: Long,
    val playerId: Long,
    val type: MarketOfferType,
    val itemId: Int,
    var amount: Int,
    val price: Int
)

data class TradeResult(
    val matched: Boolean,
    val settledPrice: Int? = null,
    val matchedAmount: Int = 0
)

class MarketManager {
    private var nextOfferId = 1L
    private val offers = mutableListOf<MarketOffer>()

    fun createSellOffer(sellerId: Long, itemId: Int, amount: Int, price: Int): MarketOffer {
        val offer = MarketOffer(nextOfferId++, sellerId, MarketOfferType.Sell, itemId, amount, price)
        offers.add(offer)
        return offer
    }

    fun createBuyOffer(buyerId: Long, itemId: Int, amount: Int, price: Int): TradeResult {
        // Look for existing sell offer that matches or is cheaper
        val matchingSell = offers.find { it.type == MarketOfferType.Sell && it.itemId == itemId && it.price <= price }
        if (matchingSell != null) {
            val tradeAmount = kotlin.math.min(amount, matchingSell.amount)
            val tradePrice = matchingSell.price

            matchingSell.amount -= tradeAmount
            if (matchingSell.amount <= 0) {
                offers.remove(matchingSell)
            }

            return TradeResult(matched = true, settledPrice = tradePrice, matchedAmount = tradeAmount)
        }

        // No match, register as pending buy offer
        val offer = MarketOffer(nextOfferId++, buyerId, MarketOfferType.Buy, itemId, amount, price)
        offers.add(offer)
        return TradeResult(matched = false)
    }

    fun getOffersForItem(itemId: Int): List<MarketOffer> {
        return offers.filter { it.itemId == itemId }
    }
}
