package org.opentibia.canary.items.containers

import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.game.CylinderFlags
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.Cylinder
import org.opentibia.canary.items.CylinderLink_t
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.Thing
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.systems.rewardchest.ReturnValue

/**
 * Port of `Inbox` (src/items/containers/inbox/inbox.hpp:14-56,
 * inbox.cpp:16-77).
 *
 * Constructor state mirrors `Inbox(type): Container(type, 30, false, true)`
 * with `maxInboxItems = g_configManager().getNumber(MAX_INBOX_ITEMS)` or
 * `UINT32_MAX` when the config is `0` (inbox.cpp:16-20).
 */
class Inbox(
    id: Int,
    type: ItemType? = null,
    maxInboxItemsConfig: Int = MAX_INBOX_ITEMS,
) : Container(id, capacity = 30, type = type, unlocked = false, pagination = true) {

    /** `Inbox::maxInboxItems` (src/items/containers/inbox/inbox.hpp:55); `uint32_t`. */
    private var maxInboxItems: Long = if (maxInboxItemsConfig != 0) maxInboxItemsConfig.toLong() else UINT32_MAX

    /** `Inbox::setMaxInboxItems` (inbox.hpp:18-20). */
    fun setMaxInboxItems(maxitems: Int) {
        maxInboxItems = maxitems.toLong()
    }

    /** `Inbox::getMaxInboxItems` (inbox.hpp:22-24). */
    fun getMaxInboxItems(): Long = maxInboxItems

    /** `Inbox::getRemainingItemCapacity` (inbox.hpp:26-29). */
    fun getRemainingItemCapacity(): Long {
        val currentItems = getItemHoldingCount().toLong()
        return if (currentItems >= maxInboxItems) 0 else maxInboxItems - currentItems
    }

    /** `Inbox::queryAdd` (inbox.cpp:22-55). */
    override fun queryAdd(index: Int, thing: Thing?, count: Int, flags: Int, actor: Creature?): ReturnValue {
        val item = thing?.getItem() ?: return ReturnValue.NOTPOSSIBLE
        var addCount = 0

        if ((flags and CylinderFlags.FLAG_NOLIMIT) == 0) {
            return ReturnValue.CONTAINERNOTENOUGHROOM
        }

        if (item === getItem()) {
            return ReturnValue.THISISIMPOSSIBLE
        }

        if (!item.isPickupable()) {
            return ReturnValue.CANNOTPICKUP
        }

        if (item.getTopParent() !== this) {
            val container = item.getContainer()
            addCount = if (container != null) container.getItemHoldingCount() + 1 else 1
        }

        if (getItemHoldingCount() + addCount > maxInboxItems) {
            return ReturnValue.DEPOTISFULL
        }

        return ReturnValue.NOERROR
    }

    /** `Inbox::postAddNotification` (inbox.cpp:57-62). */
    override fun postAddNotification(thing: Thing?, oldParent: Cylinder?, index: Int, link: CylinderLink_t) {
        getParent()?.postAddNotification(thing, oldParent, index, CylinderLink_t.LINK_PARENT)
    }

    /** `Inbox::postRemoveNotification` (inbox.cpp:64-69). */
    override fun postRemoveNotification(thing: Thing?, newParent: Cylinder?, index: Int, link: CylinderLink_t) {
        getParent()?.postRemoveNotification(thing, newParent, index, CylinderLink_t.LINK_PARENT)
    }

    /** `Inbox::isInbox` (inbox.hpp:37-39). */
    override fun isInbox(): Boolean = true

    /** `Inbox::canRemove` (inbox.hpp:42-44). */
    override fun canRemove(): Boolean = false

    /** `Inbox::isRemoved` (inbox.hpp:45-47). */
    override fun isRemoved(): Boolean = false

    /** `Inbox::getParent` (inbox.cpp:71-77). */
    override fun getParent(): Cylinder? {
        val parentLocked = super.getParent() ?: return null
        return parentLocked.getParent()
    }

    /** `Inbox::getRealParent` (inbox.hpp:50-52). */
    override fun getRealParent(): Cylinder? = super.getParent()

    companion object {
        /** `MAX_INBOX_ITEMS` config default (configmanager.cpp:311). */
        const val MAX_INBOX_ITEMS = 0

        /** `std::numeric_limits<uint32_t>::max()` (inbox.cpp:19). */
        const val UINT32_MAX = 0xFFFFFFFFL

        /** `ITEM_INBOX` (src/utils/utils_definitions.hpp:621). */
        const val ITEM_INBOX = 12902
    }
}
