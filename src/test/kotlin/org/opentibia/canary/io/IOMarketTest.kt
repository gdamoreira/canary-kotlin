package org.opentibia.canary.io

import com.zaxxer.hikari.HikariDataSource
import java.sql.Statement
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.opentibia.canary.database.DatabaseConnectionFactory
import org.opentibia.canary.economy.market.MarketOfferType

/**
 * H2 (Flyway V1-V12) round-trip tests for [IOMarket], transcribed from Canary
 * `src/io/iomarket.cpp` / `src/io/iomarket.hpp` and the `market_offers` /
 * `market_history` tables (schema.sql:516-551).
 *
 * The MySQL string-interpolated statements of the C++ original are preserved;
 * only the MySQL-only `^` XOR counter lookup is evaluated in Kotlin.
 */
class IOMarketTest {

    private lateinit var dataSource: HikariDataSource
    private lateinit var escrow: RecordingMarketEscrow
    private lateinit var market: IOMarket
    private val scheduledChecks = mutableListOf<Long>()
    private val fixedNow = 1_700_000_000L
    private var buyerId = 0L
    private var sellerId = 0L

    private val duration: Long get() = MarketConfig().marketOfferDuration

    @BeforeEach
    fun setUp() {
        dataSource = DatabaseConnectionFactory.createInMemoryDataSource("canary_io_market_${System.nanoTime()}")
        val applied = DatabaseConnectionFactory.migrate(dataSource)
        assertThat(applied).isGreaterThanOrEqualTo(10)
        escrow = RecordingMarketEscrow()
        scheduledChecks.clear()
        market = newMarket()
        buyerId = insertPlayer(100, "Buyer")
        sellerId = insertPlayer(101, "Seller")
    }

    @AfterEach
    fun tearDown() {
        dataSource.close()
    }

    private fun newMarket(
        config: MarketConfig = MarketConfig(),
        marketEscrow: MarketEscrow = escrow,
    ): IOMarket = IOMarket(
        dataSource = dataSource,
        config = config,
        escrow = marketEscrow,
        now = { fixedNow },
        scheduleCheck = { scheduledChecks.add(it) },
    )

    @Test
    fun `tier from database table parses atoi and clamps above max`() {
        assertThat(market.getTierFromDatabaseTable("0")).isEqualTo(0)
        assertThat(market.getTierFromDatabaseTable("7")).isEqualTo(7)
        assertThat(market.getTierFromDatabaseTable("10")).isEqualTo(10)
        assertThat(market.getTierFromDatabaseTable("11")).isEqualTo(0)
        assertThat(market.getTierFromDatabaseTable("3x")).isEqualTo(3)
        assertThat(market.getTierFromDatabaseTable("abc")).isEqualTo(0)
    }

    @Test
    fun `create offer persists sell rows with name anonymous and duration timestamp`() {
        market.createOffer(sellerId, MarketOfferType.Sell, 2160, 5, 1000, 2, false)
        market.createOffer(sellerId, MarketOfferType.Sell, 2160, 3, 900, 0, true)

        val offers = market.getActiveOffers(MarketOfferType.Sell)
        assertThat(offers).hasSize(2)

        val named = offers.single { it.tier == 2 }
        val namedId = rawOfferId(sellerId, amount = 5)
        assertThat(named.itemId).isEqualTo(2160)
        assertThat(named.amount).isEqualTo(5)
        assertThat(named.price).isEqualTo(1000)
        assertThat(named.timestamp).isEqualTo(fixedNow + duration)
        assertThat(named.counter).isEqualTo(counterOf(namedId))
        assertThat(named.playerName).isEqualTo("Seller")

        val anonymous = offers.single { it.tier == 0 }
        assertThat(anonymous.playerName).isEqualTo("Anonymous")
    }

