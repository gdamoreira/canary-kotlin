package org.opentibia.canary.map

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.game.Game
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.items.Items
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.lua.runtime.ScriptingDispatch
import org.opentibia.canary.systems.rewardchest.ReturnValue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.opentibia.canary.lua.bindings.core.pushPosition

/**
 * Transcript tests for the `g_moveEvents()` tile hooks of
 * `Tile::postAddNotification` (src/items/tile.cpp:1666-1709) and
 * `Tile::postRemoveNotification` (src/items/tile.cpp:1711-1736):
 *
 * - a creature entering/leaving a tile runs
 *   `MoveEvents::onCreatureMove(creature, tile, MOVE_EVENT_STEP_IN|STEP_OUT)`
 *   (tile.cpp:1704, 1729; movement.cpp:290-322);
 * - an item added to/removed from a tile runs
 *   `MoveEvents::onItemMove(item, tile, isAdd)`
 *   (tile.cpp:1706, 1733; movement.cpp:344-390).
 *
 * The `g_moveEvents()` global is the [GameMap.moveEventsOnCreatureMove] /
 * [GameMap.moveEventsOnItemMove] seam, wired here to the world
 * `ScriptingDispatch` exactly as the composition root binds it; the Lua
 * callbacks run confined to the WorldCommit lane.
 */
class TileMoveEventHookTest {

    private val center = Position(1000, 1000, 7)
    private val neighbour = Position(1001, 1000, 7)

    private fun itemRegistry(): Items = Items().also {
        it.register(ItemType(id = 100, name = "ground", isGroundTile = true))
        it.register(ItemType(id = 410, name = "trap", isMovable = true, weight = 100.0))
        it.register(ItemType(id = 411, name = "plate", isMovable = true, weight = 100.0))
        it.register(ItemType(id = 2160, name = "helmet", isMovable = true, weight = 100.0))
    }

    private class Harness(
        val game: Game,
        val map: GameMap,
        val environment: LuaEnvironment,
        val dispatcher: GameDispatcher,
    ) {
        fun flush() {
            val ran = CountDownLatch(1)
            dispatcher.dispatch { ran.countDown() }
            assertThat(ran.await(2, TimeUnit.SECONDS)).isTrue()
        }

        fun close() {
            dispatcher.shutdown()
        }
    }

    private fun harness(): Harness {
        Spectators.clearCache()
        val environment = LuaEnvironment().apply { initState() }
        val dispatcher = GameDispatcher().apply { start() }
        val game = Game(GameMap(), GameDispatcher(), itemTypes = itemRegistry())
        val scripting = ScriptingDispatch(environment, dispatcher)
        game.map.moveEventsOnCreatureMove = { creature, tile, eventType ->
            scripting.moveEventsOnCreatureMove(creature, tile, eventType)
        }
        game.map.moveEventsOnItemMove = { item, tile, isAdd ->
            scripting.moveEventsOnItemMove(item, tile, isAdd)
        }
        game.map.getOrCreateTile(center).ground = Item(100)
        game.map.getOrCreateTile(neighbour).ground = Item(100)
        return Harness(game, game.map, environment, dispatcher)
    }

    @Test
    fun `a position-registered stepin event fires when a creature enters the tile`() {
        // `MoveEvents::getEvent(tile, MOVE_EVENT_STEP_IN)` + `fireStepEvent`
        // (movement.cpp:278-322) reached through
        // `Tile::postAddNotification` (tile.cpp:1704).
        val harness = harness()
        try {
            harness.environment.globals.set("trapPos", pushPosition(harness.environment, center))
            harness.environment.loadChunk(
                """
                local trap = MoveEvent()
                function trap.onStepIn(creature, item, position, fromPosition)
                    entered = creature:getName()
                    entered_pos = position:toString()
                    entered_from = fromPosition:toString()
                end
                trap:type("stepin")
                trap:position(trapPos)
                trap:register()
                """.trimIndent()
            )
            val monster = Monster(id = 2, name = "Rat", maxHealth = 20, health = 20, position = neighbour)
            assertThat(harness.map.placeCreature(neighbour, monster)).isTrue()

            harness.map.moveCreature(monster, harness.map.getOrCreateTile(center))
            harness.flush()

            assertThat(harness.environment.getGlobal("entered").tojstring()).isEqualTo("Rat")
            assertThat(harness.environment.getGlobal("entered_pos").tojstring()).isEqualTo("( 1000, 1000, 7 )")
            assertThat(harness.environment.getGlobal("entered_from").tojstring()).isEqualTo("( 1001, 1000, 7 )")
        } finally {
            harness.close()
        }
    }

