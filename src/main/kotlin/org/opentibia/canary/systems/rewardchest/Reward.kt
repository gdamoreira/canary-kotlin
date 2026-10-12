package org.opentibia.canary.systems.rewardchest

import org.opentibia.canary.items.Item

/**
 * Kotlin port of Canary `class Reward final : public Container`.
 *
 * Source: /Users/damoreira/.projects/canary/src/items/containers/rewards/reward.hpp
 *         /Users/damoreira/.projects/canary/src/items/containers/rewards/reward.cpp
 *
 * Mirrors the constructor state (`Container(ITEM_REWARD_CONTAINER); maxSize = 32;
 * unlocked = false; pagination = true;`), the `queryAdd` return-value contract and
 * the `canRemove() == true` override. The item list mirrors `Container::itemlist`.
 *
 * Note: the shared Kotlin `org.opentibia.canary.items.Container` is final for the
 * operations this port needs (its backing list is private and read-only), so Reward
 * is modeled directly on top of `Item` while reproducing Container's item-list
 * semantics. This is recorded as a parity gap.
 */
class Reward() : Item(ITEM_REWARD_CONTAINER) {

    val maxSize: Int = MAX_SIZE
    val unlocked: Boolean = false
    val pagination: Boolean = true

    private val itemlist = mutableListOf<Item>()

    val items: List<Item> get() = itemlist
    val size: Int get() = itemlist.size
    val empty: Boolean get() = itemlist.isEmpty()

    /** Mirrors `Container::canRemove()` overridden to always return true. */
    val canRemove: Boolean = true

    /**
     * Mirrors `Reward::queryAdd`. Attribute `ItemAttribute_t::DATE` is modeled by
     * [date]; the reward id is stored there by the owner (see [RewardChest.getReward]).
     */
    var date: Long = 0L

    /** Mirrors `Container::addItem`. Returns false when the container is full. */
    fun addItem(item: Item): Boolean {
        if (itemlist.size >= maxSize) {
            return false
        }
        itemlist.add(item)
        return true
    }

    /** Mirrors removing an item from `Container::itemlist` and resetting its parent. */
    fun removeItem(item: Item?) {
        if (item == null) {
            return
        }
        itemlist.remove(item)
    }

    /**
     * Mirrors `Reward::queryAdd(int32_t, thing, count, flags, actor)`.
     * Only the argument-independent branches are observable here.
     */
    fun queryAdd(actor: Any?, thing: Item?): ReturnValue {
        if (actor != null || thing == null) {
            return ReturnValue.NOTPOSSIBLE
        }

        if (thing === this) {
            return ReturnValue.THISISIMPOSSIBLE
        }

        val pickupable = thing.type?.isPickupable ?: true
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