    @Test
    fun `active offers for item order by price and created per side`() {
        insertOffer(sellerId, MarketOfferType.Sell, 2160, amount = 1, created = 100, anonymous = false, price = 700, tier = 0)
        insertOffer(sellerId, MarketOfferType.Sell, 2160, amount = 1, created = 200, anonymous = false, price = 500, tier = 0)
        insertOffer(sellerId, MarketOfferType.Sell, 2160, amount = 1, created = 50, anonymous = false, price = 700, tier = 0)
        insertOffer(sellerId, MarketOfferType.Sell, 2160, amount = 1, created = 300, anonymous = false, price = 500, tier = 1)
        insertOffer(sellerId, MarketOfferType.Buy, 2160, amount = 1, created = 10, anonymous = false, price = 100, tier = 0)

        assertThat(market.getActiveOffers(MarketOfferType.Sell, 2160, 0).map { it.timestamp - duration })
            .containsExactly(200L, 50L, 100L)
        assertThat(market.getActiveOffers(MarketOfferType.Sell, 2160, 1).map { it.timestamp - duration })
            .containsExactly(300L)

        insertOffer(buyerId, MarketOfferType.Buy, 2160, amount = 1, created = 10, anonymous = false, price = 100, tier = 0)
        insertOffer(buyerId, MarketOfferType.Buy, 2160, amount = 1, created = 20, anonymous = false, price = 300, tier = 0)
        assertThat(market.getActiveOffers(MarketOfferType.Buy, 2160, 0).map { it.price })
            .containsExactly(300L, 100L, 100L)
    }

    @Test
    fun `own offers lists player rows ordered by created`() {
        insertOffer(sellerId, MarketOfferType.Sell, 2160, amount = 2, created = 300, anonymous = false, price = 700, tier = 3)
        insertOffer(sellerId, MarketOfferType.Sell, 2152, amount = 1, created = 100, anonymous = false, price = 500, tier = 0)
        insertOffer(sellerId, MarketOfferType.Buy, 2160, amount = 1, created = 50, anonymous = false, price = 100, tier = 0)
        insertOffer(buyerId, MarketOfferType.Sell, 2160, amount = 1, created = 1, anonymous = false, price = 100, tier = 0)

        val offers = market.getOwnOffers(MarketOfferType.Sell, sellerId)
        assertThat(offers.map { it.itemId }).containsExactly(2152, 2160)
        assertThat(offers.map { it.timestamp - duration }).containsExactly(100L, 300L)
        assertThat(offers[1].amount).isEqualTo(2)
        assertThat(offers[1].price).isEqualTo(700)
        assertThat(offers[1].tier).isEqualTo(3)
        assertThat(offers[1].playerName).isEmpty()
    }

    @Test
    fun `own history normalizes accepted exchange state`() {
        appendHistoryRow(sellerId, MarketOfferType.Sell, 2160, 4, 2500, 777, 3, MarketOfferState.Expired)
        appendHistoryRow(sellerId, MarketOfferType.Sell, 2160, 1, 500, 778, 0, MarketOfferState.AcceptedEx)
        appendHistoryRow(sellerId, MarketOfferType.Buy, 2160, 1, 500, 779, 0, MarketOfferState.Accepted)
        appendHistoryRow(buyerId, MarketOfferType.Sell, 2160, 1, 500, 780, 0, MarketOfferState.Cancelled)

        val history = market.getOwnHistory(MarketOfferType.Sell, sellerId)
        assertThat(history).hasSize(2)
        assertThat(history.map { it.state }).containsExactlyInAnyOrder(
            MarketOfferState.Expired,
            MarketOfferState.Accepted,
        )
        val expired = history.single { it.state == MarketOfferState.Expired }
        assertThat(expired.itemId).isEqualTo(2160)
        assertThat(expired.amount).isEqualTo(4)
        assertThat(expired.price).isEqualTo(2500)
        assertThat(expired.timestamp).isEqualTo(777)
        assertThat(expired.tier).isEqualTo(3)
    }

    @Test
    fun `accept offer subtracts amount and delete offer removes the row`() {
        val offerId = insertOffer(sellerId, MarketOfferType.Sell, 2160, amount = 10, created = 100, anonymous = false, price = 100, tier = 0)
        val otherId = insertOffer(sellerId, MarketOfferType.Sell, 2152, amount = 4, created = 100, anonymous = false, price = 100, tier = 0)

        market.acceptOffer(offerId, 3)
        market.deleteOffer(otherId)

        val offers = market.getOwnOffers(MarketOfferType.Sell, sellerId)
        assertThat(offers.map { it.amount }).containsExactly(7)
    }

