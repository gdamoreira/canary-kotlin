package org.opentibia.canary.combat

import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Player
import kotlin.random.Random

object CombatEngine {

    /**
     * Calculates maximum physical attack damage using standard Canary formula:
     * (2 * (attack * (skill + 5.8) / 25) + (level / 5))
     */
    fun calculateMaxPhysicalDamage(level: Int, skill: Int, attack: Int): Int {
        val attackFactor = 2.0 * (attack.toDouble() * (skill.toDouble() + 5.8) / 25.0)
        val levelFactor = level.toDouble() / 5.0
        return (attackFactor + levelFactor).toInt()
    }

    /**
     * Applies armor reduction: random value between (armor / 2) and (armor - 1).
     */
    fun applyArmorReduction(rawDamage: Int, armor: Int, random: Random = Random): Int {
        if (armor <= 0) return rawDamage
        val minReduction = armor / 2
        val maxReduction = kotlin.math.max(minReduction, armor - 1)
        val reduction = if (maxReduction > minReduction) random.nextInt(minReduction, maxReduction + 1) else minReduction
        return kotlin.math.max(0, rawDamage - reduction)
    }

    /**
     * Simulates a melee attack round from attacker to target.
     */
    fun executeMeleeAttack(
        attacker: Player,
        target: Creature,
        weaponAttack: Int,
        skill: Int,
        targetArmor: Int,
        random: Random = Random
    ): Int {
        if (attacker.position.getDistanceTo(target.position) > 1) {
            return 0 // out of melee range
        }

        val maxDamage = calculateMaxPhysicalDamage(attacker.level, skill, weaponAttack)
        val rolledDamage = if (maxDamage > 0) random.nextInt(1, maxDamage + 1) else 0
        val finalDamage = applyArmorReduction(rolledDamage, targetArmor, random)

        if (finalDamage > 0) {
            target.drainHealth(finalDamage)
        }
        return finalDamage
    }
}
