package org.opentibia.canary.io

import io.github.oshai.kotlinlogging.KotlinLogging
import java.sql.SQLException
import javax.sql.DataSource
import org.opentibia.canary.database.DatabaseConnectionFactory
import org.opentibia.canary.database.DatabaseConnectionFactory.DBResult
import org.opentibia.canary.economy.market.MarketOfferType

private val logger = KotlinLogging.logger {}

/**
 * Faithful port of Canary `IOMarket` (`src/io/iomarket.hpp:16-60`,
 * `src/io/iomarket.cpp:21-410`) plus the two persistence calls the expired-offer
 * escrow makes outside the class:
 *  - `IOLoginData::increaseBankBalance` (src/io/iologindata.cpp:357-361)
 *  - `Game::getPlayerByGUID` / `Game::internalAddItem` / `Player::setBankBalance`
 *    behind the [MarketEscrow] seam.
 *
 * The global `g_database()` becomes a constructor-injected [DataSource]
 * (AGENTS.md 1.1); the `statisticsMutex` is dropped because the market tasks are
 * single-thread confined (AGENTS.md 1.2). The MySQL-only `id ^ 0xABCDEF` counter
 * filter of [getOfferByCounter] is evaluated in Kotlin because neither H2
 * PostgreSQL mode nor PostgreSQL implement MySQL's `^` XOR operator.
 */

/** `MARKET_OFFER_DURATION`, `CHECK_EXPIRED_MARKET_OFFERS_EACH_MINUTES`, `FORGE_MAX_ITEM_TIER` (configmanager.cpp:61,237,268). */
data class MarketConfig(
    val marketOfferDuration: Long = 30L * 24L * 60L * 60L,
    val checkExpiredMarketOffersEachMinutes: Int = 60,
    val forgeMaxItemTier: Int = 10,
)

/** `MarketOfferState_t` (src/creatures/creatures_definitions.hpp:358-365). */
enum class MarketOfferState(val value: Int) {
    Active(0),
    Cancelled(1),
    Expired(2),
    Accepted(3),
    AcceptedEx(255),
}

/** `MarketOffer` (src/creatures/creatures_definitions.hpp:1660-1668). */
data class MarketOffer(
    val itemId: Int,
    val amount: Int,
    val price: Long,
    val timestamp: Long,
    val counter: Int,
    val playerName: String,
    val tier: Int,
)

/** `MarketOfferEx` (src/creatures/creatures_definitions.hpp:1670-1694). */
data class MarketOfferEx(
    val id: Long = 0,
    val playerId: Long = 0,
    val timestamp: Long = 0,
    val price: Long = 0,
    val amount: Int = 0,
    val counter: Int = 0,
    val itemId: Int = 0,
    val type: MarketOfferType = MarketOfferType.Buy,
    val tier: Int = 0,
    val playerName: String = "",
)

/** `HistoryMarketOffer` (src/creatures/creatures_definitions.hpp:1696-1703). */
data class HistoryMarketOffer(
    val timestamp: Long,
    val price: Long,
    val itemId: Int,
    val amount: Int,
    val tier: Int,
    val state: MarketOfferState,
)

/** `MarketStatistics` (src/io/io_definitions.hpp:80-92). */
data class MarketStatistics(
    val numTransactions: Long = 0,
    val highestPrice: Long = 0,
    val totalPrice: Long = 0,
    val lowestPrice: Long = 0,
)

/** `IOMarket::StatisticsSnapshot` (iomarket.hpp:47-51). */
data class MarketStatisticsSnapshot(
    val purchase: MarketStatistics? = null,
    val sale: MarketStatistics? = null,
)

/** `IOMarket::StatisticsMap` (iomarket.hpp:44): item id then tier to statistics. */
typealias MarketStatisticsMap = Map<Int, Map<Int, MarketStatistics>>

/** The subset of `ItemType` read by the expired sell-offer escrow (iomarket.cpp:169-215). */
data class MarketEscrowItemType(
    val id: Int,
    val stackable: Boolean,
    val charges: Int,
)