    @Test
    fun `player offer count counts every side`() {
        insertOffer(sellerId, MarketOfferType.Sell, 2160, amount = 1, created = 100, anonymous = false, price = 100, tier = 0)
        insertOffer(sellerId, MarketOfferType.Buy, 2160, amount = 1, created = 100, anonymous = false, price = 100, tier = 0)
        insertOffer(buyerId, MarketOfferType.Sell, 2160, amount = 1, created = 100, anonymous = false, price = 100, tier = 0)

        assertThat(market.getPlayerOfferCount(sellerId)).isEqualTo(2)
        assertThat(market.getPlayerOfferCount(buyerId)).isEqualTo(1)
        assertThat(market.getPlayerOfferCount(9999)).isEqualTo(0)
    }

    @Test
    fun `offer by counter decodes the xor counter and anonymous name`() {
        val id = insertOffer(sellerId, MarketOfferType.Sell, 2160, amount = 5, created = 12_345, anonymous = false, price = 900, tier = 4)
        val anonymousId = insertOffer(sellerId, MarketOfferType.Buy, 2152, amount = 2, created = 12_345, anonymous = true, price = 400, tier = 0)

        val offer = market.getOfferByCounter(12_345 + duration, counterOf(id))
        assertThat(offer.id).isEqualTo(id)
        assertThat(offer.playerId).isEqualTo(sellerId)
        assertThat(offer.type).isEqualTo(MarketOfferType.Sell)
        assertThat(offer.amount).isEqualTo(5)
        assertThat(offer.counter).isEqualTo(counterOf(id))
        assertThat(offer.timestamp).isEqualTo(12_345)
        assertThat(offer.price).isEqualTo(900)
        assertThat(offer.itemId).isEqualTo(2160)
        assertThat(offer.tier).isEqualTo(4)
        assertThat(offer.playerName).isEqualTo("Seller")

        val anonymous = market.getOfferByCounter(12_345 + duration, counterOf(anonymousId))
        assertThat(anonymous.type).isEqualTo(MarketOfferType.Buy)
        assertThat(anonymous.playerName).isEqualTo("Anonymous")
    }

    @Test
    fun `offer by counter returns zero id when nothing matches`() {
        insertOffer(sellerId, MarketOfferType.Sell, 2160, amount = 1, created = 12_345, anonymous = false, price = 100, tier = 0)

        assertThat(market.getOfferByCounter(12_345 + duration, 0).id).isEqualTo(0)
        assertThat(market.getOfferByCounter(999, 1).id).isEqualTo(0)
    }

    @Test
    fun `append history persists expires inserted state and tier`() {
        market.appendHistory(sellerId, MarketOfferType.Sell, 2160, 4, 2500, 777, 3, MarketOfferState.Expired)

        val row = queryHistoryRow(sellerId)
        assertThat(row.sale).isEqualTo(MarketOfferType.Sell.ordinal)
        assertThat(row.itemtype).isEqualTo(2160)
        assertThat(row.amount).isEqualTo(4)
        assertThat(row.price).isEqualTo(2500)
        assertThat(row.expiresAt).isEqualTo(777)
        assertThat(row.inserted).isEqualTo(fixedNow)
        assertThat(row.state).isEqualTo(MarketOfferState.Expired.value)
        assertThat(row.tier).isEqualTo(3)
    }

    @Test
    fun `move offer to history deletes and appends`() {
        val id = insertOffer(sellerId, MarketOfferType.Sell, 2160, amount = 6, created = 50, anonymous = false, price = 1500, tier = 2)

        assertThat(market.moveOfferToHistory(id, MarketOfferState.Cancelled)).isTrue()

        assertThat(market.getOwnOffers(MarketOfferType.Sell, sellerId)).isEmpty()
        val row = queryHistoryRow(sellerId)
        assertThat(row.itemtype).isEqualTo(2160)
        assertThat(row.amount).isEqualTo(6)
        assertThat(row.price).isEqualTo(1500)
        assertThat(row.expiresAt).isEqualTo(fixedNow)
        assertThat(row.state).isEqualTo(MarketOfferState.Cancelled.value)
        assertThat(row.tier).isEqualTo(2)

        assertThat(market.moveOfferToHistory(id, MarketOfferState.Cancelled)).isFalse()
    }

