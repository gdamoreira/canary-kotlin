package org.opentibia.canary.creatures

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.map.Position
import org.opentibia.canary.systems.rewardchest.PlayerRewardChestOwner
import org.opentibia.canary.systems.rewardchest.ReturnValue
import org.opentibia.canary.systems.rewardchest.RewardChest

/**
 * Transcript tests for wiring the boss reward chest into per-player state.
 *
 * Source of truth: ../.projects/canary
 *  - src/creatures/players/player.cpp:2058 `Player::getRewardChest` (lazy
 *    `std::make_shared<RewardChest>(ITEM_REWARD_CHEST)`).
 *  - src/creatures/players/player.cpp:2067 `Player::getReward` (autoCreate adds
 *    the reward to `getRewardChest()`).
 *  - src/game/game.cpp:3898 `Game::collectRewardChestItems`.
 */
class PlayerRewardChestTest {

    private fun newPlayer(capacity: Double = 400.0) = Player(
        id = 42,
        name = "Reward Hero",
        maxHealth = 200,
        health = 200,
        capacity = capacity,
        position = Position(100, 100, 7),
    )

    @Test
    fun `player features expose a lazily created reward chest`() {
        val player = newPlayer()

        val chest = player.features.rewardChest

        assertThat(chest.id).isEqualTo(RewardChest.ITEM_REWARD_CHEST)
        assertThat(chest.maxSize).isEqualTo(32)
        assertThat(chest.canRemove).isFalse()
        assertThat(chest.empty).isTrue()
        assertThat(player.features.rewardChest).isSameAs(chest)
    }

    @Test
    fun `adding a reward stores it in the player chest and collecting it works`() {
        val player = newPlayer()
        val chest = player.features.rewardChest
        val reward = chest.getReward(1_700_000_000L, autoCreate = true)!!
        val loot = Item(id = 2376, type = ItemType(id = 2376, name = "Sword", weight = 50.0))
        reward.addItem(loot)

        val collected = mutableListOf<Item>()
        val owner = PlayerRewardChestOwner(
            capacityProvider = { player.capacity },
            cancelMessageSink = {},
            textMessageSink = {},
            collectManagedItem = {
                reward.removeItem(it)
                collected.add(it)
                ReturnValue.NOERROR
            },
        )

        val result = chest.collectRewardChestItems(owner)

        assertThat(result).isEqualTo(ReturnValue.NOERROR)
        assertThat(collected).containsExactly(loot)
        assertThat(reward.empty).isTrue()
    }

    @Test
    fun `reward chest owner adapter delegates to the player seams`() {
        val cancels = mutableListOf<ReturnValue>()
        val texts = mutableListOf<String>()
        val collected = mutableListOf<Item>()
        val owner = PlayerRewardChestOwner(
            capacityProvider = { 123.5 },
            cancelMessageSink = { cancels.add(it) },
            textMessageSink = { texts.add(it) },
            collectManagedItem = {
                collected.add(it)
                ReturnValue.NOTPOSSIBLE
            },
        )

        assertThat(owner.getCapacity()).isEqualTo(123.5)
        owner.sendCancelMessage(ReturnValue.NOTPOSSIBLE)
        owner.sendTextMessage("hi")
        val result = owner.internalCollectManagedItems(Item(2376))

        assertThat(cancels).containsExactly(ReturnValue.NOTPOSSIBLE)
        assertThat(texts).containsExactly("hi")
        assertThat(collected).hasSize(1)
        assertThat(result).isEqualTo(ReturnValue.NOTPOSSIBLE)
    }
}
