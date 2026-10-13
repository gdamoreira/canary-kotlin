package org.opentibia.canary.game

import com.zaxxer.hikari.HikariDataSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.database.DatabaseConnectionFactory
import org.opentibia.canary.economy.market.MarketOfferType
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.io.HistoryMarketOffer
import org.opentibia.canary.io.IOMarket
import org.opentibia.canary.io.MarketOffer
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.items.Items
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position

/**
 * Transcript tests for the market `Game` handlers:
 *
 *  - `Game::playerBrowseMarket` (game.cpp:10282)
 *  - `Game::playerBrowseMarketOwnOffers` (game.cpp:10307)
 *  - `Game::playerBrowseMarketOwnHistory` (game.cpp:10322)
 *  - `Game::getItemMarketPrice` (game.cpp:4027)
 *  - `Game::getItemMarketAveragePrice` (game.cpp:4078)
 *
 * The `IOMarket` persistence is the ported [IOMarket] over an in-memory H2
 * database (Flyway V1-V12), matching `IOMarketTest`.
 */
class GameMarketTest {

    private lateinit var dataSource: HikariDataSource
    private lateinit var ioMarket: IOMarket

    private val center = Position(1000, 1000, 7)
    private val fixedNow = 1_700_000_000L

    @BeforeEach
    fun setUp() {
        dataSource = DatabaseConnectionFactory.createInMemoryDataSource("canary_game_market_${System.nanoTime()}")
        val applied = DatabaseConnectionFactory.migrate(dataSource)
        assertThat(applied).isGreaterThanOrEqualTo(10)
        ioMarket = IOMarket(dataSource = dataSource, now = { fixedNow })
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
            addPlayer(it)
        }

    private fun marketItems(wareId: Int = 2160, itemId: Int = 2160): Items =
        Items().also { it.register(ItemType(id = itemId, name = "market item", wareId = wareId, buyPrice = 50, sellPrice = 25)) }

    private fun insertPlayerRow(id: Long, name: String) {
        dataSource.connection.use { connection ->
            connection.prepareStatement("INSERT INTO players (id, name, account_id, balance) VALUES (?, ?, 1, 0)").use { statement ->
                statement.setLong(1, id)
                statement.setString(2, name)
                statement.executeUpdate()
            }
        }
    }

    // ----- playerBrowseMarket -----

    @Test
    fun `playerBrowseMarket ignores a player outside the market`() {
        val game = newGame(marketItems())
        val player = game.player(1, "Buyer", inMarket = false)
        var browsed = false
        game.sendMarketBrowseItem = { _, _, _, _, _ -> browsed = true }

        game.playerBrowseMarket(player.id, 2160, 0)

        assertThat(browsed).isFalse()
    }

    @Test
    fun `playerBrowseMarket ignores an unknown item id`() {
        val game = newGame(Items())
        val player = game.player(1, "Buyer")
        var browsed = false
        game.sendMarketBrowseItem = { _, _, _, _, _ -> browsed = true }

        game.playerBrowseMarket(player.id, 2160, 0)

        assertThat(browsed).isFalse()
    }

    @Test
    fun `playerBrowseMarket ignores an item without ware id`() {
        val game = newGame(marketItems(wareId = 0))
        val player = game.player(1, "Buyer")
        var browsed = false
        game.sendMarketBrowseItem = { _, _, _, _, _ -> browsed = true }

        game.playerBrowseMarket(player.id, 2160, 0)

        assertThat(browsed).isFalse()
    }

    @Test
    fun `playerBrowseMarket sends the active offers and the item detail`() {
        insertPlayerRow(100, "Seller")
        insertPlayerRow(101, "BuyerTwo")
        ioMarket.createOffer(100, MarketOfferType.Sell, 2160, 5, 1000, 0, false)
        ioMarket.createOffer(101, MarketOfferType.Buy, 2160, 3, 900, 0, false)
        val game = newGame(marketItems())
        val player = game.player(1, "Buyer")
        val browsed = mutableListOf<Triple<Int, Pair<List<MarketOffer>, List<MarketOffer>>, Int>>()
        game.sendMarketBrowseItem = { _, itemId, buyOffers, sellOffers, tier ->
            browsed.add(Triple(itemId, buyOffers to sellOffers, tier))
        }
        val details = mutableListOf<Pair<Int, Int>>()
        game.sendMarketDetail = { _, itemId, tier -> details.add(itemId to tier) }

        game.playerBrowseMarket(player.id, 2160, 0)

        assertThat(browsed).hasSize(1)
        assertThat(browsed[0].first).isEqualTo(2160)
        assertThat(browsed[0].second.first).hasSize(1)
        assertThat(browsed[0].second.second).hasSize(1)
        assertThat(browsed[0].third).isEqualTo(0)
        assertThat(details).containsExactly(2160 to 0)
    }

    // ----- playerBrowseMarketOwnOffers / History -----