    @Test
    fun `a stepout event on a tile item fires when a creature leaves the tile`() {
        // `MoveEvents::onCreatureMove` scans the tile's ground and stacked
        // items (movement.cpp:300-320) reached through
        // `Tile::postRemoveNotification` (tile.cpp:1729).
        val harness = harness()
        try {
            harness.environment.loadChunk(
                """
                local plate = MoveEvent()
                function plate.onStepOut(creature, item, position, fromPosition)
                    left = creature:getName()
                    left_item = item:getId()
                end
                plate:type("stepout")
                plate:id(411)
                plate:register()
                """.trimIndent()
            )
            harness.map.getOrCreateTile(neighbour).addItem(Item(411))
            val monster = Monster(id = 2, name = "Rat", maxHealth = 20, health = 20, position = neighbour)
            assertThat(harness.map.placeCreature(neighbour, monster)).isTrue()

            harness.map.moveCreature(monster, harness.map.getOrCreateTile(center))
            harness.flush()

            assertThat(harness.environment.getGlobal("left").tojstring()).isEqualTo("Rat")
            assertThat(harness.environment.getGlobal("left_item").toint()).isEqualTo(411)
        } finally {
            harness.close()
        }
    }

    @Test
    fun `an additem event on a tile item fires when an item is added`() {
        // `MoveEvents::onItemMove(item, tile, true)` (movement.cpp:344-390):
        // the MOVE_EVENT_ADD_ITEM_ITEMTILE scan resolves the registration on
        // the flat tile item and receives the moving item as its first
        // argument, reached through `Tile::postAddNotification`
        // (tile.cpp:1706, firing from `Game::internalAddItem`, game.cpp:2772).
        val harness = harness()
        try {
            harness.environment.loadChunk(
                """
                local plate = MoveEvent()
                function plate.onAddItem(moveItem, tileItem, position)
                    added = moveItem:getId()
                    added_tile_item = tileItem:getId()
                    added_pos = position:toString()
                end
                plate:type("additem")
                plate:id(411)
                plate:register()
                """.trimIndent()
            )
            val tile = harness.map.getOrCreateTile(center)
            assertThat(harness.game.internalAddItem(tile, Item(411))).isEqualTo(ReturnValue.NOERROR)

            assertThat(harness.game.internalAddItem(tile, Item(2160))).isEqualTo(ReturnValue.NOERROR)
            harness.flush()

            assertThat(harness.environment.getGlobal("added").toint()).isEqualTo(2160)
            assertThat(harness.environment.getGlobal("added_tile_item").toint()).isEqualTo(411)
            assertThat(harness.environment.getGlobal("added_pos").tojstring()).isEqualTo("( 1000, 1000, 7 )")
        } finally {
            harness.close()
        }
    }

    @Test
    fun `a removeitem event on the moved item fires when the item is removed`() {
        // `MoveEvents::onItemMove(item, tile, false)` (movement.cpp:344-390):
        // the MOVE_EVENT_REMOVE_ITEM lookup resolves the registration on the
        // item being removed, reached through `Tile::postRemoveNotification`
        // (tile.cpp:1733, firing from `Game::internalRemoveItem`, game.cpp:2836).
        val harness = harness()
        try {
            harness.environment.loadChunk(
                """
                local plate = MoveEvent()
                function plate.onRemoveItem(moveItem, tileItem, position)
                    removed = moveItem:getId()
                    removed_pos = position:toString()
                end
                plate:type("removeitem")
                plate:id(411)
                plate:register()
                """.trimIndent()
            )
            val tile = harness.map.getOrCreateTile(center)
            val plate = Item(411)
            assertThat(harness.game.internalAddItem(tile, plate)).isEqualTo(ReturnValue.NOERROR)

            assertThat(harness.game.internalRemoveItem(plate)).isEqualTo(ReturnValue.NOERROR)
            harness.flush()

            assertThat(harness.environment.getGlobal("removed").toint()).isEqualTo(411)
            assertThat(harness.environment.getGlobal("removed_pos").tojstring()).isEqualTo("( 1000, 1000, 7 )")
        } finally {
            harness.close()
        }
    }
}
