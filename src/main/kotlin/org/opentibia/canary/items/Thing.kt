package org.opentibia.canary.items

import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.Tile

/**
 * Port of `class Thing` (src/items/thing.hpp:22-92, src/items/thing.cpp:14-28).
 *
 * The C++ const/non-const overload pairs collapse to a single Kotlin member and
 * `getPosition()`/`getTile()` become the [position] property plus [getTile], the
 * codebase's existing accessor shape. `getDepotChest()` has no Kotlin owner and
 * is not modelled (PARITY GAP).
 */
interface Thing {
    /** `Thing::getDescription(int32_t lookDistance)`. */
    fun getDescription(lookDistance: Int): String

    /**
     * `Thing::getPosition()` (thing.cpp:14-28): the parent tile position, or the
     * `Tile::nullptr_tile` position when the thing has no tile.
     */
    val position: Position
        get() = getTile()?.position ?: Tile.nullptrTilePosition

    /** `Thing::getParent()`. */
    fun getParent(): Cylinder? = null

    /** `Thing::getRealParent()` defaults to [getParent]. */
    fun getRealParent(): Cylinder? = getParent()

    /** `Thing::setParent(std::weak_ptr<Cylinder>)`; the base body is a no-op. */
    fun setParent(cylinder: Cylinder?) {}

    /** `Thing::getTile()`. */
    fun getTile(): Tile? = null

    /** `Thing::getThrowRange()`. */
    fun getThrowRange(): Int

    /** `Thing::isPushable()`. */
    fun isPushable(): Boolean

    /** `Thing::getPlayer()`; base returns `nullptr`. */
    fun getPlayer(): Player? = null

    /** `Thing::getContainer()`; base returns `nullptr`. */
    fun getContainer(): Container? = null

    /** `Thing::getItem()`; base returns `nullptr`. */
    fun getItem(): Item? = null

    /** `Thing::getCreature()`; base returns `nullptr`. */
    fun getCreature(): Creature? = null

    /** `Thing::getCylinder()`; base returns `nullptr`. */
    fun getCylinder(): Cylinder? = null

    /** `Thing::isRemoved()`; base returns `true`. */
    fun isRemoved(): Boolean = true
}
