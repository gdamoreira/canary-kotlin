package org.opentibia.canary.map.cache

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.opentibia.canary.game.zones.TileFlags
import org.opentibia.canary.game.zones.Zones
import org.opentibia.canary.items.ItemAttribute
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.items.Items
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.Tile
import org.opentibia.canary.map.house.Houses

/**
 * Transcript tests for the `MapCache` item cache, reserve math and tile
 * materialization (src/map/mapcache.cpp:32-63, 179-364, 406-452).
 */
class MapCacheTest {

    private lateinit var cache: MapCache

    @BeforeEach
    fun setUp() {
        cache = MapCache()
        cache.flush()
    }

    private fun simpleItem(id: Int): BasicItem = BasicItem().apply { this.id = id }

    private fun cacheBasicTile(basic: BasicTile, x: Int = 100, y: Int = 100, z: Int = 7): BasicTile {
        cache.setBasicTile(x, y, z, basic)
        return cache.getOrCreateBasicTileFromCache(basic)
    }

    private fun floorOf(x: Int, y: Int, z: Int): Floor = requireNotNull(cache.getMapSector(x, y)).getFloor(z)!!

    @Test
    fun `caches basic items by id`() {
        val first = cache.getBasicItemFromCache(2160)

        assertThat(first.id).isEqualTo(2160)
        assertThat(cache.getBasicItemFromCache(2160)).isSameAs(first)
        assertThat(cache.getBasicItemFromCache(2161)).isNotSameAs(first)
        assertThat(cache.getBasicItemFromCache(0xFFFF).id).isEqualTo(0xFFFF)
    }

    @Test
    fun `replaces equal basic items with the cached instance and rejects null`() {
        val reference = BasicItem().apply {
            id = 100
            charges = 4
        }

        assertThat(cache.tryReplaceItemFromCache(null)).isNull()
        assertThat(cache.tryReplaceItemFromCache(reference)).isSameAs(reference)

        val equal = BasicItem().apply {
            id = 100
            charges = 4
        }
        assertThat(cache.tryReplaceItemFromCache(equal)).isSameAs(reference)
    }

    @Test
    fun `hashes item keys with the canary combine formula`() {
        assertThat(hashInt(0L)).isZero()
        assertThat(hashInt(1L)).isEqualTo(0xff51afd792fd5b26UL.toLong())
        assertThat(hashCombine(0L, 5L)).isEqualTo(0xfc986f387b1f7153UL.toLong())
    }

    @Test
    fun `estimates cache reserves from the map file size`() {
        assertThat(estimateCacheReserve(fileSize = 0, bytesPerEntry = 512, maxReserve = 131072)).isZero()
        assertThat(estimateCacheReserve(fileSize = 512, bytesPerEntry = 512, maxReserve = 131072)).isEqualTo(2)
        assertThat(estimateCacheReserve(fileSize = 1024, bytesPerEntry = 512, maxReserve = 3)).isEqualTo(3)
        assertThat(estimateCacheReserve(fileSize = 100_000, bytesPerEntry = 0, maxReserve = 131072)).isZero()
        assertThat(estimateCacheReserve(fileSize = Long.MAX_VALUE, bytesPerEntry = 512, maxReserve = 131072))
            .isEqualTo(131072)
    }

    @Test
    fun `estimates the sector reserve from the map dimensions`() {
        assertThat(estimateSectorReserve(width = 1, height = 1)).isEqualTo(1)
        assertThat(estimateSectorReserve(width = SECTOR_SIZE, height = SECTOR_SIZE)).isEqualTo(1)
        assertThat(estimateSectorReserve(width = SECTOR_SIZE + 1, height = SECTOR_SIZE)).isEqualTo(2)
        assertThat(estimateSectorReserve(width = 1000, height = 1000)).isEqualTo(3969)
        assertThat(estimateSectorReserve(width = 0xFFFF, height = 0xFFFF)).isEqualTo(1_048_576)
    }

    @Test
    fun `builds the flags and id keys`() {
        assertThat(makeFlagsAndIdKey(flags = 0, id = 0)).isZero()
        assertThat(makeFlagsAndIdKey(flags = 1, id = 2)).isEqualTo(65538L)
        assertThat(makeFlagsAndIdKey(flags = 0xFFFF, id = 0xFFFF)).isEqualTo(0xFFFFFFFFL)
        assertThat(makeGroundAndItemKey(flags = 0, groundId = 0, itemId = 0)).isZero()
        assertThat(makeGroundAndItemKey(flags = 1, groundId = 2, itemId = 3)).isEqualTo(0x1_0002_0003L)
    }

