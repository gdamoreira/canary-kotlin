package org.opentibia.canary.items

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.Tile

/**
 * Transcripts of `Thing`/`Item` parent, tile and reachability behavior
 * (src/items/thing.cpp:14-28, src/items/item.cpp:832-866).
 */
class ThingTest {

    @Test
    fun `orphan thing position falls back to the nullptr tile position`() {
        val item = Item(100)

        assertThat(item.getTile()).isNull()
        assertThat(item.position).isEqualTo(Tile.nullptrTilePosition)
        assertThat(Tile.nullptrTilePosition).isEqualTo(Position(0xFFFF, 0xFFFF, 0xFF))
    }

    @Test
    fun `item position and tile resolve through the parent tile`() {
        val tile = Tile(Position(10, 20, 7))
        val item = Item(100)

        tile.internalAddThing(item)

        assertThat(item.getParent()).isSameAs(tile)
        assertThat(item.getTile()).isSameAs(tile)
        assertThat(item.position).isEqualTo(Position(10, 20, 7))
    }

    @Test
    fun `getRealParent defaults to getParent`() {
        val tile = Tile(Position(1, 2, 3))
        val item = Item(1)

        item.setParent(tile)
        assertThat(item.getRealParent()).isSameAs(tile)

        item.resetParent()
        assertThat(item.getRealParent()).isNull()
    }

    @Test
    fun `item getTile walks out of nested containers`() {
        val tile = Tile(Position(5, 5, 5))
        val bag = Container(1987, capacity = 8, type = ItemType(1987, "bag", isContainer = true, capacity = 8))
        val sword = Item(100)

        tile.internalAddThing(bag)
        bag.internalAddThing(sword)

        assertThat(sword.getParent()).isSameAs(bag)
        assertThat(sword.getTile()).isSameAs(tile)
    }

    @Test
    fun `item is removed only when the whole parent chain is removed`() {
        val item = Item(100)
        assertThat(item.isRemoved()).isTrue()

        val tile = Tile(Position(0, 0, 0))
        tile.internalAddThing(item)
        assertThat(item.isRemoved()).isFalse()
    }

    @Test
    fun `pickupable item throws 15 squares and other items throw 2`() {
        val pickupable = Item(200, type = ItemType(200, "pickupable", isPickupable = true))
        val fixed = Item(201, type = ItemType(201, "fixed", isPickupable = false))
        val untyped = Item(202)

        assertThat(pickupable.getThrowRange()).isEqualTo(15)
        assertThat(fixed.getThrowRange()).isEqualTo(2)
        assertThat(untyped.getThrowRange()).isEqualTo(2)
    }

    @Test
    fun `item is pushable when its type is movable`() {
        val movable = Item(203, type = ItemType(203, "movable", isMovable = true))
        val fixed = Item(204, type = ItemType(204, "fixed"))

        assertThat(movable.isPushable()).isTrue()
        assertThat(fixed.isPushable()).isFalse()
    }

    @Test
    fun `item exposes itself as its item view`() {
        val item = Item(100)

        assertThat(item.getItem()).isSameAs(item)
        assertThat(item.getCreature()).isNull()
        assertThat(item.getContainer()).isNull()
        assertThat(item.getCylinder()).isNull()
    }

    @Test
    fun `container exposes itself as cylinder`() {
        val container = Container(1987, capacity = 8)

        assertThat(container.getCylinder()).isSameAs(container)
        assertThat(container.getContainer()).isSameAs(container)
    }
}