    @Test
    fun `check expired offers moves only expired rows and reschedules`() {
        val expiredId = insertOffer(sellerId, MarketOfferType.Sell, 2160, amount = 1, created = fixedNow - duration - 1, anonymous = false, price = 100, tier = 0)
        insertOffer(sellerId, MarketOfferType.Sell, 2152, amount = 1, created = fixedNow - duration + 1, anonymous = false, price = 100, tier = 0)

        market.checkExpiredOffers()

        assertThat(market.getPlayerOfferCount(sellerId)).isEqualTo(1)
        val row = queryHistoryRow(sellerId)
        assertThat(row.state).isEqualTo(MarketOfferState.Expired.value)
        assertThat(row.expiresAt).isEqualTo(fixedNow)
        assertThat(rawOfferIds(sellerId)).doesNotContain(expiredId)
        assertThat(scheduledChecks).containsExactly(3_600_000L)
    }

    @Test
    fun `check expired offers does not reschedule when interval is zero`() {
        val noReschedule = newMarket(config = MarketConfig(checkExpiredMarketOffersEachMinutes = 0))

        insertOffer(sellerId, MarketOfferType.Sell, 2160, amount = 1, created = fixedNow - duration - 1, anonymous = false, price = 100, tier = 0)
        noReschedule.checkExpiredOffers()

        assertThat(scheduledChecks).isEmpty()
        assertThat(noReschedule.getPlayerOfferCount(sellerId)).isEqualTo(0)
    }

    @Test
    fun `expired sell offer delivers stackable items in chunks of one hundred`() {
        escrow.itemTypes[2160] = MarketEscrowItemType(2160, stackable = true, charges = 0)
        escrow.players[sellerId] = FakeEscrowPlayer(sellerId, offline = false)
        insertOffer(sellerId, MarketOfferType.Sell, 2160, amount = 250, created = fixedNow - duration - 1, anonymous = false, price = 100, tier = 4)

        market.checkExpiredOffers()

        assertThat(escrow.deliveries).containsExactly(
            Delivery(sellerId, 2160, 100, 4),
            Delivery(sellerId, 2160, 100, 4),
            Delivery(sellerId, 2160, 50, 4),
        )
        assertThat(escrow.players.getValue(sellerId).saved).isFalse()
    }

    @Test
    fun `expired sell offer delivers non stackable items with charges subtype`() {
        escrow.itemTypes[2376] = MarketEscrowItemType(2376, stackable = false, charges = 3)
        escrow.itemTypes[2380] = MarketEscrowItemType(2380, stackable = false, charges = 0)
        escrow.players[sellerId] = FakeEscrowPlayer(sellerId, offline = false)
        insertOffer(sellerId, MarketOfferType.Sell, 2376, amount = 2, created = fixedNow - duration - 1, anonymous = false, price = 100, tier = 1)
        insertOffer(sellerId, MarketOfferType.Sell, 2380, amount = 3, created = fixedNow - duration - 1, anonymous = false, price = 100, tier = 0)

        market.checkExpiredOffers()

        assertThat(escrow.deliveries.filter { it.itemId == 2376 })
            .containsExactly(Delivery(sellerId, 2376, 3, 1), Delivery(sellerId, 2376, 3, 1))
        assertThat(escrow.deliveries.filter { it.itemId == 2380 })
            .containsExactly(
                Delivery(sellerId, 2380, -1, 0),
                Delivery(sellerId, 2380, -1, 0),
                Delivery(sellerId, 2380, -1, 0),
            )
    }

