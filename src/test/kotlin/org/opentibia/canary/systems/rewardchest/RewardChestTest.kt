package org.opentibia.canary.systems.rewardchest

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemAttribute
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.map.Position

/**
 * Transcript tests for the Canary Boss Reward Chest engine core.
 *
 * Source of truth: /Users/damoreira/.projects/canary
 *  - src/items/containers/rewards/reward.hpp/.cpp
 *  - src/items/containers/rewards/rewardchest.hpp/.cpp
 *  - src/creatures/players/player.cpp (Player::getReward/getRewardChest/removeReward/getRewardList)
 *  - src/game/game.cpp (Game::collectRewardChestItems)
 */
class RewardChestTest {

    /**
     * Minimal stand-in for the owning Player. Mirrors the exact C++ Player/Game
     * members the reward-chest collection touches: getCapacity(),
     * sendCancelMessage(ReturnValue), sendTextMessage(text) and
     * internalCollectManagedItems(item).
     */
    private class TestRewardChestOwner(
        private val capacity: Double = 400.0
    ) : RewardChestOwner {
        val collected = mutableListOf<Item>()
        val cancelMessages = mutableListOf<ReturnValue>()
        val textMessages = mutableListOf<String>()
        var collectResult: ReturnValue = ReturnValue.NOERROR
        var sourceRewards: List<Reward> = emptyList()

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

    @Test
    fun `reward mirrors Canary container identity capacity and flags`() {
        val reward = Reward()

        assertThat(reward.id).isEqualTo(19202)
        assertThat(reward.capacity).isEqualTo(32)
        assertThat(reward.isUnlocked()).isFalse()
        assertThat(reward.hasPagination()).isTrue()
        assertThat(reward.canRemove()).isTrue()
        assertThat(reward.empty()).isTrue()
        assertThat(reward.getContainer()).isSameAs(reward)
    }

    @Test
    fun `reward chest mirrors Canary identity capacity and is not removable`() {
        val chest = RewardChest()

        assertThat(chest.id).isEqualTo(19250)
        assertThat(chest.maxSize).isEqualTo(32)
        assertThat(chest.unlocked).isFalse()
        assertThat(chest.pagination).isTrue()
        assertThat(chest.canRemove).isFalse()
        assertThat(chest.empty).isTrue()
    }

    @Test
    fun `getReward auto creates and tracks reward container in chest`() {
        val chest = RewardChest()

        val reward = chest.getReward(rewardId = 1_700_000_000L, autoCreate = true)

        assertThat(reward).isNotNull()
        // `Player::getReward` stores the id in the DATE attribute (player.cpp:2067-2083).
        assertThat(reward!!.getAttribute(ItemAttribute.DATE)).isEqualTo(1_700_000_000L)
        assertThat(chest.items).containsExactly(reward)
        assertThat(chest.getRewardList()).containsExactly(1_700_000_000L)
        assertThat(chest.getReward(1_700_000_000L, true)).isSameAs(reward)
    }

    @Test
    fun `getReward prepends the proxy like Game internalAddItem does`() {
        val chest = RewardChest()

        val older = chest.getReward(1L, autoCreate = true)!!
        val newer = chest.getReward(2L, autoCreate = true)!!

        // `Game::internalAddItem` reaches `Container::addThing` which push_front's.
        assertThat(chest.items).containsExactly(newer, older)
    }

    @Test
    fun `reward internalAddThing links the child parent and prepends`() {
        val reward = Reward()
        val first = Item(id = 100)
        val second = Item(id = 200)

        reward.internalAddThing(first)
        reward.internalAddThing(second)

        // `Container::internalAddThing` sets the parent and push_front's.
        assertThat(reward.items.map { it.id }).containsExactly(200, 100)
        assertThat(first.getParent()).isSameAs(reward)
        assertThat(second.getParent()).isSameAs(reward)
    }

    @Test
    fun `reward addItem appends like Container addItem`() {
        val reward = Reward()
        val first = Item(id = 100)
        val second = Item(id = 200)

        reward.addItem(first)
        reward.addItem(second)

        assertThat(reward.items.map { it.id }).containsExactly(100, 200)
        assertThat(first.getParent()).isSameAs(reward)
    }

    @Test
    fun `getReward without autoCreate returns null for unknown id`() {
        val chest = RewardChest()

        assertThat(chest.getReward(42L, autoCreate = false)).isNull()
        assertThat(chest.empty).isTrue()
    }

    @Test
    fun `removeReward drops tracked reward`() {
        val chest = RewardChest()
        chest.getReward(7L, true)

        chest.removeReward(7L)

        assertThat(chest.getRewardList()).isEmpty()
        assertThat(chest.getReward(7L, false)).isNull()
    }

    @Test
    fun `reward queryAdd mirrors Canary return values`() {
        val reward = Reward()
        val sword = Item(id = 2376)
        val actor = Player(
            id = 1,
            name = "Reward Actor",
            maxHealth = 10,
            health = 10,
            position = Position(1, 1, 7),
        )

        assertThat(reward.queryAdd(0, sword, 1, 0, actor)).isEqualTo(ReturnValue.NOTPOSSIBLE)
        assertThat(reward.queryAdd(0, null, 1, 0, null)).isEqualTo(ReturnValue.NOTPOSSIBLE)
        assertThat(reward.queryAdd(0, reward, 1, 0, null)).isEqualTo(ReturnValue.THISISIMPOSSIBLE)

        val notPickupable = Item(
            id = 100,
            type = ItemType(id = 100, name = "Fixture", isPickupable = false)
        )
        assertThat(reward.queryAdd(0, notPickupable, 1, 0, null)).isEqualTo(ReturnValue.CANNOTPICKUP)
        assertThat(reward.queryAdd(0, sword, 1, 0, null)).isEqualTo(ReturnValue.NOERROR)
    }

    @Test
    fun `collect moves every reward item and reports counts`() {
        val chest = RewardChest()
        val owner = TestRewardChestOwner()
        val reward = chest.getReward(100L, true)!!
        owner.sourceRewards = listOf(reward)
        val first = Item(id = 2376)
        val second = Item(id = 2385)
        reward.addItem(first)
        reward.addItem(second)

        val result = chest.collectRewardChestItems(owner, maxMoveItems = 0)

        assertThat(result).isEqualTo(ReturnValue.NOERROR)
        assertThat(owner.collected).containsExactly(first, second)
        assertThat(reward.empty()).isTrue()
        assertThat(owner.textMessages).containsExactly("2 of 2 objects were picked up.")
    }

    @Test
    fun `collect honors max collect items limit`() {
        val chest = RewardChest()
        val owner = TestRewardChestOwner()
        val reward = chest.getReward(200L, true)!!
        owner.sourceRewards = listOf(reward)
        repeat(3) { reward.addItem(Item(id = 3000 + it)) }

        val result = chest.collectRewardChestItems(owner, maxMoveItems = 2)

        assertThat(result).isEqualTo(ReturnValue.NOERROR)
        assertThat(owner.collected).hasSize(2)
        assertThat(reward.items).hasSize(1)
        assertThat(owner.textMessages)
            .containsExactly("You can only collect 2 items at a time. 2 of 3 objects were picked up.")
    }

    @Test
    fun `collect returns reward chest is empty when nothing to collect`() {
        val chest = RewardChest()
        val owner = TestRewardChestOwner()

        val result = chest.collectRewardChestItems(owner)

        assertThat(result).isEqualTo(ReturnValue.REWARDCHESTISEMPTY)
        assertThat(owner.textMessages).isEmpty()
        assertThat(owner.collected).isEmpty()
    }

    @Test
    fun `collect reports not enough capacity and stops`() {
        val chest = RewardChest()
        val owner = TestRewardChestOwner(capacity = 0.0)
        val reward = chest.getReward(300L, true)!!
        owner.sourceRewards = listOf(reward)
        reward.addItem(
            Item(id = 2376, type = ItemType(id = 2376, name = "Sword", weight = 50.0))
        )

        val result = chest.collectRewardChestItems(owner)

        assertThat(result).isEqualTo(ReturnValue.NOTPOSSIBLE)
        assertThat(owner.cancelMessages).containsExactly(ReturnValue.NOTENOUGHCAPACITY)
        assertThat(owner.collected).isEmpty()
        assertThat(owner.textMessages).containsExactly("0 of 1 objects were picked up.")
    }

    @Test
    fun `collect flattens nested reward containers like getRewardsFromContainer`() {
        val chest = RewardChest()
        val owner = TestRewardChestOwner()
        val outer = chest.getReward(400L, true)!!
        val nested = Reward()
        val loot = Item(id = 999)
        nested.addItem(loot)
        outer.addItem(nested)
        owner.sourceRewards = listOf(outer, nested)

        val result = chest.collectRewardChestItems(owner)

        assertThat(result).isEqualTo(ReturnValue.NOERROR)
        assertThat(owner.collected).containsExactly(loot)
        assertThat(owner.textMessages).containsExactly("1 of 1 objects were picked up.")
    }

    @Test
    fun `collect counts only successfully collected items`() {
        val chest = RewardChest()
        val owner = TestRewardChestOwner()
        val reward = chest.getReward(500L, true)!!
        owner.sourceRewards = listOf(reward)
        reward.addItem(Item(id = 2376))

        owner.collectResult = ReturnValue.NOTPOSSIBLE
        val result = chest.collectRewardChestItems(owner)

        assertThat(result).isEqualTo(ReturnValue.NOTPOSSIBLE)
        assertThat(owner.collected).isEmpty()
        assertThat(reward.items).hasSize(1)
        assertThat(owner.textMessages).containsExactly("0 of 1 objects were picked up.")
    }
}
