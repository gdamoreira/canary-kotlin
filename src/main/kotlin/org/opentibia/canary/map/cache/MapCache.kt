package org.opentibia.canary.map.cache

import io.github.oshai.kotlinlogging.KotlinLogging
import org.opentibia.canary.game.zones.Zone
import org.opentibia.canary.io.IOMapException
import org.opentibia.canary.items.ATTR_ACTION_ID
import org.opentibia.canary.items.ATTR_CHARGES
import org.opentibia.canary.items.ATTR_COUNT
import org.opentibia.canary.items.ATTR_DEPOT_ID
import org.opentibia.canary.items.ATTR_DESC
import org.opentibia.canary.items.ATTR_HOUSEDOORID
import org.opentibia.canary.items.ATTR_TELE_DEST
import org.opentibia.canary.items.ATTR_TEXT
import org.opentibia.canary.items.ATTR_UNIQUE_ID
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemAttribute
import org.opentibia.canary.items.Items
import org.opentibia.canary.items.containers.depot.DepotLocker
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.SpectatorSector
import org.opentibia.canary.map.Tile
import org.opentibia.canary.map.house.Houses

private val logger = KotlinLogging.logger {}

/** `MAP_MAX_LAYERS` (`src/map/map_const.hpp:17`). */
internal const val MAP_MAX_LAYERS: Int = 16

/** `kMaxItemCacheReserve` (src/map/mapcache.cpp:33). */
private const val MAX_ITEM_CACHE_RESERVE: Int = 131072

/** `kMaxGenericTileCacheReserve` (src/map/mapcache.cpp:34). */
private const val MAX_GENERIC_TILE_CACHE_RESERVE: Int = 262144

/** `kMaxFlaggedTileCacheReserve` (src/map/mapcache.cpp:35). */
private const val MAX_FLAGGED_TILE_CACHE_RESERVE: Int = 65536

/** `kMaxGroundAndItemTileCacheReserve` (src/map/mapcache.cpp:36). */
private const val MAX_GROUND_AND_ITEM_TILE_CACHE_RESERVE: Int = 262144

/** `kMaxMapSectorReserve` (src/map/mapcache.cpp:37). */
private const val MAX_MAP_SECTOR_RESERVE: Int = 1048576

/** `kMaxRetainedBasicTileReserve` (src/map/mapcache.cpp:38). */
private const val MAX_RETAINED_BASIC_TILE_RESERVE: Int = 2097152

/** `OTB::Node::END` (`src/io/fileloader.hpp:31`). */
private const val OTB_NODE_END: Int = 0xFF

/** `OTBM_ITEM` (`src/io/io_definitions.hpp:52-70`). */
private const val OTBM_ITEM: Int = 0x06

/**
 * `estimateCacheReserve` (src/map/mapcache.cpp:41-47): `fileSize / bytesPerEntry + 1`
 * capped at [maxReserve], or `0` for an empty file / zero entry size.
 */
internal fun estimateCacheReserve(fileSize: Long, bytesPerEntry: Long, maxReserve: Int): Int {
    if (fileSize == 0L || bytesPerEntry == 0L) {
        return 0
    }

    return minOf(fileSize / bytesPerEntry + 1, maxReserve.toLong()).toInt()
}

/** `estimateSectorReserve` (src/map/mapcache.cpp:49-53). */
internal fun estimateSectorReserve(width: Int, height: Int): Int {
    val sectorColumns = (width.toLong() + SECTOR_SIZE - 1) / SECTOR_SIZE
    val sectorRows = (height.toLong() + SECTOR_SIZE - 1) / SECTOR_SIZE
    return minOf(sectorColumns * sectorRows, MAX_MAP_SECTOR_RESERVE.toLong()).toInt()
}

/** `makeFlagsAndIdKey` (src/map/mapcache.cpp:55-57). */
internal fun makeFlagsAndIdKey(flags: Int, id: Int): Long =
    (flags.toLong() shl 16) or id.toLong()

/** `makeGroundAndItemKey` (src/map/mapcache.cpp:59-61). */
internal fun makeGroundAndItemKey(flags: Int, groundId: Int, itemId: Int): Long =
    (flags.toLong() shl 32) or (groundId.toLong() shl 16) or itemId.toLong()

/** `stdext::hash_int` (`src/utils/hash.hpp:21-26`). */
internal fun hashInt(x: Long): Long {
    var value = x
    value = value xor (value ushr 33)
    value *= 0xff51afd7ed558ccdUL.toLong()
    value = value xor (value ushr 33)
    return value
}

