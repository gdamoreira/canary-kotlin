package org.opentibia.canary.systems.imbuement

import org.opentibia.canary.combat.condition.Skill
import org.opentibia.canary.combat.condition.Stat

/**
 * Test double for the injected [ImbuementPlayer]/[ImbuementStatsTarget] seams.
 *
 * It records every mutation the engine performs so the Canary arithmetic can be
 * asserted without a live world. It is intentionally *not* Canary's `Player`.
 */
class FakeImbuementPlayer(
    @JvmField var capacity: Int = 100,
    var money: Long = 0,
    var removeMoneyResult: Boolean = true,
    var internalRemoveItemResult: Boolean = true,
    var removeItemOfTypeResult: Boolean = true,
    var inProtectionZone: Boolean = false,
    var inFight: Boolean = true
) : ImbuementPlayer {

    @JvmField var bonusCapacity: Int = 0
    var statsSent: Int = 0
    var skillsSent: Int = 0
    var trackerUpdates: Int = 0

    val storage: MutableMap<Int, Int> = mutableMapOf()
    val itemCounts: MutableMap<Int, Int> = mutableMapOf()
    val stashCounts: MutableMap<Int, Int> = mutableMapOf()
    val itemNames: MutableMap<Int, String> = mutableMapOf()
    val skillBonuses: MutableMap<Skill, Int> = mutableMapOf()
    val statBonuses: MutableMap<Stat, Int> = mutableMapOf()
    val speedChanges: MutableList<Int> = mutableListOf()
    val resultMessages: MutableList<String> = mutableListOf()
    val textMessages: MutableList<String> = mutableListOf()
    val removedMoney: MutableList<Long> = mutableListOf()
    val removedItemTypes: MutableList<Pair<Int, Int>> = mutableListOf()
    val withdrawnItems: MutableList<Pair<Int, Int>> = mutableListOf()
    val internalRemovedItems: MutableList<Pair<ImbuementItem, Int>> = mutableListOf()

    override fun getCapacity(): Int = capacity

    /** `Player::setVarSkill` (player.cpp:1247) accumulates the modifier. */
    override fun setVarSkill(skill: Skill, value: Int) {
        skillBonuses[skill] = (skillBonuses[skill] ?: 0) + value
    }

    /** `Player::setVarStats` (player.cpp:1251) accumulates the modifier. */
    override fun setVarStats(stat: Stat, value: Int) {
        statBonuses[stat] = (statBonuses[stat] ?: 0) + value
    }

    override fun changeSpeed(value: Int) {
        speedChanges += value
    }

    override fun setBonusCapacity(value: Int) {
        bonusCapacity = value
    }

    override fun sendStats() {
        statsSent++
    }

    override fun sendSkills() {
        skillsSent++
    }

    override fun getStorageValue(key: Int): Int = storage[key] ?: 0

    override fun sendImbuementResult(message: String) {
        resultMessages += message
    }

    override fun sendTextMessage(message: String) {
        textMessages += message
    }

    override fun removeMoney(amount: Long): Boolean {
        if (!removeMoneyResult) {
            return false
        }
        removedMoney += amount
        money -= amount
        return true
    }

    override fun getItemTypeCount(itemId: Int): Int = itemCounts[itemId] ?: 0

    override fun getStashItemCount(itemId: Int): Int = stashCounts[itemId] ?: 0

    override fun getItemTypeName(itemId: Int): String = itemNames[itemId] ?: "item $itemId"

    override fun removeItemOfType(itemId: Int, count: Int, subType: Int, ignoreEquipped: Boolean): Boolean {
        if (!removeItemOfTypeResult) {
            return false
        }
        removedItemTypes += itemId to count
        itemCounts[itemId] = (itemCounts[itemId] ?: 0) - count
        return true
    }

    override fun withdrawItem(itemId: Int, amount: Int) {
        withdrawnItems += itemId to amount
    }

    override fun internalRemoveItem(item: ImbuementItem, count: Int): Boolean {
        if (!internalRemoveItemResult) {
            return false
        }
        internalRemovedItems += item to count
        return true
    }

    override fun updateImbuementTrackerStats() {
        trackerUpdates++
    }

    override fun isInProtectionZone(): Boolean = inProtectionZone

    override fun hasConditionInFight(): Boolean = inFight
}
