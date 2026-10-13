package org.opentibia.canary.creatures.monsters

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.ItemAttribute
import org.opentibia.canary.map.Position

/**
 * Transcribes Canary:
 * - `struct LootBlock` + `MAX_LOOTCHANCE` (src/creatures/creatures_definitions.hpp:1765-1802,
 *   src/utils/const.hpp:12)
 * - `Monster::dropLoot` (src/creatures/monsters/monster.cpp:3415-3436)
 * - `Monster::getCorpse` corpse owner (src/creatures/monsters/monster.cpp:3305-3320)
 * - the base `monsterOnDropLoot` roll (data/libs/functions/monstertype.lua:4-87) and
 *   `getLootRandom` (data/libs/functions/functions.lua:68-75)
 */
class MonsterLootTest {

    private class RollQueue(vararg values: Int) {
        private val queue = ArrayDeque(values.toList())
        var calls = 0
            private set

        fun next(): Int {
            calls++
            check(queue.isNotEmpty()) { "unexpected loot roll" }
            return queue.removeFirst()
        }
    }

    private fun block(
        id: Int,
        chance: Int = 0,
        countMin: Int = 1,
        countMax: Int = 1,
        unique: Boolean = false,
    ) = LootBlock(id = id, chance = chance, countMin = countMin, countMax = countMax, unique = unique)

    private fun ownerFacts(
        id: Long,
        playerId: Long? = null,
        masterId: Long? = null,
        masterPlayerId: Long? = null,
    ) = TargetCreatureFacts(
        id = id,
        position = Position(100, 100, 7),
        playerId = playerId,
        masterId = masterId,
        masterPlayerId = masterPlayerId,
    )

    // --- loot rolls ---

    @Test
    fun `loot roll drops when the basis point roll is below the chance`() {
        val loot = MonsterLoot.rollLoot(listOf(block(id = 2160, chance = 1000)), RollQueue(999)::next)

        assertThat(loot).containsExactly(LootedItem(2160, 1))
    }

    @Test
    fun `loot roll never drops when the basis point roll reaches the chance`() {
        assertThat(MonsterLoot.rollLoot(listOf(block(id = 2160, chance = 1000)), RollQueue(1000)::next)).isEmpty()
        assertThat(MonsterLoot.rollLoot(listOf(block(id = 2160, chance = 100000)), RollQueue(100000)::next)).isEmpty()
        assertThat(MonsterLoot.rollLoot(listOf(block(id = 2160, chance = 0)), RollQueue(0)::next)).isEmpty()
    }

    @Test
    fun `stackable count uses the drop roll modulo the count range`() {
        val stackable = { _: Int -> LootItemType(isStackable = true) }
        val loot = { roll: Int ->
            MonsterLoot.rollLoot(listOf(block(id = 2160, chance = 100000, countMin = 2, countMax = 5)), RollQueue(roll)::next, stackable)
        }

        assertThat(loot(7)).containsExactly(LootedItem(2160, 5))
        assertThat(loot(4)).containsExactly(LootedItem(2160, 2))
        assertThat(loot(0)).containsExactly(LootedItem(2160, 2))
    }

    @Test
    fun `charged items drop with their charge count`() {
        val charged = { _: Int -> LootItemType(charges = 7, isStackable = true) }

        val loot = MonsterLoot.rollLoot(listOf(block(id = 2160, chance = 100000)), RollQueue(0)::next, charged)

        assertThat(loot).containsExactly(LootedItem(2160, 7))
    }

    @Test
    fun `non stackable items drop a single unit`() {
        val loot = MonsterLoot.rollLoot(listOf(block(id = 2160, chance = 100000, countMin = 2, countMax = 9)), RollQueue(0)::next)

        assertThat(loot).containsExactly(LootedItem(2160, 1))
    }

    @Test
    fun `unique entries roll at most once`() {
        val queue = RollQueue(0, 5)
        val loot = MonsterLoot.rollLoot(
            listOf(block(id = 2160, chance = 100000, unique = true), block(id = 2160, chance = 100000, unique = true)),
            queue::next,
        )

        assertThat(loot).containsExactly(LootedItem(2160, 1))
        assertThat(queue.calls).isEqualTo(1)
    }

    @Test
    fun `a failed unique roll lets a later duplicate entry roll`() {
        val queue = RollQueue(100000, 0)
        val loot = MonsterLoot.rollLoot(
            listOf(block(id = 2160, chance = 100000, unique = true), block(id = 2160, chance = 100000, unique = true)),
            queue::next,
        )

        assertThat(loot).containsExactly(LootedItem(2160, 1))
        assertThat(queue.calls).isEqualTo(2)
    }