/** `stdext::hash_union` (`src/utils/hash.hpp:29-31`). */
internal fun hashUnion(seed: Long, h: Long): Long =
    seed xor (h + 0x9e3779b9L + (seed shl 6) + (seed ushr 2))

/** `stdext::hash_combine` integral overloads (`src/utils/hash.hpp:33-47`). */
internal fun hashCombine(seed: Long, v: Long): Long = hashUnion(seed, hashInt(v))

/**
 * Kotlin hides `java.util.HashMap.ensureCapacity` behind the mapped collection
 * type, so the C++ `reserve` hint is applied by rebuilding the map at the
 * requested capacity with every entry preserved. An equal or smaller capacity
 * is a no-op, like `reserve` on a map that already fits.
 */
private fun <K, V> ensureCapacity(map: java.util.HashMap<K, V>, capacity: Int): java.util.HashMap<K, V> {
    if (capacity <= map.size) {
        return map
    }

    val resized = java.util.HashMap<K, V>(capacity)
    resized.putAll(map)
    return resized
}

/**
 * Seam over the node-aware `FileStream` surface
 * (src/io/filestream.hpp:19-139) that `BasicItem::readAttr` /
 * `unserializeItemNode` consume. The io-owned byte stream port will implement
 * this; the test utility mirrors the escape/node semantics.
 */
interface MapItemStream {
    fun getU8(): Int
    fun getU16(): Int
    fun getString(): String
    fun isProp(prop: Int): Boolean
    fun back()
    fun startNode(): Boolean
    fun endNode(): Boolean
}

/**
 * Port of `struct BasicItem` (src/map/mapcache.hpp:26-76,
 * src/map/mapcache.cpp:474-585).
 *
 * The C++ `std::vector<std::shared_ptr<BasicItem>>` is modelled as a non-null
 * list; null entries are never produced by the parser. `text` hashing uses the
 * JVM string hash instead of `phmap::Hash<std::string>` (equal strings still
 * hash equally, which is all the cache dedup relies on).
 */
class BasicItem {
    var text: String = ""

    var id: Int = 0

    var charges: Int = 0
    var actionId: Int = 0
    var uniqueId: Int = 0
    var destX: Int = 0
    var destY: Int = 0
    var doorOrDepotId: Int = 0

    var destZ: Int = 0

    val items: MutableList<BasicItem> = mutableListOf()

    private var cachedHash: Long = 0
    private var cachedHashValid: Boolean = false

    /** `BasicItem::isSimple` (mapcache.hpp:45-55). */
    fun isSimple(): Boolean =
        charges == 0 &&
            actionId == 0 &&
            uniqueId == 0 &&
            destX == 0 &&
            destY == 0 &&
            destZ == 0 &&
            doorOrDepotId == 0 &&
            text.isEmpty() &&
            items.isEmpty()

    /** `BasicItem::hash` (mapcache.hpp:57-67, mapcache.cpp:474-492). */
    fun hash(): Long {
        if (cachedHashValid) {
            return cachedHash
        }

        val h = computeHash()
        cachedHash = h
        cachedHashValid = true
        return cachedHash
    }

    private fun computeHash(): Long {
        var h = 0L
        val attributes = arrayOf(id, charges, actionId, uniqueId, destX, destY, destZ, doorOrDepotId)
        for (value in attributes) {
            if (value > 0) {
                h = hashCombine(h, value.toLong())
            }
        }

        if (text.isNotEmpty()) {
            h = hashUnion(h, text.hashCode().toLong())
        }

        if (items.isNotEmpty()) {
            h = hashCombine(h, items.size.toLong())
            for (item in items) {
                h = hashCombine(h, item.hash())
            }
        }

        return h
    }

    /**
     * `BasicItem::unserializeItemNode` (mapcache.cpp:494-529). The C++
     * container-reserve hint (`kInitialParsedContainerItemReserve`) has no
     * Kotlin `std::vector` equivalent and is omitted; it does not affect the
     * parsed tree.
     */
    fun unserializeItemNode(stream: MapItemStream, x: Int, y: Int, z: Int): Boolean {
        if (stream.isProp(OTB_NODE_END)) {
            stream.back()
            return true
        }

        readAttr(stream)

        while (stream.startNode()) {
            if (stream.getU8() != OTBM_ITEM) {
                throw IOMapException("[x:$x, y:$y, z:$z] Could not read item node.")
            }

            val streamId = stream.getU16()

            val item = BasicItem()
            item.id = streamId

            if (!item.unserializeItemNode(stream, x, y, z)) {
                throw IOMapException("[x:$x, y:$y, z:$z] Failed to load item.")
            }

            val cachedItem = if (item.isSimple()) {
                MapCache.staticGetBasicItemFromCache(streamId)
            } else {
                MapCache.staticTryGetItemFromCache(item)
            }
            items.add(requireNotNull(cachedItem))

            if (!stream.endNode()) {
                throw IOMapException("[x:$x, y:$y, z:$z] Could not end node.")
            }
        }

        return true
    }

