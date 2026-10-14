package org.opentibia.canary.network.protocol.market

import org.opentibia.canary.creatures.Player
import org.opentibia.canary.economy.market.MarketOfferType
import org.opentibia.canary.game.CylinderFlags
import org.opentibia.canary.io.IOMarket
import org.opentibia.canary.io.MarketOfferEx
import org.opentibia.canary.io.MarketOfferState
import org.opentibia.canary.io.marketTimeNow
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.ITEM_CRYSTAL_COIN
import org.opentibia.canary.items.ITEM_GOLD_COIN
import org.opentibia.canary.items.ITEM_PLATINUM_COIN
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.items.Items
import org.opentibia.canary.items.containers.depot.DepotLocker
import org.opentibia.canary.systems.rewardchest.ReturnValue
import kotlin.math.min

/**
 * Handler-layer application of the market offer client opcodes (`0xF6`-`0xF8`)
 * over the ported [IOMarket] transaction engine and the [Player] state:
 *
 *  - `0xF6` `Game::playerCreateMarketOffer` (game.cpp:10717) through
 *    `Game::checkCanInitCreateMarketOffer` (game.cpp:10640) and the
 *    `Game::removeOfferItems` escrow (game.cpp:10338).
 *  - `0xF7` `Game::playerCancelMarketOffer` (game.cpp:10819).
 *  - `0xF8` `Game::playerAcceptMarketOffer` (game.cpp:10884).
 *
 * The `Game` member access (`IOMarket::getInstance`, bank balance, money
 * removal, the player registry and the exhaustion clock) arrives through
 * [MarketOfferContext] seams, matching the port's dependency-injection policy
 * (AGENTS.md 1.1).
 *
 * PARITY GAPS: the market window senders (`sendMarketEnter`,
 * `sendMarketCancelOffer`, `sendMarketAcceptOffer`, the error
 * `sendTextMessage`s), the `ITEM_STORE_COIN` account-coin transactions, the
 * buyer-inbox rollback (`Game::rollbackInboxInsertion`) and the metrics
 * counters have no ported equivalent; `Player::getMoney` is approximated with
 * the inventory/container coin counts and the inbox insertion uses
 * `Container::addItem`'s direct list append instead of
 * `Player::addItemBatchToPaginedContainer`.
 */
class MarketOfferContext(
    val player: Player,
    val ioMarket: IOMarket,
    val itemTypes: Items?,
    /** `Game::getPlayerByGUID(guid, allowOffline)` (game.cpp:1419-1437). */
    val resolvePlayer: (Long) -> Player? = { null },
    /** `Player::getBankBalance()` (player.hpp:354). */
    val getBankBalance: (Player) -> Long = { 0L },
    /** `Player::setBankBalance(balance)` (player.hpp:355). */
    val setBankBalance: (Player, Long) -> Unit = { _, _ -> },
    /** `Game::removeMoney(player, money, flags, useBalance)` (game.cpp:3299). */
    val removeMoney: (Player, Long) -> Boolean = { _, _ -> false },
    /** `Player::isUIExhausted(milliseconds)` (player.cpp:3412). */
    val isUIExhausted: (Player) -> Boolean = { false },
    /** `Player::updateUIExhausted()` (player.cpp:3416). */
    val updateUIExhausted: (Player) -> Unit = { },
    /** `FORGE_MAX_ITEM_TIER` config default (configmanager.cpp:268). */
    val maxItemTier: Int = 10,
    /** `MARKET_PREMIUM` config default (configmanager.cpp:187). */
    val marketPremium: Boolean = false,
    /** `MAX_MARKET_OFFERS_AT_A_TIME_PER_PLAYER` config default (configmanager.cpp:206). */
    val maxOfferCount: Int = 0,
)

object MarketOfferApplication {

    /** `ITEM_STORE_COIN` (src/utils/utils_definitions.hpp:634). */
    const val ITEM_STORE_COIN: Int = 24747

    /** `MARKET_OFFER_DURATION` config default (configmanager.cpp:237). */
    const val MARKET_OFFER_DURATION_DEFAULT: Long = 30L * 24L * 60L * 60L

