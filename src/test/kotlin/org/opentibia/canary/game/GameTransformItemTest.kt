package org.opentibia.canary.game

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.items.Items
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.Tile
import org.opentibia.canary.systems.rewardchest.ReturnValue

/**
 * Transcript tests for the charged-item paths of `Game::transformItem`
 * (src/game/game.cpp:3401-3500): an item whose last charge is consumed
 * (`newCount == 0` with the `ItemAttribute_t::CHARGES` attribute) is removed,
 * or replaced by its `ItemType::decayTo` prototype, and a stackable item
 * transformed to count zero is removed.
 */
class GameTransformItemTest {

    private val center = Position(1000, 1000, 7)

    private class Harness(
        val game: Game,
        val items: Items,
    )

    private fun itemsRegistry(): Items = Items().also {
        it.register(ItemType(id = 100, name = "ground", isGroundTile = true))
        it.register(ItemType(id = 2160, name = "charged ring", charges = 20))
        it.register(ItemType(id = 2161, name = "uncharged ring"))
        it.register(ItemType(id = 2170, name = "gold coin", isStackable = true, stackSize = 100))
    }

    private fun newGame(): Harness {
        val items = itemsRegistry()
        return Harness(Game(GameMap(), GameDispatcher(), itemTypes = items), items)
    }

    private fun Harness.place(item: Item): Tile {
        val tile = game.map.getOrCreateTile(center).also {
            it.ground = Item(id = 100, type = items.get(100))
        }
        assertThat(game.internalAddItem(tile, item)).isEqualTo(ReturnValue.NOERROR)
        return tile
    }

    private fun chargedRing(harness: Harness): Item =
        Item(id = 2160, count = 0, type = harness.items.get(2160))

    @Test
    fun `transformItem removes a charged item when the last charge is consumed`() {
        // game.cpp:3437-3444: `newCount == 0 && item->hasAttribute(CHARGES)` with no decayTo.
        val harness = newGame()
        val ring = chargedRing(harness)
        val tile = harness.place(ring)

        val result = harness.game.transformItem(ring, ring.id, 0)

        assertThat(result).isNull()
        assertThat(tile.items).doesNotContain(ring)
    }

    @Test
    fun `transformItem replaces a charged item with its decayTo prototype`() {
        // game.cpp:3444-3458: `curType.id == newType.id` resolves `newItemId = curType.decayTo`.
        val harness = newGame()
        harness.items.get(2160)?.decayTo = 2161
        val ring = chargedRing(harness)
        val tile = harness.place(ring)

        val result = harness.game.transformItem(ring, ring.id, 0)

        assertThat(result).isNotNull
        assertThat(result?.id).isEqualTo(2161)
        assertThat(tile.items).containsExactly(result)
        assertThat(tile.items).doesNotContain(ring)
    }

    @Test
    fun `transformItem removes a stackable item transformed to zero count`() {
        // game.cpp:3438-3441: `newCount == 0 && item->isStackable()` removes the stack.
        val harness = newGame()
        val coins = Item(id = 2170, count = 5, type = harness.items.get(2170))
        val tile = harness.place(coins)

        val result = harness.game.transformItem(coins, coins.id, 0)

        assertThat(result).isNull()
        assertThat(tile.items).doesNotContain(coins)
    }

    @Test
    fun `transformItem keeps a charged item with remaining charges and stores the new count`() {
        // game.cpp:3480-3486: same-type in-place update with `newType.hasSubType()`.
        val harness = newGame()
        val ring = chargedRing(harness)
        val tile = harness.place(ring)

        val result = harness.game.transformItem(ring, ring.id, 19)

        assertThat(result).isSameAs(ring)
        assertThat(ring.getSubType()).isEqualTo(19)
        assertThat(tile.items).containsExactly(ring)
    }
}
