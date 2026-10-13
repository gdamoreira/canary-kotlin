package org.opentibia.canary.items.containers.depot

import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.io.PropStream
import org.opentibia.canary.items.AttrReadValue
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.Cylinder
import org.opentibia.canary.items.CylinderLink_t
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.Thing
import org.opentibia.canary.items.ItemAttribute
import org.opentibia.canary.items.containers.Inbox
import org.opentibia.canary.systems.rewardchest.ReturnValue

/**
 * Port of `DepotLocker` (src/items/containers/depot/depotlocker.hpp:15-50,
 * depotlocker.cpp:12-49).
 */
class DepotLocker(
    id: Int,
    size: Int,
) : Container(id, capacity = size) {

    /** `DepotLocker::depotId` (src/items/containers/depot/depotlocker.hpp:49). */
    private var depotId: Int = 0

    /** `DepotLocker::getDepotLocker` (depotlocker.hpp:19-21). */
    fun getDepotLocker(): DepotLocker = this

    /** `DepotLocker::removeInbox` (depotlocker.cpp:43-49). */
    fun removeInbox(inbox: Inbox) {
        itemlist.remove(inbox)
    }

    /**
     * `DepotLocker::getDepotId` (depotlocker.hpp:28-30).
     */
    fun getDepotId(): Int = depotId

    /** `DepotLocker::setDepotId` (depotlocker.hpp:31-33); the field is a `uint16_t`. */
    fun setDepotId(newDepotId: Int) {
        depotId = newDepotId and 0xFFFF
    }

    /**
     * `DepotLocker::readAttr` (depotlocker.cpp:15-23). C++ falls back to
     * `Item::readAttr` directly, so a locker only reads the depot id here; the
     * `ATTR_CONTAINER_ITEMS` terminator is handled by [Container.readAttr].
     */
    override fun readAttr(attr: Int, propStream: PropStream): AttrReadValue {
        if (attr == ATTR_DEPOT_ID) {
            val value = propStream.readU16() ?: return AttrReadValue.ATTR_READ_ERROR
            depotId = value
            return AttrReadValue.ATTR_READ_CONTINUE
        }
        return super.readAttr(attr, propStream)
    }

    /** `DepotLocker::queryAdd` (depotlocker.cpp:25-27). */
    override fun queryAdd(index: Int, thing: Thing?, count: Int, flags: Int, actor: Creature?): ReturnValue =
        ReturnValue.NOTENOUGHROOM

    /** `DepotLocker::postAddNotification` (depotlocker.cpp:29-34). */
    override fun postAddNotification(thing: Thing?, oldParent: Cylinder?, index: Int, link: CylinderLink_t) {
        getParent()?.postAddNotification(thing, oldParent, index, CylinderLink_t.LINK_PARENT)
    }

    /** `DepotLocker::postRemoveNotification` (depotlocker.cpp:36-41). */
    override fun postRemoveNotification(thing: Thing?, newParent: Cylinder?, index: Int, link: CylinderLink_t) {
        getParent()?.postRemoveNotification(thing, newParent, index, CylinderLink_t.LINK_PARENT)
    }

    /** `DepotLocker::canRemove` (depotlocker.hpp:41-43). */
    override fun canRemove(): Boolean = false

    /** `DepotLocker::isRemoved` (depotlocker.hpp:44-46). */
    override fun isRemoved(): Boolean = false

    companion object {
        /** `ITEM_LOCKER` (src/utils/utils_definitions.hpp:620). */
        const val ITEM_LOCKER = 3497

        /** `ITEM_MARKET` (src/utils/utils_definitions.hpp:622). */
        const val ITEM_MARKET = 12903

        /** `ITEM_STASH` (src/utils/utils_definitions.hpp:624). */
        const val ITEM_STASH = 28750

        /** `ATTR_DEPOT_ID` wire code (src/items/items_definitions.hpp, `AttrTypes_t`). */
        const val ATTR_DEPOT_ID = 10

        /** `ITEM_DEPOT` (src/utils/utils_definitions.hpp:619): the depot box container. */
        const val ITEM_DEPOT = 3502

        /** `DEPOT_BOXES` config default (configmanager.cpp:55). */
        const val DEPOT_BOXES = 20

        /**
         * `Player::getDepotLocker` construction order (player.cpp:2038-2055):
         * market item, inbox, optional stash and the `ITEM_DEPOT` box container
         * holding `depotBoxes` [DepotChest]s. `inbox` is the player's own inbox
         * instance, and `createStash` mirrors `!client->oldProtocol`.
         */
        fun create(depotId: Int, inbox: Inbox, depotBoxes: Int = DEPOT_BOXES, createStash: Boolean = true): DepotLocker {
            val locker = DepotLocker(ITEM_LOCKER, if (createStash) 4 else 3)
            locker.setDepotId(depotId)
            locker.internalAddThing(Item(ITEM_MARKET))
            locker.internalAddThing(inbox)
            if (createStash) {
                locker.internalAddThing(Item(ITEM_STASH))
            }
            val depotContainer = Container(ITEM_DEPOT, capacity = depotBoxes)
            for (boxId in 1..depotBoxes) {
                val depotBox = DepotChest(DepotChest.depotChestItemId(boxId))
                depotContainer.internalAddThing(depotBox)
                depotBox.setParent(depotContainer)
            }
            locker.internalAddThing(depotContainer)
            return locker
        }
    }
}
