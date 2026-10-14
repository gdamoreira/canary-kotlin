package org.opentibia.canary.network.protocol

import com.zaxxer.hikari.HikariDataSource
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.database.DatabaseConnectionFactory
import org.opentibia.canary.economy.market.MarketOfferType
import org.opentibia.canary.game.Game
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.io.IOMarket
import org.opentibia.canary.io.MarketOfferState
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.items.Items
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.protocol.market.MarketPacketParser
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Router application tests for the market opcodes over the ported [IOMarket]
 * transaction engine:
 *
 *  - `0xF4` `Game::playerLeaveMarket` (game.cpp:10273)
 *  - `0xF5` `Game::playerBrowseMarket`/`playerBrowseMarketOwnOffers`/
 *    `playerBrowseMarketOwnHistory` (game.cpp:10282-10337)
 *  - `0xF6` `Game::playerCreateMarketOffer` (game.cpp:10717)
 *  - `0xF7` `Game::playerCancelMarketOffer` (game.cpp:10819)
 *  - `0xF8` `Game::playerAcceptMarketOffer` (game.cpp:10884)
 *
 * The `IOMarket` persistence runs over an in-memory H2 database (Flyway
 * V1-V12), matching `IOMarketTest`/`GameMarketTest`.
 */
class OpcodeApplicationMarketRouterTest {

    private lateinit var dataSource: HikariDataSource
    private lateinit var ioMarket: IOMarket
    private lateinit var dispatcher: GameDispatcher
    private lateinit var map: GameMap
    private lateinit var game: Game

    private val fixedNow = 1_700_000_000L
    private val balances = mutableMapOf<Long, Long>()
    private val removedMoney = mutableListOf<Pair<Long, Long>>()

    @BeforeEach
    fun setUp() {
        dataSource = DatabaseConnectionFactory.createInMemoryDataSource("canary_market_router_${System.nanoTime()}")
        assertThat(DatabaseConnectionFactory.migrate(dataSource)).isGreaterThanOrEqualTo(10)
        ioMarket = IOMarket(dataSource = dataSource, now = { fixedNow })
        dispatcher = GameDispatcher()
        dispatcher.start()
        map = GameMap()
        game = Game(map, dispatcher, itemTypes = marketItems(), ioMarket = ioMarket)
        game.getBankBalance = { target -> balances[target.id] ?: 0L }
        game.setBankBalance = { target, value -> balances[target.id] = value }
        game.removeMoney = { target, amount, _, _ ->
            removedMoney.add(target.id to amount)
            true
        }
        game.isUIExhausted = { it.isUIExhausted() }
        game.updateUIExhausted = { it.updateUIExhausted() }
    }

    @AfterEach
    fun tearDown() {
        dispatcher.shutdown()
        dataSource.close()
    }

    private fun context(): GamePacketContext = GamePacketContext(
        map = map,
        dispatcher = dispatcher,
        luaEnvironment = LuaEnvironment().apply { initState() },
        itemTypes = marketItems(),
        ioMarket = ioMarket,
        game = game,
    )

    private fun marketItems(): Items =
        Items().also {
            it.register(
                ItemType(
                    id = 2160,
                    name = "market item",
                    wareId = 2160,
                    isStackable = true,
                    weight = 1.0,
                ),
            )
        }

    private fun hero(id: Long = 1, name: String = "Hero", inMarket: Boolean = true): Player =
        Player(id, name, 150, 150, position = Position(1000, 1000, 7)).also {
            it.setInMarket(inMarket)
            game.addPlayer(it)
        }

    private fun route(frame: ByteBuf, player: Player): Int {
        val handled = GamePacketRouter.route(player, frame, context())
        awaitWorldCommit()
        return handled
    }

    private fun awaitWorldCommit() {
        val ran = CountDownLatch(1)
        dispatcher.dispatch { ran.countDown() }
        assertThat(ran.await(2, TimeUnit.SECONDS)).isTrue()
    }