    /** `BasicItem::readAttr` (mapcache.cpp:531-585). */
    fun readAttr(stream: MapItemStream) {
        var end = false
        while (!end) {
            when (stream.getU8()) {
                ATTR_DEPOT_ID -> doorOrDepotId = stream.getU16()

                ATTR_HOUSEDOORID -> doorOrDepotId = stream.getU8()

                ATTR_TELE_DEST -> {
                    destX = stream.getU16()
                    destY = stream.getU16()
                    destZ = stream.getU8()
                }

                ATTR_COUNT -> charges = stream.getU8()

                ATTR_CHARGES -> charges = stream.getU16()

                ATTR_ACTION_ID -> actionId = stream.getU16()

                ATTR_UNIQUE_ID -> uniqueId = stream.getU16()

                ATTR_TEXT -> {
                    val text = stream.getString()
                    if (text.isNotEmpty()) {
                        this.text = text
                    }
                }

                ATTR_DESC -> stream.getString()

                else -> {
                    stream.back()
                    end = true
                }
            }
        }
    }
}

/**
 * Port of `struct BasicTile` (src/map/mapcache.hpp:78-141,
 * src/map/mapcache.cpp:454-472).
 */
class BasicTile {
    var ground: BasicItem? = null
    val items: MutableList<BasicItem> = mutableListOf()

    var flags: Int = 0
    var houseId: Int = 0
    var type: Int = 0

    var isStatic: Boolean = false

    internal var retainedByMapCacheOwner: MapCache? = null

    private var cachedHash: Long = 0
    private var cachedHashValid: Boolean = false

    /** `BasicTile::reset` (mapcache.hpp:87-97). */
    fun reset() {
        ground = null
        items.clear()
        flags = 0
        houseId = 0
        type = 0
        isStatic = false
        retainedByMapCacheOwner = null
        cachedHash = 0
        cachedHashValid = false
    }

    /** `BasicTile::isEmpty` (mapcache.hpp:99-101). */
    fun isEmpty(ignoreFlag: Boolean = false): Boolean =
        (ignoreFlag || flags == 0) && ground == null && items.isEmpty()

    /** `BasicTile::isHouse` (mapcache.hpp:103-105). */
    fun isHouse(): Boolean = houseId != 0

    /** `BasicTile::isCacheShareable` (mapcache.hpp:107-119). */
    fun isCacheShareable(): Boolean {
        if (isHouse()) {
            return false
        }

        val currentGround = ground
        if (currentGround != null && !currentGround.isSimple()) {
            return false
        }

        return items.none { !it.isSimple() }
    }

    /** `BasicTile::hash` (mapcache.hpp:121-131, mapcache.cpp:454-472). */
    fun hash(): Long {
        if (cachedHashValid) {
            return cachedHash
        }

        val h = computeHash()
        cachedHash = h
        cachedHashValid = true
        return cachedHash
    }

    private fun computeHash(): Long {
        var h = 0L
        val attributes = arrayOf(flags, houseId, type, if (isStatic) 1 else 0)
        for (value in attributes) {
            if (value > 0) {
                h = hashCombine(h, value.toLong())
            }
        }

        ground?.let {
            h = hashCombine(h, it.hash())
        }

        if (items.isNotEmpty()) {
            h = hashCombine(h, items.size.toLong())
            for (item in items) {
                h = hashCombine(h, item.hash())
            }
        }

        return h
    }

    /**
     * The implicit `BasicTile(const BasicTile&)` copy constructor; it copies
     * the private cache markers exactly like the C++ implicit copy.
     */
    internal fun copyOf(): BasicTile {
        val copy = BasicTile()
        copy.ground = ground
        copy.items.addAll(items)
        copy.flags = flags
        copy.houseId = houseId
        copy.type = type
        copy.isStatic = isStatic
        copy.retainedByMapCacheOwner = retainedByMapCacheOwner
        copy.cachedHash = cachedHash
        copy.cachedHashValid = cachedHashValid
        return copy
    }
}

/** `struct MapCacheFloorCursor` (src/map/mapcache.hpp:143-148). */
class MapCacheFloorCursor {
    var valid: Boolean = false
    var sectorIndex: Int = 0
    var z: Int = 0
    var floor: Floor? = null
}

