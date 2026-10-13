package org.opentibia.canary.network.protocol.forge

import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.Slots_t
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemAttribute
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.items.ItemTypeRegistry
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.protocol.GamePacketContext
import org.opentibia.canary.network.protocol.GamePacketRouter
import org.opentibia.canary.systems.forge.ForgeAction_t
import org.opentibia.canary.systems.forge.ForgeItemIds
import org.opentibia.canary.systems.forge.ItemClassification
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

    /**
     * Regression test for the router seam added by `feat/forge-registry-wire`:
     * the `0xBF` adapter must resolve each item's
     * `ItemType::upgradeClassification` (src/items/items.hpp:329) through the
     * context registry and charge the price table owned by the Lua environment
     * (`Game::getItemsClassifications`, src/game/game.hpp:591), exactly like
     * `Player::forgeTransferItemTier` (src/creatures/players/player.cpp:11542)
     * reading `g_game().getItemsClassifications()`.
     */
    @Test
    fun `forge transfer resolves classification costs from the context item registry`() {
        val player = hero()
        player.addItem(tieredItem(100, 2))
        player.addItem(tieredItem(200, 0))
        player.features.forge.setForgeDusts(200)

        val itemTypes = ItemTypeRegistry()
        itemTypes.register(ItemType(id = 100, name = "Donor", upgradeClassification = 4))
        itemTypes.register(ItemType(id = 200, name = "Receiver", upgradeClassification = 4))

        val luaEnvironment = LuaEnvironment().apply { initState() }
        luaEnvironment.itemClassifications.addClassification(classification4())

        val frame = Unpooled.buffer()
        frame.writeByte(ForgePacketParser.OPCODE_FORGE_ENTER)
        frame.writeByte(ForgeAction_t.TRANSFER.value)
        frame.writeByte(0)
        frame.writeShortLE(100)
        frame.writeByte(2)
        frame.writeShortLE(200)

        withStartedDispatcher { dispatcher ->
            val context = context(dispatcher, luaEnvironment, itemTypes)
            assertThat(GamePacketRouter.route(player, frame, context)).isEqualTo(1)
            awaitDispatched(dispatcher)

            assertThat(player.getItemCount(100)).isEqualTo(0)
            val receiver = inventoryItem(player, 200)
            assertThat(receiver).isNotNull()
            assertThat(receiver!!.getAttribute(ItemAttribute.TIER)).isEqualTo(1L)
            assertThat(player.features.forge.getForgeDusts()).isEqualTo(100)
        }
    }

    /**
     * Without a registry the adapter defaults every item to classification 0
     * (`ItemType::upgradeClassification` default), whose default
     * [ItemClassification] table has no tiers, so the transfer is refused and
     * nothing is mutated.
     */
    @Test
    fun `forge transfer without an item registry resolves classification 0 and is refused`() {
        val player = hero()
        player.addItem(tieredItem(100, 2))
        player.addItem(tieredItem(200, 0))
        player.features.forge.setForgeDusts(200)

        val luaEnvironment = LuaEnvironment().apply { initState() }
        luaEnvironment.itemClassifications.addClassification(classification4())

        val frame = Unpooled.buffer()
        frame.writeByte(ForgePacketParser.OPCODE_FORGE_ENTER)
        frame.writeByte(ForgeAction_t.TRANSFER.value)
        frame.writeByte(0)
        frame.writeShortLE(100)
        frame.writeByte(2)
        frame.writeShortLE(200)

        withStartedDispatcher { dispatcher ->
            val context = context(dispatcher, luaEnvironment)
            assertThat(GamePacketRouter.route(player, frame, context)).isEqualTo(1)
            awaitDispatched(dispatcher)

            assertThat(player.getItemCount(100)).isEqualTo(1)
            assertThat(inventoryItem(player, 200)!!.getAttribute(ItemAttribute.TIER)).isEqualTo(0L)
            assertThat(player.features.forge.getForgeDusts()).isEqualTo(200)
        }
    }

    private fun classification4(): ItemClassification = ItemClassification(4).apply {
        addTier(1, 0, 0, 0, 0)
        addTier(2, 0, 0, 0, 0)
    }

    private fun tieredItem(itemId: Int, tier: Int): Item =
        Item.create(itemId).apply { setAttribute(ItemAttribute.TIER, tier.toLong()) }

    private fun inventoryItem(player: Player, itemId: Int): Item? =
        Slots_t.entries
            .filter { it.value in Slots_t.FIRST..Slots_t.LAST }
            .firstNotNullOfOrNull { player.getInventoryItem(it)?.takeIf { item -> item.id == itemId } }

    private fun hero(): Player = Player(1, "Hero", 100, 100, position = Position(100, 100, 7))

    private fun context(
        dispatcher: GameDispatcher,
        luaEnvironment: LuaEnvironment = LuaEnvironment().apply { initState() },
        itemTypes: ItemTypeRegistry? = null,
    ): GamePacketContext = GamePacketContext(
        map = GameMap(),
        dispatcher = dispatcher,
        luaEnvironment = luaEnvironment,
        itemTypes = itemTypes,
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