    @Test
    fun `reserves the map caches without corrupting cache lookups`() {
        cache.reserveForMap(width = 1024, height = 1024, fileSize = 1L shl 20)

        assertThat(cache.getBasicItemFromCache(100).id).isEqualTo(100)
        assertThat(cache.getOrCreateBasicTileFromCache(BasicTile().apply { ground = simpleItem(1) }))
            .isSameAs(cache.getOrCreateBasicTileFromCache(BasicTile().apply { ground = simpleItem(1) }))
    }

    @Test
    fun `flush drops the item and tile caches`() {
        val item = cache.getBasicItemFromCache(5)
        val basic = BasicTile().apply { ground = simpleItem(5) }
        val tile = cache.getOrCreateBasicTileFromCache(basic)

        cache.flush()

        assertThat(cache.getBasicItemFromCache(5)).isNotSameAs(item)
        assertThat(cache.getOrCreateBasicTileFromCache(BasicTile().apply { ground = simpleItem(5) }))
            .isNotSameAs(tile)
    }

    @Test
    fun `returns the existing live tile when no cached tile is present`() {
        val floor = Floor(7)
        val live = Tile(Position(5, 5, 7))
        floor.setTile(5, 5, live)

        assertThat(cache.getOrCreateTileFromCache(floor, 5, 5)).isSameAs(live)
    }

    @Test
    fun `materializes a cached tile with ground items and flags`() {
        val registry = Items()
        registry.register(ItemType(id = 4526, name = "ground", isGroundTile = true))
        val itemCache = MapCache(itemTypes = registry)
        itemCache.flush()
        val basic = BasicTile().apply {
            ground = simpleItem(4526)
            items.add(simpleItem(2160))
            flags = TileFlags.TILESTATE_PROTECTIONZONE.value
        }
        itemCache.setBasicTile(100, 100, 7, basic)

        val floor = requireNotNull(itemCache.getMapSector(100, 100)).getFloor(7)!!
        val tile = itemCache.getOrCreateTileFromCache(floor, 100, 100)

        assertThat(tile).isNotNull()
        assertThat(tile!!.position).isEqualTo(Position(100, 100, 7))
        assertThat(tile.ground!!.id).isEqualTo(4526)
        assertThat(tile.items.map { it.id }).containsExactly(2160)
        assertThat(tile.hasFlag(TileFlags.TILESTATE_PROTECTIONZONE.value)).isTrue()
        assertThat(floor.getTile(100, 100)).isSameAs(tile)
        assertThat(floor.getTileCache(100, 100)).isNull()
        assertThat(requireNotNull(itemCache.getMapSector(100, 100)).getTopologyRevision(7)).isEqualTo(3L)
    }

    @Test
    fun `applies parsed item attributes to the live items`() {
        val registry = Items()
        registry.register(ItemType(id = 4526, name = "ground", isGroundTile = true, decayTo = 5))
        val itemCache = MapCache(itemTypes = registry)
        itemCache.flush()

        val basic = BasicTile().apply {
            ground = BasicItem().apply {
                id = 4526
                charges = 7
                actionId = 100
                uniqueId = 9
                text = "hello"
            }
        }
        itemCache.setBasicTile(100, 100, 7, basic)
        val floor = requireNotNull(itemCache.getMapSector(100, 100)).getFloor(7)!!

        val tile = itemCache.getOrCreateTileFromCache(floor, 100, 100)!!
        val ground = tile.ground!!

        assertThat(ground.id).isEqualTo(4526)
        assertThat(ground.getItemCount()).isEqualTo(7)
        assertThat(ground.getActionId()).isEqualTo(100)
        assertThat(ground.getUniqueId()).isEqualTo(9L)
        assertThat(ground.getAttribute(ItemAttribute.TEXT)).isEqualTo("hello")
        assertThat(ground.isLoadedFromMap()).isTrue()
        assertThat(ground.decayDisabled).isTrue()
    }