    /**
     * `Game::playerCreateMarketOffer` (game.cpp:10717-10817): the
     * `checkCanInitCreateMarketOffer` gates, the tier clamp, the 2% fee
     * (20 gp minimum, 1kk cap), the sell escrow (`removeOfferItems`) or buy
     * money withdrawal, then `IOMarket::createOffer` and
     * `Player::updateUIExhausted`.
     */
    fun applyCreateOffer(command: MarketCreateOfferCommand, context: MarketOfferContext) {
        val player = context.player

        if (!player.isInMarket()) {
            return
        }
        if (command.price == 0L) {
            return
        }
        if (command.price > 999_999_999_999L) {
            return
        }
        if (context.isUIExhausted(player)) {
            return
        }

        val itemType = context.itemTypes?.get(command.itemId) ?: return
        if (itemType.id == 0 || itemType.wareId == 0) {
            return
        }
        if (command.amount == 0 ||
            (!itemType.isStackable && command.amount > 2000) ||
            (itemType.isStackable && command.amount > 64000)
        ) {
            return
        }
        if (context.marketPremium && !player.isPremium()) {
            return
        }
        if (context.maxOfferCount != 0 &&
            context.ioMarket.getPlayerOfferCount(player.getGUID()) >= context.maxOfferCount
        ) {
            return
        }

        val tier = clampTier(itemType, command.tier, context.maxItemTier)

        val totalPrice = command.price * command.amount
        val totalFee = (totalPrice * 0.02).toLong()
        val maxFee = maxOf(min(1_000_000L, totalFee), 20L)
        val fee = totalFee.coerceIn(20L, maxFee)

        if (command.action == MarketOfferType.Sell) {
            if (fee > (getMoney(player) + context.getBankBalance(player))) {
                return
            }

            val depotLocker = player.getDepotLocker(player.getLastDepotId())
            if (!removeOfferItems(context, player, depotLocker, itemType, command.amount, tier)) {
                return
            }

            context.removeMoney(player, fee)
        } else {
            val totalWithFee = totalPrice + fee
            if (totalWithFee > (getMoney(player) + context.getBankBalance(player))) {
                return
            }

            context.removeMoney(player, totalWithFee)
        }

        context.ioMarket.createOffer(
            playerId = player.getGUID(),
            action = command.action,
            itemId = itemType.id.toLong(),
            amount = command.amount,
            price = command.price,
            tier = tier,
            anonymous = command.anonymous,
        )
        context.updateUIExhausted(player)
    }

    /**
     * `Game::playerCancelMarketOffer` (game.cpp:10819-10882): the in-market and
     * exhaustion gates, the own-offer check, the buy bank refund or sell
     * item return to the inbox, and `IOMarket::moveOfferToHistory`.
     */
    fun applyCancelOffer(command: MarketCancelOfferCommand, context: MarketOfferContext) {
        val player = context.player

        if (!player.isInMarket()) {
            return
        }
        if (context.isUIExhausted(player)) {
            return
        }

        val offer = context.ioMarket.getOfferByCounter(command.timestamp, command.counter)
        if (offer.id == 0L || offer.playerId != player.getGUID()) {
            return
        }

        if (offer.type == MarketOfferType.Buy) {
            context.setBankBalance(player, context.getBankBalance(player) + offer.price * offer.amount)
        } else {
            val itemType = context.itemTypes?.get(offer.itemId) ?: return
            if (itemType.id == 0) {
                return
            }
            if (itemType.id == ITEM_STORE_COIN) {
                return
            }

            val offerTier = clampTier(itemType, offer.tier, context.maxItemTier)
            if (insertToInbox(player, itemType, offer.amount, offerTier) != ReturnValue.NOERROR) {
                return
            }
        }

        context.ioMarket.moveOfferToHistory(offer.id, MarketOfferState.Cancelled)
        context.updateUIExhausted(player)
    }

    /**
     * `Game::playerAcceptMarketOffer` (game.cpp:10884-11096): the in-market,
     * exhaustion, offer, item type, tier and amount gates, the buy-offer item
     * delivery to the buyer's inbox plus seller payment, or the sell-offer
     * buyer payment plus seller inbox delivery, the double history append and
     * the offer decrement/deletion.
     */
    fun applyAcceptOffer(command: MarketAcceptOfferCommand, context: MarketOfferContext) {
        val player = context.player

        if (!player.isInMarket()) {
            return
        }
        if (context.isUIExhausted(player)) {
            return
        }

        val offer = context.ioMarket.getOfferByCounter(command.timestamp, command.counter)
        if (offer.id == 0L) {
            return
        }

        val itemType = context.itemTypes?.get(offer.itemId) ?: return
        if (itemType.id == 0) {
            return
        }
        if (itemType.id == ITEM_STORE_COIN) {
            return
        }

        val offerTier = clampTier(itemType, offer.tier, context.maxItemTier)
        if (command.amount == 0 ||
            (!itemType.isStackable && command.amount > 2000) ||
            (itemType.isStackable && command.amount > 64000) ||
            command.amount > offer.amount
        ) {
            return
        }

        val totalPrice = offer.price * command.amount

        if (offer.type == MarketOfferType.Buy) {
            acceptBuyOffer(context, offer, itemType, offerTier, command.amount, totalPrice)
        } else {
            acceptSellOffer(context, offer, itemType, offerTier, command.amount, totalPrice)
        }
    }