    @Test
    fun `duplicate non unique entries accumulate counts`() {
        val loot = MonsterLoot.rollLoot(
            listOf(block(id = 2160, chance = 100000), block(id = 2160, chance = 100000)),
            RollQueue(0, 0)::next,
        )

        assertThat(loot).containsExactly(LootedItem(2160, 2))
    }

    // --- Monster::dropLoot ---

    @Test
    fun `dropLoot ignores loot when lootDrop is false`() {
        val corpse = Container(id = 1987, capacity = 20)
        val queue = RollQueue()
        val loot = MonsterLoot.dropLoot(
            corpse = corpse,
            request = MonsterLootDropRequest(lootItems = listOf(block(id = 2160, chance = 100000)), lootDrop = false),
            nextRoll = queue::next,
        )

        assertThat(loot).isEmpty()
        assertThat(corpse.items).isEmpty()
        assertThat(queue.calls).isZero()
    }

    @Test
    fun `dropLoot grants forge slivers to fiendish monsters`() {
        val corpse = Container(id = 1987, capacity = 20)
        val loot = MonsterLoot.dropLoot(
            corpse = corpse,
            request = MonsterLootDropRequest(
                lootItems = emptyList(),
                rateLoot = 0,
                forgeClassification = ForgeClassification.Fiendish,
                forgeMinSlivers = 2,
                forgeMaxSlivers = 5,
            ),
            nextRoll = RollQueue()::next,
            nextSliverCount = { _, _ -> 3 },
        )

        assertThat(loot).containsExactly(LootedItem(MonsterLoot.ITEM_FORGE_SLIVER, 3))
        assertThat(corpse.items).hasSize(1)
        assertThat(corpse.items[0].id).isEqualTo(MonsterLoot.ITEM_FORGE_SLIVER)
        assertThat(corpse.items[0].count).isEqualTo(3)
    }

    @Test
    fun `dropLoot skips the roll for reward bosses and zero loot rates`() {
        val lootItems = listOf(block(id = 2160, chance = 100000))
        val rewardQueue = RollQueue()
        val rewardLoot = MonsterLoot.dropLoot(
            corpse = Container(id = 1987, capacity = 20),
            request = MonsterLootDropRequest(lootItems = lootItems, isRewardBoss = true),
            nextRoll = rewardQueue::next,
        )
        val zeroRateQueue = RollQueue()
        val zeroRateLoot = MonsterLoot.dropLoot(
            corpse = Container(id = 1987, capacity = 20),
            request = MonsterLootDropRequest(lootItems = lootItems, rateLoot = 0),
            nextRoll = zeroRateQueue::next,
        )

        assertThat(rewardLoot).isEmpty()
        assertThat(zeroRateLoot).isEmpty()
        assertThat(rewardQueue.calls).isZero()
        assertThat(zeroRateQueue.calls).isZero()
    }

    @Test
    fun `dropLoot rolls regular loot into the corpse`() {
        val corpse = Container(id = 1987, capacity = 20)
        val loot = MonsterLoot.dropLoot(
            corpse = corpse,
            request = MonsterLootDropRequest(lootItems = listOf(block(id = 2160, chance = 1000))),
            nextRoll = RollQueue(0)::next,
        )

        assertThat(loot).containsExactly(LootedItem(2160, 1))
        assertThat(corpse.items).hasSize(1)
        assertThat(corpse.items[0].id).isEqualTo(2160)
        assertThat(corpse.items[0].count).isEqualTo(1)
    }

    // --- corpse owner ---

    @Test
    fun `corpse owner is the most damage player`() {
        assertThat(MonsterLoot.resolveCorpseOwner(ownerFacts(id = 42, playerId = 42))).isEqualTo(42L)
    }

    @Test
    fun `corpse owner falls back to the most damage creature master player`() {
        assertThat(MonsterLoot.resolveCorpseOwner(ownerFacts(id = 7, masterId = 42, masterPlayerId = 42))).isEqualTo(42L)
    }

    @Test
    fun `corpse has no owner without any player`() {
        assertThat(MonsterLoot.resolveCorpseOwner(ownerFacts(id = 7))).isNull()
        assertThat(MonsterLoot.resolveCorpseOwner(null)).isNull()
    }

    @Test
    fun `apply corpse owner writes the corpse owner attribute`() {
        val corpse = Container(id = 1987, capacity = 20)

        MonsterLoot.applyCorpseOwner(corpse, ownerFacts(id = 42, playerId = 42))
        assertThat(corpse.getAttribute(ItemAttribute.CORPSEOWNER)).isEqualTo(42L)

        val unowned = Container(id = 1987, capacity = 20)
        MonsterLoot.applyCorpseOwner(unowned, ownerFacts(id = 7))
        assertThat(unowned.getAttribute(ItemAttribute.CORPSEOWNER)).isEqualTo(0L)
    }
}
