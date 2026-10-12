package org.opentibia.canary.economy.market

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class MarketTest {

    @Test
    fun `creates buy and sell offers and matches compatible trades`() {
        val market = MarketManager()

        // Seller offers Demon Shield (id 2520) for 30000 gp
        val sellOffer = market.createSellOffer(
            sellerId = 1,
            itemId = 2520,
            amount = 1,
            price = 30000
        )
        assertThat(sellOffer).isNotNull
        assertThat(market.getOffersForItem(2520)).hasSize(1)

        // Buyer offers 32000 gp for Demon Shield (id 2520) -> should match!
        val matchResult = market.createBuyOffer(
            buyerId = 2,
            itemId = 2520,
            amount = 1,
            price = 32000
        )

        assertThat(matchResult.matched).isTrue()
        assertThat(matchResult.settledPrice).isEqualTo(30000) // settles at seller asking price
        assertThat(market.getOffersForItem(2520)).isEmpty()
    }

    @Test
    fun `does not match incompatible offers`() {
        val market = MarketManager()

        market.createSellOffer(sellerId = 1, itemId = 2160, amount = 10, price = 10000) // 10k each
        val buyResult = market.createBuyOffer(buyerId = 2, itemId = 2160, amount = 10, price = 5000) // buyer offers 5k

        assertThat(buyResult.matched).isFalse()
        assertThat(market.getOffersForItem(2160)).hasSize(2)
    }
}
