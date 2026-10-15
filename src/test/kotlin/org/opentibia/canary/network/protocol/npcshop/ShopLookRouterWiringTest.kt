package org.opentibia.canary.network.protocol.npcshop

import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Npc
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.Game
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.items.Items
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.protocol.GamePacketContext
import org.opentibia.canary.network.protocol.GamePacketRouter
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Router wiring tests for the NPC shop look opcode `0x79`
 * (`ProtocolGame::parseLookInShop`, protocolgame.cpp:2806-2810) through
 * `Game::playerLookInShop` (game.cpp:6328-6358) and its renderer/veto seams.
 */
class ShopLookRouterWiringTest {

    @Test
    fun `routes look in shop 0x79 through Game playerLookInShop and emits the renderer text`() {
        val dispatcher = GameDispatcher()
        dispatcher.start()
        try {
            val harness = harness(dispatcher)
            val frame = Unpooled.buffer()
            frame.writeByte(NpcShopPacketHandler.OPCODE_LOOK_IN_SHOP)
            frame.writeShortLE(2160)
            frame.writeByte(10)

            val handled = GamePacketRouter.route(harness.player, frame, context(harness, dispatcher))
            awaitWorldCommit(dispatcher)

            assertThat(handled).isEqualTo(1)
            assertThat(harness.texts.single()).isEqualTo("You see 10 gold coins.\nIt weighs 0.10 oz.")
        } finally {
            dispatcher.shutdown()
        }
    }

    @Test
    fun `the playerOnLookInShop event veto suppresses the rendered look text`() {
        val dispatcher = GameDispatcher()
        dispatcher.start()
        try {
            val harness = harness(dispatcher)
            harness.game.eventPlayerOnLookInShop = { _, _, _ -> false }
            val frame = Unpooled.buffer()
            frame.writeByte(NpcShopPacketHandler.OPCODE_LOOK_IN_SHOP)
            frame.writeShortLE(2160)
            frame.writeByte(10)

            val handled = GamePacketRouter.route(harness.player, frame, context(harness, dispatcher))
            awaitWorldCommit(dispatcher)

            assertThat(handled).isEqualTo(1)
            assertThat(harness.texts).isEmpty()
        } finally {
            dispatcher.shutdown()
        }
    }

    private class Harness(val game: Game, val player: Player, val texts: MutableList<String>)

    private fun harness(dispatcher: GameDispatcher): Harness {
        val items = Items().apply {
            register(
                ItemType(
                    id = 2160, name = "gold coin", isStackable = true, pluralName = "gold coins", weight = 0.1,
                ),
            )
        }
        val game = Game(GameMap(), dispatcher, itemTypes = items)
        val texts = mutableListOf<String>()
        game.sendTextMessage = { _, _, text -> texts.add(text) }
        val player = Player(id = 1, name = "Hero", maxHealth = 100, health = 100, position = Position(100, 100, 7))
        game.addPlayer(player)
        val merchant = Npc(id = 50, name = "Merchant", position = Position(101, 100, 7))
        game.getShopOwner = { merchant }
        return Harness(game, player, texts)
    }

    private fun context(harness: Harness, dispatcher: GameDispatcher): GamePacketContext = GamePacketContext(
        map = harness.game.map,
        dispatcher = dispatcher,
        luaEnvironment = LuaEnvironment().apply { initState() },
        game = harness.game,
    )

    private fun awaitWorldCommit(dispatcher: GameDispatcher) {
        val ran = CountDownLatch(1)
        dispatcher.dispatch { ran.countDown() }
        assertThat(ran.await(2, TimeUnit.SECONDS)).isTrue()
    }
}
