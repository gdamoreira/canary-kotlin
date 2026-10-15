package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.Game
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.items.Items
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Router wiring tests for the inspection object opcode `0xCD`
 * (`ProtocolGame::parseInspectionObject`, protocolgame.cpp:2913-2931) through
 * the ported `Game::playerInspectItem` overloads (game.cpp:1635, 1652).
 */
class ItemInspectionRouterWiringTest {

    @Test
    fun `routes the normal object inspection through Game playerInspectItem by position`() {
        val dispatcher = GameDispatcher()
        dispatcher.start()
        try {
            val items = Items().apply {
                register(ItemType(id = 2160, name = "gold coin", isStackable = true, weight = 0.1))
            }
            val map = GameMap()
            val game = Game(map, dispatcher, itemTypes = items)
            val player = Player(id = 1, name = "Hero", maxHealth = 100, health = 100, position = center)
            game.addPlayer(player)
            val tile = map.getOrCreateTile(center)
            tile.ground = Item(id = 100, type = ItemType(id = 100, name = "ground", isGroundTile = true))
            val coin = Item(id = 2160, count = 5, type = items.get(2160))
            tile.addThing(coin)
            val inspected = mutableListOf<Triple<Int, Int, Boolean>>()
            var inspectedItem: Item? = null
            game.sendItemInspection = { _, itemId, count, item, cyclopedia ->
                inspected.add(Triple(itemId, count, cyclopedia))
                inspectedItem = item
            }
            val frame = Unpooled.buffer()
            frame.writeByte(InspectionPacketHandler.OPCODE_INSPECTION_OBJECT)
            frame.writeByte(InspectionPacketHandler.INSPECT_NORMALOBJECT)
            writePosition(frame, center)

            val handled = route(frame, player, game, dispatcher)

            assertThat(handled).isEqualTo(1)
            assertThat(inspected).containsExactly(Triple(2160, 5, false))
            assertThat(inspectedItem).isSameAs(coin)
        } finally {
            dispatcher.shutdown()
        }
    }

    @Test
    fun `routes the npc trade inspection through Game playerInspectItem by id`() {
        val dispatcher = GameDispatcher()
        dispatcher.start()
        try {
            val game = Game(GameMap(), dispatcher)
            val player = hero()
            game.addPlayer(player)
            val inspected = mutableListOf<Triple<Int, Int, Boolean>>()
            var inspectedItem: Item? = null
            game.sendItemInspection = { _, itemId, count, item, cyclopedia ->
                inspected.add(Triple(itemId, count, cyclopedia))
                inspectedItem = item
            }
            val frame = Unpooled.buffer()
            frame.writeByte(InspectionPacketHandler.OPCODE_INSPECTION_OBJECT)
            frame.writeByte(InspectionPacketHandler.INSPECT_NPCTRADE)
            frame.writeShortLE(2160)
            frame.writeByte(3)

            val handled = route(frame, player, game, dispatcher)

            assertThat(handled).isEqualTo(1)
            assertThat(inspected).containsExactly(Triple(2160, 3, false))
            assertThat(inspectedItem).isNull()
        } finally {
            dispatcher.shutdown()
        }
    }

    @Test
    fun `routes the cyclopedia inspection with the cyclopedia flag`() {
        val dispatcher = GameDispatcher()
        dispatcher.start()
        try {
            val game = Game(GameMap(), dispatcher)
            val player = hero()
            game.addPlayer(player)
            val inspected = mutableListOf<Triple<Int, Int, Boolean>>()
            game.sendItemInspection = { _, itemId, count, item, cyclopedia ->
                inspected.add(Triple(itemId, count, cyclopedia))
                assertThat(item).isNull()
            }
            val frame = Unpooled.buffer()
            frame.writeByte(InspectionPacketHandler.OPCODE_INSPECTION_OBJECT)
            frame.writeByte(InspectionPacketHandler.INSPECT_CYCLOPEDIA)
            frame.writeShortLE(2160)
            frame.writeByte(3)

            val handled = route(frame, player, game, dispatcher)

            assertThat(handled).isEqualTo(1)
            assertThat(inspected).containsExactly(Triple(2160, 3, true))
        } finally {
            dispatcher.shutdown()
        }
    }

    @Test
    fun `the proficiency inspection stays a handled decode without an item window`() {
        val dispatcher = GameDispatcher()
        dispatcher.start()
        try {
            val game = Game(GameMap(), dispatcher)
            val player = hero()
            game.addPlayer(player)
            val inspected = mutableListOf<Triple<Int, Int, Boolean>>()
            game.sendItemInspection = { _, itemId, count, _, cyclopedia ->
                inspected.add(Triple(itemId, count, cyclopedia))
            }
            val frame = Unpooled.buffer()
            frame.writeByte(InspectionPacketHandler.OPCODE_INSPECTION_OBJECT)
            frame.writeByte(InspectionPacketHandler.INSPECT_PROFICIENCY)
            frame.writeShortLE(2160)
            frame.writeByte(0)

            val handled = route(frame, player, game, dispatcher)

            assertThat(handled).isEqualTo(1)
            assertThat(inspected).isEmpty()
        } finally {
            dispatcher.shutdown()
        }
    }

    private fun route(frame: ByteBuf, player: Player, game: Game, dispatcher: GameDispatcher): Int {
        val handled = GamePacketRouter.route(
            player,
            frame,
            GamePacketContext(
                map = game.map,
                dispatcher = dispatcher,
                luaEnvironment = LuaEnvironment().apply { initState() },
                game = game,
            ),
        )
        awaitWorldCommit(dispatcher)
        return handled
    }

    private fun writePosition(frame: ByteBuf, position: Position) {
        frame.writeShortLE(position.x)
        frame.writeShortLE(position.y)
        frame.writeByte(position.z)
    }

    private fun hero(): Player = Player(1, "Hero", 100, 100, position = center)

    private fun awaitWorldCommit(dispatcher: GameDispatcher) {
        val ran = CountDownLatch(1)
        dispatcher.dispatch { ran.countDown() }
        assertThat(ran.await(2, TimeUnit.SECONDS)).isTrue()
    }

    private companion object {
        val center = Position(100, 100, 7)
    }
}
