package org.opentibia.canary.systems.rewardchest

import org.opentibia.canary.items.Item

/**
 * Function-backed [RewardChestOwner] adapter.
 *
 * Canary's reward-chest engine reaches the owning [Player] through four concrete
 * members: `Player::getCapacity()`, `Player::sendCancelMessage(ReturnValue)`,
 * `Player::sendTextMessage(MessageType, text)` and
 * `Game::internalCollectManagedItems(player, item)`. The Kotlin [RewardChest] must
 * not depend on the `Player` type to stay testable, so this adapter exposes those
 * same four members as injected seams:
 *
 *  - `getCapacity()`                          -> [capacityProvider]
 *  - `sendCancelMessage(ReturnValue)`         -> [cancelMessageSink]
 *  - `sendTextMessage(text)`                  -> [textMessageSink]
 *  - `internalCollectManagedItems(item)`      -> [collectManagedItem]
 *
 * An existing `Player` can satisfy the adapter by supplying those members; the
 * adapter adds no gameplay behavior of its own (AGENTS.md 1.1).
 */
class PlayerRewardChestOwner(
    private val capacityProvider: () -> Double,
    private val cancelMessageSink: (ReturnValue) -> Unit,
    private val textMessageSink: (String) -> Unit,
    private val collectManagedItem: (Item) -> ReturnValue,
) : RewardChestOwner {

    override fun getCapacity(): Double = capacityProvider()

    override fun sendCancelMessage(message: ReturnValue) = cancelMessageSink(message)

    override fun sendTextMessage(text: String) = textMessageSink(text)

    override fun internalCollectManagedItems(item: Item): ReturnValue = collectManagedItem(item)
}
