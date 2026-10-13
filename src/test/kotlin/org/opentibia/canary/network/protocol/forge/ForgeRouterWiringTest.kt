package org.opentibia.canary.network.protocol.forge

import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.protocol.GamePacketContext
import org.opentibia.canary.network.protocol.GamePacketRouter
import org.opentibia.canary.systems.forge.ForgeAction_t
import org.opentibia.canary.systems.forge.ForgeItemIds
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Router wiring tests for the forge entry opcode `0xBF`
 * (`ProtocolGame::parseForgeEnter`, src/server/network/protocol/protocolgame.cpp:7398).
 *
 * The parsed [ForgeEnterCommand] must reach `player.features.forge` through
 * `ForgeApplication` on the WorldCommit lane (AGENTS.md 1.2); the current
 * inventory-backed forge adapter is `PlayerForgeAdapter` (systems/forge).
 */
class ForgeRouterWiringTest {

    @Test
    fun `applies a dust to slivers command from a 0xBF payload to the player forge`() {
        val player = hero()
        player.features.forge.setForgeDusts(60)

        val frame = Unpooled.buffer()
        frame.writeByte(ForgePacketParser.OPCODE_FORGE_ENTER)
        frame.writeByte(ForgeAction_t.DUSTTOSLIVERS.value)

        withStartedDispatcher { dispatcher ->
            assertThat(GamePacketRouter.route(player, frame, context(dispatcher))).isEqualTo(1)
            awaitDispatched(dispatcher)

            assertThat(player.features.forge.getForgeDusts()).isEqualTo(0)
            assertThat(player.getItemCount(ForgeItemIds.ITEM_FORGE_SLIVER)).isEqualTo(3)
        }
    }

    @Test
    fun `applies a slivers to cores command from a 0xBF payload to the player forge`() {
        val player = hero()
        player.addItem(org.opentibia.canary.items.Item.create(ForgeItemIds.ITEM_FORGE_SLIVER, 50))

        val frame = Unpooled.buffer()
        frame.writeByte(ForgePacketParser.OPCODE_FORGE_ENTER)
        frame.writeByte(ForgeAction_t.SLIVERSTOCORES.value)

        withStartedDispatcher { dispatcher ->
            assertThat(GamePacketRouter.route(player, frame, context(dispatcher))).isEqualTo(1)
            awaitDispatched(dispatcher)

            assertThat(player.getItemCount(ForgeItemIds.ITEM_FORGE_SLIVER)).isEqualTo(0)
            assertThat(player.getItemCount(ForgeItemIds.ITEM_FORGE_CORE)).isEqualTo(1)
        }
    }

    private fun hero(): Player = Player(1, "Hero", 100, 100, position = Position(100, 100, 7))

    private fun context(dispatcher: GameDispatcher): GamePacketContext = GamePacketContext(
        map = GameMap(),
        dispatcher = dispatcher,
        luaEnvironment = LuaEnvironment().apply { initState() },
    )

    private fun withStartedDispatcher(block: (GameDispatcher) -> Unit) {
        val dispatcher = GameDispatcher()
        dispatcher.start()
        try {
            block(dispatcher)
        } finally {
            dispatcher.shutdown()
        }
    }

    private fun awaitDispatched(dispatcher: GameDispatcher) {
        val ran = CountDownLatch(1)
        dispatcher.dispatch { ran.countDown() }
        assertThat(ran.await(2, TimeUnit.SECONDS)).isTrue()
    }
}
