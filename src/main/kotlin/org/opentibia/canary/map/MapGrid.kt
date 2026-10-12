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

class GameMap {
    private val tiles = mutableMapOf<Position, Tile>()

    fun getOrCreateTile(position: Position): Tile {
        return tiles.getOrPut(position) { Tile(position) }
    }

    fun getTile(position: Position): Tile? = tiles[position]

    fun getTilesInRange(center: Position, radiusX: Int, radiusY: Int): List<Tile> {
        return tiles.values.filter { tile ->
            tile.position.isWithinRange(center, radiusX, radiusY)
        }
    }
}