    @Test
    fun `playerBrowseMarketOwnOffers requires the market and sends both sides`() {
        ioMarket.createOffer(1, MarketOfferType.Buy, 2160, 3, 900, 0, false)
        ioMarket.createOffer(1, MarketOfferType.Sell, 2160, 5, 1000, 0, false)
        val game = newGame()
        val player = game.player(1, "Buyer")
        val sent = mutableListOf<Pair<List<MarketOffer>, List<MarketOffer>>>()
        game.sendMarketBrowseOwnOffers = { _, buyOffers, sellOffers -> sent.add(buyOffers to sellOffers) }

        game.playerBrowseMarketOwnOffers(player.id)

        assertThat(sent).hasSize(1)
        assertThat(sent[0].first).hasSize(1)
        assertThat(sent[0].second).hasSize(1)
    }

    @Test
    fun `playerBrowseMarketOwnOffers ignores a player outside the market`() {
        val game = newGame()
        val player = game.player(1, "Buyer", inMarket = false)
        var sent = false
        game.sendMarketBrowseOwnOffers = { _, _, _ -> sent = true }

        game.playerBrowseMarketOwnOffers(player.id)

        assertThat(sent).isFalse()
    }

    @Test
    fun `playerBrowseMarketOwnHistory requires the market and sends both sides`() {
        ioMarket.createOffer(1, MarketOfferType.Buy, 2160, 3, 900, 0, false)
        val offerId = ioMarket.getActiveOffers(MarketOfferType.Buy).first().let { rawOfferId(it) }
        ioMarket.moveOfferToHistory(offerId, org.opentibia.canary.io.MarketOfferState.Cancelled)
        val game = newGame()
        val player = game.player(1, "Buyer")
        val sent = mutableListOf<Pair<List<HistoryMarketOffer>, List<HistoryMarketOffer>>>()
        game.sendMarketBrowseOwnHistory = { _, buyOffers, sellOffers -> sent.add(buyOffers to sellOffers) }

        game.playerBrowseMarketOwnHistory(player.id)

        assertThat(sent).hasSize(1)
        assertThat(sent[0].first).hasSize(1)
        assertThat(sent[0].second).isEmpty()
    }

    private fun rawOfferId(offer: MarketOffer): Long {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "SELECT id FROM market_offers WHERE itemtype = ? AND amount = ? ORDER BY id DESC LIMIT 1",
            ).use { statement ->
                statement.setLong(1, offer.itemId.toLong())
                statement.setLong(2, offer.amount.toLong())
                statement.executeQuery().use { result ->
                    result.next()
                    return result.getLong(1)
                }
            }
        }
    }

    // ----- getItemMarketPrice -----

    @Test
    fun `getItemMarketPrice converts gold platinum and crystal coins`() {
        val game = newGame()

        val total = game.getItemMarketPrice(mapOf(3031 to 7L, 3035 to 3L, 3043 to 2L), buyPrice = true)

        assertThat(total).isEqualTo(7 + 300 + 20000)
    }

    @Test
    fun `getItemMarketPrice prefers the tier zero market price over the npc price`() {
        val game = newGame(marketItems(itemId = 2160))
        game.itemsPriceMap = mapOf(2160 to mapOf(0 to 777L))

        assertThat(game.getItemMarketPrice(mapOf(2160 to 2L), buyPrice = true)).isEqualTo(1554)
    }

    @Test
    fun `getItemMarketPrice falls back to the npc price`() {
        val game = newGame(marketItems(itemId = 2160))

        assertThat(game.getItemMarketPrice(mapOf(2160 to 2L), buyPrice = true)).isEqualTo(100)
        assertThat(game.getItemMarketPrice(mapOf(2160 to 2L), buyPrice = false)).isEqualTo(50)
    }

    @Test
    fun `getItemMarketPrice returns zero for an unknown item`() {
        val game = newGame()

        assertThat(game.getItemMarketPrice(mapOf(9999 to 2L), buyPrice = true)).isZero()
    }

    // ----- getItemMarketAveragePrice -----

    @Test
    fun `getItemMarketAveragePrice returns the fixed coin values`() {
        val game = newGame()

        assertThat(game.getItemMarketAveragePrice(3031, 0)).isEqualTo(1)
        assertThat(game.getItemMarketAveragePrice(3035, 0)).isEqualTo(100)
        assertThat(game.getItemMarketAveragePrice(3043, 0)).isEqualTo(10000)
    }

    @Test
    fun `getItemMarketAveragePrice is zero without statistics`() {
        val game = newGame()

        assertThat(game.getItemMarketAveragePrice(2160, 0)).isZero()
    }

    @Test
    fun `getItemMarketAveragePrice blends purchase and sale averages with the bit formula`() {
        insertPlayerRow(100, "Historian")
        insertHistoryRow(itemId = 2160, sale = MarketOfferType.Buy.ordinal, price = 50, tier = 0)
        insertHistoryRow(itemId = 2160, sale = MarketOfferType.Buy.ordinal, price = 50, tier = 0)
        insertHistoryRow(itemId = 2160, sale = MarketOfferType.Sell.ordinal, price = 150, tier = 0)
        insertHistoryRow(itemId = 2160, sale = MarketOfferType.Sell.ordinal, price = 150, tier = 0)
        ioMarket.updateStatistics()
        val game = newGame()

        assertThat(game.getItemMarketAveragePrice(2160, 0)).isEqualTo(100)
    }

    private fun insertHistoryRow(itemId: Int, sale: Int, price: Long, tier: Int) {
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
                statement.setInt(8, 3)
                statement.setInt(9, tier)
                statement.executeUpdate()
            }
        }
    }
}
