package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.Game
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.protocol.market.MarketPacketParser
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Router application tests for the market leave opcode (`0xF4`):
 * `Game::playerLeaveMarket` (game.cpp:10273) clears `Player::inMarket` through
 * the engine. The browse/create/cancel/accept operations stay decode-only until
 * the `IOMarket` transaction engine and its senders are composed.
 */
class OpcodeApplicationMarketRouterTest {

    private lateinit var dispatcher: GameDispatcher
    private lateinit var map: GameMap
    private lateinit var game: Game

    private fun start() {
        dispatcher = GameDispatcher()
        dispatcher.start()
        map = GameMap()
        game = Game(map, dispatcher)
    }

    private fun stop() {
        dispatcher.shutdown()
    }

    private fun context(): GamePacketContext = GamePacketContext(
        map = map,
        dispatcher = dispatcher,
        luaEnvironment = LuaEnvironment().apply { initState() },
        game = game,
    )

    private fun hero(): Player =
        Player(1, "Hero", 150, 150, position = Position(1000, 1000, 7)).also { game.addPlayer(it) }

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

    @Test
    fun `market leave opcode 0xF4 clears the in-market state`() {
        start()
        try {
            val player = hero()
            player.setInMarket(true)

            val frame = Unpooled.buffer()
            frame.writeByte(MarketPacketParser.OPCODE_MARKET_LEAVE)

            assertThat(route(frame, player)).isEqualTo(1)
            assertThat(player.isInMarket()).isFalse()
        } finally {
            stop()
        }
    }

    @Test
    fun `market leave opcode 0xF4 keeps a player outside the market outside`() {
        start()
        try {
            val player = hero()

            val frame = Unpooled.buffer()
            frame.writeByte(MarketPacketParser.OPCODE_MARKET_LEAVE)

            assertThat(route(frame, player)).isEqualTo(1)
            assertThat(player.isInMarket()).isFalse()
        } finally {
            stop()
        }
    }
}
