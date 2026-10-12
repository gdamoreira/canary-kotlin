package org.opentibia.canary.map

import org.opentibia.canary.items.Item
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

class Tile(val position: Position) {
    var ground: Item? = null
    private val _items = mutableListOf<Item>()
    val items: List<Item> get() = _items

    fun addItem(item: Item) {
        _items.add(item)
    }

    fun removeItem(item: Item): Boolean {
        return _items.remove(item)
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

class GameMap {
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