/**
 * `Player` handle of the escrow path: `Player::getGUID`,
 * `Player::isOffline`, `Player::setBankBalance` and `SaveManager::savePlayer`
 * (iomarket.cpp:174-229).
 */
interface MarketEscrowPlayer {
    val playerId: Long

    fun isOffline(): Boolean

    fun save()
}

/**
 * Seam replacing the game-world calls of `IOMarket::processExpiredOffers`
 * (iomarket.cpp:155-232): `Item::items` / `Item::CreateItem` /
 * `Game::internalAddItem(player->getInbox(), ...)` and
 * `Game::getPlayerByGUID(guid, allowOffline)`.
 */
interface MarketEscrow {
    /** `Item::items[itemId]`; `null` mirrors `itemType.id == 0`. */
    fun getItemType(itemId: Int): MarketEscrowItemType?

    /** `Game::getPlayerByGUID(guid, allowOffline)` (game.cpp:1419-1437). */
    fun resolvePlayer(playerId: Long, allowOffline: Boolean): MarketEscrowPlayer?

    /** `Game::internalAddItem(player->getInbox(), item, INDEX_WHEREEVER, FLAG_NOLIMIT) != RETURNVALUE_NOERROR`. */
    fun addItemToInbox(player: MarketEscrowPlayer, itemId: Int, count: Int, tier: Int): Boolean

    /** `Player::setBankBalance(player->getBankBalance() + amount)` (iomarket.cpp:226). */
    fun addBankBalance(player: MarketEscrowPlayer, amount: Long)
}

/** Inert [MarketEscrow] used when no item/player world is wired. */
object NoMarketEscrow : MarketEscrow {
    override fun getItemType(itemId: Int): MarketEscrowItemType? = null

    override fun resolvePlayer(playerId: Long, allowOffline: Boolean): MarketEscrowPlayer? = null

    override fun addItemToInbox(player: MarketEscrowPlayer, itemId: Int, count: Int, tier: Int): Boolean = false

    override fun addBankBalance(player: MarketEscrowPlayer, amount: Long) = Unit
}

/** Current Unix time in seconds, Canary `getTimeNow()` (src/utils/tools.cpp:533). */
fun marketTimeNow(): Long = System.currentTimeMillis() / 1000

