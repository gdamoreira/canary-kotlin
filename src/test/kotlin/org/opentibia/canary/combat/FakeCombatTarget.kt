package org.opentibia.canary.combat

/**
 * Test double for the injected [CombatTarget] seam.
 *
 * It records every health/mana mutation the dispatch layer performs so the
 * Canary semantics can be asserted without a live world. It is intentionally
 * *not* Canary's `Creature`.
 */
class FakeCombatTarget(
    health: Int = 1000,
    maxHealth: Int = 1000,
    mana: Int = 100,
    maxMana: Int = 500,
) : CombatTarget {

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
}
