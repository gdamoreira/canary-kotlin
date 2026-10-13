package org.opentibia.canary.items

import org.opentibia.canary.creatures.Player
import org.opentibia.canary.map.Position

class MagicField(
    id: Int,
    damage: Int,
    var replaceable: Boolean = true,
    type: ItemType? = null,
) : Item(id, type = type) {

    private val damageValue: Int = damage

    /** `MagicField::getMagicField` (src/creatures/combat/combat.cpp:2787-2789). */
    override fun getMagicField(): MagicField = this

    /** `MagicField::isReplaceable` (src/creatures/combat/combat.cpp:2790-2792). */
    fun isReplaceable(): Boolean = replaceable

    /** `MagicField::getDamage` (src/creatures/combat/combat.cpp:2799-2805). */
    fun getDamage(): Int = damageValue

    /**
     * Mirrors Canary MagicField::onStepInField(creature)
     * src/creatures/combat/combat.hpp:369
     */
    fun onStepInField(player: Player) {
        player.drainHealth(damageValue)
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