    private fun insertAccountAndPlayerRow(accountId: Long, playerId: Long, name: String) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "INSERT INTO accounts (id, name, \"password\", email, premdays, \"type\") VALUES (?, ?, '1', ?, 0, 1)",
            ).use { statement ->
                statement.setLong(1, accountId)
                statement.setString(2, accountId.toString())
                statement.setString(3, "account$accountId@opentibiabr.com")
                statement.executeUpdate()
            }
            connection.prepareStatement("INSERT INTO players (id, name, account_id, balance) VALUES (?, ?, ?, 0)").use { statement ->
                statement.setLong(1, playerId)
                statement.setString(2, name)
                statement.setLong(3, accountId)
                statement.executeUpdate()
            }
        }
    }

    private fun writeCreateOffer(
        frame: ByteBuf,
        action: MarketOfferType,
        itemId: Int,
        amount: Int,
        price: Long,
        anonymous: Boolean = false,
    ) {
        frame.writeByte(MarketPacketParser.OPCODE_MARKET_CREATE_OFFER)
        frame.writeByte(action.ordinal)
        frame.writeShortLE(itemId)
        frame.writeShortLE(amount)
        frame.writeLongLE(price)
        frame.writeByte(if (anonymous) 1 else 0)
    }

    private fun writeCancelOffer(frame: ByteBuf, timestamp: Long, counter: Int) {
        frame.writeByte(MarketPacketParser.OPCODE_MARKET_CANCEL_OFFER)
        frame.writeIntLE(timestamp.toInt())
        frame.writeShortLE(counter)
    }

    private fun writeAcceptOffer(frame: ByteBuf, timestamp: Long, counter: Int, amount: Int) {
        frame.writeByte(MarketPacketParser.OPCODE_MARKET_ACCEPT_OFFER)
        frame.writeIntLE(timestamp.toInt())
        frame.writeShortLE(counter)
        frame.writeShortLE(amount)
    }

    @Test
    fun `market leave opcode 0xF4 clears the in-market state`() {
        val player = hero()
        player.setInMarket(true)

        val frame = Unpooled.buffer()
        frame.writeByte(MarketPacketParser.OPCODE_MARKET_LEAVE)

        assertThat(route(frame, player)).isEqualTo(1)
        assertThat(player.isInMarket()).isFalse()
    }

    @Test
    fun `market leave opcode 0xF4 keeps a player outside the market outside`() {
        val player = hero(inMarket = false)

        val frame = Unpooled.buffer()
        frame.writeByte(MarketPacketParser.OPCODE_MARKET_LEAVE)

        assertThat(route(frame, player)).isEqualTo(1)
        assertThat(player.isInMarket()).isFalse()
    }

    @Test
    fun `market create offer opcode 0xF6 creates a sell offer and escrows the depot items`() {
        val player = hero()
        balances[player.id] = 1_000
        player.setLastDepotId(1)
        val locker = player.getDepotLocker(1)
        val depotChest = locker.getItemList()
            .first { it.getContainer() != null && it.getContainer()!!.getItemList().any { box -> box.getContainer() != null } }
            .getContainer()!!
            .getItemList()
            .first { it.getContainer() != null }
            .getContainer()!!
        depotChest.addItem(Item(id = 2160, count = 5, type = marketItems().get(2160)))

        val frame = Unpooled.buffer()
        writeCreateOffer(frame, MarketOfferType.Sell, itemId = 2160, amount = 5, price = 1000)

        assertThat(route(frame, player)).isEqualTo(1)
        assertThat(depotChest.items).isEmpty()
        assertThat(removedMoney).containsExactly(player.id to 100L)
        val offers = ioMarket.getOwnOffers(MarketOfferType.Sell, player.id)
        assertThat(offers).hasSize(1)
        assertThat(offers[0].amount).isEqualTo(5)
        assertThat(offers[0].price).isEqualTo(1000)
        assertThat(player.isUIExhausted(1000)).isTrue()
    }

    @Test
    fun `market create offer opcode 0xF6 creates a buy offer and charges the price plus fee`() {
        val player = hero()
        balances[player.id] = 100_000

        val frame = Unpooled.buffer()
        writeCreateOffer(frame, MarketOfferType.Buy, itemId = 2160, amount = 5, price = 1000)

        assertThat(route(frame, player)).isEqualTo(1)
        assertThat(removedMoney).containsExactly(player.id to 5_100L)
        assertThat(ioMarket.getOwnOffers(MarketOfferType.Buy, player.id)).hasSize(1)
    }

    @Test
    fun `market create offer opcode 0xF6 ignores a zero price`() {
        val player = hero()
        balances[player.id] = 100_000

        val frame = Unpooled.buffer()
        writeCreateOffer(frame, MarketOfferType.Buy, itemId = 2160, amount = 5, price = 0)

        assertThat(route(frame, player)).isEqualTo(1)
        assertThat(removedMoney).isEmpty()
        assertThat(ioMarket.getOwnOffers(MarketOfferType.Buy, player.id)).isEmpty()
    }

    @Test
    fun `market create offer opcode 0xF6 ignores a player outside the market`() {
        val player = hero(inMarket = false)
        balances[player.id] = 100_000

        val frame = Unpooled.buffer()
        writeCreateOffer(frame, MarketOfferType.Buy, itemId = 2160, amount = 5, price = 1000)

        assertThat(route(frame, player)).isEqualTo(1)
        assertThat(ioMarket.getOwnOffers(MarketOfferType.Buy, player.id)).isEmpty()
    }

    @Test
    fun `market cancel offer opcode 0xF7 refunds a buy offer and moves it to history`() {
        val player = hero()
        ioMarket.createOffer(player.id, MarketOfferType.Buy, 2160, 5, 1000, 0, false)
        val offer = ioMarket.getOwnOffers(MarketOfferType.Buy, player.id).single()
        balances[player.id] = 0

        val frame = Unpooled.buffer()
        writeCancelOffer(frame, timestamp = offer.timestamp, counter = offer.counter)

        assertThat(route(frame, player)).isEqualTo(1)
        assertThat(balances[player.id]).isEqualTo(5_000L)
        assertThat(ioMarket.getOwnOffers(MarketOfferType.Buy, player.id)).isEmpty()
        assertThat(ioMarket.getOwnHistory(MarketOfferType.Buy, player.id).map { it.state })
            .containsExactly(MarketOfferState.Cancelled)
    }

    @Test
    fun `market cancel offer opcode 0xF7 returns a sell offer to the inbox`() {
        val player = hero()
        ioMarket.createOffer(player.id, MarketOfferType.Sell, 2160, 5, 1000, 0, false)
        val offer = ioMarket.getOwnOffers(MarketOfferType.Sell, player.id).single()

        val frame = Unpooled.buffer()
        writeCancelOffer(frame, timestamp = offer.timestamp, counter = offer.counter)

        assertThat(route(frame, player)).isEqualTo(1)
        assertThat(player.inbox.items).hasSize(1)
        assertThat(player.inbox.items[0].id).isEqualTo(2160)
        assertThat(player.inbox.items[0].getItemCount()).isEqualTo(5)
        assertThat(ioMarket.getOwnHistory(MarketOfferType.Sell, player.id).map { it.state })
            .containsExactly(MarketOfferState.Cancelled)
    }

    @Test
    fun `market accept offer opcode 0xF8 pays the seller and delivers the items to the buyer`() {
        insertAccountAndPlayerRow(2, 3, "Seller")
        val buyer = hero(id = 1, name = "Buyer")
        hero(id = 3, name = "Seller")
        balances[buyer.id] = 10_000
        balances[3] = 0
        ioMarket.createOffer(3, MarketOfferType.Sell, 2160, 5, 1000, 0, false)
        val offer = ioMarket.getActiveOffers(MarketOfferType.Sell, 2160, 0).single()

        val frame = Unpooled.buffer()
        writeAcceptOffer(frame, timestamp = offer.timestamp, counter = offer.counter, amount = 5)

        assertThat(route(frame, buyer)).isEqualTo(1)
        assertThat(removedMoney).containsExactly(buyer.id to 5_000L)
        assertThat(balances[3]).isEqualTo(5_000L)
        assertThat(buyer.inbox.items).hasSize(1)
        assertThat(buyer.inbox.items[0].id).isEqualTo(2160)
        assertThat(buyer.inbox.items[0].getItemCount()).isEqualTo(5)
        assertThat(ioMarket.getActiveOffers(MarketOfferType.Sell, 2160, 0)).isEmpty()
        assertThat(ioMarket.getOwnHistory(MarketOfferType.Sell, 3).map { it.state })
            .containsExactly(MarketOfferState.Accepted)
        assertThat(ioMarket.getOwnHistory(MarketOfferType.Buy, buyer.id).map { it.state })
            .containsExactly(MarketOfferState.Accepted)
    }

    @Test
    fun `market accept offer opcode 0xF8 rejects the player's own offer`() {
        val player = hero()
        balances[player.id] = 10_000
        ioMarket.createOffer(player.id, MarketOfferType.Sell, 2160, 5, 1000, 0, false)
        val offer = ioMarket.getActiveOffers(MarketOfferType.Sell, 2160, 0).single()

        val frame = Unpooled.buffer()
        writeAcceptOffer(frame, timestamp = offer.timestamp, counter = offer.counter, amount = 5)

        assertThat(route(frame, player)).isEqualTo(1)
        assertThat(removedMoney).isEmpty()
        assertThat(ioMarket.getActiveOffers(MarketOfferType.Sell, 2160, 0)).hasSize(1)
    }
}
