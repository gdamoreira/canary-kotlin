package org.opentibia.canary.creatures.players

import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.Slots_t
import org.opentibia.canary.game.CylinderFlags
import org.opentibia.canary.items.Thing
import org.opentibia.canary.systems.rewardchest.ReturnValue

/**
 * The player-cylinder query producers of `Player::queryRemove`
 * (src/creatures/players/player.cpp:4865-4884) and its inventory lookup
 * `Player::getThingIndex` (player.cpp:5134-5142). The C++ `Player` is a
 * `Cylinder`; the Kotlin [Player] is not (PARITY GAP, `creatures/Creature.kt`
 * ownership), so the two operations are modelled as extension members over the
 * inventory slots and keep the exact C++ names and gate order.
 */

/**
 * `Player::getThingIndex(thing)` (player.cpp:5134-5142): the inventory slot
 * currently holding [thing], or `-1`.
 */
fun Player.getThingIndex(thing: Thing?): Int {
    if (thing == null) {
        return -1
    }

    for (slot in Slots_t.entries) {
        if (slot.value < Slots_t.FIRST || slot.value > Slots_t.LAST) {
            continue
        }
        if (getInventoryItem(slot) === thing) {
            return slot.value
        }
    }
    return -1
}

/**
 * `Player::queryRemove(thing, count, flags, actor)` (player.cpp:4865-4884):
 * the inventory index, stack-count and movable gates an inventory removal must
 * pass. The C++ `actor` parameter is unused there, kept for signature parity.
 */
fun Player.queryRemove(
    thing: Thing?,
    count: Int,
    flags: Int,
    actor: Creature? = null,
): ReturnValue {
    val index = getThingIndex(thing)
    if (index == -1) {
        return ReturnValue.NOTPOSSIBLE
    }

    val item = thing?.getItem()
    if (item == null) {
        return ReturnValue.NOTPOSSIBLE
    }

    if (count == 0 || (item.isStackable() && count > item.getItemCount())) {
        return ReturnValue.NOTPOSSIBLE
    }

    if (!item.isMovable() && (flags and CylinderFlags.FLAG_IGNORENOTMOVABLE) == 0) {
        return ReturnValue.NOTMOVABLE
    }

    return ReturnValue.NOERROR
}
