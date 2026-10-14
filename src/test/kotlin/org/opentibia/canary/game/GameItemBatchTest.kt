package org.opentibia.canary.game

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemAttribute
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.items.Items
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.Tile
import org.opentibia.canary.systems.rewardchest.ReturnValue

/**
 * Transcript tests for the item batch creation `Game` members:
 *
 *  - `Game::addItemBatch` (game.cpp:2848)
 *  - `Game::createItemBatch` (game.cpp:2947)
 *  - `Game::createItem` (game.cpp:2977)
 *  - `Game::internalPlayerAddItem` (game.cpp:2981)
 *  - `Game::findItemOfType` (game.cpp:3013)
 */
class GameItemBatchTest {

    private val center = Position(1000, 1000, 7)

    /** `ITEM_DECORATION_KIT` (`src/utils/utils_definitions.hpp:554`). */
    private val decorationKit = 23398

    private fun itemsRegistry(): Items = Items().also {
        it.register(ItemType(id = 100, name = "ground", isGroundTile = true))
        it.register(ItemType(id = 2160, name = "magic sword", isMovable = true, weight = 100.0))
        it.register(ItemType(id = 2170, name = "gold coin", isMovable = true, isStackable = true, stackSize = 5))
        it.register(ItemType(id = 1987, name = "backpack", isContainer = true, capacity = 8))
        it.register(ItemType(id = decorationKit, name = "decoration kit"))
    }

    private class Harness(
        val game: Game,
        val items: Items,
        val forged: MutableList<Player>,
    )

    private fun newGame(): Harness {
        val items = itemsRegistry()
        val game = Game(GameMap(), GameDispatcher(), itemTypes = items)
        val forged = mutableListOf<Player>()
        game.sendForgingData = { player -> forged.add(player) }
        return Harness(game, items, forged)
    }

    private fun Game.tile(position: Position = center): Tile =
        map.getOrCreateTile(position).also {
            it.ground = Item(id = 100, type = ItemType(id = 100, name = "ground", isGroundTile = true))
        }

    private fun item(items: Items, id: Int, count: Int = 1, type: ItemType? = null): Item =
        Item(id = id, count = count, type = type ?: items.get(id))

    private fun container(id: Int = 1987, capacity: Int = 8): Container =
        Container(
            id = id,
            capacity = capacity,
            type = ItemType(id = id, name = "backpack-$id", isContainer = true, capacity = capacity),
        )

    // ----- addItemBatch -----

    @Test
    fun `addItemBatch drops every item on the cylinder tile and counts them`() {
        val harness = newGame()
        val tile = harness.game.tile()
        val plain = item(harness.items, 2160)
        val bag = container()

        val (ret, totalAdded, containersCreated) =
            harness.game.addItemBatch(tile, listOf(plain, bag), dropOnMap = true)

        assertThat(ret).isEqualTo(ReturnValue.NOERROR)
        assertThat(totalAdded).isEqualTo(2L)
        assertThat(containersCreated).isEqualTo(1)
        // Down items insert at the front of the down range
        // (src/items/tile.cpp:1242-1244), so the last dropped item leads.
        assertThat(tile.items).containsExactly(bag, plain)
    }

    @Test
    fun `addItemBatch reports the last failed drop without adding anything`() {
        val harness = newGame()
        val unattached = container()

        val (ret, totalAdded, containersCreated) =
            harness.game.addItemBatch(unattached, listOf(item(harness.items, 2160)), dropOnMap = true)

        assertThat(ret).isEqualTo(ReturnValue.NOTPOSSIBLE)
        assertThat(totalAdded).isZero()
        assertThat(containersCreated).isZero()
    }

    @Test
    fun `addItemBatch without dropOnMap inserts into the container destination`() {
        val harness = newGame()
        val destination = container()
        val plain = item(harness.items, 2160)

        val (ret, totalAdded, _) = harness.game.addItemBatch(destination, listOf(plain), dropOnMap = false)

        assertThat(ret).isEqualTo(ReturnValue.NOERROR)
        assertThat(totalAdded).isEqualTo(1L)
        assertThat(destination.items).containsExactly(plain)
    }

    @Test
    fun `addItemBatch routes items through the auto container`() {
        val harness = newGame()
        val destination = container()
        val plain = item(harness.items, 2160)

        val (ret, totalAdded, containersCreated) =
            harness.game.addItemBatch(destination, listOf(plain), dropOnMap = false, autoContainerId = 1987)

        assertThat(ret).isEqualTo(ReturnValue.NOERROR)
        assertThat(totalAdded).isEqualTo(1L)
        assertThat(containersCreated).isEqualTo(1)
        val autoContainer = destination.items.single()
        assertThat(autoContainer.id).isEqualTo(1987)
        assertThat(autoContainer.getContainer()?.items).containsExactly(plain)
    }

    @Test
    fun `addItemBatch sends forging data only for successful placements`() {
        val harness = newGame()
        val tile = harness.game.tile()
        val player = Player(id = 1, name = "Batch", maxHealth = 150, health = 150, position = center)
        harness.game.addPlayer(player)

        harness.game.addItemBatch(tile, listOf(item(harness.items, 2160)), dropOnMap = true)

        assertThat(harness.forged).isEmpty()
    }

    // ----- createItemBatch / createItem -----

