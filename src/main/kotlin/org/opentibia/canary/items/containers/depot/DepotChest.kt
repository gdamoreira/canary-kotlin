package org.opentibia.canary.items.containers.depot

import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.game.CylinderFlags
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.Cylinder
import org.opentibia.canary.items.CylinderLink_t
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.systems.rewardchest.ReturnValue

/**
 * Port of `DepotChest` (src/items/containers/depot/depotchest.hpp:14-73,
 * depotchest.cpp:14-78).
 *
 * Constructor state mirrors `DepotChest(type): Container(type)` followed by
 * `maxDepotItems = 2000; maxSize = 32; pagination = true;` (depotchest.cpp:14-19).
 */
class DepotChest(
    id: Int,
    type: ItemType? = null,
) : Container(id, capacity = 32, type = type, pagination = true) {

    /** `DepotChest::maxDepotItems` (src/items/containers/depot/depotchest.hpp:72). */
    private var maxDepotItems: Int = DEFAULT_MAX_DEPOT_ITEMS

    /** `DepotChest::setMaxDepotItems` (depotchest.hpp:19-21). */
    fun setMaxDepotItems(maxitems: Int) {
        maxDepotItems = maxitems
    }

    /** `DepotChest::getDepotChest` (depotchest.hpp:29-38). */
    fun getDepotChest(): DepotChest = this

    /** `DepotChest::isDepotChest` (depotchest.hpp:52-54). */
    fun isDepotChest(): Boolean = true

    /** `DepotChest::queryAdd` (depotchest.cpp:21-56). */
    override fun queryAdd(index: Int, thing: Item, count: Int, flags: Int, actor: Creature?): ReturnValue {
        if (actor != null && thing.hasOwner() && !thing.isOwner(actor)) {
            return ReturnValue.ITEMISNOTYOURS
        }

        val skipLimit = (flags and CylinderFlags.FLAG_NOLIMIT) != 0
        if (!skipLimit) {
            var addCount = 0

            if (thing.isStackable() && thing.getItemCount() != count) {
                addCount = 1
            }

            if (thing.getTopParent() !== this) {
                val container = thing.getContainer()
                addCount = if (container != null) container.getItemHoldingCount() + 1 else 1
            }

            if (getRealParent() != null) {
                if ((getRealParent()?.getContainer()?.getItemHoldingCount() ?: 0) + addCount > maxDepotItems) {
                    return ReturnValue.DEPOTISFULL
                }
            } else if (getItemHoldingCount() + addCount > maxDepotItems) {
                return ReturnValue.DEPOTISFULL
            }
        }

        return super.queryAdd(index, thing, count, flags, actor)
    }

    /** `DepotChest::postAddNotification` (depotchest.cpp:58-63). */
    override fun postAddNotification(thing: Item, oldParent: Cylinder?, index: Int, link: CylinderLink_t) {
        getParent()?.postAddNotification(thing, oldParent, index, CylinderLink_t.LINK_PARENT)
    }

    /** `DepotChest::postRemoveNotification` (depotchest.cpp:65-70). */
    override fun postRemoveNotification(thing: Item, newParent: Cylinder?, index: Int, link: CylinderLink_t) {
        getParent()?.postRemoveNotification(thing, newParent, index, CylinderLink_t.LINK_PARENT)
    }

    /** `DepotChest::getParent` (depotchest.cpp:72-77). */
    override fun getParent(): Cylinder? {
        val parentLocked = super.getParent()
        if (parentLocked != null && parentLocked.getParent() != null) {
            return parentLocked.getParent()?.getParent()
        }
        return null
    }

    /** `DepotChest::getRealParent` (depotchest.hpp:65-67). */
    override fun getRealParent(): Cylinder? = super.getParent()

    /** `DepotChest::canRemove` (depotchest.hpp:57-59). */
    override fun canRemove(): Boolean = false

    /** `DepotChest::isRemoved` (depotchest.hpp:60-62). */
    override fun isRemoved(): Boolean = false

    companion object {
        /** `DepotChest::maxDepotItems` constructor default (depotchest.cpp:16). */
        const val DEFAULT_MAX_DEPOT_ITEMS = 2000

        /** `Container::maxSize` set by the DepotChest constructor (depotchest.cpp:17). */
        const val MAX_SIZE = 32

        /** `ITEM_DEPOT_NULL` (src/utils/utils_definitions.hpp:553). */
        const val ITEM_DEPOT_NULL = 22796

        /** `ITEM_DEPOT_XVIII` (src/utils/utils_definitions.hpp:576). */
        const val ITEM_DEPOT_XVIII = 31915

        /** `ITEM_DEPOT_XIX` (src/utils/utils_definitions.hpp:577). */
        const val ITEM_DEPOT_XIX = 39723

        /** `ITEM_DEPOT_XX` (src/utils/utils_definitions.hpp:578). */
        const val ITEM_DEPOT_XX = 39724

        /**
         * Town depot id to depot chest item id, ported from the `Player::getDepotChest`
         * selector (src/creatures/players/player.cpp:2007-2016): `depotId` 1..17 use
         * `ITEM_DEPOT_NULL + depotId`, 18 and 19 their Roman-numeral chests, and the
         * rest `ITEM_DEPOT_XX`.
         */
        fun depotChestItemId(depotId: Int): Int = when {
            depotId > 0 && depotId < 18 -> ITEM_DEPOT_NULL + depotId
            depotId == 18 -> ITEM_DEPOT_XVIII
            depotId == 19 -> ITEM_DEPOT_XIX
            else -> ITEM_DEPOT_XX
        }
    }
}
