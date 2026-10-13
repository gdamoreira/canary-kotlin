package org.opentibia.canary.map.cache

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Transcript tests for `struct BasicTile`
 * (src/map/mapcache.hpp:78-141, src/map/mapcache.cpp:454-472) and its
 * `MapCache::getOrCreateBasicTileFromCache` dedup branches
 * (src/map/mapcache.cpp:88-177).
 */
class MapCacheBasicTileTest {

    private lateinit var cache: MapCache

    @BeforeEach
    fun setUp() {
        cache = MapCache()
        cache.flush()
    }

    private fun simpleItem(id: Int): BasicItem = BasicItem().apply { this.id = id }

    private fun groundTile(groundId: Int, flags: Int = 0): BasicTile = BasicTile().apply {
        ground = simpleItem(groundId)
        this.flags = flags
    }

    private fun itemTile(itemId: Int, flags: Int = 0): BasicTile = BasicTile().apply {
        items.add(simpleItem(itemId))
        this.flags = flags
    }

    @Test
    fun `is empty until a flag ground or item is present`() {
        assertThat(BasicTile().isEmpty()).isTrue()
        assertThat(BasicTile().isEmpty(ignoreFlag = true)).isTrue()
        assertThat(BasicTile().apply { flags = 1 }.isEmpty()).isFalse()
        assertThat(BasicTile().apply { flags = 1 }.isEmpty(ignoreFlag = true)).isTrue()
        assertThat(BasicTile().apply { ground = simpleItem(1) }.isEmpty()).isFalse()
        assertThat(BasicTile().apply { ground = simpleItem(1) }.isEmpty(ignoreFlag = true)).isFalse()
        assertThat(BasicTile().apply { items.add(simpleItem(1)) }.isEmpty()).isFalse()
    }

    @Test
    fun `reports a house tile only with a house id`() {
        assertThat(BasicTile().isHouse()).isFalse()
        assertThat(BasicTile().apply { houseId = 1 }.isHouse()).isTrue()
    }

    @Test
    fun `is cache shareable unless it is a house or carries a non simple item`() {
        assertThat(BasicTile().isCacheShareable()).isTrue()
        assertThat(BasicTile().apply { houseId = 1 }.isCacheShareable()).isFalse()
        assertThat(BasicTile().apply { ground = simpleItem(1) }.isCacheShareable()).isTrue()
        assertThat(BasicTile().apply { ground = simpleItem(1).apply { actionId = 1 } }.isCacheShareable()).isFalse()
        assertThat(BasicTile().apply { items.add(simpleItem(1)) }.isCacheShareable()).isTrue()
        assertThat(
            BasicTile().apply {
                items.add(simpleItem(1).apply { actionId = 1 })
            }.isCacheShareable(),
        ).isFalse()
    }

    @Test
    fun `reset clears every field and the cached hash`() {
        val tile = BasicTile().apply {
            flags = 5
            houseId = 1
            type = 2
            isStatic = true
            ground = simpleItem(1)
            items.add(simpleItem(2))
        }
        val hashed = tile.hash()

        tile.reset()

        assertThat(tile.flags).isZero()
        assertThat(tile.houseId).isZero()
        assertThat(tile.type).isZero()
        assertThat(tile.isStatic).isFalse()
        assertThat(tile.ground).isNull()
        assertThat(tile.items).isEmpty()
        assertThat(tile.hash()).isEqualTo(BasicTile().hash())
        assertThat(tile.hash()).isNotEqualTo(hashed)
    }

    @Test
    fun `deduplicates ground only tiles by ground id`() {
        val first = cache.getOrCreateBasicTileFromCache(groundTile(4526))
        val second = cache.getOrCreateBasicTileFromCache(groundTile(4526))
        val other = cache.getOrCreateBasicTileFromCache(groundTile(4527))

        assertThat(second).isSameAs(first)
        assertThat(other).isNotSameAs(first)
    }

    @Test
    fun `separates flagged ground tiles from the flagless ground cache`() {
        val flagless = cache.getOrCreateBasicTileFromCache(groundTile(100))
        val flagged = cache.getOrCreateBasicTileFromCache(groundTile(100, flags = 1))
        val flaggedAgain = cache.getOrCreateBasicTileFromCache(groundTile(100, flags = 1))

        assertThat(flagged).isNotSameAs(flagless)
        assertThat(flaggedAgain).isSameAs(flagged)
    }

