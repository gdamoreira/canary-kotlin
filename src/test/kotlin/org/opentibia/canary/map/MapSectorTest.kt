package org.opentibia.canary.map

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class MapSectorTest {

    @Test
    fun `allocates sector chunks on demand and resolves tiles via O(1) array math`() {
        val map = GameMap()

        // (1000, 1000, 7) and (1020, 1020, 7) belong to sector (31, 31, 7) because 1000/32 = 31, 1020/32 = 31
        val tile1 = map.getOrCreateTile(Position(1000, 1000, 7))
        val tile2 = map.getOrCreateTile(Position(1020, 1020, 7))

        assertThat(map.getTile(Position(1000, 1000, 7))).isSameAs(tile1)
        assertThat(map.getTile(Position(1020, 1020, 7))).isSameAs(tile2)

        // Non-existent tile in unallocated sector returns null
        assertThat(map.getTile(Position(50000, 50000, 7))).isNull()
    }
}
