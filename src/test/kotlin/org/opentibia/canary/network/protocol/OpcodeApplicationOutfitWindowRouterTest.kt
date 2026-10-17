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
 * Transcript of the outfit-window request opcode `0xD2`
 * (`Game::playerRequestOutfit`, game.cpp:7206-7216): the `ALLOW_CHANGEOUTFIT`
 * gate (game.cpp:7207) resolves before `Player::sendOutfitWindow` reaches the
 * session sink.
 */
class OpcodeApplicationOutfitWindowRouterTest {

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
        writeByte(OutfitRequestPacketHandler.OPCODE_PLAYER_REQUEST_OUTFIT)
    }

    @Test
    fun `outfit request sends the window when the change-outfit config allows it`() {
        val game = Game(map, dispatcher)
        val player = Player(id = 1, name = "Hero", maxHealth = 150, health = 150, position = center)
        game.addPlayer(player)
        val requested = mutableListOf<Player>()
        game.sendOutfitWindow = { requested.add(it) }

        val handled = route(player, game)

        assertThat(handled).isEqualTo(1)
        assertThat(requested).containsExactly(player)

        dispatcher.shutdown()
    }

    @Test
    fun `outfit request is dropped when the change-outfit config forbids it`() {
        val game = Game(map, dispatcher)
        game.allowChangeOutfit = false
        val player = Player(id = 2, name = "Hero", maxHealth = 150, health = 150, position = center)
        game.addPlayer(player)
        val requested = mutableListOf<Player>()
        game.sendOutfitWindow = { requested.add(it) }

        assertThat(route(player, game)).isEqualTo(1)
        assertThat(requested).isEmpty()

        dispatcher.shutdown()
    }
}
