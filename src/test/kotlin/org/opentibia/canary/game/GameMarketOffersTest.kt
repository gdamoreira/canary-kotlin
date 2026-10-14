package org.opentibia.canary.game

import com.zaxxer.hikari.HikariDataSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.opentibia.canary.account.Account
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.database.DatabaseConnectionFactory
import org.opentibia.canary.economy.market.MarketOfferType
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.io.HistoryMarketOffer
import org.opentibia.canary.io.IOMarket
import org.opentibia.canary.io.MarketOfferEx
import org.opentibia.canary.io.MarketOfferState
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.items.Items
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.protocol.MessageClass
import org.opentibia.canary.systems.rewardchest.ReturnValue

/**
 * Transcript tests for the remaining market `Game` handlers:
 *
 *  - `Game::loadItemsPrice` (game.cpp:1037)
 *  - `Game::updatePlayerSaleItems` (game.cpp:11596)
 *  - `Game::playerCreateMarketOffer` (game.cpp:10717) and the
 *    `checkCanInitCreateMarketOffer` / `removeOfferItems` helpers
 *  - `Game::playerCancelMarketOffer` (game.cpp:10819)
 *  - `Game::playerAcceptMarketOffer` (game.cpp:10884)
 *
 * The `IOMarket` persistence is the ported [IOMarket] over an in-memory H2
 * database (Flyway V1-V12), matching `IOMarketTest`.
 */
class GameMarketOffersTest {

    private lateinit var dataSource: HikariDataSource
    private lateinit var ioMarket: IOMarket

    private val center = Position(1000, 1000, 7)
    private val fixedNow = 1_700_000_000L

    @BeforeEach
    fun setUp() {
        dataSource = DatabaseConnectionFactory.createInMemoryDataSource("canary_game_market_offers_${System.nanoTime()}")
        val applied = DatabaseConnectionFactory.migrate(dataSource)
        assertThat(applied).isGreaterThanOrEqualTo(10)
        ioMarket = IOMarket(dataSource = dataSource, now = { fixedNow })
        insertPlayerRow(100, "PlayerHundred")
    }

    @AfterEach
    fun tearDown() {
        dataSource.close()
    }

    private fun newGame(itemTypes: Items? = null): Game =
        Game(GameMap(), GameDispatcher(), itemTypes = itemTypes, ioMarket = ioMarket)

    private fun Game.player(id: Long, name: String, inMarket: Boolean = true): Player =
        Player(id = id, name = name, maxHealth = 150, health = 150, position = center).also {
            it.setInMarket(inMarket)
            it.account = Account(id = id)
            addPlayer(it)
        }

    private fun marketItems(
        itemId: Int = 2160,
        wareId: Int = 2160,
        stackable: Boolean = false,
        upgradeClassification: Int = 0,
    ): Items = Items().also {
        it.register(
            ItemType(
                id = itemId,
                name = "market item",
                wareId = wareId,
                isStackable = stackable,
                upgradeClassification = upgradeClassification,
                buyPrice = 50,
                sellPrice = 25,
            ),
        )
    }

    private fun insertPlayerRow(id: Long, name: String) {
        dataSource.connection.use { connection ->
            connection.prepareStatement("INSERT INTO players (id, name, account_id, balance) VALUES (?, ?, 1, 0)").use { statement ->
                statement.setLong(1, id)
                statement.setString(2, name)
                statement.executeUpdate()
            }
        }
    }

