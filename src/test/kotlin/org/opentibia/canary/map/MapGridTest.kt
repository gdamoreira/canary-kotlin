package org.opentibia.canary.map

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.items.Item

class MapGridTest {

    @Test
    fun `calculates Chebyshev distance and range on Position`() {
        val p1 = Position(100, 100, 7)
        val p2 = Position(105, 103, 7)
        val p3 = Position(100, 100, 8)

        assertThat(p1.getDistanceTo(p2)).isEqualTo(5)
        assertThat(p1.isWithinRange(p2, rangeX = 8, rangeY = 6)).isTrue()
        assertThat(p1.isWithinRange(p3, rangeX = 8, rangeY = 6)).isFalse() // different z
    }

    @Test
    fun `manages tiles and items on GameMap`() {
        val map = GameMap()
        val pos = Position(1000, 1000, 7)

        val tile = map.getOrCreateTile(pos)
        assertThat(tile.position).isEqualTo(pos)

        val ground = Item(id = 100) // ground item
        val stone = Item(id = 1285) // obstacle
        tile.ground = ground
        tile.addItem(stone)

        assertThat(map.getTile(pos)?.ground?.id).isEqualTo(100)
        assertThat(map.getTile(pos)?.items).hasSize(1)
    }

    @Test
    fun `retrieves spectator positions within viewport radius`() {
        val map = GameMap()
        val center = Position(500, 500, 7)

        // Seed 3 tiles around center
        map.getOrCreateTile(Position(502, 502, 7))
        map.getOrCreateTile(Position(505, 505, 7))
        map.getOrCreateTile(Position(520, 520, 7)) // out of standard 9x7 radius

        val nearbyTiles = map.getTilesInRange(center, radiusX = 8, radiusY = 6)
        assertThat(nearbyTiles).hasSize(2)
    }
}
