package org.opentibia.canary.map

import org.opentibia.canary.creatures.spawns.SpawnsMonster
import org.opentibia.canary.creatures.spawns.SpawnsNpc
import org.opentibia.canary.game.zones.Zones
import org.opentibia.canary.items.Cylinder
import org.opentibia.canary.items.Item
import org.opentibia.canary.map.house.Houses
import kotlin.math.abs
import kotlin.math.max

data class Position(val x: Int, val y: Int, val z: Int) {
    fun getDistanceTo(other: Position): Int {
        if (z != other.z) return Int.MAX_VALUE
        return max(abs(x - other.x), abs(y - other.y))
    }

    fun isWithinRange(other: Position, rangeX: Int, rangeY: Int): Boolean {
        return z == other.z && abs(x - other.x) <= rangeX && abs(y - other.y) <= rangeY
    }
}

class Tile(val position: Position) : Cylinder {
    var ground: Item? = null
    private val _items = mutableListOf<Item>()
    val items: List<Item> get() = _items

    /** `HouseTile` marker; `0` for a plain tile. */
    var houseId: Int = 0

    /** `Tile::flags` (`TileFlags_t` bits, src/items/items_definitions.hpp:443-477). */
    var flags: Int = 0

    fun addItem(item: Item) {
        _items.add(item)
        item.parent = this
    }

    /** `Tile::internalAddThing` — insert bypassing the query checks. */
    override fun internalAddThing(item: Item) {
        _items.add(item)
        item.parent = this
    }

    /** `Tile::isHouse` (src/items/tile.hpp). */
    fun isHouse(): Boolean = houseId != 0

    /** `Tile::isEmpty` (src/items/tile.hpp) with the house check enabled. */
    fun isEmpty(): Boolean = ground == null && _items.isEmpty() && !isHouse()

    fun removeItem(item: Item): Boolean {
        val removed = _items.remove(item)
        if (removed) {
            item.parent = null
        }
        return removed
    }
}

class MapSector(val sectorX: Int, val sectorY: Int, val sectorZ: Int) {
    // 32x32 = 1024 flat tile array
    private val tiles = arrayOfNulls<Tile>(1024)

    fun getTile(localX: Int, localY: Int): Tile? {
        val index = (localY shl 5) or localX
        return tiles[index]
    }

    fun getOrCreateTile(globalPos: Position, localX: Int, localY: Int): Tile {
        val index = (localY shl 5) or localX
        val existing = tiles[index]
        if (existing != null) return existing

        val newTile = Tile(globalPos)
        tiles[index] = newTile
        return newTile
    }

    fun getAllTiles(): List<Tile> = tiles.filterNotNull()
}

class GameMap(
    val houses: Houses = Houses(),
    val zones: Zones = Zones(),
) {
    /** `Map::spawnsMonster` (`src/map/map.hpp:178`), loaded by `IOMap::loadMonsters`. */
    var spawnsMonster: SpawnsMonster = SpawnsMonster()

    /** `Map::spawnsNpc` (`src/map/map.hpp:179`), loaded by `IOMap::loadNpcs`. */
    var spawnsNpc: SpawnsNpc = SpawnsNpc()

    /** `Map::monsterfile` (`src/map/map.hpp:158`), resolved by `IOMap::loadMonsters`. */
    var monsterfile: String = ""

    /** `Map::npcfile` (`src/map/map.hpp:159`), resolved by `IOMap::loadNpcs`. */
    var npcfile: String = ""

    /** `Map::housefile` (`src/map/map.hpp:160`), resolved by `IOMap::loadHouses`. */
    var housefile: String = ""

    /** `Map::zonesfile` (`src/map/map.hpp:161`), resolved by `IOMap::loadZones`. */
    var zonesfile: String = ""

    /** `Map::path` (`src/map/map.hpp:146`), the loaded OTBM file identifier. */
    var path: String = ""

    // Sector key: (z.toLong() shl 32) or ((sectorX.toLong() and 0xFFFF) shl 16) or (sectorY.toLong() and 0xFFFF)
    private val sectors = mutableMapOf<Long, MapSector>()

    private fun getSectorKey(sx: Int, sy: Int, sz: Int): Long {
        return (sz.toLong() shl 32) or ((sx.toLong() and 0xFFFFL) shl 16) or (sy.toLong() and 0xFFFFL)
    }

    fun getOrCreateTile(position: Position): Tile {
        val sx = position.x shr 5
        val sy = position.y shr 5
        val key = getSectorKey(sx, sy, position.z)
        val sector = sectors.getOrPut(key) { MapSector(sx, sy, position.z) }
        return sector.getOrCreateTile(position, position.x and 0x1F, position.y and 0x1F)
    }

    fun getTile(position: Position): Tile? {
        val sx = position.x shr 5
        val sy = position.y shr 5
        val key = getSectorKey(sx, sy, position.z)
        val sector = sectors[key] ?: return null
        return sector.getTile(position.x and 0x1F, position.y and 0x1F)
    }

    fun getTilesInRange(center: Position, radiusX: Int, radiusY: Int): List<Tile> {
        val minX = center.x - radiusX
        val maxX = center.x + radiusX
        val minY = center.y - radiusY
        val maxY = center.y + radiusY

        val minSx = minX shr 5
        val maxSx = maxX shr 5
        val minSy = minY shr 5
        val maxSy = maxY shr 5

        val result = mutableListOf<Tile>()
        for (sx in minSx..maxSx) {
            for (sy in minSy..maxSy) {
                val sector = sectors[getSectorKey(sx, sy, center.z)] ?: continue
                for (tile in sector.getAllTiles()) {
                    if (tile.position.isWithinRange(center, radiusX, radiusY)) {
                        result.add(tile)
                    }
                }
            }
        }
        return result
    }
}
