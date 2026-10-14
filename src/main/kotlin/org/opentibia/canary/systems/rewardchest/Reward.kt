package org.opentibia.canary.systems.rewardchest

import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.Thing

/**
 * Kotlin port of Canary `class Reward final : public Container`.
 *
 * Source: ../.projects/canary/src/items/containers/rewards/reward.hpp
 *         ../.projects/canary/src/items/containers/rewards/reward.cpp
 *
 * Mirrors the constructor state (`Container(ITEM_REWARD_CONTAINER); maxSize = 32;
 * unlocked = false; pagination = true;`) through the shared [Container] port and
 * the `queryAdd` return-value contract plus the `canRemove() == true` override.
 * Because it is a real [Container], the load/save paths reach it through
 * `getContainer()`/`internalAddThing` exactly like Canary: the loaded children
 * are prepended (restoring the serialized ascending order) and every child gets
 * its parent link, and `serializeAttr` carries the `DATE` attribute set by
 * `Player::getReward` without a save-time patch.
 */
class Reward : Container(
    ITEM_REWARD_CONTAINER,
    capacity = MAX_SIZE,
    unlocked = false,
    pagination = true,
) {

    /** `Reward::canRemove()` (reward.hpp:36-38) overridden to always return true. */
    override fun canRemove(): Boolean = true

    /**
     * Port-call-site alias for `Container::empty` (`container.hpp:211-213`):
     * existing port callers read `reward.empty` while the [Container] port
     * exposes Canary's `empty()` function. Both read the same item list.
     */
    val empty: Boolean get() = itemlist.isEmpty()

    /**
     * `Reward::queryAdd` (reward.cpp:21-38). The index/count/flags parameters are
     * unused, exactly like the C++ override; a non-null `actor` or a non-item
     * `thing` is rejected. An item type that is not registered keeps the default
     * `ItemType` behavior (`isPickupable == true`), like `Item::items[id]`.
     */
    override fun queryAdd(index: Int, thing: Thing?, count: Int, flags: Int, actor: Creature?): ReturnValue {
        if (actor != null || thing == null) {
            return ReturnValue.NOTPOSSIBLE
        }

        val item = thing.getItem() ?: return ReturnValue.NOTPOSSIBLE
        if (item === this) {
            return ReturnValue.THISISIMPOSSIBLE
        }

        val pickupable = item.type?.isPickupable ?: true
        if (!pickupable) {
            return ReturnValue.CANNOTPICKUP
        }

        return ReturnValue.NOERROR
    }

    companion object {
        /** `ITEM_REWARD_CONTAINER` from utils/utils_definitions.hpp:616. */
        const val ITEM_REWARD_CONTAINER = 19202
        const val MAX_SIZE = 32
    }
}