    @Test
    fun `deduplicates item only tiles by item id and separates the flagged cache`() {
        val flagless = cache.getOrCreateBasicTileFromCache(itemTile(200))
        val flaglessAgain = cache.getOrCreateBasicTileFromCache(itemTile(200))
        val flagged = cache.getOrCreateBasicTileFromCache(itemTile(200, flags = 1))
        val flaggedAgain = cache.getOrCreateBasicTileFromCache(itemTile(200, flags = 1))

        assertThat(flaglessAgain).isSameAs(flagless)
        assertThat(flagged).isNotSameAs(flagless)
        assertThat(flaggedAgain).isSameAs(flagged)
    }

    @Test
    fun `deduplicates ground and item tiles by flags ground id and item id`() {
        val first = BasicTile().apply {
            ground = simpleItem(100)
            items.add(simpleItem(200))
        }
        val same = BasicTile().apply {
            ground = simpleItem(100)
            items.add(simpleItem(200))
        }
        val otherItem = BasicTile().apply {
            ground = simpleItem(100)
            items.add(simpleItem(201))
        }
        val otherGround = BasicTile().apply {
            ground = simpleItem(101)
            items.add(simpleItem(200))
        }
        val flagged = BasicTile().apply {
            flags = 1
            ground = simpleItem(100)
            items.add(simpleItem(200))
        }

        val cached = cache.getOrCreateBasicTileFromCache(first)

        assertThat(cache.getOrCreateBasicTileFromCache(same)).isSameAs(cached)
        assertThat(cache.getOrCreateBasicTileFromCache(otherItem)).isNotSameAs(cached)
        assertThat(cache.getOrCreateBasicTileFromCache(otherGround)).isNotSameAs(cached)
        assertThat(cache.getOrCreateBasicTileFromCache(flagged)).isNotSameAs(cached)
    }

    @Test
    fun `keeps the shared instance across interleaved flagged lookups`() {
        val first = cache.getOrCreateBasicTileFromCache(groundTile(100, flags = 5))
        val other = cache.getOrCreateBasicTileFromCache(groundTile(100, flags = 9))
        val firstAgain = cache.getOrCreateBasicTileFromCache(groundTile(100, flags = 5))

        assertThat(firstAgain).isSameAs(first)
        assertThat(other).isNotSameAs(first)
    }

    @Test
    fun `shares tiles with equal contents through the generic hash cache`() {
        val first = BasicTile().apply {
            type = 2
            ground = simpleItem(100)
        }
        val same = BasicTile().apply {
            type = 2
            ground = simpleItem(100)
        }
        val other = BasicTile().apply {
            type = 3
            ground = simpleItem(100)
        }

        val cached = cache.getOrCreateBasicTileFromCache(first)

        assertThat(cache.getOrCreateBasicTileFromCache(same)).isSameAs(cached)
        assertThat(cache.getOrCreateBasicTileFromCache(other)).isNotSameAs(cached)
    }

    @Test
    fun `retains non shareable tiles separately`() {
        val house = BasicTile().apply { houseId = 7 }
        val houseAgain = BasicTile().apply { houseId = 7 }
        assertThat(cache.getOrCreateBasicTileFromCache(house))
            .isNotSameAs(cache.getOrCreateBasicTileFromCache(houseAgain))

        val custom = BasicTile().apply { items.add(simpleItem(1).apply { actionId = 1 }) }
        val customAgain = BasicTile().apply { items.add(simpleItem(1).apply { actionId = 1 }) }
        assertThat(cache.getOrCreateBasicTileFromCache(custom))
            .isNotSameAs(cache.getOrCreateBasicTileFromCache(customAgain))
    }

    @Test
    fun `deduplicates tiles with several items through the hash cache`() {
        val first = BasicTile().apply {
            ground = simpleItem(100)
            items.add(simpleItem(200))
            items.add(simpleItem(201))
        }
        val same = BasicTile().apply {
            ground = simpleItem(100)
            items.add(simpleItem(200))
            items.add(simpleItem(201))
        }
        val swapped = BasicTile().apply {
            ground = simpleItem(100)
            items.add(simpleItem(201))
            items.add(simpleItem(200))
        }

        val cached = cache.getOrCreateBasicTileFromCache(first)

        assertThat(cache.getOrCreateBasicTileFromCache(same)).isSameAs(cached)
        assertThat(cache.getOrCreateBasicTileFromCache(swapped)).isNotSameAs(cached)
    }
}
