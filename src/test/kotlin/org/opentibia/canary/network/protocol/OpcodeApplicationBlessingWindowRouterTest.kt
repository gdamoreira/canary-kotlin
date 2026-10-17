package org.opentibia.canary.network.protocol

import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.Game
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position

/**
 * Transcript of the blessing-window request opcode `0xCF`
 * (`case 0xCF: sendBlessingWindow();`, protocolgame.cpp:2009-2010): the
 * session sink re-serializes the player's blessing state. The
 * `oldProtocol`/`version < 1100` guards live in the sink
 * (`ProtocolGame::sendBlessingWindow`, protocolgame.cpp:5899-5901).
 */
class OpcodeApplicationBlessingWindowRouterTest {

    private val map = GameMap()
    private val dispatcher = GameDispatcher().apply { start() }
    private val center = Position(100, 100, 7)


    private fun awaitWorldCommit() {
        val ran = java.util.concurrent.CountDownLatch(1)
        dispatcher.dispatch { ran.countDown() }
        assertThat(ran.await(2, java.util.concurrent.TimeUnit.SECONDS)).isTrue()
    }

    private fun route(player: Player, game: Game): Int {
        val handled = GamePacketRouter.route(player, payload(), context(game))
        awaitWorldCommit()
        return handled
    }

    private fun context(game: Game): GamePacketContext = GamePacketContext(
        map = map,
        dispatcher = dispatcher,
        luaEnvironment = LuaEnvironment().apply { initState() },
        game = game,
    )

    private fun payload(): io.netty.buffer.ByteBuf = Unpooled.buffer().apply {
        writeByte(BlessingWindowPacketHandler.OPCODE_BLESSING_WINDOW)
    }

    @Test
    fun `blessing window opcode sends the 0x9B frame through the session sink`() {
        val game = Game(map, dispatcher)
        val player = Player(id = 1, name = "Hero", maxHealth = 150, health = 150, position = center)
        game.addPlayer(player)
        val sink = InMemoryProtocolSink(player, map)
        player.protocolSink = sink

        val handled = route(player, game)

        assertThat(handled).isEqualTo(1)
        assertThat(sink.frames).hasSize(1)
        assertThat(sink.frames.single()[0]).isEqualTo(0x9B.toByte())

        dispatcher.shutdown()
    }

    @Test
    fun `blessing window opcode stays silent on the old protocol`() {
        val game = Game(map, dispatcher)
        val player = Player(id = 2, name = "Hero", maxHealth = 150, health = 150, position = center)
        game.addPlayer(player)
        val sink = InMemoryProtocolSink(player, map)
        sink.oldProtocol = true
        player.protocolSink = sink

        assertThat(route(player, game)).isEqualTo(1)
        assertThat(sink.frames).isEmpty()

        dispatcher.shutdown()
    }
}
