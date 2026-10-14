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
import org.opentibia.canary.network.protocol.cyclopedia.CyclopediaWindowWriter
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Router delegation tests for the `0xE5` cyclopedia character-info branch: the
 * decoded command must flow through `Game::playerCyclopediaCharacterInfo`
 * (src/game/game.cpp:9823-9888) instead of the reduced inline dispatch.
 *
 * `ProtocolGame::parseCyclopediaCharacterInfo` (protocolgame.cpp:3040-3042)
 * resolves the zero character id to the requesting player before the `Game`
 * call; the router keeps that resolution.
 */
class CyclopediaCharacterInfoRouterDelegationTest {

    @Test
    fun `delegates the base information request to the Game member`() {
        val dispatcher = GameDispatcher()
        dispatcher.start()
        try {
            val player = hero()
            val game = Game(GameMap(), dispatcher)
            val baseInformation = mutableListOf<Player>()
            game.sendCyclopediaCharacterBaseInformation = { baseInformation.add(it) }
            val frame = Unpooled.buffer()
            writeCharacterInfo(
                frame,
                characterId = 0,
                infoType = CyclopediaWindowWriter.CYCLOPEDIA_CHARACTERINFO_BASEINFORMATION,
            )

            val handled = GamePacketRouter.route(player, frame, context(dispatcher, game))
            awaitWorldCommit(dispatcher)

            assertThat(handled).isEqualTo(1)
            assertThat(baseInformation).containsExactly(player)
        } finally {
            dispatcher.shutdown()
        }
    }

    @Test
    fun `refuses another character through the Game member`() {
        val dispatcher = GameDispatcher()
        dispatcher.start()
        try {
            val player = hero()
            val game = Game(GameMap(), dispatcher)
            val refusals = mutableListOf<Pair<Int, Int>>()
            game.sendCyclopediaCharacterNoData = { _, infoType, error -> refusals.add(infoType to error) }
            val frame = Unpooled.buffer()
            writeCharacterInfo(
                frame,
                characterId = 99,
                infoType = CyclopediaWindowWriter.CYCLOPEDIA_CHARACTERINFO_BASEINFORMATION,
            )

            val handled = GamePacketRouter.route(player, frame, context(dispatcher, game))
            awaitWorldCommit(dispatcher)

            assertThat(handled).isEqualTo(1)
            assertThat(refusals).containsExactly(
                CyclopediaWindowWriter.CYCLOPEDIA_CHARACTERINFO_BASEINFORMATION to 2,
            )
        } finally {
            dispatcher.shutdown()
        }
    }

    private fun writeCharacterInfo(frame: ByteBuf, characterId: Int, infoType: Int) {
        frame.writeByte(CyclopediaPacketHandler.OPCODE_CYCLOPEDIA_CHARACTER_INFO)
        frame.writeIntLE(characterId)
        frame.writeByte(infoType)
    }

    private fun context(dispatcher: GameDispatcher, game: Game): GamePacketContext = GamePacketContext(
        map = GameMap(),
        dispatcher = dispatcher,
        luaEnvironment = LuaEnvironment().apply { initState() },
        game = game,
    )

    private fun hero(): Player = Player(1, "Hero", 100, 100, position = Position(100, 100, 7))

    private fun awaitWorldCommit(dispatcher: GameDispatcher) {
        val ran = CountDownLatch(1)
        dispatcher.dispatch { ran.countDown() }
        assertThat(ran.await(2, TimeUnit.SECONDS)).isTrue()
    }
}
