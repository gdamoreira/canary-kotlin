package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Decode and router-wiring tests for the modal window answer client opcode
 * `0xF9` (`ProtocolGame::parseModalWindowAnswer`, protocolgame.cpp:4484-4489):
 * a `uint32_t` window id, the button byte and the choice byte handed to
 * `Game::playerAnswerModalWindow`.
 */
class ModalAnswerPacketHandlerTest {

    @Test
    fun `parses modal window answer 0xF9 window id button and choice`() {
        val payload = Unpooled.buffer()
        payload.writeIntLE(7)
        payload.writeByte(1)
        payload.writeByte(2)

        val command = ModalAnswerPacketHandler.parse(ModalAnswerPacketHandler.OPCODE_MODAL_WINDOW_ANSWER, payload)

        assertThat(command).isEqualTo(ModalWindowAnswerCommand(windowId = 7L, button = 1, choice = 2))
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `rejects a truncated modal window answer payload`() {
        val payload = Unpooled.buffer()
        payload.writeIntLE(7)
        payload.writeByte(1)

        assertThat(
            ModalAnswerPacketHandler.parse(ModalAnswerPacketHandler.OPCODE_MODAL_WINDOW_ANSWER, payload),
        ).isNull()
    }

    @Test
    fun `returns null for an unrelated opcode`() {
        assertThat(ModalAnswerPacketHandler.parse(0xF8, Unpooled.buffer())).isNull()
    }

    @Test
    fun `decodes modal window answer via EmbeddedChannel inbound message`() {
        val channel = EmbeddedChannel()
        try {
            val bytes = Unpooled.buffer()
            bytes.writeIntLE(31)
            bytes.writeByte(1)
            bytes.writeByte(0)
            channel.writeInbound(bytes)

            val payload = channel.readInbound<ByteBuf>()
            val command = ModalAnswerPacketHandler.parse(ModalAnswerPacketHandler.OPCODE_MODAL_WINDOW_ANSWER, payload)

            assertThat(command).isEqualTo(ModalWindowAnswerCommand(windowId = 31L, button = 1, choice = 0))
        } finally {
            channel.finishAndReleaseAll()
        }
    }

    @Test
    fun `routes modal answer 0xF9 to the registered callback on WorldCommit`() {
        val dispatcher = GameDispatcher()
        dispatcher.start()
        try {
            val player = Player(1, "Hero", 100, 100, position = Position(100, 100, 7))
            val modalManager = ModalWindowManager()
            var chosenButton = -1
            var chosenChoice = -1
            val windowId = modalManager.openWindow(player, "Quest", "Accept?") { button, choice ->
                chosenButton = button
                chosenChoice = choice
            }

            val frame = Unpooled.buffer()
            frame.writeByte(ModalAnswerPacketHandler.OPCODE_MODAL_WINDOW_ANSWER)
            frame.writeIntLE(windowId)
            frame.writeByte(1)
            frame.writeByte(0)

            val handled = GamePacketRouter.route(
                player,
                frame,
                GamePacketContext(
                    map = GameMap(),
                    dispatcher = dispatcher,
                    luaEnvironment = LuaEnvironment().apply { initState() },
                    modalManager = modalManager,
                ),
            )
            awaitWorldCommit(dispatcher)

            assertThat(handled).isEqualTo(1)
            assertThat(chosenButton).isEqualTo(1)
            assertThat(chosenChoice).isEqualTo(0)
        } finally {
            dispatcher.shutdown()
        }
    }

    @Test
    fun `counts a modal answer for an unknown window id as handled`() {
        val dispatcher = GameDispatcher()
        dispatcher.start()
        try {
            val frame = Unpooled.buffer()
            frame.writeByte(ModalAnswerPacketHandler.OPCODE_MODAL_WINDOW_ANSWER)
            frame.writeIntLE(999)
            frame.writeByte(0)
            frame.writeByte(0)

            val handled = GamePacketRouter.route(
                Player(1, "Hero", 100, 100, position = Position(100, 100, 7)),
                frame,
                GamePacketContext(
                    map = GameMap(),
                    dispatcher = dispatcher,
                    luaEnvironment = LuaEnvironment().apply { initState() },
                    modalManager = ModalWindowManager(),
                ),
            )
            awaitWorldCommit(dispatcher)

            assertThat(handled).isEqualTo(1)
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
