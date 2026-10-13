package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.Game
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Router application tests for the container opcodes: `0x87`
 * (`Game::playerCloseContainer`, game.cpp:5028) and `0x88`
 * (`Game::playerMoveUpContainer`, game.cpp:5038). `0x75` stays a read-and-discard
 * parse like Canary's `ProtocolGame::parseContainerAction`.
 */
class OpcodeApplicationContainerRouterTest {

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

    private fun backpack(id: Int, capacity: Int = 20): Container =
        Container(id = id, capacity = capacity, type = ItemType(id = id, name = "backpack", isContainer = true))

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
    fun `close container opcode 0x87 drops the open container through the engine`() {
        start()
        try {
            val player = hero()
            player.addContainer(2, backpack(2000))
            var closedCid = -1
            player.sendCloseContainer = { cid -> closedCid = cid }

            val frame = Unpooled.buffer()
            frame.writeByte(TradeAndContainerPacketHandler.OPCODE_CLOSE_CONTAINER)
            frame.writeByte(2)

            assertThat(route(frame, player)).isEqualTo(1)
            assertThat(player.getContainerByID(2)).isNull()
            assertThat(closedCid).isEqualTo(2)
        } finally {
            stop()
        }
    }

    @Test
    fun `up arrow opcode 0x88 replaces the open container with its parent`() {
        start()
        try {
            val player = hero()
            val parent = backpack(2000)
            val child = backpack(2001, capacity = 10)
            parent.addItem(child)
            player.addContainer(2, child)
            val sent = mutableListOf<Triple<Int, Container, Boolean>>()
            player.sendContainer = { cid, container, hasParent, _ ->
                sent.add(Triple(cid, container, hasParent))
            }

            val frame = Unpooled.buffer()
            frame.writeByte(TradeAndContainerPacketHandler.OPCODE_UP_ARROW_CONTAINER)
            frame.writeByte(2)

            assertThat(route(frame, player)).isEqualTo(1)
            assertThat(player.getContainerByID(2)).isSameAs(parent)
            assertThat(sent).containsExactly(Triple(2, parent, false))
        } finally {
            stop()
        }
    }

    @Test
    fun `up arrow opcode 0x88 is inert for an unopened container id`() {
        start()
        try {
            val player = hero()

            val frame = Unpooled.buffer()
            frame.writeByte(TradeAndContainerPacketHandler.OPCODE_UP_ARROW_CONTAINER)
            frame.writeByte(9)

            assertThat(route(frame, player)).isEqualTo(1)
            assertThat(player.getOpenContainers()).isEmpty()
        } finally {
            stop()
        }
    }
}
