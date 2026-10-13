package org.opentibia.canary.map

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