/**
 * Port of `struct Floor` (src/map/utils/mapsector.hpp:24-86) holding the
 * materialized tile grid and the `BasicTile` observer cache for one sector
 * layer.
 *
 * The C++ `std::shared_mutex` guards cross-thread reads; the Kotlin world is
 * single-thread confined (AGENTS.md 1.2), so the port has no lock.
 */
class Floor(private val z: Int) {

    /** `Floor::TileAndCache` (mapsector.hpp:28-31). */
    data class TileAndCache(val tile: Tile?, val cachedTile: BasicTile?)

    private val tiles: Array<Array<Tile?>> = Array(SECTOR_SIZE) { arrayOfNulls(SECTOR_SIZE) }
    private val tileCache: Array<Array<BasicTile?>> = Array(SECTOR_SIZE) { arrayOfNulls(SECTOR_SIZE) }

    /** `Floor::getTile` (mapsector.hpp:36-39). */
    fun getTile(x: Int, y: Int): Tile? = tiles[x and SECTOR_MASK][y and SECTOR_MASK]

    /** `Floor::getTileAndCache` (mapsector.hpp:43-51). */
    fun getTileAndCache(x: Int, y: Int): TileAndCache = TileAndCache(
        tile = tiles[x and SECTOR_MASK][y and SECTOR_MASK],
        cachedTile = tileCache[x and SECTOR_MASK][y and SECTOR_MASK],
    )

    /** `Floor::setTile` (mapsector.hpp:53-55). */
    fun setTile(x: Int, y: Int, tile: Tile?) {
        tiles[x and SECTOR_MASK][y and SECTOR_MASK] = tile
    }

    /** `Floor::getTileCache` (mapsector.hpp:57-60). */
    fun getTileCache(x: Int, y: Int): BasicTile? = tileCache[x and SECTOR_MASK][y and SECTOR_MASK]

    /** `Floor::setTileCache` (mapsector.hpp:62-64). */
    fun setTileCache(x: Int, y: Int, newTile: BasicTile?) {
        tileCache[x and SECTOR_MASK][y and SECTOR_MASK] = newTile
    }

    /** `Floor::getTiles` (mapsector.hpp:66-69). */
    fun getTiles(): Array<Array<Tile?>> = tiles

    /** `Floor::getZ` (mapsector.hpp:71-73). */
    fun getZ(): Int = z
}

/**
 * Port of `class MapSector` (src/map/utils/mapsector.hpp:88-154,
 * src/map/utils/mapsector.cpp:18-102): floors, revisions, the neighbour links
 * `getBestMapSector` maintains, and the creature/player/monster/npc lists
 * inherited from [SpectatorSector] (`MapSector::creature_list` etc.,
 * mapsector.hpp:136-142).
 */
class MapSector : SpectatorSector() {

    private val floors: Array<Floor?> = arrayOfNulls(MAP_MAX_LAYERS)
    private val topologyRevisions: LongArray = LongArray(MAP_MAX_LAYERS) { 1L }
    private val occupancyRevisions: LongArray = LongArray(MAP_MAX_LAYERS) { 1L }

    /** `MapSector::createFloor` (mapsector.hpp:97-107). */
    fun createFloor(z: Int): Floor? {
        if (z >= MAP_MAX_LAYERS) {
            logger.error { "Attempt to create floor on invalid coordinate: $z" }
            return null
        }

        var floor = floors[z]
        if (floor == null) {
            floor = Floor(z)
            floors[z] = floor
        }
        return floor
    }

    /** `MapSector::getFloor` (mapsector.hpp:109-116). */
    fun getFloor(z: Int): Floor? {
        if (z >= MAP_MAX_LAYERS) {
            logger.error { "Attempt to get floor on invalid coordinate: $z" }
            return null
        }
        return floors[z]
    }

    /** `MapSector::markTopologyChanged` (mapsector.cpp:41-43). */
    fun markTopologyChanged(z: Int): Long = incrementRevision(topologyRevisions, z)

    /** `MapSector::markOccupancyChanged` (mapsector.cpp:45-47). */
    fun markOccupancyChanged(z: Int): Long = incrementRevision(occupancyRevisions, z)

    /** `MapSector::getTopologyRevision` (mapsector.hpp:125-127). */
    fun getTopologyRevision(z: Int): Long = if (z < MAP_MAX_LAYERS) topologyRevisions[z] else 0

    /** `MapSector::getOccupancyRevision` (mapsector.hpp:129-131). */
    fun getOccupancyRevision(z: Int): Long = if (z < MAP_MAX_LAYERS) occupancyRevisions[z] else 0