class IOMarket(
    private val dataSource: DataSource,
    private val config: MarketConfig = MarketConfig(),
    private val escrow: MarketEscrow = NoMarketEscrow,
    private val now: () -> Long = ::marketTimeNow,
    private val scheduleCheck: (delayMillis: Long) -> Unit = {},
) {
    private var purchaseStatistics: MarketStatisticsMap = emptyMap()
    private var saleStatistics: MarketStatisticsMap = emptyMap()

    /**
     * `IOMarket::getTierFromDatabaseTable` (iomarket.cpp:21-29): `std::atoi`
     * semantics, clamped to `FORGE_MAX_ITEM_TIER`.
     */
    fun getTierFromDatabaseTable(string: String): Int {
        val tier = atoi(string)
        if (tier > config.forgeMaxItemTier) {
            logger.error { "[getTierFromDatabaseTable] - Failed to get number value $tier for tier table result" }
            return 0
        }
        return tier
    }

    /** `IOMarket::getActiveOffers(MarketAction_t)` (iomarket.cpp:31-64). */
    fun getActiveOffers(action: MarketOfferType): List<MarketOffer> {
        val query = "SELECT id, itemtype, amount, price, tier, created, anonymous, " +
            "(SELECT name FROM players WHERE id = player_id) AS player_name " +
            "FROM market_offers WHERE sale = ${action.ordinal}"
        val result = DatabaseConnectionFactory.storeQuery(dataSource, query) ?: return emptyList()

        val offerList = mutableListOf<MarketOffer>()
        do {
            offerList.add(
                MarketOffer(
                    itemId = result.getNumber("itemtype").toInt(),
                    amount = result.getNumber("amount").toInt(),
                    price = result.getNumber("price"),
                    timestamp = result.getNumber("created") + config.marketOfferDuration,
                    counter = counterOf(result.getNumber("id")),
                    playerName = playerNameOf(result),
                    tier = getTierFromDatabaseTable(result.getString("tier")),
                ),
            )
        } while (result.next())
        return offerList
    }

    /**
     * `IOMarket::getActiveOffers(MarketAction_t, uint16_t, uint8_t)`
     * (iomarket.cpp:66-96): buy offers are ordered by descending price.
     */
    fun getActiveOffers(action: MarketOfferType, itemId: Int, tier: Int): List<MarketOffer> {
        val priceOrder = if (action == MarketOfferType.Buy) "DESC" else "ASC"
        val query = "SELECT id, amount, price, tier, created, anonymous, " +
            "(SELECT name FROM players WHERE id = player_id) AS player_name " +
            "FROM market_offers WHERE sale = ${action.ordinal} AND itemtype = $itemId AND tier = $tier " +
            "ORDER BY price $priceOrder, created ASC"
        val result = DatabaseConnectionFactory.storeQuery(dataSource, query) ?: return emptyList()

        val offerList = mutableListOf<MarketOffer>()
        do {
            offerList.add(
                MarketOffer(
                    itemId = itemId,
                    amount = result.getNumber("amount").toInt(),
                    price = result.getNumber("price"),
                    timestamp = result.getNumber("created") + config.marketOfferDuration,
                    counter = counterOf(result.getNumber("id")),
                    playerName = playerNameOf(result),
                    tier = getTierFromDatabaseTable(result.getString("tier")),
                ),
            )
        } while (result.next())
        return offerList
    }

    /** `IOMarket::getOwnOffers` (iomarket.cpp:98-122). */
    fun getOwnOffers(action: MarketOfferType, playerId: Long): List<MarketOffer> {
        val query = "SELECT id, amount, price, created, itemtype, tier FROM market_offers " +
            "WHERE player_id = $playerId AND sale = ${action.ordinal} ORDER BY created ASC"
        val result = DatabaseConnectionFactory.storeQuery(dataSource, query) ?: return emptyList()

        val offerList = mutableListOf<MarketOffer>()
        do {
            offerList.add(
                MarketOffer(
                    itemId = result.getNumber("itemtype").toInt(),
                    amount = result.getNumber("amount").toInt(),
                    price = result.getNumber("price"),
                    timestamp = result.getNumber("created") + config.marketOfferDuration,
                    counter = counterOf(result.getNumber("id")),
                    playerName = "",
                    tier = getTierFromDatabaseTable(result.getString("tier")),
                ),
            )
        } while (result.next())
        return offerList
    }

    /**
     * `IOMarket::getOwnHistory` (iomarket.cpp:124-153): `OFFERSTATE_ACCEPTEDEX`
     * is normalized to `OFFERSTATE_ACCEPTED`.
     */
    fun getOwnHistory(action: MarketOfferType, playerId: Long): List<HistoryMarketOffer> {
        val query = "SELECT itemtype, amount, price, expires_at, state, tier FROM market_history " +
            "WHERE player_id = $playerId AND sale = ${action.ordinal}"
        val result = DatabaseConnectionFactory.storeQuery(dataSource, query) ?: return emptyList()

        val offerList = mutableListOf<HistoryMarketOffer>()
        do {
            var offerState = stateOf(result.getNumber("state"))
            if (offerState == MarketOfferState.AcceptedEx) {
                offerState = MarketOfferState.Accepted
            }

            offerList.add(
                HistoryMarketOffer(
                    timestamp = result.getNumber("expires_at"),
                    price = result.getNumber("price"),
                    itemId = result.getNumber("itemtype").toInt(),
                    amount = result.getNumber("amount").toInt(),
                    tier = getTierFromDatabaseTable(result.getString("tier")),
                    state = offerState,
                ),
            )
        } while (result.next())
        return offerList
    }

    /**
     * `IOMarket::processExpiredOffers` (iomarket.cpp:155-232).
     *
     * The second C++ callback parameter is unused there and kept here only for
     * signature parity; the database task itself is run synchronously by
     * [checkExpiredOffers].
     */
    fun processExpiredOffers(result: DBResult?, storeSucceeded: Boolean = true) {
        if (result == null) {
            return
        }

        do {
            if (!moveOfferToHistory(result.getNumber("id"), MarketOfferState.Expired)) {
                continue
            }

            val playerId = result.getNumber("player_id")
            val amount = result.getNumber("amount").toInt()
            val tier = getTierFromDatabaseTable(result.getString("tier"))
            if (result.getNumber("sale") == MarketOfferType.Sell.ordinal.toLong()) {
                val itemType = escrow.getItemType(result.getNumber("itemtype").toInt()) ?: continue
                val player = escrow.resolvePlayer(playerId, true) ?: continue

                if (itemType.stackable) {
                    var tmpAmount = amount
                    while (tmpAmount > 0) {
                        val stackCount = minOf(100, tmpAmount)
                        if (!escrow.addItemToInbox(player, itemType.id, stackCount, tier)) {
                            logger.error {
                                "[processExpiredOffers] Ocurred an error to add item with id ${itemType.id} to player $playerId"
                            }
                            break
                        }
                        tmpAmount -= stackCount
                    }
                } else {
                    val subType = if (itemType.charges != 0) itemType.charges else -1
                    var delivered = 0
                    while (delivered < amount) {
                        if (!escrow.addItemToInbox(player, itemType.id, subType, tier)) {
                            break
                        }
                        delivered++
                    }
                }

                if (player.isOffline()) {
                    player.save()
                }
            } else {
                val totalPrice = result.getNumber("price") * amount
                val player = escrow.resolvePlayer(playerId, false)
                if (player != null) {
                    escrow.addBankBalance(player, totalPrice)
                } else {
                    increaseBankBalance(playerId, totalPrice)
                }
            }
        } while (result.next())
    }

    /**
     * `IOMarket::checkExpiredOffers` (iomarket.cpp:234-247). The
     * `g_databaseTasks().store` callback runs synchronously here and the
     * dispatcher reschedule becomes the injected [scheduleCheck] seam.
     */
    fun checkExpiredOffers() {
        val lastExpireDate = now() - config.marketOfferDuration
        val query = "SELECT id, amount, price, itemtype, player_id, sale, tier FROM market_offers " +
            "WHERE created <= $lastExpireDate"
        processExpiredOffers(DatabaseConnectionFactory.storeQuery(dataSource, query))

        val checkExpiredMarketOffersEachMinutes = config.checkExpiredMarketOffersEachMinutes
        if (checkExpiredMarketOffersEachMinutes <= 0) {
            return
        }

        scheduleCheck(checkExpiredMarketOffersEachMinutes * 60 * 1000L)
    }

    /** `IOMarket::getPlayerOfferCount` (iomarket.cpp:249-258). */
    fun getPlayerOfferCount(playerId: Long): Int {
        val query = "SELECT COUNT(*) AS count FROM market_offers WHERE player_id = $playerId"
        val result = DatabaseConnectionFactory.storeQuery(dataSource, query) ?: return 0
        return result.getNumber("count").toInt()
    }

    /**
     * `IOMarket::getOfferByCounter` (iomarket.cpp:260-289). MySQL's
     * `((id ^ 0xABCDEF) & 65535) = counter` filter is applied in Kotlin; the
     * `LIMIT 1` is reproduced by returning the first matching row.
     */
    fun getOfferByCounter(timestamp: Long, counter: Int): MarketOfferEx {
        val created = timestamp - config.marketOfferDuration
        val query = "SELECT id, sale, itemtype, amount, created, price, player_id, anonymous, tier, " +
            "(SELECT name FROM players WHERE id = player_id) AS player_name " +
            "FROM market_offers WHERE created = $created"
        val result = DatabaseConnectionFactory.storeQuery(dataSource, query) ?: return MarketOfferEx(id = 0)

        do {
            val id = result.getNumber("id")
            if (counterOf(id) != (counter and 0xFFFF)) {
                continue
            }

            return MarketOfferEx(
                id = id,
                playerId = result.getNumber("player_id"),
                timestamp = result.getNumber("created"),
                price = result.getNumber("price"),
                amount = result.getNumber("amount").toInt(),
                counter = counterOf(id),
                itemId = result.getNumber("itemtype").toInt(),
                type = actionOf(result.getNumber("sale")),
                tier = getTierFromDatabaseTable(result.getString("tier")),
                playerName = playerNameOf(result),
            )
        } while (result.next())

        return MarketOfferEx(id = 0)
    }

    /** `IOMarket::createOffer` (iomarket.cpp:291-295). */
    fun createOffer(
        playerId: Long,
        action: MarketOfferType,
        itemId: Long,
        amount: Int,
        price: Long,
        tier: Int,
        anonymous: Boolean,
    ) {
        val query = "INSERT INTO market_offers (player_id, sale, itemtype, amount, created, anonymous, price, tier) " +
            "VALUES ($playerId, ${action.ordinal}, $itemId, $amount, ${now()}, ${if (anonymous) 1 else 0}, $price, $tier)"
        DatabaseConnectionFactory.executeQuery(dataSource, query)
    }

    /** `IOMarket::acceptOffer` (iomarket.cpp:297-301). */
    fun acceptOffer(offerId: Long, amount: Int) {
        val query = "UPDATE market_offers SET amount = amount - $amount WHERE id = $offerId"
        DatabaseConnectionFactory.executeQuery(dataSource, query)
    }

    /** `IOMarket::deleteOffer` (iomarket.cpp:303-307). */
    fun deleteOffer(offerId: Long) {
        DatabaseConnectionFactory.executeQuery(dataSource, "DELETE FROM market_offers WHERE id = $offerId")
    }

    /** `IOMarket::appendHistory` (iomarket.cpp:309-315). */
    fun appendHistory(
        playerId: Long,
        type: MarketOfferType,
        itemId: Int,
        amount: Int,
        price: Long,
        timestamp: Long,
        tier: Int,
        state: MarketOfferState,
    ) {
        val query = "INSERT INTO market_history (player_id, sale, itemtype, amount, price, expires_at, inserted, state, tier) " +
            "VALUES ($playerId, ${type.ordinal}, $itemId, $amount, $price, $timestamp, ${now()}, ${state.value}, $tier)"
        DatabaseConnectionFactory.executeQuery(dataSource, query)
    }

    /** `IOMarket::moveOfferToHistory` (iomarket.cpp:317-344). */
    fun moveOfferToHistory(offerId: Long, state: MarketOfferState): Boolean {
        val query = "SELECT player_id, sale, itemtype, amount, price, created, tier FROM market_offers WHERE id = $offerId"
        val result = DatabaseConnectionFactory.storeQuery(dataSource, query) ?: return false

        if (!DatabaseConnectionFactory.executeQuery(dataSource, "DELETE FROM market_offers WHERE id = $offerId")) {
            return false
        }

        appendHistory(
            playerId = result.getNumber("player_id"),
            type = actionOf(result.getNumber("sale")),
            itemId = result.getNumber("itemtype").toInt(),
            amount = result.getNumber("amount").toInt(),
            price = result.getNumber("price"),
            timestamp = now(),
            tier = getTierFromDatabaseTable(result.getString("tier")),
            state = state,
        )
        return true
    }

    /**
     * `IOMarket::updateStatistics` (iomarket.cpp:346-381). A statement without
     * result rows returns early like the C++ original, leaving the previous
     * snapshot in place.
     */
    fun updateStatistics() {
        val query = "SELECT sale, itemtype, COUNT(price) AS num, MIN(price) AS min, MAX(price) AS max, " +
            "SUM(price) AS sum, tier FROM market_history " +
            "WHERE state = '${MarketOfferState.Accepted.value}' GROUP BY itemtype, sale, tier"
        val result = DatabaseConnectionFactory.storeQuery(dataSource, query) ?: return

        val newPurchase = mutableMapOf<Int, MutableMap<Int, MarketStatistics>>()
        val newSale = mutableMapOf<Int, MutableMap<Int, MarketStatistics>>()

        do {
            val tier = getTierFromDatabaseTable(result.getString("tier"))
            val itemId = result.getNumber("itemtype").toInt()
            val statistics = if (result.getNumber("sale") == MarketOfferType.Buy.ordinal.toLong()) {
                newPurchase
            } else {
                newSale
            }
            statistics.getOrPut(itemId) { mutableMapOf() }[tier] = MarketStatistics(
                numTransactions = result.getNumber("num"),
                highestPrice = result.getNumber("max"),
                totalPrice = result.getNumber("sum"),
                lowestPrice = result.getNumber("min"),
            )
        } while (result.next())

        purchaseStatistics = newPurchase
        saleStatistics = newSale
    }

    /** `IOMarket::getPurchaseStatistics` (iomarket.cpp:383-386). */
    fun getPurchaseStatistics(): MarketStatisticsMap = purchaseStatistics

    /** `IOMarket::getSaleStatistics` (iomarket.cpp:388-391). */
    fun getSaleStatistics(): MarketStatisticsMap = saleStatistics

    /** `IOMarket::getStatistics` (iomarket.cpp:393-410). */
    fun getStatistics(itemId: Int, tier: Int): MarketStatisticsSnapshot =
        MarketStatisticsSnapshot(
            purchase = purchaseStatistics[itemId]?.get(tier),
            sale = saleStatistics[itemId]?.get(tier),
        )

    /**
     * `IOLoginData::increaseBankBalance` (iologindata.cpp:357-361), inlined
     * here because it is the only persistence call of the expired-offer escrow.
     */
    fun increaseBankBalance(playerId: Long, bankBalance: Long) {
        try {
            dataSource.connection.use { connection ->
                connection.prepareStatement("UPDATE players SET balance = balance + ? WHERE id = ?").use { statement ->
                    statement.setLong(1, bankBalance)
                    statement.setLong(2, playerId)
                    statement.executeUpdate()
                }
            }
        } catch (e: SQLException) {
            logger.error(e) { "[increaseBankBalance] Failed to increase balance of player $playerId" }
        }
    }

    private fun playerNameOf(result: DBResult): String =
        if (result.getNumber("anonymous") == 0L) {
            result.getString("player_name")
        } else {
            "Anonymous"
        }

    private fun counterOf(id: Long): Int = ((id xor 0xABCDEF) and 0xFFFF).toInt()

    private fun actionOf(value: Long): MarketOfferType =
        MarketOfferType.entries.firstOrNull { it.ordinal.toLong() == value } ?: MarketOfferType.Buy

    private fun stateOf(value: Long): MarketOfferState =
        MarketOfferState.entries.firstOrNull { it.value.toLong() == value } ?: MarketOfferState.Active

    /**
     * `std::atoi` (src/utils/tools.cpp): parses an optional sign and the leading
     * decimal digits, returning zero when none are present.
     */
    private fun atoi(value: String): Int {
        var index = 0
        var sign = 1
        while (index < value.length && value[index].isWhitespace()) {
            index++
        }
        if (index < value.length && (value[index] == '+' || value[index] == '-')) {
            sign = if (value[index] == '-') -1 else 1
            index++
        }

        var result = 0
        while (index < value.length && value[index].isDigit()) {
            result = result * 10 + (value[index] - '0')
            index++
        }
        return sign * result
    }
}
