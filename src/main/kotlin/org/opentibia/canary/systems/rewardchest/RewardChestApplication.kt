package org.opentibia.canary.systems.rewardchest

import org.opentibia.canary.items.Item
import org.opentibia.canary.network.protocol.rewardchest.RewardChestCollectCommand

/**
 * Application service that applies a decoded [RewardChestCollectCommand] to a
 * [RewardChest].
 *
 * It transcribes the guard chain Canary runs between the wire decoder and the
 * collection engine:
 *
 *  - `ProtocolGame::parseRewardChestCollect`
 *    (src/server/network/protocol/protocolgame.cpp:4491) blocks the collect when
 *    `REWARD_CHEST_COLLECT_ENABLED` is false and passes
 *    `REWARD_CHEST_MAX_COLLECT_ITEMS` as `maxMoveItems`.
 *  - `Game::playerRewardChestCollect` (src/game/game.cpp:12296) rejects anything
 *    that is not an `ITEM_REWARD_CHEST` with `RETURNVALUE_NOTPOSSIBLE`.
 *  - `Game::collectRewardChestItems` (src/game/game.cpp:3898) moves the reward
 *    items and counts the ones the owner accepted (`movedRewardItems`).
 *
 * The command's position/stack-position identify and locate the chest in the
 * world; that world resolution happens before this service, which operates on
 * the already-resolved [chest]. The item id is still validated here because it
 * is part of the observed Canary guard.
 */
object RewardChestApplication {

    /**
     * Mirrors the `REWARD_CHEST_MAX_COLLECT_ITEMS` default
     * (src/config/configmanager.cpp:348). Callers should pass
     * `FeatureFlags.rewardChestMaxCollectItems` / `rewardChestCollectEnabled`.
     */
    const val DEFAULT_MAX_COLLECT_ITEMS: Int = 200

    /**
     * Applies [command] against [chest] on behalf of [owner].
     *
     * @param collectEnabled mirrors `REWARD_CHEST_COLLECT_ENABLED`; when false
     *   the request is dropped silently, exactly as Canary returns early.
     * @param maxCollectItems mirrors `REWARD_CHEST_MAX_COLLECT_ITEMS`; `0` means
     *   no limit, matching `Game::collectRewardChestItems`.
     * @return the number of reward items successfully collected, mirroring
     *   Canary's `movedRewardItems` counter.
     */
    fun apply(
        chest: RewardChest,
        owner: RewardChestOwner,
        command: RewardChestCollectCommand,
        collectEnabled: Boolean = true,
        maxCollectItems: Int = DEFAULT_MAX_COLLECT_ITEMS,
    ): Int {
        if (!collectEnabled) {
            return 0
        }

        if (command.itemId != RewardChest.ITEM_REWARD_CHEST) {
            owner.sendCancelMessage(ReturnValue.NOTPOSSIBLE)
            return 0
        }

        val counting = CountingRewardChestOwner(owner)
        chest.collectRewardChestItems(counting, maxCollectItems)
        return counting.collectedCount
    }

    /**
     * Delegates every [RewardChestOwner] call to the real owner while counting
     * the items the owner accepted, i.e. the Kotlin transcript of the C++
     * `if (internalCollectManagedItems(...) == RETURNVALUE_NOERROR) {
     * movedRewardItems++; }` counter (src/game/game.cpp:3922).
     */
    private class CountingRewardChestOwner(
        private val delegate: RewardChestOwner,
    ) : RewardChestOwner {
        var collectedCount: Int = 0
            private set

        override fun getCapacity(): Double = delegate.getCapacity()

        override fun sendCancelMessage(message: ReturnValue) = delegate.sendCancelMessage(message)

        override fun sendTextMessage(text: String) = delegate.sendTextMessage(text)

        override fun internalCollectManagedItems(item: Item): ReturnValue {
            val result = delegate.internalCollectManagedItems(item)
            if (result == ReturnValue.NOERROR) {
                collectedCount++
            }
            return result
        }
    }
}
