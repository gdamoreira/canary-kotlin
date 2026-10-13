package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.Game
import org.opentibia.canary.game.QuickLootFilter_t
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Router application tests for the quick-loot state opcodes: `0x90` action `3`
 * (`Game::playerSetQuickLootFallback`, game.cpp:6898) and `0x91`
 * (`Game::playerQuickLootBlackWhitelist`, game.cpp:6907). The corpse-loot and
 * managed-container actions stay decode-only where their engines are unported.
 */
class OpcodeApplicationQuickLootRouterTest {

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
    fun `loot container action 3 toggles the quick loot fallback`() {
        start()
        try {
            val player = hero()

            val enable = Unpooled.buffer()
            enable.writeByte(QuickLootPacketHandler.OPCODE_LOOT_CONTAINER)
            enable.writeByte(3)
            enable.writeByte(0x01)
            assertThat(route(enable, player)).isEqualTo(1)
            assertThat(player.quickLootFallbackToMainContainer).isTrue()

            val disable = Unpooled.buffer()
            disable.writeByte(QuickLootPacketHandler.OPCODE_LOOT_CONTAINER)
            disable.writeByte(3)
            disable.writeByte(0x00)
            assertThat(route(disable, player)).isEqualTo(1)
            assertThat(player.quickLootFallbackToMainContainer).isFalse()
        } finally {
            stop()
        }
    }

    @Test
    fun `black whitelist opcode 0x91 stores the filter and item ids`() {
        start()
        try {
            val player = hero()

            val frame = Unpooled.buffer()
            frame.writeByte(QuickLootPacketHandler.OPCODE_QUICK_LOOT_BLACK_WHITELIST)
            frame.writeByte(QuickLootFilter_t.QUICKLOOTFILTER_ACCEPTEDLOOT.value)
            frame.writeShortLE(2)
            frame.writeShortLE(3031)
            frame.writeShortLE(3035)

            assertThat(route(frame, player)).isEqualTo(1)
            assertThat(player.quickLootFilter).isEqualTo(QuickLootFilter_t.QUICKLOOTFILTER_ACCEPTEDLOOT)
            assertThat(player.quickLootListItemIds).containsExactly(3031, 3035)
        } finally {
            stop()
        }
    }

    @Test
    fun `black whitelist opcode 0x91 replaces a previous list`() {
        start()
        try {
            val player = hero()
            player.quickLootListItemIds = listOf(1, 2, 3)

            val frame = Unpooled.buffer()
            frame.writeByte(QuickLootPacketHandler.OPCODE_QUICK_LOOT_BLACK_WHITELIST)
            frame.writeByte(QuickLootFilter_t.QUICKLOOTFILTER_SKIPPEDLOOT.value)
            frame.writeShortLE(1)
            frame.writeShortLE(2148)

            assertThat(route(frame, player)).isEqualTo(1)
            assertThat(player.quickLootListItemIds).containsExactly(2148)
        } finally {
            stop()
        }
    }
}
