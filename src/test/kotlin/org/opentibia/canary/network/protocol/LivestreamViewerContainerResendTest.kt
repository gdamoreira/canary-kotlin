package org.opentibia.canary.network.protocol

import io.netty.buffer.Unpooled
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position

/**
 * Routing tests for the livestream-viewer `0xCA` container resend
 * (`ProtocolGame::parsePacketFromDispatcher`'s `m_isLivestreamViewer` arm,
 * protocolgame.cpp:1647-1666 -> `resendLivestreamViewerContainer`,
 * protocolgame.cpp:12448-12462): a viewer session's one-byte `0xCA` payload
 * re-sends the requested open container or closes it, instead of falling into
 * the exiva-restrictions parse.
 */
class LivestreamViewerContainerResendTest {

    private fun player(): Player = Player(
        id = 1L,
        name = "Watcher",
        maxHealth = 150,
        health = 100,
        position = Position(1000, 1000, 7),
    )

    private fun context(
        dispatcher: GameDispatcher,
        isLivestreamViewer: Boolean,
    ): GamePacketContext = GamePacketContext(
        map = GameMap(),
        dispatcher = dispatcher,
        luaEnvironment = LuaEnvironment().apply { initState() },
        isLivestreamViewer = isLivestreamViewer,
    )

    private fun awaitWorldCommit(dispatcher: GameDispatcher) {
        val ran = CountDownLatch(1)
        dispatcher.dispatch { ran.countDown() }
        assertThat(ran.await(2, TimeUnit.SECONDS)).isTrue()
    }

    @Test
    fun `a viewer 0xCA resends the open container`() {
        val dispatcher = GameDispatcher().apply { start() }
        try {
            val player = player()
            val sink = InMemoryProtocolSink(player, GameMap())
            player.protocolSink = sink
            val container = Container(1987, 8, ItemType(id = 1987, name = "backpack", isContainer = true))
            player.addContainer(5, container)

            val frame = Unpooled.buffer()
            frame.writeByte(ContainerBrowsePacketHandler.OPCODE_LIVESTREAM_VIEWER_CONTAINER)
            frame.writeByte(5)

            assertThat(GamePacketRouter.route(player, frame, context(dispatcher, isLivestreamViewer = true)))
                .isEqualTo(1)
            awaitWorldCommit(dispatcher)

            assertThat(sink.frames).hasSize(1)
            assertThat(sink.frames[0][0]).isEqualTo(0x6E.toByte())
            assertThat(sink.frames[0][1]).isEqualTo(5.toByte())
        } finally {
            dispatcher.shutdown()
        }
    }

    @Test
    fun `a viewer 0xCA for an unopened container closes it`() {
        val dispatcher = GameDispatcher().apply { start() }
        try {
            val player = player()
            val sink = InMemoryProtocolSink(player, GameMap())
            player.protocolSink = sink

            val frame = Unpooled.buffer()
            frame.writeByte(ContainerBrowsePacketHandler.OPCODE_LIVESTREAM_VIEWER_CONTAINER)
            frame.writeByte(9)

            assertThat(GamePacketRouter.route(player, frame, context(dispatcher, isLivestreamViewer = true)))
                .isEqualTo(1)
            awaitWorldCommit(dispatcher)

            assertThat(sink.frames).hasSize(1)
            assertThat(sink.frames[0][0]).isEqualTo(0x6F.toByte())
            assertThat(sink.frames[0][1]).isEqualTo(9.toByte())
        } finally {
            dispatcher.shutdown()
        }
    }

    @Test
    fun `a viewer 0xCA payload of another size is dropped`() {
        val dispatcher = GameDispatcher().apply { start() }
        try {
            val player = player()
            val sink = InMemoryProtocolSink(player, GameMap())
            player.protocolSink = sink

            val frame = Unpooled.buffer()
            frame.writeByte(ContainerBrowsePacketHandler.OPCODE_LIVESTREAM_VIEWER_CONTAINER)
            frame.writeByte(5)
            frame.writeByte(0x11)

            assertThat(GamePacketRouter.route(player, frame, context(dispatcher, isLivestreamViewer = true)))
                .isEqualTo(1)
            awaitWorldCommit(dispatcher)

            assertThat(sink.frames).isEmpty()
        } finally {
            dispatcher.shutdown()
        }
    }
}
