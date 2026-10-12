# Feature: Global Player Market System (IOMarket)

## 1. Explanation
Implements the in-game auction house / Market window (`IOMarket`). Players can create buy/sell offers for items, browse active offers, track transaction history, and receive proceeds/items via town depots or player mailboxes.

## 2. Impact
High economic impact. Replaces trade chat spam with an asynchronous in-game economy. Affects gold balances, item ownership, and depot storage.

## 3. Functionality Affected
- **Offer Engine**: Buy offers (requiring gold escrow) and sell offers (requiring item escrow).
- **Matching Engine**: Auto-matches compatible buy/sell offers and transfers gold/items asynchronously.
- **Market Packets**: Opcode handlers for browse offers, my offers, offer history, and cancel offer.

## 4. Agent Implementation Details
- Implement `MarketOffer`, `MarketOfferType` (Buy, Sell), and `MarketManager` in `org.opentibia.canary.economy.market`.
- Implement transactional matching logic between buyers and sellers.
- Handle escrow release to depot on canceled or completed offers.

## 5. Test Reference
- `org.opentibia.canary.economy.market.MarketTest`: Tests offer creation, escrow deduction, matching, and fund distribution.