    /**
     * `Game::playerAcceptMarketOffer` buy-offer branch (game.cpp:10911-11027):
     * the buyer's inbox receives the items, the accepting player (the seller)
     * hands them over through `removeOfferItems` and is paid from escrow.
     */
    private fun acceptBuyOffer(
        context: MarketOfferContext,
        offer: MarketOfferEx,
        itemType: ItemType,
        offerTier: Int,
        amount: Int,
        totalPrice: Long,
    ) {
        val player = context.player
        val buyerPlayer = context.resolvePlayer(offer.playerId) ?: return
        if (player === buyerPlayer) {
            return
        }

        if (insertToInbox(buyerPlayer, itemType, amount, offerTier) != ReturnValue.NOERROR) {
            return
        }

        val depotLocker = player.getDepotLocker(player.getLastDepotId())
        if (!removeOfferItems(context, player, depotLocker, itemType, amount, offerTier)) {
            return
        }

        context.setBankBalance(player, context.getBankBalance(player) + totalPrice)
        completeOffer(context, offer, offerTier, amount)
    }

    /**
     * `Game::playerAcceptMarketOffer` sell-offer branch (game.cpp:11028-11082):
     * the buyer (the accepting player) pays from bank and inventory, receives
     * the items in the inbox and the seller is paid from the escrow.
     */
    private fun acceptSellOffer(
        context: MarketOfferContext,
        offer: MarketOfferEx,
        itemType: ItemType,
        offerTier: Int,
        amount: Int,
        totalPrice: Long,
    ) {
        val player = context.player
        val sellerPlayer = context.resolvePlayer(offer.playerId) ?: return
        if (player === sellerPlayer) {
            return
        }

        if (totalPrice > (context.getBankBalance(player) + getMoney(player))) {
            return
        }

        if (insertToInbox(player, itemType, amount, offer.tier) != ReturnValue.NOERROR) {
            return
        }

        if (!context.removeMoney(player, totalPrice)) {
            return
        }

        context.setBankBalance(sellerPlayer, context.getBankBalance(sellerPlayer) + totalPrice)
        completeOffer(context, offer, offerTier, amount)
    }

    /** The double history append and the offer decrement/deletion of both accept branches. */
    private fun completeOffer(context: MarketOfferContext, offer: MarketOfferEx, offerTier: Int, amount: Int) {
        val acceptingPlayerType = if (offer.type == MarketOfferType.Buy) MarketOfferType.Sell else MarketOfferType.Buy
        context.ioMarket.appendHistory(
            playerId = context.player.getGUID(),
            type = acceptingPlayerType,
            itemId = offer.itemId,
            amount = amount,
            price = offer.price,
            timestamp = marketTimeNow(),
            tier = offerTier,
            state = MarketOfferState.AcceptedEx,
        )
        context.ioMarket.appendHistory(
            playerId = offer.playerId,
            type = offer.type,
            itemId = offer.itemId,
            amount = amount,
            price = offer.price,
            timestamp = marketTimeNow(),
            tier = offerTier,
            state = MarketOfferState.Accepted,
        )

        if (offer.amount == amount) {
            context.ioMarket.deleteOffer(offer.id)
        } else {
            context.ioMarket.acceptOffer(offer.id, amount)
        }
        context.updateUIExhausted(context.player)
    }

