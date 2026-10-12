package org.opentibia.canary.items

import org.opentibia.canary.creatures.Player
import org.opentibia.canary.map.Position

class MagicField(
    id: Int,
    val damage: Int
) : Item(id) {
    fun onStepIn(player: Player) {
        player.drainHealth(damage)
    }
}

class TeleportItem(
    id: Int,
    val destination: Position
) : Item(id) {
    fun onStepIn(player: Player) {
        player.position = destination
    }
}

class DoorItem(
    val closedId: Int,
    val openId: Int,
    var isOpen: Boolean = false
) : Item(if (isOpen) openId else closedId) {

    fun toggle() {
        isOpen = !isOpen
    }

    override val weight: Double
        get() = 0.0

    // Dynamic ID reflecting current open/closed visual state
    val currentId: Int
        get() = if (isOpen) openId else closedId
}
