package org.opentibia.canary.systems.rewardchest

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.protocol.rewardchest.RewardChestCollectCommand

/**
 * Transcript tests for applying a parsed reward-chest collect command.
 *
 * Source of truth: /Users/damoreira/.projects/canary
 *  - src/server/network/protocol/protocolgame.cpp:4491 `parseRewardChestCollect`
 *    gates on `REWARD_CHEST_COLLECT_ENABLED` and passes
 *    `REWARD_CHEST_MAX_COLLECT_ITEMS` as `maxMoveItems`.
 *  - src/game/game.cpp:12296 `Game::playerRewardChestCollect` validates the
 *    resolved item is an `ITEM_REWARD_CHEST` and delegates to
 *    `Game::collectRewardChestItems`.
 *  - src/game/game.cpp:3898 `Game::collectRewardChestItems` (movedRewardItems).
 */
class RewardChestApplicationTest {

    private class TestOwner(
        private val capacity: Double = 400.0,
    ) : RewardChestOwner {
        val collected = mutableListOf<Item>()
        val cancelMessages = mutableListOf<ReturnValue>()
        val textMessages = mutableListOf<String>()
        var collectResult: ReturnValue = ReturnValue.NOERROR
        val sourceRewards = mutableListOf<Reward>()

        override fun getCapacity(): Double = capacity

        override fun sendCancelMessage(message: ReturnValue) {
            cancelMessages.add(message)
        }

        override fun sendTextMessage(text: String) {
            textMessages.add(text)
        }

        override fun internalCollectManagedItems(item: Item): ReturnValue {
            if (collectResult != ReturnValue.NOERROR) {
                return collectResult
            }
            collected.add(item)
            sourceRewards.forEach { it.removeItem(item) }
            return ReturnValue.NOERROR
        }
    }

    private fun command(itemId: Int = RewardChest.ITEM_REWARD_CHEST) = RewardChestCollectCommand(
        position = Position(100, 100, 7),
        itemId = itemId,
        stackPosition = 0,
    )

    private fun chestWith(vararg items: Item): Pair<RewardChest, Reward> {
        val chest = RewardChest()
        val reward = chest.getReward(1_700_000_000L, true)!!
        items.forEach { reward.addItem(it) }
        return chest to reward
    }

    @Test
    fun `collect returns the number of reward items moved`() {
        val (chest, reward) = chestWith(Item(2376), Item(2385))
        val owner = TestOwner()
        owner.sourceRewards.add(reward)

        val collected = RewardChestApplication.apply(chest, owner, command())

        assertThat(collected).isEqualTo(2)
        assertThat(owner.collected).hasSize(2)
        assertThat(reward.empty).isTrue()
    }

    @Test
    fun `collect honors the max collect items limit`() {
        val (chest, reward) = chestWith(Item(3000), Item(3001), Item(3002))
        val owner = TestOwner()
        owner.sourceRewards.add(reward)

        val collected = RewardChestApplication.apply(chest, owner, command(), maxCollectItems = 2)

        assertThat(collected).isEqualTo(2)
        assertThat(owner.collected).hasSize(2)
        assertThat(reward.items).hasSize(1)
    }

    @Test
    fun `max collect items of zero means unlimited`() {
        val (chest, reward) = chestWith(Item(3000), Item(3001), Item(3002))
        val owner = TestOwner()
        owner.sourceRewards.add(reward)

        val collected = RewardChestApplication.apply(chest, owner, command(), maxCollectItems = 0)

        assertThat(collected).isEqualTo(3)
        assertThat(reward.empty).isTrue()
    }

    @Test
    fun `disabled collect config moves nothing and returns zero`() {
        val (chest, reward) = chestWith(Item(2376))
        val owner = TestOwner()
        owner.sourceRewards.add(reward)

        val collected = RewardChestApplication.apply(chest, owner, command(), collectEnabled = false)

        assertThat(collected).isEqualTo(0)
        assertThat(owner.collected).isEmpty()
        assertThat(reward.items).hasSize(1)
    }

    @Test
    fun `command targeting a non reward chest is cancelled and collects nothing`() {
        val (chest, reward) = chestWith(Item(2376))
        val owner = TestOwner()
        owner.sourceRewards.add(reward)

        val collected = RewardChestApplication.apply(chest, owner, command(itemId = 2376))

        assertThat(collected).isEqualTo(0)
        assertThat(owner.collected).isEmpty()
        assertThat(owner.cancelMessages).containsExactly(ReturnValue.NOTPOSSIBLE)
    }

    @Test
    fun `empty reward chest collects nothing`() {
        val chest = RewardChest()
        val owner = TestOwner()

        val collected = RewardChestApplication.apply(chest, owner, command())

        assertThat(collected).isEqualTo(0)
        assertThat(owner.collected).isEmpty()
        assertThat(owner.textMessages).isEmpty()
    }

    @Test
    fun `collect stops at capacity and reports the partial count`() {
        val heavy = Item(id = 2376, type = ItemType(id = 2376, name = "Sword", weight = 50.0))
        val (chest, reward) = chestWith(heavy)
        val owner = TestOwner(capacity = 0.0)
        owner.sourceRewards.add(reward)

        val collected = RewardChestApplication.apply(chest, owner, command())

        assertThat(collected).isEqualTo(0)
        assertThat(owner.cancelMessages).containsExactly(ReturnValue.NOTENOUGHCAPACITY)
        assertThat(owner.textMessages).containsExactly("0 of 1 objects were picked up.")
    }

    @Test
    fun `collect counts only items accepted by the owner`() {
        val (chest, reward) = chestWith(Item(2376))
        val owner = TestOwner()
        owner.sourceRewards.add(reward)
        owner.collectResult = ReturnValue.NOTPOSSIBLE

        val collected = RewardChestApplication.apply(chest, owner, command())

        assertThat(collected).isEqualTo(0)
        assertThat(owner.collected).isEmpty()
        assertThat(reward.items).hasSize(1)
    }
}