    @Test
    fun `expired sell offer with unknown item type keeps the history row only`() {
        escrow.players[sellerId] = FakeEscrowPlayer(sellerId, offline = false)
        insertOffer(sellerId, MarketOfferType.Sell, 9999, amount = 1, created = fixedNow - duration - 1, anonymous = false, price = 100, tier = 0)

        market.checkExpiredOffers()

        assertThat(escrow.deliveries).isEmpty()
        assertThat(queryHistoryRow(sellerId).state).isEqualTo(MarketOfferState.Expired.value)
    }

    @Test
    fun `expired sell offer skips delivery for unresolvable player`() {
        escrow.itemTypes[2160] = MarketEscrowItemType(2160, stackable = true, charges = 0)
        insertOffer(sellerId, MarketOfferType.Sell, 2160, amount = 5, created = fixedNow - duration - 1, anonymous = false, price = 100, tier = 0)

        market.checkExpiredOffers()

        assertThat(escrow.deliveries).isEmpty()
        assertThat(market.getPlayerOfferCount(sellerId)).isEqualTo(0)
    }

    @Test
    fun `expired sell offer stops delivering after a failed inbox add`() {
        escrow.itemTypes[2160] = MarketEscrowItemType(2160, stackable = true, charges = 0)
        escrow.players[sellerId] = FakeEscrowPlayer(sellerId, offline = false)
        escrow.failDeliveryAttempt = 2
        insertOffer(sellerId, MarketOfferType.Sell, 2160, amount = 250, created = fixedNow - duration - 1, anonymous = false, price = 100, tier = 0)

        market.checkExpiredOffers()

        assertThat(escrow.deliveries).containsExactly(Delivery(sellerId, 2160, 100, 0))
    }

    @Test
    fun `expired sell offer saves the offline player after delivery`() {
        escrow.itemTypes[2160] = MarketEscrowItemType(2160, stackable = true, charges = 0)
        escrow.players[sellerId] = FakeEscrowPlayer(sellerId, offline = true)
        insertOffer(sellerId, MarketOfferType.Sell, 2160, amount = 10, created = fixedNow - duration - 1, anonymous = false, price = 100, tier = 2)

        market.checkExpiredOffers()

        assertThat(escrow.deliveries).containsExactly(Delivery(sellerId, 2160, 10, 2))
        assertThat(escrow.players.getValue(sellerId).saved).isTrue()
    }

    @Test
    fun `expired buy offer credits an online player balance`() {
        escrow.players[buyerId] = FakeEscrowPlayer(buyerId, offline = false)
        insertOffer(buyerId, MarketOfferType.Buy, 2160, amount = 4, created = fixedNow - duration - 1, anonymous = false, price = 300, tier = 0)

        market.checkExpiredOffers()

        assertThat(escrow.bankBalanceAdds).containsExactly(buyerId to 1200L)
        assertThat(playerBalance(buyerId)).isEqualTo(0)
    }

    @Test
    fun `expired buy offer refunds an offline player through the database`() {
        insertOffer(buyerId, MarketOfferType.Buy, 2160, amount = 4, created = fixedNow - duration - 1, anonymous = false, price = 300, tier = 0)

        market.checkExpiredOffers()

        assertThat(escrow.bankBalanceAdds).isEmpty()
        assertThat(playerBalance(buyerId)).isEqualTo(1200)
    }

