package org.opentibia.canary.systems.proficiency

import kotlin.math.pow

/**
 * Weapon Proficiencies experience tables and level math, ported verbatim from
 * Canary src/creatures/players/components/weapon_proficiency.cpp.
 *
 * The three static tables, mastery offset, table selection, per-level experience
 * requirements, gain scaling, and bosstiary/bestiary experience formulas are
 * deterministic and ported here.
 */
object WeaponProficiencyExperience {
    const val MASTERY_EXPERIENCE_OFFSET = 2

    val CROSSBOW_EXPERIENCE: IntArray = intArrayOf(
        600, 8000, 30000, 150000, 650000, 2500000, 10000000, 20000000, 30000000,
    )

    val KNIGHT_EXPERIENCE: IntArray = intArrayOf(
        1250, 20000, 80000, 300000, 1500000, 6000000, 20000000, 40000000, 60000000,
    )

    val STANDARD_EXPERIENCE: IntArray = intArrayOf(
        1750, 25000, 100000, 400000, 2000000, 8000000, 30000000, 60000000, 90000000,
    )

    fun experienceArray(isBolt: Boolean, isKnightWeapon: Boolean): IntArray = when {
        isBolt -> CROSSBOW_EXPERIENCE
        isKnightWeapon -> KNIGHT_EXPERIENCE
        else -> STANDARD_EXPERIENCE
    }

    fun masteryExperienceTierCount(maxLevel: Int, experienceArray: IntArray): Int {
        if (maxLevel == 0 || experienceArray.isEmpty()) {
            return 0
        }
        return minOf(experienceArray.size, maxLevel + MASTERY_EXPERIENCE_OFFSET)
    }

    fun nextLevelExperience(experience: Int, maxLevel: Int, experienceArray: IntArray): Int {
        if (experienceArray.isEmpty()) {
            return 0
        }
        val maxExpLevels = masteryExperienceTierCount(maxLevel, experienceArray)
        for (i in 0 until maxExpLevels) {
            if (experience >= experienceArray[i]) {
                continue
            }
            return experienceArray[i] - experience
        }
        return 0
    }

    fun maxExperience(maxLevel: Int, experienceArray: IntArray): Int {
        val count = masteryExperienceTierCount(maxLevel, experienceArray)
        return if (count > 0) experienceArray[count - 1] else 0
    }

    fun scaleGain(experience: Int, multiplier: Double): Int {
        val safeMultiplier = if (multiplier < 0.0) 0.0 else multiplier
        val scaled = Math.round(experience * safeMultiplier)
        return minOf(scaled, Int.MAX_VALUE.toLong()).toInt()
    }

    fun bosstiaryExperience(rarity: BosstiaryRarity): Int = when (rarity) {
        BosstiaryRarity.RARITY_BANE -> 500
        BosstiaryRarity.RARITY_ARCHFOE -> 5000
        BosstiaryRarity.RARITY_NEMESIS -> 15000
    }

    fun bestiaryExperience(monsterStar: Int): Int {
        val star = if (monsterStar > 5) 5 else monsterStar
        val poly = -1.133 * star.toDouble().pow(5) +
            14.083 * star.toDouble().pow(4) +
            -59.666 * star.toDouble().pow(3) +
            102.916 * star.toDouble().pow(2) +
            -27.2 * star +
            1.0
        return maxOf(0.0, poly).toInt()
    }
}