    private fun incrementRevision(revisions: LongArray, z: Int): Long {
        if (z >= MAP_MAX_LAYERS) {
            return 0
        }

        val current = revisions[z]
        val next = if (current == -1L) 1L else current + 1L
        revisions[z] = next
        return next
    }

    companion object {
        /** `MapSector::newSector` (mapsector.cpp:18). */
        var newSector: Boolean = false
    }
}

/**
 * Port of `class MapCache` (src/map/mapcache.hpp:150-205,
 * src/map/mapcache.cpp).
 *
 * The C++ file-scope item/tile caches are `MapCache` companion state exactly
 * like the C++ statics, so every instance shares them and `flush()` clears
 * them. `Floor`/`MapSector` mirror `src/map/utils/mapsector.hpp` until the
 * map-core port (owned by another agent) unifies the world-facing sector type.
 * Cross-object hooks the C++ code reaches through `static_cast<Map*>` /
 * `g_game()` are constructor seams or open methods:
 *
 * - [markNavigationTopologyChanged] is `Map::markNavigationTopologyChanged`;
 *   override it to invalidate the navigation epoch/snapshots.
 * - [zones] replaces `Zone::getZones(pos)` in the tile materialization commit.
 * - [startDecaying] replaces `Item::startDecaying()`.
 * - [houses] replaces `map->houses`.
 */