    @Test
    fun `update statistics groups accepted history by item sale and tier`() {
        market.appendHistory(buyerId, MarketOfferType.Buy, 100, 1, 100, 10, 1, MarketOfferState.Accepted)
        market.appendHistory(buyerId, MarketOfferType.Buy, 100, 1, 300, 11, 1, MarketOfferState.Accepted)
        market.appendHistory(buyerId, MarketOfferType.Sell, 100, 1, 50, 12, 0, MarketOfferState.Accepted)
        market.appendHistory(buyerId, MarketOfferType.Buy, 200, 1, 10, 13, 2, MarketOfferState.Accepted)
        market.appendHistory(buyerId, MarketOfferType.Buy, 100, 1, 999, 14, 1, MarketOfferState.Expired)

        market.updateStatistics()

        val purchase = market.getPurchaseStatistics()
        assertThat(purchase[100]?.get(1)).isEqualTo(MarketStatistics(2, 300, 400, 100))
        assertThat(purchase[200]?.get(2)).isEqualTo(MarketStatistics(1, 10, 10, 10))
        assertThat(purchase[100]?.get(0)).isNull()

        val sale = market.getSaleStatistics()
        assertThat(sale[100]?.get(0)).isEqualTo(MarketStatistics(1, 50, 50, 50))
        assertThat(sale[200]).isNull()

        val snapshot = market.getStatistics(100, 1)
        assertThat(snapshot.purchase).isEqualTo(MarketStatistics(2, 300, 400, 100))
        assertThat(snapshot.sale).isNull()

        val saleSnapshot = market.getStatistics(100, 0)
        assertThat(saleSnapshot.purchase).isNull()
        assertThat(saleSnapshot.sale).isEqualTo(MarketStatistics(1, 50, 50, 50))

        val empty = market.getStatistics(999, 0)
        assertThat(empty.purchase).isNull()
        assertThat(empty.sale).isNull()
    }

    @Test
    fun `update statistics keeps the previous snapshot when history is empty`() {
        market.appendHistory(buyerId, MarketOfferType.Buy, 100, 1, 100, 10, 1, MarketOfferState.Accepted)
        market.updateStatistics()
        assertThat(market.getPurchaseStatistics()).isNotEmpty()

        execute("DELETE FROM market_history")
        market.updateStatistics()

        // iomarket.cpp:355-358 returns before the swap when the query has no rows.
        assertThat(market.getPurchaseStatistics()).containsKey(100)
        assertThat(market.getSaleStatistics()).isEmpty()
    }

    @Test
    fun `increase bank balance adds to the players balance column`() {
        assertThat(playerBalance(buyerId)).isEqualTo(0)

        market.increaseBankBalance(buyerId, 250)

        assertThat(playerBalance(buyerId)).isEqualTo(250)
    }

    private fun insertPlayer(id: Long, name: String, balance: Long = 0): Long {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "INSERT INTO players (id, name, account_id, balance) VALUES (?, ?, 1, ?)",
            ).use { stmt ->
                stmt.setLong(1, id)
                stmt.setString(2, name)
                stmt.setLong(3, balance)
                stmt.executeUpdate()
            }
        }
        return id
    }

    private fun insertOffer(
        playerId: Long,
        sale: MarketOfferType,
        itemId: Int,
        amount: Int,
        created: Long,
        anonymous: Boolean,
        price: Long,
        tier: Int,
    ): Long {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "INSERT INTO market_offers (player_id, sale, itemtype, amount, created, anonymous, price, tier) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS,
            ).use { stmt ->
                stmt.setLong(1, playerId)
                stmt.setInt(2, sale.ordinal)
                stmt.setInt(3, itemId)
                stmt.setInt(4, amount)
                stmt.setLong(5, created)
                stmt.setInt(6, if (anonymous) 1 else 0)
                stmt.setLong(7, price)
                stmt.setInt(8, tier)
                stmt.executeUpdate()
                stmt.generatedKeys.use { keys ->
                    keys.next()
                    return keys.getLong(1)
                }
            }
        }
    }

    private fun appendHistoryRow(
        playerId: Long,
        sale: MarketOfferType,
        itemId: Int,
        amount: Int,
        price: Long,
        expiresAt: Long,
        tier: Int,
        state: MarketOfferState,
    ) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "INSERT INTO market_history (player_id, sale, itemtype, amount, price, expires_at, inserted, state, tier) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
            ).use { stmt ->
                stmt.setLong(1, playerId)
                stmt.setInt(2, sale.ordinal)
                stmt.setInt(3, itemId)
                stmt.setInt(4, amount)
                stmt.setLong(5, price)
                stmt.setLong(6, expiresAt)
                stmt.setLong(7, fixedNow)
                stmt.setInt(8, state.value)
                stmt.setInt(9, tier)
                stmt.executeUpdate()
            }
        }
    }

    private fun execute(sql: String) {
        dataSource.connection.use { conn ->
            conn.createStatement().use { it.executeUpdate(sql) }
        }
    }

    private fun counterOf(id: Long): Int = ((id xor 0xABCDEF) and 0xFFFF).toInt()

    private fun rawOfferId(playerId: Long, amount: Int): Long =
        dataSource.connection.use { conn ->
            conn.prepareStatement("SELECT id FROM market_offers WHERE player_id = ? AND amount = ?").use { stmt ->
                stmt.setLong(1, playerId)
                stmt.setInt(2, amount)
                stmt.executeQuery().use { rs ->
                    rs.next()
                    rs.getLong(1)
                }
            }
        }

    private fun rawOfferIds(playerId: Long): List<Long> =
        dataSource.connection.use { conn ->
            conn.prepareStatement("SELECT id FROM market_offers WHERE player_id = ?").use { stmt ->
                stmt.setLong(1, playerId)
                stmt.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            add(rs.getLong(1))
                        }
                    }
                }
            }
        }

    private fun queryHistoryRow(playerId: Long): HistoryRow =
        dataSource.connection.use { conn ->
            conn.prepareStatement("SELECT * FROM market_history WHERE player_id = ?").use { stmt ->
                stmt.setLong(1, playerId)
                stmt.executeQuery().use { rs ->
                    rs.next()
                    HistoryRow(
                        sale = rs.getInt("sale"),
                        itemtype = rs.getInt("itemtype"),
                        amount = rs.getInt("amount"),
                        price = rs.getLong("price"),
                        expiresAt = rs.getLong("expires_at"),
                        inserted = rs.getLong("inserted"),
                        state = rs.getInt("state"),
                        tier = rs.getInt("tier"),
                    )
                }
            }
        }

    private fun playerBalance(playerId: Long): Long =
        dataSource.connection.use { conn ->
            conn.prepareStatement("SELECT balance FROM players WHERE id = ?").use { stmt ->
                stmt.setLong(1, playerId)
                stmt.executeQuery().use { rs ->
                    rs.next()
                    rs.getLong(1)
                }
            }
        }
}

