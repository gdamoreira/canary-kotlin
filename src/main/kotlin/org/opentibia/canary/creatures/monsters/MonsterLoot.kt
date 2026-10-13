package org.opentibia.canary.creatures.monsters

import org.opentibia.canary.items.Container
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemAttribute

/**
 * Port of the loot fields read by the drop-loot roll; the optional
 * `subType`/`actionId`/`text`/`childLoot` columns of `struct LootBlock`
 * (src/creatures/creatures_definitions.hpp:1765-1802) are not modeled yet (PARITY GAP).
 *
 * [chance] is a basis point value out of [MonsterLoot.MAX_LOOT_CHANCE].
 */
data class LootBlock(
    val id: Int = 0,
    val countMin: Int = 1,
    val countMax: Int = 1,
    val chance: Int = 0,
    val unique: Boolean = false,
)

/**
 * Item-type facts the roll reads: `ItemType::getCharges` and `ItemType::isStackable`
 * (src/items/items.hpp). The lookup is injected so the loot engine stays independent
 * of the item registry wiring.
 */
class LootItemType(
    val charges: Int = 0,
    val isStackable: Boolean = false,
)

/** One accumulated loot roll result: merged `count` for [itemId]. */
data class LootedItem(val itemId: Int, val count: Int)

/**
 * `Monster::dropLoot` inputs (src/creatures/monsters/monster.cpp:3415-3436):
 * [lootDrop] is `Creature::lootDrop`, [isRewardBoss] `MonsterType::info.isRewardBoss`,
 * [rateLoot] the `RATE_LOOT` config, and the forge fields feed the fiendish
 * sliver grant (`FORGE_MIN_SLIVERS`/`FORGE_MAX_SLIVERS`).
 */
class MonsterLootDropRequest(
    val lootItems: List<LootBlock> = emptyList(),
    val lootDrop: Boolean = true,
    val isRewardBoss: Boolean = false,
    val rateLoot: Int = 1,
    val forgeClassification: ForgeClassification = ForgeClassification.Normal,
    val forgeMinSlivers: Int = 0,
    val forgeMaxSlivers: Int = 0,
    val itemType: (Int) -> LootItemType? = { null },
)

/** `enum class ForgeClassifications_t` (src/creatures/creatures_definitions.hpp:596-600). */
enum class ForgeClassification(val value: Int) {
    Normal(0),
    Influenced(1),
    Fiendish(2),
}

/**
 * Port of the monster loot surface:
 * - `MAX_LOOTCHANCE` (src/utils/const.hpp:12) and `ITEM_FORGE_SLIVER`
 *   (src/utils/utils_definitions.hpp:696)
 * - `Monster::dropLoot` (src/creatures/monsters/monster.cpp:3415-3436)
 * - `Monster::getCorpse` corpse-owner rules (src/creatures/monsters/monster.cpp:3305-3320)
 * - the base `monsterOnDropLoot` roll (data/libs/functions/monstertype.lua:4-87) with
 *   `getLootRandom` (data/libs/functions/functions.lua:68-75)
 *
 * The runtime dice are injected: [rollLoot] and [dropLoot] take a `nextRoll` in
 * `0..MAX_LOOT_CHANCE` (Canary's `getLootRandom` result at unit loot rate/factor) and
 * [dropLoot] takes the sliver count draw. The loot-rate scaling, loot factor, gut
 * charm and post-drop-loot callbacks remain Lua-side and are not ported (PARITY GAP).
 */
object MonsterLoot {
    const val MAX_LOOT_CHANCE = 100000
    const val ITEM_FORGE_SLIVER = 37109

    /**
     * Rolls a loot table once per entry with Canary's `generateLootRoll` arithmetic:
     * an entry drops when `roll < chance`; stackable counts use
     * `roll % (countMax - countMin + 1) + countMin`; charged items use their charges;
     * a `unique` entry is rolled at most once per drop. Results merge by item id.
     */
    fun rollLoot(
        lootItems: List<LootBlock>,
        nextRoll: () -> Int,
        itemType: (Int) -> LootItemType? = { null },
    ): List<LootedItem> {
        val results = LinkedHashMap<Int, Int>()
        val uniqueDropped = mutableSetOf<Int>()

        for (block in lootItems) {
            if (block.unique && uniqueDropped.contains(block.id)) {
                continue
            }

            val type = itemType(block.id)
            val roll = nextRoll()
            if (roll >= block.chance) {
                continue
            }

            val count = when {
                type != null && type.charges > 0 -> type.charges
                type?.isStackable == true -> maxOf(0, roll % (block.countMax - block.countMin + 1)) + block.countMin
                else -> 1
            }

            if (count > 0 && block.unique) {
                uniqueDropped.add(block.id)
            }
            results[block.id] = (results[block.id] ?: 0) + count
        }

        return results
            .filterValues { it > 0 }
            .map { (itemId, count) -> LootedItem(itemId, count) }
    }

    /**
     * `Monster::dropLoot` shell folded with the base drop-loot roll: nothing drops
     * without a corpse or with `lootDrop` disabled; fiendish monsters always grant
     * `uniform_random(forgeMinSlivers, forgeMaxSlivers)` slivers; the loot table is
     * only rolled when the monster is not a reward boss and `RATE_LOOT > 0`.
     * Rolled items are added to [corpse] in grant order (slivers first).
     */
    fun dropLoot(
        corpse: Container?,
        request: MonsterLootDropRequest,
        nextRoll: () -> Int,
        nextSliverCount: (Int, Int) -> Int = { min, _ -> min },
    ): List<LootedItem> {
        if (corpse == null || !request.lootDrop) {
            return emptyList()
        }

        val dropped = mutableListOf<LootedItem>()
        if (request.forgeClassification == ForgeClassification.Fiendish) {
            val sliverCount = nextSliverCount(request.forgeMinSlivers, request.forgeMaxSlivers)
            if (sliverCount > 0) {
                addToCorpse(corpse, ITEM_FORGE_SLIVER, sliverCount)
                dropped.add(LootedItem(ITEM_FORGE_SLIVER, sliverCount))
            }
        }

        if (!request.isRewardBoss && request.rateLoot > 0) {
            for (item in rollLoot(request.lootItems, nextRoll, request.itemType)) {
                addToCorpse(corpse, item.itemId, item.count)
                dropped.add(item)
            }
        }

        return dropped
    }

    /**
     * `Monster::getCorpse` (monster.cpp:3305-3320): the corpse owner is the
     * most-damage creature when it is a player, otherwise its master when that
     * master is a player. `null` when neither holds a player.
     */
    fun resolveCorpseOwner(mostDamageCreature: TargetCreatureFacts?): Long? {
        if (mostDamageCreature == null) {
            return null
        }
        if (mostDamageCreature.playerId != null) {
            return mostDamageCreature.id
        }
        if (mostDamageCreature.masterPlayerId != null) {
            return mostDamageCreature.masterId
        }
        return null
    }

    /**
     * Writes [resolveCorpseOwner] into `ItemAttribute_t::CORPSEOWNER`, leaving the
     * corpse unowned when no player is involved (same as Canary, which only sets
     * the attribute when `getPlayer()` resolves).
     */
    fun applyCorpseOwner(corpse: Container, mostDamageCreature: TargetCreatureFacts?) {
        val owner = resolveCorpseOwner(mostDamageCreature) ?: return
        corpse.setAttribute(ItemAttribute.CORPSEOWNER, owner)
    }

    private fun addToCorpse(corpse: Container, itemId: Int, count: Int) {
        if (count <= 0) {
            return
        }
        corpse.addItem(Item.create(itemId, count))
    }
}
