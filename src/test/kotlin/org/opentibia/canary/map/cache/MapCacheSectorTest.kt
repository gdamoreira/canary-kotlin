package org.opentibia.canary.map.cache

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.Tile

/**
 * Transcript tests for `MapCache::createMapSector`/`getBestMapSector`/
 * `getMapSector`/`setBasicTile` (src/map/mapcache.cpp:366-452), the `Floor`
 * storage (src/map/utils/mapsector.hpp:24-86) and the `MapSector` revision
 * bookkeeping (src/map/utils/mapsector.cpp:20-47).
 */
class MapCacheSectorTest {

    private lateinit var cache: MapCache

    @BeforeEach
    fun setUp() {
        cache = MapCache()
        cache.flush()
        MapSector.newSector = false
    }

    @Test
    fun `tracks topology and occupancy revisions independently`() {
        val sector = MapSector()

        assertThat(sector.getTopologyRevision(7)).isEqualTo(1L)
        assertThat(sector.getOccupancyRevision(7)).isEqualTo(1L)
        assertThat(sector.markTopologyChanged(7)).isEqualTo(2L)
        assertThat(sector.getTopologyRevision(7)).isEqualTo(2L)
        assertThat(sector.getOccupancyRevision(7)).isEqualTo(1L)
        assertThat(sector.markOccupancyChanged(7)).isEqualTo(2L)
        assertThat(sector.getTopologyRevision(7)).isEqualTo(2L)
        assertThat(sector.getOccupancyRevision(7)).isEqualTo(2L)
    }

    @Test
    fun `ignores layers beyond the map maximum`() {
        val sector = MapSector()

        assertThat(sector.markTopologyChanged(MAP_MAX_LAYERS)).isZero()
        assertThat(sector.markOccupancyChanged(MAP_MAX_LAYERS)).isZero()
        assertThat(sector.getTopologyRevision(MAP_MAX_LAYERS)).isZero()
        assertThat(sector.getOccupancyRevision(MAP_MAX_LAYERS)).isZero()
        assertThat(sector.createFloor(MAP_MAX_LAYERS)).isNull()
        assertThat(sector.getFloor(MAP_MAX_LAYERS)).isNull()
    }

    @Test
    fun `creates one floor per layer and reuses it`() {
        val sector = MapSector()

        assertThat(sector.getFloor(7)).isNull()
        val floor = sector.createFloor(7)
        assertThat(floor).isNotNull()
        assertThat(sector.createFloor(7)).isSameAs(floor)
        assertThat(sector.getFloor(7)).isSameAs(floor)
        assertThat(sector.createFloor(8)).isNotSameAs(floor)
    }

    @Test
    fun `maps floor tiles and cached tiles with the sector mask`() {
        val floor = Floor(7)
        val tile = Tile(Position(100, 101, 7))
        val cached = BasicTile().apply { ground = BasicItem().apply { id = 4526 } }

        floor.setTile(100, 101, tile)
        floor.setTileCache(100, 101, cached)

        assertThat(floor.getZ()).isEqualTo(7)
        assertThat(floor.getTile(100, 101)).isSameAs(tile)
        assertThat(floor.getTile(116, 117)).isSameAs(tile)
        assertThat(floor.getTile(101, 101)).isNull()
        assertThat(floor.getTileCache(100, 101)).isSameAs(cached)
        assertThat(floor.getTileAndCache(100, 101).tile).isSameAs(tile)
        assertThat(floor.getTileAndCache(100, 101).cachedTile).isSameAs(cached)
        assertThat(floor.getTiles()[4][5]).isSameAs(tile)
    }

    @Test
    fun `reuses the sector for the same index and flags new ones`() {
        val sector = cache.createMapSector(100, 100)

        assertThat(MapSector.newSector).isTrue()
        assertThat(cache.createMapSector(100, 100)).isSameAs(sector)
        assertThat(cache.getMapSector(100, 100)).isSameAs(sector)
        assertThat(cache.getMapSector(0, 0)).isNull()
        assertThat(cache.createMapSector(116, 100)).isNotSameAs(sector)
    }

    @Test
    fun `links the four neighbours when creating a sector`() {
        val center = cache.getBestMapSector(16, 16)
        val north = cache.getBestMapSector(16, 0)
        val south = cache.getBestMapSector(16, 32)
        val west = cache.getBestMapSector(0, 16)
        val east = cache.getBestMapSector(32, 16)

        assertThat(north.sectorS).isSameAs(center)
        assertThat(center.sectorS).isSameAs(south)
        assertThat(west.sectorE).isSameAs(center)
        assertThat(center.sectorE).isSameAs(east)
    }

    @Test
    fun `does not link an already existing sector again`() {
        val center = cache.getBestMapSector(16, 16)
        val south = cache.getBestMapSector(16, 32)
        val west = cache.getBestMapSector(0, 16)
        MapSector.newSector = true

        val again = cache.getBestMapSector(16, 16)

        assertThat(again).isSameAs(center)
        assertThat(again.sectorS).isSameAs(south)
        assertThat(again.sectorE).isNull()
        assertThat(west.sectorE).isSameAs(center)
    }

    @Test
    fun `refuses to set a tile on an invalid layer`() {
        cache.setBasicTile(0, 0, MAP_MAX_LAYERS, BasicTile().apply { ground = BasicItem().apply { id = 1 } })

        assertThat(cache.getMapSector(0, 0)).isNull()
    }

    @Test
    fun `stores the cached tile and bumps the topology revision`() {
        val basic = BasicTile().apply {
            ground = BasicItem().apply { id = 4526 }
            flags = 1
        }

        cache.setBasicTile(100, 100, 7, basic)

        val sector = cache.getMapSector(100, 100)
        assertThat(sector).isNotNull()
        val floor = sector!!.getFloor(7)
        assertThat(floor).isNotNull()
        assertThat(floor!!.getTileCache(100, 100)).isSameAs(cache.getOrCreateBasicTileFromCache(basic))
        assertThat(floor.getTile(100, 100)).isNull()
        assertThat(sector.getTopologyRevision(7)).isEqualTo(2L)
    }

    @Test
    fun `reuses the floor cursor across tiles of the same sector and layer`() {
        val cursor = MapCacheFloorCursor()
        val first = BasicTile().apply { ground = BasicItem().apply { id = 1 } }
        val second = BasicTile().apply { ground = BasicItem().apply { id = 2 } }

        cache.setBasicTile(100, 100, 7, first, cursor)
        val floor = cursor.floor
        cache.setBasicTile(101, 100, 7, second, cursor)

        assertThat(cursor.valid).isTrue()
        assertThat(cursor.floor).isSameAs(floor)
        assertThat(cursor.sectorIndex).isEqualTo((100 / SECTOR_SIZE) or ((100 / SECTOR_SIZE) shl 16))
        assertThat(cursor.z).isEqualTo(7)
    }

    @Test
    fun `creates one sector per boundary crossing`() {
        val basic = BasicTile().apply { ground = BasicItem().apply { id = 1 } }

        cache.setBasicTile(15, 15, 7, basic)
        cache.setBasicTile(16, 16, 7, basic)

        val first = cache.getMapSector(15, 15)
        val second = cache.getMapSector(16, 16)
        assertThat(first).isNotNull()
        assertThat(second).isNotNull()
        assertThat(first).isNotSameAs(second)
    }
}
