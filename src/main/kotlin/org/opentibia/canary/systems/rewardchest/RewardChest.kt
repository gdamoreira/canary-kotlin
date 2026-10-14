package org.opentibia.canary.systems.rewardchest

import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemAttribute

/**
 * Small contract for the owning Player, mirroring exactly the Canary members the
 * reward-chest code paths use. The real `org.opentibia.canary.creatures.Player`
 * must not be edited for this port, so callers supply an implementation of these
 * already-existing Canary methods:
 *
 *  - `Player::getCapacity()`                          src/creatures/players/player.hpp
 *  - `Player::sendCancelMessage(ReturnValue)`         src/creatures/players/player.cpp:2117
 *  - `Player::sendTextMessage(MessageType, text)`     src/creatures/players/player.hpp
 *  - `Game::internalCollectManagedItems(player,item)` src/game/game.cpp:3850
 */
interface RewardChestOwner {
    fun getCapacity(): Double

    fun sendCancelMessage(message: ReturnValue)

    fun sendTextMessage(text: String)

    /**
     * Moves a reward item into the player's managed inventory. Named after the
     * Canary `Game::internalCollectManagedItems` primitive whose success value
     * drives the collection counter.
     */
    fun internalCollectManagedItems(item: Item): ReturnValue
}

/**
 * Kotlin port of Canary `class RewardChest final : public Container` plus the
 * reward ownership previously held on `Player` (`rewardMap`) and the collection
 * algorithm from `Game::collectRewardChestItems`.
 *
 * Source: ../.projects/canary/src/items/containers/rewards/rewardchest.hpp
 *         ../.projects/canary/src/items/containers/rewards/rewardchest.cpp
 *         ../.projects/canary/src/creatures/players/player.cpp:2058
 *         ../.projects/canary/src/game/game.cpp:3898
 *
 * Constructor state mirrors `RewardChest(type): Container(type) { maxSize = 32;
 * unlocked = false; pagination = true; }` and `canRemove() == false`.
 *
 * PARITY GAP: unlike [Reward] this proxy is still modeled on top of `Item`
 * instead of `Container`, so the chest is not a `Cylinder`: the `Reward`
 * children of `getReward` receive the DATE attribute and the Canary prepend
 * order, but the chest cannot be their parent (the C++ `internalAddItem(chest,
 * reward, ...)` would set `reward->parent` to the chest). The child links inside
 * each [Reward] are reachable and set.
 */
class RewardChest(type: Int = ITEM_REWARD_CHEST) : Item(type) {

    val maxSize: Int = MAX_SIZE
    val unlocked: Boolean = false
    val pagination: Boolean = true

    /** Mirrors `Container::itemlist` (holds [Reward] proxy containers). */
    private val itemlist = mutableListOf<Item>()

    /** Mirrors `Player::rewardMap` (`std::map<uint64_t, std::shared_ptr<Reward>>`). */
    private val rewardMap = linkedMapOf<Long, Reward>()

    val items: List<Item> get() = itemlist
    val size: Int get() = itemlist.size
    val empty: Boolean get() = itemlist.isEmpty()

    /** Mirrors `RewardChest::canRemove()` overridden to always return false. */
    val canRemove: Boolean = false

    /**
     * Mirrors `Player::getReward(rewardId, autoCreate)` (player.cpp:2067-2083).
     *
     * Returns the tracked reward, or creates one when [autoCreate] is true:
     * `reward->setAttribute(ItemAttribute_t::DATE, rewardId)` is applied before
     * the proxy is added, and the add reaches the chest's item list through the
     * `Game::internalAddItem`/`Container::addThing` path, which prepends. Uses
     * the reward map as the source of truth.
     */
    fun getReward(rewardId: Long, autoCreate: Boolean): Reward? {
        rewardMap[rewardId]?.let { return it }
        if (!autoCreate) {
            return null
        }

        val reward = Reward()
        reward.setAttribute(ItemAttribute.DATE, rewardId)
        rewardMap[rewardId] = reward
        itemlist.add(0, reward)
        return reward
    }

    /** Mirrors `Player::removeReward(rewardId)`. */
    fun removeReward(rewardId: Long) {
        rewardMap.remove(rewardId)
    }

    /** Mirrors `Player::getRewardList(rewards)`. */
    fun getRewardList(): List<Long> = rewardMap.keys.toList()

    /**
     * Mirrors `RewardChest::removeItem(thing)`: removes the reward proxy from the
     * chest item list and resets its parent. Used when an empty reward is dropped.
     */
    fun removeItem(item: Item?) {
        if (item == null) {
            return
        }
        itemlist.remove(item)
    }

    /**
     * Mirrors `Player::getRewardsFromContainer(container)` for this chest: flattens
     * every nested `ITEM_REWARD_CONTAINER` proxy into the actual loot items.
     */
    fun getRewardsFromContainer(): List<Item> = flattenRewardItems(itemlist)

    private fun flattenRewardItems(source: List<Item>): List<Item> {
        val rewardItems = mutableListOf<Item>()
        for (item in source) {
            if (item.id == Reward.ITEM_REWARD_CONTAINER && item is Reward) {
                rewardItems.addAll(flattenRewardItems(item.items))
            } else {
                rewardItems.add(item)
            }
        }
        return rewardItems
    }

    /**
     * Faithful port of `Game::collectRewardChestItems(player, maxMoveItems)`.
     *
     * Moves reward items into the owner, stopping early when the owner runs out of
     * capacity or when `maxMoveItems` items have been collected. Return values and
     * the two informational messages match the C++ implementation exactly.
     */
    fun collectRewardChestItems(owner: RewardChestOwner, maxMoveItems: Int = 0): ReturnValue {
        if (empty) {
            return ReturnValue.REWARDCHESTISEMPTY
        }

        val rewardItemsVector = getRewardsFromContainer()
        val rewardCount = rewardItemsVector.size
        var movedRewardItems = 0

        for (item in rewardItemsVector) {
            // Stop if player does not have free capacity.
            if (owner.getCapacity() < item.weight) {
                owner.sendCancelMessage(ReturnValue.NOTENOUGHCAPACITY)
                break
            }

            // Limit the collect count only when "maxMoveItems" is not "0".
            val limitMove = maxMoveItems != 0 && movedRewardItems == maxMoveItems
            if (limitMove) {
                owner.sendTextMessage(
                    "You can only collect $maxMoveItems items at a time. " +
                        "$movedRewardItems of $rewardCount objects were picked up."
                )
                return ReturnValue.NOERROR
            }

            if (owner.internalCollectManagedItems(item) == ReturnValue.NOERROR) {
                movedRewardItems++
            }
        }

        owner.sendTextMessage("$movedRewardItems of $rewardCount objects were picked up.")

        if (movedRewardItems == 0) {
            return ReturnValue.NOTPOSSIBLE
        }

        return ReturnValue.NOERROR
    }

    companion object {
        /** `ITEM_REWARD_CHEST` from utils/utils_definitions.hpp:617. */
        const val ITEM_REWARD_CHEST = 19250
        const val MAX_SIZE = 32
    }
}