/** A raw `market_history` row read back by the tests. */
data class HistoryRow(
    val sale: Int,
    val itemtype: Int,
    val amount: Int,
    val price: Long,
    val expiresAt: Long,
    val inserted: Long,
    val state: Int,
    val tier: Int,
)

/** An escrow delivery recorded by [RecordingMarketEscrow]. */
data class Delivery(val playerId: Long, val itemId: Int, val count: Int, val tier: Int)

/** Fake `Player` handle for the escrow seam. */
class FakeEscrowPlayer(
    override val playerId: Long,
    private val offline: Boolean,
) : MarketEscrowPlayer {
    var saved: Boolean = false
        private set

    override fun isOffline(): Boolean = offline

    override fun save() {
        saved = true
    }
}

/** Recording fake for the [MarketEscrow] seam. */
class RecordingMarketEscrow : MarketEscrow {
    val itemTypes = mutableMapOf<Int, MarketEscrowItemType>()
    val players = mutableMapOf<Long, FakeEscrowPlayer>()
    val deliveries = mutableListOf<Delivery>()
    val bankBalanceAdds = mutableListOf<Pair<Long, Long>>()

    /** 1-based delivery attempt that fails; -1 never fails. */
    var failDeliveryAttempt: Int = -1

    private var deliveryAttempts = 0

    override fun getItemType(itemId: Int): MarketEscrowItemType? = itemTypes[itemId]

    override fun resolvePlayer(playerId: Long, allowOffline: Boolean): MarketEscrowPlayer? {
        val player = players[playerId] ?: return null
        if (!allowOffline && player.isOffline()) {
            return null
        }
        return player
    }

    override fun addItemToInbox(player: MarketEscrowPlayer, itemId: Int, count: Int, tier: Int): Boolean {
        deliveryAttempts++
        if (deliveryAttempts == failDeliveryAttempt) {
            return false
        }
        deliveries.add(Delivery(player.playerId, itemId, count, tier))
        return true
    }

    override fun addBankBalance(player: MarketEscrowPlayer, amount: Long) {
        bankBalanceAdds.add(player.playerId to amount)
    }
}