    /**
     * `Game::removeOfferItems` (game.cpp:10338-10398): the tier-0 stash
     * withdrawal followed by the depot-locker item removal.
     */
    fun removeOfferItems(
        context: MarketOfferContext,
        player: Player,
        depotLocker: DepotLocker,
        itemType: ItemType,
        amount: Int,
        tier: Int,
    ): Boolean {
        var removeAmount = amount

        if (tier == 0) {
            val stashItemCount = player.stash.getStashItemCount(itemType.wareId)
            if (stashItemCount > 0) {
                if (removeAmount > stashItemCount && player.stash.withdrawItem(itemType.wareId, stashItemCount)) {
                    removeAmount -= stashItemCount
                } else if (player.stash.withdrawItem(itemType.wareId, removeAmount)) {
                    removeAmount = 0
                } else {
                    return false
                }
            }
        }

        val (itemVector, totalCount) = lockerItemsAndCountById(context, depotLocker, tier, itemType.id)
        if (removeAmount > 0) {
            if (totalCount == 0 || itemVector.isEmpty()) {
                return false
            }

            var removedCount = 0
            for (item in itemVector) {
                if (removedCount >= removeAmount) {
                    break
                }

                val thisRemove = min(removeAmount - removedCount, item.getItemCount())
                if (removeItem(item, thisRemove) != ReturnValue.NOERROR) {
                    return false
                }
                removedCount += thisRemove
            }

            if (removedCount < removeAmount) {
                return false
            }
        }
        return true
    }

    /**
     * `Player::getLockerItemsAndCountById` (player.cpp:11010-11025) over the
     * `Player::requestLockerItems` locker walk (player.cpp:10933-10990); the
     * `lockerItems` ware-id map and the `ItemType::maxItems` guard stay
     * unported (PARITY GAP).
     */
    private fun lockerItemsAndCountById(
        context: MarketOfferContext,
        depotLocker: DepotLocker,
        tier: Int,
        itemId: Int,
    ): Pair<List<Item>, Int> {
        val itemVector = mutableListOf<Item>()
        val containers = ArrayDeque<Container>()
        containers.addLast(depotLocker)

        while (containers.isNotEmpty()) {
            val container = containers.removeFirst()
            for (item in container.getItemList()) {
                val lockerContainer = item.getContainer()
                if (lockerContainer != null && lockerContainer.items.isNotEmpty()) {
                    containers.addLast(lockerContainer)
                    continue
                }

                val itemType = item.type ?: continue
                if (item.isStoreItem() || itemType.wareId == 0) {
                    continue
                }
                if (!item.hasMarketAttributes() || item.getTier() != tier) {
                    continue
                }
                if (item.id == itemId) {
                    itemVector.add(item)
                }
            }
        }
        return itemVector to itemVector.size
    }

    /** `Player::removeItem` (player.cpp:10251-10281) over the item's parent cylinder. */
    private fun removeItem(item: Item, count: Int): ReturnValue {
        val cylinder = item.getParent() ?: return ReturnValue.NOTPOSSIBLE
        val removeCount = if (count == 0 || count > item.getItemCount()) item.getItemCount() else count
        val ret = cylinder.queryRemove(item, removeCount, CylinderFlags.FLAG_IGNORENOTMOVABLE)
        if (ret != ReturnValue.NOERROR) {
            return ret
        }
        cylinder.removeThing(item, removeCount)
        return ReturnValue.NOERROR
    }

    /**
     * `Player::addItemBatchToPaginedContainer(player->getInbox(), ...)`
     * (game.cpp:10465) reduced to the inbox append; the pagination capacity
     * checks and the partial-insertion rollback stay unported (PARITY GAP).
     */
    private fun insertToInbox(recipient: Player, itemType: ItemType, amount: Int, tier: Int): ReturnValue {
        val inbox = recipient.inbox
        val stackSize = if (itemType.isStackable) 100 else 1
        var remaining = amount
        while (remaining > 0) {
            if (inbox.capacity <= inbox.size()) {
                return ReturnValue.CONTAINERNOTENOUGHROOM
            }
            val chunk = min(stackSize, remaining)
            inbox.addItem(Item(id = itemType.id, count = chunk, type = itemType))
            remaining -= chunk
        }
        return ReturnValue.NOERROR
    }

    /**
     * `Player::getMoney` (player.cpp:1533-1555): the coin counts converted to
     * gold. The C++ inventory/container walk is approximated with
     * [Player.getItemCount] (PARITY GAP).
     */
    fun getMoney(player: Player): Long =
        player.getItemCount(ITEM_GOLD_COIN) +
            100 * player.getItemCount(ITEM_PLATINUM_COIN) +
            10_000 * player.getItemCount(ITEM_CRYSTAL_COIN)

    /**
     * The `maxTier`/`upgradeClassification` clamp of
     * `playerCreateMarketOffer` (game.cpp:10733-10741).
     */
    private fun clampTier(itemType: ItemType, tier: Int, maxItemTier: Int): Int {
        var clamped = if (tier > maxItemTier) maxItemTier else tier
        if (clamped > 0 && itemType.upgradeClassification == 0) {
            clamped = 0
        }
        return clamped
    }
}
