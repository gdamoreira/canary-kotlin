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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Router application tests for the depot-search state opcodes: `0x93`
 * (`Game::playerRequestCloseDepotSearch`) resets `Player::depotSearchOnItem`
 * through the engine. `0x92`/`0x94`/`0x95` stay decode-only until the depot
 * locker scan and its senders are ported.
 */
class OpcodeApplicationDepotSearchRouterTest {

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
    fun `close depot search opcode 0x93 resets the open item state`() {
        start()
        try {
            val player = hero()
            player.setDepotSearchIsOpen(3031, 0)

            val frame = Unpooled.buffer()
            frame.writeByte(DepotAndStashPacketHandler.OPCODE_CLOSE_DEPOT_SEARCH)

            assertThat(route(frame, player)).isEqualTo(1)
            assertThat(player.isDepotSearchOpen()).isFalse()
        } finally {
            stop()
        }
    }

    @Test
    fun `close depot search opcode 0x93 is inert when no search is open`() {
        start()
        try {
            val player = hero()

            val frame = Unpooled.buffer()
            frame.writeByte(DepotAndStashPacketHandler.OPCODE_CLOSE_DEPOT_SEARCH)

            assertThat(route(frame, player)).isEqualTo(1)
            assertThat(player.isDepotSearchOpen()).isFalse()
        } finally {
            stop()
        }
    }
}
