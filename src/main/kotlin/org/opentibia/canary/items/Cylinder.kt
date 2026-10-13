package org.opentibia.canary.items

import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.systems.rewardchest.ReturnValue

/**
 * `INDEX_WHEREEVER` (src/items/cylinder.hpp:18): `-1` means "add to an empty
 * position without a destination item".
 */
const val INDEX_WHEREEVER = -1

/** `CylinderLink_t` (src/items/items_definitions.hpp:500-505). */
enum class CylinderLink_t {
    LINK_OWNER,
    LINK_PARENT,
    LINK_TOPPARENT,
    LINK_NEAR,
}

/**
 * Port of the `Cylinder` insertion seam (src/items/cylinder.hpp:20-177).
 *
 * The C++ pure virtuals map to Kotlin defaults matching the base
 * `Cylinder::`/`VirtualCylinder::` bodies so non-game cylinders (and the
 * not-yet-ported `Tile` query checks) keep the Canary failure behavior.
 * `Thing` is not modelled yet, so item accessors take [Item] directly and the
 * creature/player accessors are interfaces seams (AGENTS.md 1.1).
 */
interface Cylinder {
    /** `Cylinder::internalAddThing` (src/items/cylinder.cpp:38-44, empty base). */
    fun internalAddThing(item: Item) {
        // Cylinder::internalAddThing is a no-op (cylinder.cpp:38-40).
    }

    /** `Thing::getParent` (src/items/thing.hpp:34). */
    fun getParent(): Cylinder? = null

    /** `Thing::getRealParent` (src/items/thing.hpp:37). */
    fun getRealParent(): Cylinder? = getParent()

    /** `Thing::isRemoved` (src/items/thing.hpp:85). */
    fun isRemoved(): Boolean = true

    /** `Cylinder::isBrowsefield` (src/items/cylinder.hpp:174-176). */
    fun isBrowsefield(): Boolean = false

    /** `Thing::getContainer` (src/items/thing.hpp:69). */
    fun getContainer(): Container? = null

    /** `Item::getItem` (src/items/item.hpp:236). */
    fun getItem(): Item? = null

    /** `Thing::getPlayer` (src/items/thing.hpp:63). */
    fun getPlayer(): Player? = null

    /** `Thing::getCreature` (src/items/thing.hpp:75). */
    fun getCreature(): Creature? = null

    /** `Cylinder::queryAdd` (src/items/cylinder.hpp:33); base returns not possible. */
    fun queryAdd(index: Int, thing: Item, count: Int, flags: Int, actor: Creature? = null): ReturnValue =
        ReturnValue.NOTPOSSIBLE

    /** `Cylinder::postAddNotification` (src/items/cylinder.hpp:109). */
    fun postAddNotification(
        thing: Item,
        oldParent: Cylinder?,
        index: Int,
        link: CylinderLink_t = CylinderLink_t.LINK_OWNER,
    ) {
        // VirtualCylinder::postAddNotification is a no-op (cylinder.hpp:202).
    }

    /** `Cylinder::postRemoveNotification` (src/items/cylinder.hpp:117). */
    fun postRemoveNotification(
        thing: Item,
        newParent: Cylinder?,
        index: Int,
        link: CylinderLink_t = CylinderLink_t.LINK_OWNER,
    ) {
        // VirtualCylinder::postRemoveNotification is a no-op (cylinder.hpp:203).
    }
}