    @Test
    fun `createItemBatch splits stackable counts into stack sized items`() {
        val harness = newGame()
        val destination = container(capacity = 20)

        val (ret, totalAdded, _) = harness.game.createItemBatch(
            destination,
            listOf(Triple(2170, 12L, 0)),
            dropOnMap = false,
        )

        assertThat(ret).isEqualTo(ReturnValue.NOERROR)
        assertThat(totalAdded).isEqualTo(12L)
        assertThat(destination.items.map { it.getItemCount() }).containsExactlyInAnyOrder(5, 5, 2)
    }

    @Test
    fun `createItemBatch wraps wrappable items into stamped decoration kits`() {
        val harness = newGame()
        harness.items.register(
            ItemType(id = 3000, name = "house item", isMovable = true, isWrappable = true, wrapableTo = decorationKit),
        )
        val tile = harness.game.tile()

        val (ret, totalAdded, _) = harness.game.createItemBatch(
            tile,
            listOf(Triple(3000, 2L, 7)),
            dropOnMap = true,
        )

        assertThat(ret).isEqualTo(ReturnValue.NOERROR)
        assertThat(totalAdded).isEqualTo(2L)
        val kits = tile.items
        assertThat(kits.map { it.id }).containsExactly(decorationKit, decorationKit)
        kits.forEach { kit ->
            assertThat(kit.getAttribute(ItemAttribute.DESCRIPTION))
                .isEqualTo("Unwrap this item in your own house to create a <house item>.")
            assertThat(harness.game.getItemCustomAttribute(kit, "unWrapId")).isEqualTo(3000L)
        }
    }

    @Test
    fun `createItemBatch skips unknown ids and zero counts`() {
        val harness = newGame()
        val tile = harness.game.tile()

        val (ret, totalAdded, _) = harness.game.createItemBatch(
            tile,
            listOf(Triple(9999, 1L, 0), Triple(2160, 0L, 0)),
            dropOnMap = true,
        )

        assertThat(ret).isEqualTo(ReturnValue.NOTPOSSIBLE)
        assertThat(totalAdded).isZero()
        assertThat(tile.items).isEmpty()
    }

    @Test
    fun `createItem creates a single item through the batch path`() {
        val harness = newGame()
        val tile = harness.game.tile()

        val (ret, totalAdded, _) = harness.game.createItem(tile, 2160, 1L, 0, dropOnMap = true)

        assertThat(ret).isEqualTo(ReturnValue.NOERROR)
        assertThat(totalAdded).isEqualTo(1L)
        assertThat(tile.items.single().id).isEqualTo(2160)
    }

    // ----- internalPlayerAddItem -----

    @Test
    fun `internalPlayerAddItem puts the item in the player inventory`() {
        val harness = newGame()
        val player = Player(id = 1, name = "Batch", maxHealth = 150, health = 150, position = center)
        harness.game.addPlayer(player)
        val plain = item(harness.items, 2160)

        val ret = harness.game.internalPlayerAddItem(player, plain, dropOnMap = true)

        assertThat(ret).isEqualTo(ReturnValue.NOERROR)
        assertThat(player.getInventoryItem(org.opentibia.canary.creatures.Slots_t.CONST_SLOT_HEAD)).isEqualTo(
            plain,
        )
        assertThat(harness.forged).containsExactly(player)
    }

    @Test
    fun `internalPlayerAddItem drops the item on the map when the inventory refuses it`() {
        val harness = newGame()
        val tile = harness.game.tile()
        val player = Player(
            id = 1,
            name = "Batch",
            maxHealth = 150,
            health = 150,
            position = center,
            capacity = 0.0,
        )
        harness.game.addPlayer(player)
        assertThat(harness.game.internalPlaceCreature(player, center)).isTrue()
        tile.addThing(player)
        val plain = item(harness.items, 2160)

        val ret = harness.game.internalPlayerAddItem(player, plain, dropOnMap = true)

        assertThat(ret).isEqualTo(ReturnValue.NOERROR)
        assertThat(tile.items).contains(plain)
        assertThat(player.getInventoryItem(org.opentibia.canary.creatures.Slots_t.CONST_SLOT_HEAD)).isNull()
    }

    // ----- findItemOfType -----

    @Test
    fun `findItemOfType finds a top level item by id and subtype`() {
        val harness = newGame()
        val bag = container()
        val stack = item(harness.items, 2170, count = 5)
        bag.addThing(stack)

        assertThat(harness.game.findItemOfType(bag, 2170)).isEqualTo(stack)
        assertThat(harness.game.findItemOfType(bag, 2170, subType = 5)).isEqualTo(stack)
        assertThat(harness.game.findItemOfType(bag, 2170, subType = 4)).isNull()
        assertThat(harness.game.findItemOfType(bag, 9999)).isNull()
    }

    @Test
    fun `findItemOfType searches containers in depth only when requested`() {
        val harness = newGame()
        val outer = container()
        val inner = container(id = 1988)
        val nested = item(harness.items, 2160)
        inner.addThing(nested)
        outer.addThing(inner)

        assertThat(harness.game.findItemOfType(outer, 2160, depthSearch = true)).isEqualTo(nested)
        assertThat(harness.game.findItemOfType(outer, 2160, depthSearch = false)).isNull()
    }
}
