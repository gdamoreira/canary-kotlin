package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.Game
import org.opentibia.canary.game.modal.ModalWindow
import org.opentibia.canary.game.modal.ModalWindowClient
import org.opentibia.canary.game.modal.ModalWindowManager
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.message.readTibiaString
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Wave 67 D4 transcript tests for the modal window sink and answer route:
 *
 *  - `Player::sendModalWindow` (player.cpp:9021-9028) ->
 *    `ProtocolGame::sendModalWindow` (protocolgame.cpp:9630-9659, opcode 0xFA)
 *    through the session [PlayerSendSink];
 *  - `ProtocolGame::parseModalWindowAnswer` (protocolgame.cpp:4484-4489,
 *    client opcode 0xF9) -> `Game::playerAnswerModalWindow`
 *    (game.cpp:11144-11176) instead of the decode-only legacy manager.
 */
class ModalWindowSinkRouterTest {

    @Test
    fun `the session sink emits the 0xFA modal window frame`() {
        val channel = EmbeddedChannel()
        try {
            val player = Player(id = 1L, name = "Hero", maxHealth = 100, health = 100, position = Position(100, 100, 7))
            val sink = NettyProtocolSink(channel, player, GameMap())
            val window = ModalWindow(id = 7L, title = "Quest", message = "Accept?").apply {
                buttons.add("Okay" to 1)
                buttons.add("Cancel" to 0)
                choices.add("Reward" to 3)
                defaultEscapeButton = 1
                defaultEnterButton = 0
                priority = true
            }

            sink.sendModalWindow(window)

            val frame = channel.readOutbound<ByteBuf>()
            assertThat(frame.readUnsignedByte().toInt()).isEqualTo(SocialWindowWriter.PACKET_MODAL_WINDOW)
            assertThat(frame.readIntLE()).isEqualTo(7)
            assertThat(frame.readTibiaString()).isEqualTo("Quest")
            assertThat(frame.readTibiaString()).isEqualTo("Accept?")
            assertThat(frame.readUnsignedByte().toInt()).isEqualTo(2)
            assertThat(frame.readTibiaString()).isEqualTo("Okay")
            assertThat(frame.readUnsignedByte().toInt()).isEqualTo(1)
            assertThat(frame.readTibiaString()).isEqualTo("Cancel")
            assertThat(frame.readUnsignedByte().toInt()).isEqualTo(0)
            assertThat(frame.readUnsignedByte().toInt()).isEqualTo(1)
            assertThat(frame.readTibiaString()).isEqualTo("Reward")
            assertThat(frame.readUnsignedByte().toInt()).isEqualTo(3)
            assertThat(frame.readUnsignedByte().toInt()).isEqualTo(1)
            assertThat(frame.readUnsignedByte().toInt()).isEqualTo(0)
            assertThat(frame.readUnsignedByte().toInt()).isEqualTo(1)
            frame.release()
        } finally {
            channel.finishAndReleaseAll()
        }
    }

    @Test
    fun `routes 0xF9 into Game playerAnswerModalWindow`() {
        val dispatcher = GameDispatcher()
        dispatcher.start()
        try {
            val map = GameMap()
            val manager = ModalWindowManager(client = ModalWindowClient { _ -> })
            val game = Game(map, dispatcher, modalWindowManager = manager)
            val player = Player(id = 1L, name = "Hero", maxHealth = 100, health = 100, position = Position(100, 100, 7))
            game.addPlayer(player)

            // The production manager records the window when it is handed to a client.
            manager.sendModalWindow(player, ModalWindow(id = 42L, title = "Quest", message = "Accept?"))
            assertThat(manager.hasModalWindowOpen(player, 42L)).isTrue()

            // The legacy decode-only manager must NOT be consulted once a world is wired:
            // reach its window id 42 (ids start at 1) and watch its callback stay silent.
            val legacy = org.opentibia.canary.network.protocol.ModalWindowManager()
            var legacyCallbacks = 0
            repeat(42) { legacy.openWindow(player, "Legacy", "Legacy?") { _, _ -> legacyCallbacks++ } }

            val frame = Unpooled.buffer()
            frame.writeByte(ModalAnswerPacketHandler.OPCODE_MODAL_WINDOW_ANSWER)
            frame.writeIntLE(42)
            frame.writeByte(1)
            frame.writeByte(0)

            val handled = GamePacketRouter.route(
                player,
                frame,
                GamePacketContext(
                    map = map,
                    dispatcher = dispatcher,
                    luaEnvironment = LuaEnvironment().apply { initState() },
                    game = game,
                    modalManager = legacy,
                ),
            )
            awaitWorldCommit(dispatcher)

            assertThat(handled).isEqualTo(1)
            assertThat(manager.hasModalWindowOpen(player, 42L)).isFalse()
            assertThat(legacyCallbacks).isZero()
        } finally {
            dispatcher.shutdown()
        }
    }

    private fun awaitWorldCommit(dispatcher: GameDispatcher) {
        val ran = CountDownLatch(1)
        dispatcher.dispatch { ran.countDown() }
        assertThat(ran.await(2, TimeUnit.SECONDS)).isTrue()
    }
}
