package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.Game
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.items.Item
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Router application tests for the imbuement window opcodes:
 * `0xD7` (`Game::playerCloseImbuementWindow`, game.cpp:7178) clears
 * `Player::imbuingItem`. `0xB2`/`0xD5`/`0xD6` stay decode-only until the
 * `ImbuementEngine` item seam is backed by the real `Item` attribute store.
 */
class OpcodeApplicationImbuementRouterTest {

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
    fun `close imbuement window opcode 0xD7 clears the imbuing item`() {
        start()
        try {
            val player = hero()
            player.setImbuingItem(Item(id = 2160))

            val frame = Unpooled.buffer()
            frame.writeByte(ImbuementPacketHandler.OPCODE_CLOSE_IMBUEMENT_WINDOW)

            assertThat(route(frame, player)).isEqualTo(1)
            assertThat(player.hasImbuingItem()).isFalse()
        } finally {
            stop()
        }
    }

    @Test
    fun `close imbuement window opcode 0xD7 is inert without an imbuing item`() {
        start()
        try {
            val player = hero()

            val frame = Unpooled.buffer()
            frame.writeByte(ImbuementPacketHandler.OPCODE_CLOSE_IMBUEMENT_WINDOW)

            assertThat(route(frame, player)).isEqualTo(1)
            assertThat(player.hasImbuingItem()).isFalse()
        } finally {
            stop()
        }
    }
}
