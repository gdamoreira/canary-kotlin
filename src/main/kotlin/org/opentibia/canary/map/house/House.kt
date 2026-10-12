package org.opentibia.canary.map.house

import org.opentibia.canary.creatures.Player
import org.opentibia.canary.map.Position

data class HouseTile(
    val position: Position,
    val houseId: Int
)

class House(
    val id: Int,
    val name: String,
    var ownerId: Long = 0,
    var rent: Long = 0,
    val townId: Int = 0
) {
    private val guestList = mutableSetOf<String>()
    private val subOwnerList = mutableSetOf<String>()
    private val _tiles = mutableListOf<HouseTile>()

    val tiles: List<HouseTile> get() = _tiles

    fun addGuest(name: String) = guestList.add(name.lowercase())
    fun removeGuest(name: String) = guestList.remove(name.lowercase())

    fun addSubOwner(name: String) = subOwnerList.add(name.lowercase())
    fun removeSubOwner(name: String) = subOwnerList.remove(name.lowercase())

    fun addTile(position: Position): HouseTile {
        val tile = HouseTile(position, houseId = id)
        _tiles.add(tile)
        return tile
    }

    fun canEnter(player: Player): Boolean {
        if (ownerId != 0L && player.id == ownerId) return true
        val lowerName = player.name.lowercase()
        return subOwnerList.contains(lowerName) || guestList.contains(lowerName)
    }
}