open class MapCache(
    private val houses: Houses = Houses(),
    private val zones: (position: Position) -> List<Zone> = { emptyList() },
    private val startDecaying: (item: Item) -> Unit = {},
    private val itemTypes: Items? = null,
) {

    /** `MapCache::mapSectors` (mapcache.hpp:187). */
    protected var mapSectors: java.util.HashMap<Int, MapSector> = java.util.HashMap()

    /**
     * `MapCache::retainedBasicTiles` (mapcache.hpp:196-197): the shared owners
     * that keep the `Floor` observer pointers valid after `flush()`.
     */
    private val retainedBasicTiles: java.util.ArrayList<BasicTile> = java.util.ArrayList()

    private var lastFlaggedGroundTileKey: Long = 0
    private var lastFlaggedGroundTile: BasicTile? = null
    private var lastFlaggedItemTileKey: Long = 0
    private var lastFlaggedItemTile: BasicTile? = null
    private var lastGroundAndItemTileKey: Long = 0
    private var lastGroundAndItemTile: BasicTile? = null

    /** `MapCache::getOrCreateBasicTileFromCache` (mapcache.cpp:88-177). */
    internal fun getOrCreateBasicTileFromCache(ref: BasicTile): BasicTile {
        if (!ref.isHouse() && ref.type == 0 && !ref.isStatic) {
            val ground = ref.ground
            if (ground != null && ground.isSimple() && ref.items.isEmpty()) {
                if (ref.flags == 0) {
                    val cachedTile = groundOnlyTiles[ground.id]
                    if (cachedTile == null) {
                        val tile = ref.copyOf()
                        groundOnlyTiles[ground.id] = tile
                        return retainBasicTile(tile)
                    }

                    return cachedTile
                }

                val key = makeFlagsAndIdKey(ref.flags, ground.id)
                lastFlaggedGroundTile?.let {
                    if (lastFlaggedGroundTileKey == key) {
                        return it
                    }
                }

                flaggedGroundTiles[key]?.let {
                    lastFlaggedGroundTileKey = key
                    lastFlaggedGroundTile = it
                    return it
                }

                val tile = ref.copyOf()
                lastFlaggedGroundTileKey = key
                lastFlaggedGroundTile = retainBasicTile(tile)
                flaggedGroundTiles[key] = tile
                return requireNotNull(lastFlaggedGroundTile)
            }

            if (ground == null && ref.items.size == 1 && ref.items[0].isSimple()) {
                val itemId = ref.items[0].id
                if (ref.flags == 0) {
                    val cachedTile = itemOnlyTiles[itemId]
                    if (cachedTile == null) {
                        val tile = ref.copyOf()
                        itemOnlyTiles[itemId] = tile
                        return retainBasicTile(tile)
                    }

                    return cachedTile
                }

                val key = makeFlagsAndIdKey(ref.flags, itemId)
                lastFlaggedItemTile?.let {
                    if (lastFlaggedItemTileKey == key) {
                        return it
                    }
                }

                flaggedItemTiles[key]?.let {
                    lastFlaggedItemTileKey = key
                    lastFlaggedItemTile = it
                    return it
                }

                val tile = ref.copyOf()
                lastFlaggedItemTileKey = key
                lastFlaggedItemTile = retainBasicTile(tile)
                flaggedItemTiles[key] = tile
                return requireNotNull(lastFlaggedItemTile)
            }

            if (ground != null && ground.isSimple() && ref.items.size == 1 && ref.items[0].isSimple()) {
                val key = makeGroundAndItemKey(ref.flags, ground.id, ref.items[0].id)
                lastGroundAndItemTile?.let {
                    if (lastGroundAndItemTileKey == key) {
                        return it
                    }
                }

                groundAndItemTiles[key]?.let {
                    lastGroundAndItemTileKey = key
                    lastGroundAndItemTile = it
                    return it
                }

                val tile = ref.copyOf()
                lastGroundAndItemTileKey = key
                lastGroundAndItemTile = retainBasicTile(tile)
                groundAndItemTiles[key] = tile
                return requireNotNull(lastGroundAndItemTile)
            }
        }

        if (!ref.isCacheShareable()) {
            return retainBasicTile(ref.copyOf())
        }

        val hash = ref.hash()
        tiles[hash]?.let {
            return it
        }

        val tile = ref.copyOf()
        tiles[hash] = tile
        return retainBasicTile(tile)
    }

    /** `MapCache::reserveForMap` (mapcache.cpp:179-188). */
    fun reserveForMap(width: Int, height: Int, fileSize: Long) {
        items = ensureCapacity(items, estimateCacheReserve(fileSize, 512, MAX_ITEM_CACHE_RESERVE))
        tiles = ensureCapacity(tiles, estimateCacheReserve(fileSize, 192, MAX_GENERIC_TILE_CACHE_RESERVE))
        flaggedGroundTiles = ensureCapacity(
            flaggedGroundTiles,
            estimateCacheReserve(fileSize, 4096, MAX_FLAGGED_TILE_CACHE_RESERVE),
        )
        flaggedItemTiles = ensureCapacity(
            flaggedItemTiles,
            estimateCacheReserve(fileSize, 4096, MAX_FLAGGED_TILE_CACHE_RESERVE),
        )
        groundAndItemTiles = ensureCapacity(
            groundAndItemTiles,
            estimateCacheReserve(fileSize, 192, MAX_GROUND_AND_ITEM_TILE_CACHE_RESERVE),
        )
        retainedBasicTiles.ensureCapacity(
            estimateCacheReserve(fileSize, 64, MAX_RETAINED_BASIC_TILE_RESERVE),
        )
        mapSectors = ensureCapacity(mapSectors, estimateSectorReserve(width, height))
    }

    /** `MapCache::flush` (mapcache.cpp:190-206). */
    fun flush() {
        items.clear()
        tiles.clear()
        flaggedGroundTiles.clear()
        flaggedItemTiles.clear()
        groundAndItemTiles.clear()
        resetBasicTileLookupCache()
        simpleItems.fill(null)
        groundOnlyTiles.fill(null)
        itemOnlyTiles.fill(null)
    }

    /** `MapCache::resetBasicTileLookupCache` (mapcache.cpp:208-215). */
    private fun resetBasicTileLookupCache() {
        lastFlaggedGroundTileKey = 0
        lastFlaggedGroundTile = null
        lastFlaggedItemTileKey = 0
        lastFlaggedItemTile = null
        lastGroundAndItemTileKey = 0
        lastGroundAndItemTile = null
    }

    /** `MapCache::retainBasicTile` (mapcache.cpp:217-224). */
    private fun retainBasicTile(tile: BasicTile): BasicTile {
        if (tile.retainedByMapCacheOwner !== this) {
            tile.retainedByMapCacheOwner = this
            retainedBasicTiles.add(tile)
        }

        return tile
    }

    /** `MapCache::parseItemAttr` (mapcache.cpp:226-258). */
    private fun parseItemAttr(basicItem: BasicItem, item: Item) {
        if (basicItem.charges > 0) {
            item.setSubType(basicItem.charges)
        }

        if (basicItem.actionId > 0) {
            item.setAttribute(ItemAttribute.ACTIONID, basicItem.actionId.toLong())
        }

        if (basicItem.uniqueId > 0) {
            // `Item::addUniqueId` also registers the unique id with `g_game()`;
            // the registry is not modelled (PARITY GAP).
            item.setUniqueId(basicItem.uniqueId.toLong())
        }

        val teleport = item.getTeleport()
        if (teleport != null && (basicItem.destX != 0 || basicItem.destY != 0 || basicItem.destZ != 0)) {
            teleport.setDestPos(Position(basicItem.destX, basicItem.destY, basicItem.destZ))
        }

        val door = item.getDoor()
        if (door != null && basicItem.doorOrDepotId != 0) {
            door.setDoorId(basicItem.doorOrDepotId)
        }

        // C++ `item->getContainer()->getDepotLocker()`; only `DepotLocker`
        // overrides the virtual accessor, so the Kotlin cast is the seam.
        (item.getContainer() as? DepotLocker)?.let {
            if (basicItem.doorOrDepotId != 0) {
                it.setDepotId(basicItem.doorOrDepotId)
            }
        }

        if (basicItem.text.isNotEmpty()) {
            item.setAttribute(ItemAttribute.TEXT, basicItem.text)
        }
    }

    /**
     * `MapCache::createItem` (mapcache.cpp:260-288). The `position` argument of
     * the C++ `Item::CreateItem(id, position)` has no Kotlin item field yet
     * (PARITY GAP); [org.opentibia.canary.items.Container.addItem] already folds
     * the C++ `updateItemWeight` call.
     */
    @Suppress("UNUSED_PARAMETER")
    private fun createItem(basicItem: BasicItem, position: Position): Item {
        val item = Item.create(basicItem.id, registry = itemTypes)
        parseItemAttr(basicItem, item)

        val container = item.getContainer()
        if (container != null && basicItem.items.isNotEmpty()) {
            for (basicItemInside in basicItem.items) {
                container.addItem(createItem(basicItemInside, position))
            }
        }

        if (item.getItemCount() == 0) {
            item.setItemCount(1)
        }

        if (item.canDecay()) {
            startDecaying(item)
        }
        item.loadedFromMap = true
        item.decayDisabled = (item.type?.decayTo ?: -1) != -1

        return item
    }

    /**
     * `MapCache::getOrCreateTileFromCache` (mapcache.cpp:290-364). C++
     * `Tile::safeCall` and the `HouseTile`/`StaticTile`/`DynamicTile` split are
     * not modelled: the world is single-thread confined (AGENTS.md 1.2) and the
     * Kotlin [Tile] merges the storage variants, so the commit runs inline on a
     * plain [Tile].
     */
    internal fun getOrCreateTileFromCache(floor: Floor, x: Int, y: Int): Tile? {
        val (oldTile, cachedTile) = floor.getTileAndCache(x, y)
        if (cachedTile == null) {
            return oldTile
        }

        val z = floor.getZ()
        val oldCreatureList = oldTile?.getCreatures()?.toList() ?: emptyList()
        val position = Position(x, y, z)

        val tile = Tile(position)
        if (cachedTile.isHouse()) {
            val house = houses.getHouse(cachedTile.houseId)
            if (house == null) {
                logger.error { "[MapCache::getOrCreateTileFromCache] house not found for houseId ${cachedTile.houseId}" }
                return oldTile
            }
            tile.houseId = cachedTile.houseId
            tile.house = house
        }

        cachedTile.ground?.let {
            tile.internalAddThing(createItem(it, position))
        }

        for (basicItem in cachedTile.items) {
            tile.internalAddThing(createItem(basicItem, position))
        }

        tile.setFlag(cachedTile.flags)

        tile.house?.addTile(position)
        for (creature in oldCreatureList) {
            tile.internalAddThing(creature)
        }

        for (zone in zones(position)) {
            tile.addZone(zone)
        }

        floor.setTile(x, y, tile)
        markNavigationTopologyChanged(position)

        // Remove Tile from cache
        floor.setTileCache(x, y, null)

        return tile
    }

    /** `MapCache::setBasicTile` (mapcache.cpp:366-369). */
    fun setBasicTile(x: Int, y: Int, z: Int, newTile: BasicTile) {
        setBasicTile(x, y, z, newTile, MapCacheFloorCursor())
    }

    /** `MapCache::setBasicTile` with cursor (mapcache.cpp:371-404). */
    fun setBasicTile(x: Int, y: Int, z: Int, newTile: BasicTile, floorCursor: MapCacheFloorCursor) {
        if (z >= MAP_MAX_LAYERS) {
            logger.error { "Attempt to set tile on invalid coordinate: ${Position(x, y, z)}" }
            return
        }

        val tile = getOrCreateBasicTileFromCache(newTile)
        val sectorIndex = (x / SECTOR_SIZE) or ((y / SECTOR_SIZE) shl 16)
        var floor: Floor? = null

        if (floorCursor.valid && floorCursor.sectorIndex == sectorIndex && floorCursor.z == z) {
            floor = floorCursor.floor
        }

        if (floor == null) {
            val cachedFloor = getMapSector(x, y)?.createFloor(z) ?: getBestMapSector(x, y).createFloor(z)

            floorCursor.valid = true
            floorCursor.sectorIndex = sectorIndex
            floorCursor.z = z
            floorCursor.floor = cachedFloor
            floor = cachedFloor
        }

        if (floor != null) {
            floor.setTileCache(x, y, tile)
            markNavigationTopologyChanged(Position(x, y, z))
        }
    }

    /** `MapCache::tryReplaceItemFromCache` (mapcache.cpp:406-408). */
    fun tryReplaceItemFromCache(ref: BasicItem?): BasicItem? = staticTryGetItemFromCache(ref)

    /** `MapCache::getBasicItemFromCache` (mapcache.cpp:410-412). */
    fun getBasicItemFromCache(id: Int): BasicItem = staticGetBasicItemFromCache(id)

    /** `MapCache::createMapSector` (mapcache.cpp:414-423). */
    fun createMapSector(x: Int, y: Int): MapSector {
        val index = (x / SECTOR_SIZE) or ((y / SECTOR_SIZE) shl 16)
        mapSectors[index]?.let {
            return it
        }

        MapSector.newSector = true
        val sector = MapSector()
        mapSectors[index] = sector
        return sector
    }

    /** `MapCache::getBestMapSector` (mapcache.cpp:425-452). */
    fun getBestMapSector(x: Int, y: Int): MapSector {
        MapSector.newSector = false
        val sector = createMapSector(x, y)

        if (MapSector.newSector) {
            // update north sector
            getMapSector(x, y - SECTOR_SIZE)?.let { it.sectorS = sector }

            // update west sector
            getMapSector(x - SECTOR_SIZE, y)?.let { it.sectorE = sector }

            // update south sector
            getMapSector(x, y + SECTOR_SIZE)?.let { sector.sectorS = it }

            // update east sector
            getMapSector(x + SECTOR_SIZE, y)?.let { sector.sectorE = it }
        }

        return sector
    }

    /** `MapCache::getMapSector` (mapcache.hpp:174-182). */
    open fun getMapSector(x: Int, y: Int): MapSector? =
        mapSectors[(x / SECTOR_SIZE) or ((y / SECTOR_SIZE) shl 16)]

    /**
     * `Map::markNavigationTopologyChanged` (src/map/map.cpp:540-544), the hook
     * `MapCache::setBasicTile`/`getOrCreateTileFromCache` reach through
     * `static_cast<Map*>(this)`. The map-core port overrides it to also
     * invalidate the navigation epoch and snapshots.
     */
    open fun markNavigationTopologyChanged(position: Position) {
        getMapSector(position.x, position.y)?.markTopologyChanged(position.z)
    }

    companion object {
        /** `items` file-scope cache (src/map/mapcache.cpp:65). */
        private var items: java.util.HashMap<Long, BasicItem> = java.util.HashMap()

        /** `tiles` file-scope cache (src/map/mapcache.cpp:66). */
        private var tiles: java.util.HashMap<Long, BasicTile> = java.util.HashMap()

        /** `flaggedGroundTiles` file-scope cache (src/map/mapcache.cpp:67). */
        private var flaggedGroundTiles: java.util.HashMap<Long, BasicTile> = java.util.HashMap()

        /** `flaggedItemTiles` file-scope cache (src/map/mapcache.cpp:68). */
        private var flaggedItemTiles: java.util.HashMap<Long, BasicTile> = java.util.HashMap()

        /** `groundAndItemTiles` file-scope cache (src/map/mapcache.cpp:69). */
        private var groundAndItemTiles: java.util.HashMap<Long, BasicTile> = java.util.HashMap()

        /** `simpleItems` file-scope cache (src/map/mapcache.cpp:70). */
        private val simpleItems: Array<BasicItem?> = arrayOfNulls(0x10000)

        /** `groundOnlyTiles` file-scope cache (src/map/mapcache.cpp:71). */
        private val groundOnlyTiles: Array<BasicTile?> = arrayOfNulls(0x10000)

        /** `itemOnlyTiles` file-scope cache (src/map/mapcache.cpp:72). */
        private val itemOnlyTiles: Array<BasicTile?> = arrayOfNulls(0x10000)

        /** `static_tryGetItemFromCache` (mapcache.cpp:74-76). */
        internal fun staticTryGetItemFromCache(ref: BasicItem?): BasicItem? {
            if (ref == null) {
                return null
            }

            return items.getOrPut(ref.hash()) { ref }
        }

        /** `static_getBasicItemFromCache` (mapcache.cpp:78-86). */
        internal fun staticGetBasicItemFromCache(id: Int): BasicItem {
            var cachedItem = simpleItems[id]
            if (cachedItem == null) {
                cachedItem = BasicItem()
                cachedItem.id = id
                simpleItems[id] = cachedItem
            }

            return cachedItem
        }
    }
}
