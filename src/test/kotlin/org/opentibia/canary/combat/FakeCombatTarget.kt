package org.opentibia.canary.combat

import org.opentibia.canary.combat.condition.Buff
import org.opentibia.canary.combat.condition.Condition
import org.opentibia.canary.combat.condition.ConditionPlayer
import org.opentibia.canary.combat.condition.ConditionType

/**
 * Test double for the injected [CombatTarget] seam.
 *
 * It records every health/mana mutation the dispatch layer performs so the
 * Canary semantics can be asserted without a live world. It is intentionally
 * *not* Canary's `Creature`.
 */
class FakeCombatTarget(
    id: Int = 0,
    health: Int = 1000,
    maxHealth: Int = 1000,
    mana: Int = 100,
    maxMana: Int = 500,
) : CombatTarget {

    var idValue: Int = id
    var healthValue: Int = health
    var maxHealthValue: Int = maxHealth
    var manaValue: Int = mana
    var maxManaValue: Int = maxMana

    var attackable: Boolean = true
    var immuneTypes: MutableSet<CombatType> = mutableSetOf()
    var defenseValue: Int = 0
    var armorValue: Int = 0
    var mitigationValue: Double = 0.0
    var hasDefenseValue: Boolean = true
    var canUseDefenseValue: Boolean = true
    var immuneConditionTypes: MutableSet<ConditionType> = mutableSetOf()
    var playerValue: ConditionPlayer? = null

    /** `Creature::getAbsorbPercent(combat)` (`creature.cpp:1979`). */
    var absorbPercentByType: MutableMap<CombatType, Int> = mutableMapOf()

    /** `Creature::getAbsorbFlat(combat)` (`creature.cpp:1962`). */
    var absorbFlatByType: MutableMap<CombatType, Int> = mutableMapOf()

    /** `Creature::getIncreasePercent(combat)` (`creature.cpp:1996`). */
    var increasePercentByType: MutableMap<CombatType, Int> = mutableMapOf()

    /** `Player::getMantra()` (`player.hpp`). */
    var mantraValue: Int = 0

    /**
     * `Creature::varBuffs` (`creature.hpp:909`); every buff starts at 100,
     * exactly like the C++ array initializer.
     */
    var buffValues: MutableMap<Buff, Int> = mutableMapOf()

    /** Every `addCombatCondition` call as `(condition, attackerPlayer)` in order. */
    val appliedConditions: MutableList<Pair<Condition, Boolean>> = mutableListOf()

    /** Every `drainHealth` amount in order. */
    val drainedHealth: MutableList<Int> = mutableListOf()

    /** Every `gainHealth` amount in order. */
    val gainedHealth: MutableList<Int> = mutableListOf()

    override fun getHealth(): Int = healthValue

    override fun getMaxHealth(): Int = maxHealthValue

    override fun addHealth(amount: Int): Int {
        gainedHealth.add(amount)
        healthValue = minOf(maxHealthValue, healthValue + amount)
        return healthValue
    }

    override fun drainHealth(amount: Int): Int {
        drainedHealth.add(amount)
        healthValue = maxOf(0, healthValue - amount)
        return healthValue
    }

    override fun getMana(): Int = manaValue

    override fun getMaxMana(): Int = maxManaValue

    override fun changeMana(amount: Int): Int {
        if (amount > 0) {
            manaValue += minOf(amount, maxManaValue - manaValue)
        } else {
            manaValue = maxOf(0, manaValue + amount)
        }
        return manaValue
    }

    override fun isAttackable(): Boolean = attackable

    override fun isImmune(combatType: CombatType): Boolean = combatType in immuneTypes

    override fun getDefense(): Int = defenseValue

    override fun getArmor(): Int = armorValue

    override fun hasDefense(): Boolean = hasDefenseValue

    override fun canUseDefense(): Boolean = canUseDefenseValue

    override fun getMitigation(): Double = mitigationValue

    override fun getID(): Int = idValue

    override fun getPlayer(): ConditionPlayer? = playerValue

    override fun getAbsorbPercent(combatType: CombatType): Int = absorbPercentByType[combatType] ?: 0

    override fun getAbsorbFlat(combatType: CombatType): Int = absorbFlatByType[combatType] ?: 0

    override fun getIncreasePercent(combatType: CombatType): Int = increasePercentByType[combatType] ?: 0

    override fun getMantra(): Int = mantraValue

    override fun getBuff(buff: Buff): Int = buffValues[buff] ?: 100

    override fun isImmune(conditionType: ConditionType): Boolean = conditionType in immuneConditionTypes

    override fun addCombatCondition(condition: Condition, attackerPlayer: Boolean): Boolean {
        appliedConditions.add(condition to attackerPlayer)
        return true
    }
}