    @Test
    fun `keeps decay disabled for item types without a decay target`() {
        val registry = Items()
        registry.register(ItemType(id = 4526, name = "ground", isGroundTile = true))
        val itemCache = MapCache(itemTypes = registry)
        itemCache.flush()
        val basic = BasicTile().apply { ground = simpleItem(4526) }
        itemCache.setBasicTile(100, 100, 7, basic)
        val floor = requireNotNull(itemCache.getMapSector(100, 100)).getFloor(7)!!

        val tile = itemCache.getOrCreateTileFromCache(floor, 100, 100)!!

        assertThat(tile.ground!!.decayDisabled).isFalse()
    }

    @Test
    fun `does not start decay for a freshly created item`() {
        val registry = Items()
        registry.register(ItemType(id = 3000, name = "torch", decayTo = 1, decayTime = 1000))
        var decayStarted = false
        val decayingCache = MapCache(itemTypes = registry, startDecaying = { decayStarted = true })
        decayingCache.flush()
        val basic = BasicTile().apply { items.add(simpleItem(3000)) }
        decayingCache.setBasicTile(100, 100, 7, basic)
        val floor = requireNotNull(decayingCache.getMapSector(100, 100)).getFloor(7)!!

        decayingCache.getOrCreateTileFromCache(floor, 100, 100)

        assertThat(decayStarted).isFalse()
    }

    @Test
    fun `creates container children recursively`() {
        val registry = Items()
        registry.register(ItemType(id = 2000, name = "backpack", isContainer = true, capacity = 8))
        val itemCache = MapCache(itemTypes = registry)
        itemCache.flush()
        val basic = BasicTile().apply {
            ground = simpleItem(4526)
            items.add(
                BasicItem().apply {
                    id = 2000
                    items.add(simpleItem(2160))
                    items.add(simpleItem(2161))
                },
            )
        }
        itemCache.setBasicTile(100, 100, 7, basic)
        val floor = requireNotNull(itemCache.getMapSector(100, 100)).getFloor(7)!!

        val tile = itemCache.getOrCreateTileFromCache(floor, 100, 100)!!

        val container = tile.items[0].getContainer()
        assertThat(container).isNotNull()
        assertThat(container!!.items.map { it.id }).containsExactly(2160, 2161)
    }

    @Test
    fun `returns the old tile and keeps the cache when the house is unknown`() {
        val basic = BasicTile().apply {
            houseId = 42
            ground = simpleItem(4526)
        }
        cacheBasicTile(basic)
        val floor = floorOf(100, 100, 7)

        assertThat(cache.getOrCreateTileFromCache(floor, 100, 100)).isNull()
        assertThat(floor.getTile(100, 100)).isNull()
        assertThat(floor.getTileCache(100, 100)).isNotNull()
    }

    @Test
    fun `attaches the zones resolved for the materialized position`() {
        val zones = Zones()
        val zone = zones.addZone("test-zone", 5)!!
        val registry = Items()
        registry.register(ItemType(id = 4526, name = "ground", isGroundTile = true))
        val itemCache = MapCache(
            itemTypes = registry,
            zones = { position -> if (position == Position(100, 100, 7)) listOf(zone) else emptyList() },
        )
        itemCache.flush()
        val basic = BasicTile().apply { ground = simpleItem(4526) }
        itemCache.setBasicTile(100, 100, 7, basic)
        val floor = requireNotNull(itemCache.getMapSector(100, 100)).getFloor(7)!!

        val tile = itemCache.getOrCreateTileFromCache(floor, 100, 100)!!

        assertThat(tile.getZones()).containsExactly(zone)
    }

    @Test
    fun `materializes a house tile and registers it with the house`() {
        val houses = Houses()
        val house = houses.addHouse(7)
        val houseCache = MapCache(houses = houses)
        houseCache.flush()
        val basic = BasicTile().apply {
            houseId = 7
            ground = simpleItem(4526)
        }
        houseCache.setBasicTile(100, 100, 7, basic)
        val floor = requireNotNull(houseCache.getMapSector(100, 100)).getFloor(7)!!

        val tile = houseCache.getOrCreateTileFromCache(floor, 100, 100)!!

        assertThat(tile.houseId).isEqualTo(7)
        assertThat(tile.house).isSameAs(house)
        assertThat(house.tiles.map { it.position }).contains(Position(100, 100, 7))
    }
}
