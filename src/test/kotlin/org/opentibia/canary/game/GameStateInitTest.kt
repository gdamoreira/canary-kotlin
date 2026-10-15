package org.opentibia.canary.game

import com.zaxxer.hikari.HikariDataSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.opentibia.canary.database.DatabaseConnectionFactory
import org.opentibia.canary.economy.market.MarketOfferType
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.io.IOMarket
import org.opentibia.canary.io.MarketOfferState
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.utils.Tools

/**
 * Transcript tests for the `GAME_STATE_INIT` arm of `Game::setGameState`
 * (src/game/game.cpp:963-993): the wired pieces run in the C++ statement order
 * exactly once per transition into `GAME_STATE_INIT`, `loadItemsPrice` refreshes
 * the items price map first, and the boot-owned/unported pieces stay out of the
 * trigger.
 *
 * The `IOMarket` statistics run over the in-memory H2 database (Flyway V1-V12),
 * matching `GameMarketOffersTest`.
 */
class GameStateInitTest {

    private lateinit var dataSource: HikariDataSource
    private lateinit var ioMarket: IOMarket
    private lateinit var game: Game
    private val events = mutableListOf<String>()
    private var itemsPriceReadyAtSpawns = false

    private val fixedNow = 1_700_000_000L

    @BeforeEach
    fun setUp() {
        dataSource = DatabaseConnectionFactory.createInMemoryDataSource("canary_game_state_init_${System.nanoTime()}")
        val applied = DatabaseConnectionFactory.migrate(dataSource)
        assertThat(applied).isGreaterThanOrEqualTo(10)
        ioMarket = IOMarket(dataSource = dataSource, now = { fixedNow })
        game = Game(GameMap(), GameDispatcher(), ioMarket = ioMarket)

        game.initSpawns = {
            events += "spawns"
            itemsPriceReadyAtSpawns = game.itemsPriceMap.isNotEmpty()
        }
        game.initRaids = { events += "raids" }
        game.initMounts = { events += "mounts" }
        game.initAttachedEffects = { events += "attachedEffects" }
        game.initGlobalEvents = { events += "globalEvents" }
        game.initWheelGlobalData = { events += "wheelGlobalData" }
        game.loadMotdNumValue = { events += "motdNum"; 4L }
        game.loadMotdHashValue = { events += "motdHash"; Tools.transformToSHA1("") }
        game.loadPlayersRecordValue = { events += "playersRecord"; 7 }
    }

    @AfterEach
    fun tearDown() {
        dataSource.close()
    }

    private fun insertPurchasedHistoryRow(itemId: Int, price: Long) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "INSERT INTO market_history (player_id, sale, itemtype, amount, price, expires_at, inserted, state, tier) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
            ).use { statement ->
                statement.setLong(1, 100)
                statement.setInt(2, MarketOfferType.Buy.ordinal)
                statement.setInt(3, itemId)
                statement.setInt(4, 1)
                statement.setLong(5, price)
                statement.setLong(6, fixedNow)
                statement.setLong(7, fixedNow)
                statement.setInt(8, MarketOfferState.Accepted.value)
                statement.setInt(9, 0)
                statement.executeUpdate()
            }
        }
    }

    @Test
    fun `the init arm runs the wired pieces in the canary statement order`() {
        dataSource.connection.use { connection ->
            connection.prepareStatement("INSERT INTO players (id, name, account_id, balance) VALUES (?, ?, 1, 0)").use { statement ->
                statement.setLong(1, 100)
                statement.setString(2, "PlayerHundred")
                statement.executeUpdate()
            }
        }
        insertPurchasedHistoryRow(itemId = 2160, price = 250)

        game.setGameState(GameState_t.GAME_STATE_INIT)

        // `loadItemsPrice()` (game.cpp:964) runs before the spawn start
        // (game.cpp:970), and its refresh reaches the price map.
        assertThat(itemsPriceReadyAtSpawns).isTrue()
        assertThat(game.itemsPriceMap[2160]?.get(0)).isEqualTo(250)

        assertThat(events).containsExactly(
            "spawns",
            "raids",
            "mounts",
            "attachedEffects",
            "motdNum",
            "motdHash",
            "playersRecord",
            "globalEvents",
            "wheelGlobalData",
        )

        // `loadMotdNum` (game.cpp:985) keeps the stored counter when the hash
        // matches; `loadPlayersRecord` (game.cpp:986) adopts the stored value.
        assertThat(game.getMotdNum()).isEqualTo(4)
        assertThat(game.playersRecord).isEqualTo(7)
        assertThat(game.getGameState()).isEqualTo(GameState_t.GAME_STATE_INIT)
    }

    @Test
    fun `a repeated init transition runs the wired pieces exactly once`() {
        game.setGameState(GameState_t.GAME_STATE_INIT)
        val firstRun = events.toList()

        game.setGameState(GameState_t.GAME_STATE_INIT)

        assertThat(firstRun).hasSize(9)
        assertThat(events).isEqualTo(firstRun)
    }

    @Test
    fun `non-init transitions do not run the init pieces`() {
        game.setGameState(GameState_t.GAME_STATE_CLOSING)
        game.setGameState(GameState_t.GAME_STATE_NORMAL)

        assertThat(events).isEmpty()
    }

    @Test
    fun `the shutdown guard blocks a later init transition`() {
        game.setGameState(GameState_t.GAME_STATE_SHUTDOWN)

        game.setGameState(GameState_t.GAME_STATE_INIT)

        assertThat(events).isEmpty()
        assertThat(game.getGameState()).isEqualTo(GameState_t.GAME_STATE_SHUTDOWN)
    }

    @Test
    fun `the init arm no-ops without a market and without persistence seams`() {
        val plain = Game(GameMap(), GameDispatcher())

        plain.setGameState(GameState_t.GAME_STATE_INIT)

        assertThat(plain.itemsPriceMap).isEmpty()
        assertThat(plain.getMotdNum()).isZero()
        assertThat(plain.playersRecord).isZero()
    }
}