    private fun insertHistoryRow(itemId: Int, sale: Int, price: Long, tier: Int, state: Int = MarketOfferState.Accepted.value) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "INSERT INTO market_history (player_id, sale, itemtype, amount, price, expires_at, inserted, state, tier) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
            ).use { statement ->
                statement.setLong(1, 100)
                statement.setInt(2, sale)
                statement.setInt(3, itemId)
                statement.setInt(4, 1)
                statement.setLong(5, price)
                statement.setLong(6, fixedNow)
                statement.setLong(7, fixedNow)
                statement.setInt(8, state)
                statement.setInt(9, tier)
                statement.executeUpdate()
            }
        }
    }

    private fun historyRows(): List<HistoryMarketOffer> =
        ioMarket.getOwnHistory(MarketOfferType.Buy, 1) + ioMarket.getOwnHistory(MarketOfferType.Sell, 1)

    private data class RawHistoryRow(val playerId: Long, val sale: Int, val itemId: Int, val amount: Int, val price: Long, val state: Int)

    private fun allHistoryRows(): List<RawHistoryRow> {
        val rows = mutableListOf<RawHistoryRow>()
        dataSource.connection.use { connection ->
            connection.prepareStatement("SELECT player_id, sale, itemtype, amount, price, state FROM market_history").use { statement ->
                statement.executeQuery().use { result ->
                    while (result.next()) {
                        rows.add(
                            RawHistoryRow(
                                playerId = result.getLong(1),
                                sale = result.getInt(2),
                                itemId = result.getInt(3),
                                amount = result.getInt(4),
                                price = result.getLong(5),
                                state = result.getInt(6),
                            ),
                        )
                    }
                }
            }
        }
        return rows
    }

    // ----- loadItemsPrice -----

    @Test
    fun `loadItemsPrice averages the accepted purchase statistics per tier`() {
        insertHistoryRow(itemId = 2160, sale = MarketOfferType.Buy.ordinal, price = 40, tier = 0)
        insertHistoryRow(itemId = 2160, sale = MarketOfferType.Buy.ordinal, price = 60, tier = 0)
        insertHistoryRow(itemId = 2160, sale = MarketOfferType.Buy.ordinal, price = 70, tier = 1)
        insertHistoryRow(itemId = 2160, sale = MarketOfferType.Buy.ordinal, price = 70, tier = 1)
        val game = newGame()

        game.loadItemsPrice()

        assertThat(game.itemsPriceMap).containsEntry(2160, mapOf(0 to 50L, 1 to 70L))
    }

    @Test
    fun `loadItemsPrice merges the highest active buy offer per tier`() {
        insertHistoryRow(itemId = 2160, sale = MarketOfferType.Buy.ordinal, price = 40, tier = 0)
        insertHistoryRow(itemId = 2160, sale = MarketOfferType.Buy.ordinal, price = 60, tier = 0)
        insertHistoryRow(itemId = 2160, sale = MarketOfferType.Buy.ordinal, price = 70, tier = 1)
        insertHistoryRow(itemId = 2160, sale = MarketOfferType.Buy.ordinal, price = 70, tier = 1)
        ioMarket.createOffer(1, MarketOfferType.Buy, 2160, 5, 80, 1, false)
        ioMarket.createOffer(1, MarketOfferType.Buy, 2160, 5, 30, 0, false)
        val game = newGame()

        game.loadItemsPrice()

        assertThat(game.itemsPriceMap).containsEntry(2160, mapOf(0 to 50L, 1 to 80L))
    }

    @Test
    fun `loadItemsPrice keeps only the active buy offers when there is no statistics`() {
        ioMarket.createOffer(1, MarketOfferType.Buy, 2160, 5, 80, 1, false)
        ioMarket.createOffer(1, MarketOfferType.Sell, 2160, 5, 90, 1, false)
        val game = newGame()

        game.loadItemsPrice()

        assertThat(game.itemsPriceMap).containsExactlyEntriesOf(mapOf(2160 to mapOf(1 to 80L)))
    }

    // ----- updatePlayerSaleItems -----

    @Test
    fun `updatePlayerSaleItems ignores an unknown player`() {
        val game = newGame()
        var sent = false
        game.sendSaleItemList = { _, _ -> sent = true }

        game.updatePlayerSaleItems(404)

        assertThat(sent).isFalse()
    }

    @Test
    fun `updatePlayerSaleItems sends the sale list and clears the scheduled flag`() {
        val game = newGame()
        val player = game.player(1, "Seller")
        game.getAllSaleItemIdAndCount = { _, _ -> mapOf(2160 to 3) }
        val sent = mutableListOf<Map<Int, Int>>()
        game.sendSaleItemList = { _, items -> sent.add(items) }
        val scheduled = mutableListOf<Boolean>()
        game.setScheduledSaleUpdate = { _, value -> scheduled.add(value) }

        game.updatePlayerSaleItems(player.id)

        assertThat(sent).containsExactly(mapOf(2160 to 3))
        assertThat(scheduled).containsExactly(false)
    }

    // ----- playerCreateMarketOffer -----

    @Test
    fun `playerCreateMarketOffer ignores an unknown player`() {
        val game = newGame(marketItems()).also { it.marketPremium = false }

        game.playerCreateMarketOffer(404, MarketOfferType.Sell, 2160, 1, 100, 0, false)

        assertThat(ioMarket.getActiveOffers(MarketOfferType.Sell)).isEmpty()
    }

    @Test
    fun `playerCreateMarketOffer rejects a zero price`() {
        val game = newGame(marketItems()).also { it.marketPremium = false }
        val player = game.player(1, "Seller")

        game.playerCreateMarketOffer(player.id, MarketOfferType.Sell, 2160, 1, 0, 0, false)

        assertThat(ioMarket.getActiveOffers(MarketOfferType.Sell)).isEmpty()
    }

    @Test
    fun `playerCreateMarketOffer rejects a price above the market limit`() {
        val game = newGame(marketItems()).also { it.marketPremium = false }
        val player = game.player(1, "Seller")

        game.playerCreateMarketOffer(player.id, MarketOfferType.Sell, 2160, 1, 1_000_000_000_000, 0, false)

        assertThat(ioMarket.getActiveOffers(MarketOfferType.Sell)).isEmpty()
    }

    @Test
    fun `playerCreateMarketOffer rejects a player outside the market`() {
        val game = newGame(marketItems()).also { it.marketPremium = false }
        val player = game.player(1, "Seller", inMarket = false)

        game.playerCreateMarketOffer(player.id, MarketOfferType.Sell, 2160, 1, 100, 0, false)

        assertThat(ioMarket.getActiveOffers(MarketOfferType.Sell)).isEmpty()
    }

    @Test
    fun `playerCreateMarketOffer rejects a non stackable amount above 2000`() {
        val game = newGame(marketItems(stackable = false)).also { it.marketPremium = false }
        val player = game.player(1, "Seller")

        game.playerCreateMarketOffer(player.id, MarketOfferType.Sell, 2160, 2001, 100, 0, false)

        assertThat(ioMarket.getActiveOffers(MarketOfferType.Sell)).isEmpty()
    }

    @Test
    fun `playerCreateMarketOffer rejects an exhausted player`() {
        val game = newGame(marketItems()).also { it.marketPremium = false }
        val player = game.player(1, "Seller")
        game.isUIExhausted = { true }
        val cancels = mutableListOf<ReturnValue>()
        game.sendCancelMessage = { _, ret -> cancels.add(ret) }

        game.playerCreateMarketOffer(player.id, MarketOfferType.Sell, 2160, 1, 100, 0, false)

        assertThat(cancels).containsExactly(ReturnValue.YOUAREEXHAUSTED)
        assertThat(ioMarket.getActiveOffers(MarketOfferType.Sell)).isEmpty()
    }

    @Test
    fun `playerCreateMarketOffer rejects a non premium player when premium is required`() {
        val game = newGame(marketItems())
        val player = game.player(1, "Seller")
        val texts = mutableListOf<Pair<MessageClass, String>>()
        game.sendTextMessage = { _, messageClass, text -> texts.add(messageClass to text) }

        game.playerCreateMarketOffer(player.id, MarketOfferType.Sell, 2160, 1, 100, 0, false)

        assertThat(texts.any { it.second == "Only premium accounts may create offers for that object." }).isTrue()
        assertThat(ioMarket.getActiveOffers(MarketOfferType.Sell)).isEmpty()
    }

    @Test
    fun `playerCreateMarketOffer rejects a player at the offer count limit`() {
        val game = newGame(marketItems()).also { it.marketPremium = false; it.maxMarketOffersAtATimePerPlayer = 1 }
        val player = game.player(1, "Seller")
        ioMarket.createOffer(player.id, MarketOfferType.Sell, 2160, 1, 100, 0, false)

        game.playerCreateMarketOffer(player.id, MarketOfferType.Sell, 2160, 1, 100, 0, false)

        assertThat(ioMarket.getActiveOffers(MarketOfferType.Sell)).hasSize(1)
    }

    @Test
    fun `playerCreateMarketOffer charges the two percent fee on a sell offer`() {
        val game = newGame(marketItems(stackable = true)).also { it.marketPremium = false }
        val player = game.player(1, "Seller")
        val item = Item(id = 2160, type = marketItems(stackable = true).get(2160), count = 5)
        player.addItem(item)
        game.getLockerItemsAndCountById = { _, _, _, _ -> listOf(item) to item.count }
        val money = mutableListOf<Long>()
        game.removeMoney = { _, amount, _, _ -> money.add(amount); true }
        val cancels = mutableListOf<ReturnValue>()
        game.sendCancelMessage = { _, ret -> cancels.add(ret) }
        game.getBankBalance = { 10_000L }

        game.playerCreateMarketOffer(player.id, MarketOfferType.Sell, 2160, 5, 1000, 0, false)

        assertThat(money).containsExactly(100L)
        assertThat(cancels).isEmpty()
        val offers = ioMarket.getActiveOffers(MarketOfferType.Sell, 2160, 0)
        assertThat(offers).hasSize(1)
        assertThat(offers.first().amount).isEqualTo(5)
        assertThat(offers.first().price).isEqualTo(1000)
    }

    @Test
    fun `playerCreateMarketOffer charges the price plus fee on a buy offer`() {
        val game = newGame(marketItems()).also { it.marketPremium = false }
        val player = game.player(1, "Buyer")
        val money = mutableListOf<Long>()
        game.removeMoney = { _, amount, _, _ -> money.add(amount); true }
        game.getBankBalance = { 10_000L }

        game.playerCreateMarketOffer(player.id, MarketOfferType.Buy, 2160, 5, 1000, 0, false)

        assertThat(money).containsExactly(5100L)
        val offers = ioMarket.getActiveOffers(MarketOfferType.Buy, 2160, 0)
        assertThat(offers).hasSize(1)
        assertThat(offers.first().amount).isEqualTo(5)
        assertThat(offers.first().price).isEqualTo(1000)
    }

    @Test
    fun `playerCreateMarketOffer clamps the tier to the forge maximum`() {
        val game = newGame(marketItems(upgradeClassification = 5)).also { it.marketPremium = false; it.forgeMaxItemTier = 3 }
        val player = game.player(1, "Seller")
        val item = Item(id = 2160, type = marketItems(upgradeClassification = 5).get(2160), count = 1)
        player.addItem(item)
        game.getLockerItemsAndCountById = { _, _, _, _ -> listOf(item) to item.count }
        game.removeMoney = { _, _, _, _ -> true }
        game.getBankBalance = { 10_000L }

        game.playerCreateMarketOffer(player.id, MarketOfferType.Sell, 2160, 1, 100, 9, false)

        assertThat(ioMarket.getActiveOffers(MarketOfferType.Sell, 2160, 3)).hasSize(1)
    }

    @Test
    fun `playerCreateMarketOffer zeroes the tier for items without upgrade classification`() {
        val game = newGame(marketItems(upgradeClassification = 0)).also { it.marketPremium = false }
        val player = game.player(1, "Seller")
        val item = Item(id = 2160, type = marketItems(upgradeClassification = 0).get(2160), count = 1)
        player.addItem(item)
        game.getLockerItemsAndCountById = { _, _, _, _ -> listOf(item) to item.count }
        game.removeMoney = { _, _, _, _ -> true }
        game.getBankBalance = { 10_000L }

        game.playerCreateMarketOffer(player.id, MarketOfferType.Sell, 2160, 1, 100, 4, false)

        assertThat(ioMarket.getActiveOffers(MarketOfferType.Sell, 2160, 0)).hasSize(1)
    }

    @Test
    fun `playerCreateMarketOffer sends the browse list and saves on success`() {
        val game = newGame(marketItems(stackable = true)).also { it.marketPremium = false }
        val player = game.player(1, "Seller")
        val item = Item(id = 2160, type = marketItems(stackable = true).get(2160), count = 5)
        player.addItem(item)
        game.getLockerItemsAndCountById = { _, _, _, _ -> listOf(item) to item.count }
        game.removeMoney = { _, _, _, _ -> true }
        val calls = mutableListOf<String>()
        game.sendMarketEnter = { _, _ -> calls.add("enter") }
        game.sendMarketBrowseItem = { _, _, _, _, _ -> calls.add("browse") }
        game.updateUIExhausted = { calls.add("exhausted") }
        game.savePlayer = { calls.add("save") }
        game.getBankBalance = { 10_000L }

        game.playerCreateMarketOffer(player.id, MarketOfferType.Sell, 2160, 5, 1000, 0, false)

        assertThat(calls).containsExactly("enter", "browse", "exhausted", "save")
    }

    // ----- playerCancelMarketOffer -----

    @Test
    fun `playerCancelMarketOffer ignores an unknown player`() {
        val game = newGame()

        game.playerCancelMarketOffer(404, 0, 0)

        assertThat(ioMarket.getActiveOffers(MarketOfferType.Buy)).isEmpty()
    }

    @Test
    fun `playerCancelMarketOffer ignores a player outside the market`() {
        val game = newGame()
        val player = game.player(1, "Seller", inMarket = false)
        ioMarket.createOffer(player.id, MarketOfferType.Sell, 2160, 5, 1000, 0, false)

        game.playerCancelMarketOffer(player.id, 0, 0)

        assertThat(ioMarket.getActiveOffers(MarketOfferType.Sell)).hasSize(1)
    }

    @Test
    fun `playerCancelMarketOffer refuses an exhausted player`() {
        val game = newGame()
        val player = game.player(1, "Seller")
        ioMarket.createOffer(player.id, MarketOfferType.Sell, 2160, 5, 1000, 0, false)
        game.isUIExhausted = { true }
        val cancels = mutableListOf<ReturnValue>()
        game.sendCancelMessage = { _, ret -> cancels.add(ret) }

        game.playerCancelMarketOffer(player.id, 0, 0)

        assertThat(cancels).containsExactly(ReturnValue.YOUAREEXHAUSTED)
        assertThat(ioMarket.getActiveOffers(MarketOfferType.Sell)).hasSize(1)
    }

    @Test
    fun `playerCancelMarketOffer ignores a counter that belongs to another player`() {
        val game = newGame()
        val player = game.player(1, "Seller")
        ioMarket.createOffer(100, MarketOfferType.Sell, 2160, 5, 1000, 0, false)
        val offer = ioMarket.getActiveOffers(MarketOfferType.Sell, 2160, 0).first()

        game.playerCancelMarketOffer(player.id, offer.timestamp, offer.counter)

        assertThat(ioMarket.getActiveOffers(MarketOfferType.Sell, 2160, 0)).hasSize(1)
    }

    @Test
    fun `playerCancelMarketOffer refunds the bank and cancels a buy offer`() {
        val game = newGame()
        val player = game.player(1, "Buyer")
        ioMarket.createOffer(player.id, MarketOfferType.Buy, 2160, 5, 1000, 0, false)
        val offer = ioMarket.getActiveOffers(MarketOfferType.Buy, 2160, 0).first()
        game.getBankBalance = { 5000L }
        val balances = mutableListOf<Long>()
        game.setBankBalance = { _, value -> balances.add(value) }
        val cancelled = mutableListOf<MarketOfferEx>()
        game.sendMarketCancelOffer = { _, offerEx -> cancelled.add(offerEx) }

        game.playerCancelMarketOffer(player.id, offer.timestamp, offer.counter)

        assertThat(balances).containsExactly(10_000L)
        assertThat(cancelled).hasSize(1)
        assertThat(cancelled.first().amount).isEqualTo(0)
        assertThat(cancelled.first().timestamp).isEqualTo(offer.timestamp)
        assertThat(ioMarket.getActiveOffers(MarketOfferType.Buy)).isEmpty()
        assertThat(historyRows()).anyMatch { it.state == MarketOfferState.Cancelled && it.itemId == 2160 }
    }

    @Test
    fun `playerCancelMarketOffer inserts the item back to the inbox on a sell offer`() {
        val game = newGame(marketItems())
        val player = game.player(1, "Seller")
        ioMarket.createOffer(player.id, MarketOfferType.Sell, 2160, 5, 1000, 0, false)
        val offer = ioMarket.getActiveOffers(MarketOfferType.Sell, 2160, 0).first()
        val inserts = mutableListOf<Triple<Int, Int, Int>>()
        game.addItemBatchToPaginedContainer = { _, _, itemId, count, _, tier, testOnly ->
            if (!testOnly) {
                inserts.add(Triple(itemId, count, tier))
            }
            ReturnValue.NOERROR to count
        }
        val cancelled = mutableListOf<MarketOfferEx>()
        game.sendMarketCancelOffer = { _, offerEx -> cancelled.add(offerEx) }

        game.playerCancelMarketOffer(player.id, offer.timestamp, offer.counter)

        assertThat(inserts).containsExactly(Triple(2160, 5, 0))
        assertThat(cancelled).hasSize(1)
        assertThat(ioMarket.getActiveOffers(MarketOfferType.Sell)).isEmpty()
    }

    @Test
    fun `playerCancelMarketOffer updates the exhausted state and saves once`() {
        val game = newGame()
        val player = game.player(1, "Buyer")
        ioMarket.createOffer(player.id, MarketOfferType.Buy, 2160, 5, 1000, 0, false)
        val offer = ioMarket.getActiveOffers(MarketOfferType.Buy, 2160, 0).first()
        game.removeMoney = { _, _, _, _ -> true }
        val calls = mutableListOf<String>()
        game.updateUIExhausted = { calls.add("exhausted") }
        game.savePlayer = { calls.add("save") }
        game.sendMarketEnter = { _, _ -> calls.add("enter") }

        game.playerCancelMarketOffer(player.id, offer.timestamp, offer.counter)

        assertThat(calls).containsExactly("enter", "enter", "exhausted", "save")
    }

    // ----- playerAcceptMarketOffer -----

    @Test
    fun `playerAcceptMarketOffer ignores an unknown player`() {
        val game = newGame(marketItems())
        ioMarket.createOffer(1, MarketOfferType.Buy, 2160, 5, 100, 0, false)
        val offer = ioMarket.getActiveOffers(MarketOfferType.Buy, 2160, 0).first()

        game.playerAcceptMarketOffer(404, offer.timestamp, offer.counter, 1)

        assertThat(ioMarket.getActiveOffers(MarketOfferType.Buy)).hasSize(1)
    }

    @Test
    fun `playerAcceptMarketOffer ignores a player outside the market`() {
        val game = newGame(marketItems())
        val player = game.player(1, "Seller", inMarket = false)
        ioMarket.createOffer(100, MarketOfferType.Buy, 2160, 5, 100, 0, false)
        val offer = ioMarket.getActiveOffers(MarketOfferType.Buy, 2160, 0).first()

        game.playerAcceptMarketOffer(player.id, offer.timestamp, offer.counter, 1)

        assertThat(ioMarket.getActiveOffers(MarketOfferType.Buy)).hasSize(1)
    }

    @Test
    fun `playerAcceptMarketOffer refuses an exhausted player`() {
        val game = newGame(marketItems())
        val player = game.player(1, "Seller")
        ioMarket.createOffer(100, MarketOfferType.Buy, 2160, 5, 100, 0, false)
        val offer = ioMarket.getActiveOffers(MarketOfferType.Buy, 2160, 0).first()
        game.isUIExhausted = { true }
        val cancels = mutableListOf<ReturnValue>()
        game.sendCancelMessage = { _, ret -> cancels.add(ret) }

        game.playerAcceptMarketOffer(player.id, offer.timestamp, offer.counter, 1)

        assertThat(cancels).containsExactly(ReturnValue.YOUAREEXHAUSTED)
        assertThat(ioMarket.getActiveOffers(MarketOfferType.Buy)).hasSize(1)
    }

    @Test
    fun `playerAcceptMarketOffer ignores an unknown offer counter`() {
        val game = newGame(marketItems())
        val player = game.player(1, "Seller")

        game.playerAcceptMarketOffer(player.id, 0, 0, 1)

        assertThat(ioMarket.getActiveOffers(MarketOfferType.Buy)).isEmpty()
    }

    @Test
    fun `playerAcceptMarketOffer rejects an amount above the offer amount`() {
        val game = newGame(marketItems())
        val player = game.player(1, "Seller")
        ioMarket.createOffer(100, MarketOfferType.Buy, 2160, 5, 100, 0, false)
        val offer = ioMarket.getActiveOffers(MarketOfferType.Buy, 2160, 0).first()

        game.playerAcceptMarketOffer(player.id, offer.timestamp, offer.counter, 6)

        assertThat(ioMarket.getActiveOffers(MarketOfferType.Buy)).hasSize(1)
    }

    @Test
    fun `playerAcceptMarketOffer rejects accepting an own offer`() {
        val game = newGame(marketItems())
        val player = game.player(1, "Buyer")
        ioMarket.createOffer(player.id, MarketOfferType.Buy, 2160, 5, 100, 0, false)
        val offer = ioMarket.getActiveOffers(MarketOfferType.Buy, 2160, 0).first()
        val texts = mutableListOf<String>()
        game.sendTextMessage = { _, _, text -> texts.add(text) }

        game.playerAcceptMarketOffer(player.id, offer.timestamp, offer.counter, 1)

        assertThat(texts).contains("You cannot accept your own offer.")
        assertThat(ioMarket.getActiveOffers(MarketOfferType.Buy)).hasSize(1)
    }

    @Test
    fun `playerAcceptMarketOffer delivers to the buyer inbox and pays the seller bank`() {
        val game = newGame(marketItems(stackable = true))
        val seller = game.player(1, "Seller")
        val buyer = game.player(100, "Buyer")
        buyer.account = Account(id = 100)
        ioMarket.createOffer(buyer.id, MarketOfferType.Buy, 2160, 5, 100, 0, false)
        val offer = ioMarket.getActiveOffers(MarketOfferType.Buy, 2160, 0).first()
        val item = Item(id = 2160, type = marketItems(stackable = true).get(2160), count = 5)
        seller.addItem(item)
        game.getLockerItemsAndCountById = { _, _, _, _ -> listOf(item) to item.count }
        val inboxInserts = mutableListOf<Pair<Player, Int>>()
        game.addItemBatchToPaginedContainer = { recipient, _, _, count, _, _, testOnly ->
            if (!testOnly) {
                inboxInserts.add(recipient to count)
            }
            ReturnValue.NOERROR to count
        }
        val balances = mutableListOf<Long>()
        game.setBankBalance = { _, value -> balances.add(value) }
        val accepted = mutableListOf<MarketOfferEx>()
        game.sendMarketAcceptOffer = { _, offerEx -> accepted.add(offerEx) }
        val calls = mutableListOf<String>()
        game.updateUIExhausted = { calls.add("exhausted") }
        game.savePlayer = { calls.add("save") }

        game.playerAcceptMarketOffer(seller.id, offer.timestamp, offer.counter, 3)

        assertThat(inboxInserts).containsExactly(buyer to 3)
        assertThat(balances).containsExactly(300L)
        assertThat(accepted).hasSize(1)
        assertThat(accepted.first().amount).isEqualTo(2)
        assertThat(accepted.first().timestamp).isEqualTo(offer.timestamp)
        assertThat(calls).containsExactly("exhausted", "save")
        val remaining = ioMarket.getActiveOffers(MarketOfferType.Buy, 2160, 0)
        assertThat(remaining).hasSize(1)
        assertThat(remaining.first().amount).isEqualTo(2)
    }

    @Test
    fun `playerAcceptMarketOffer debits the buyer bank and pays the seller on a sell offer`() {
        val game = newGame(marketItems())
        val seller = game.player(1, "Seller")
        val buyer = game.player(100, "Buyer")
        seller.account = Account(id = 1)
        ioMarket.createOffer(seller.id, MarketOfferType.Sell, 2160, 5, 100, 0, false)
        val offer = ioMarket.getActiveOffers(MarketOfferType.Sell, 2160, 0).first()
        game.getBankBalance = { 1000L }
        val balances = mutableListOf<Long>()
        game.setBankBalance = { _, value -> balances.add(value) }
        val inboxInserts = mutableListOf<Pair<Player, Int>>()
        game.addItemBatchToPaginedContainer = { recipient, _, _, count, _, _, testOnly ->
            if (!testOnly) {
                inboxInserts.add(recipient to count)
            }
            ReturnValue.NOERROR to count
        }
        var receivedMail = false
        game.onReceiveMail = { receivedMail = true }
        val accepted = mutableListOf<MarketOfferEx>()
        game.sendMarketAcceptOffer = { _, offerEx -> accepted.add(offerEx) }

        game.playerAcceptMarketOffer(buyer.id, offer.timestamp, offer.counter, 3)

        assertThat(balances).containsExactly(700L, 1300L)
        assertThat(inboxInserts).containsExactly(buyer to 3)
        assertThat(receivedMail).isTrue()
        assertThat(accepted).hasSize(1)
        assertThat(accepted.first().amount).isEqualTo(2)
        val remaining = ioMarket.getActiveOffers(MarketOfferType.Sell, 2160, 0)
        assertThat(remaining).hasSize(1)
        assertThat(remaining.first().amount).isEqualTo(2)
    }

    @Test
    fun `playerAcceptMarketOffer deletes the offer when fully accepted`() {
        val game = newGame(marketItems(stackable = true))
        val seller = game.player(1, "Seller")
        val buyer = game.player(100, "Buyer")
        buyer.account = Account(id = 100)
        ioMarket.createOffer(buyer.id, MarketOfferType.Buy, 2160, 5, 100, 0, false)
        val offer = ioMarket.getActiveOffers(MarketOfferType.Buy, 2160, 0).first()
        val item = Item(id = 2160, type = marketItems(stackable = true).get(2160), count = 5)
        seller.addItem(item)
        game.getLockerItemsAndCountById = { _, _, _, _ -> listOf(item) to item.count }
        game.addItemBatchToPaginedContainer = { _, _, _, count, _, _, _ -> ReturnValue.NOERROR to count }
        game.setBankBalance = { _, _ -> }
        game.sendMarketAcceptOffer = { _, _ -> }

        game.playerAcceptMarketOffer(seller.id, offer.timestamp, offer.counter, 5)

        assertThat(ioMarket.getActiveOffers(MarketOfferType.Buy)).isEmpty()
        val history = allHistoryRows()
        assertThat(history).hasSize(2)
        assertThat(history).anyMatch { it.state == MarketOfferState.AcceptedEx.value && it.itemId == 2160 }
        assertThat(history).anyMatch { it.state == MarketOfferState.Accepted.value && it.itemId == 2160 }
    }

    @Test
    fun `playerAcceptMarketOffer refuses a sell offer the buyer cannot afford`() {
        val game = newGame(marketItems())
        val seller = game.player(1, "Seller")
        val buyer = game.player(100, "Buyer")
        ioMarket.createOffer(seller.id, MarketOfferType.Sell, 2160, 5, 1000, 0, false)
        val offer = ioMarket.getActiveOffers(MarketOfferType.Sell, 2160, 0).first()
        game.getBankBalance = { 100L }
        game.getMoney = { 0L }
        var balanceSet = false
        game.setBankBalance = { _, _ -> balanceSet = true }

        game.playerAcceptMarketOffer(buyer.id, offer.timestamp, offer.counter, 5)

        assertThat(balanceSet).isFalse()
        assertThat(ioMarket.getActiveOffers(MarketOfferType.Sell, 2160, 0)).hasSize(1)
    }
}
