package org.opentibia.canary.creatures

import org.opentibia.canary.map.Position
import kotlin.math.max

abstract class Creature(
    val id: Long,
    val name: String,
    var maxHealth: Int,
    var health: Int,
    var maxMana: Int = 0,
    var mana: Int = 0,
    var speed: Int = 200,
    var position: Position
) {
    val isDead: Boolean get() = health <= 0

    fun drainHealth(amount: Int) {
        health = max(0, health - amount)
    }

    fun addHealth(amount: Int) {
        health = kotlin.math.min(maxHealth, health + amount)
    }
}

class Monster(
    id: Long,
    name: String,
    maxHealth: Int,
    health: Int,
    maxMana: Int = 0,
    mana: Int = 0,
    speed: Int = 200,
    position: Position
) : Creature(id, name, maxHealth, health, maxMana, mana, speed, position)

class Player(
    id: Long,
    name: String,
    maxHealth: Int,
    health: Int,
    maxMana: Int = 0,
    mana: Int = 0,
    var capacity: Double = 400.0,
    speed: Int = 220,
    position: Position,
    var experience: Long = 0,
    var level: Int = 1
) : Creature(id, name, maxHealth, health, maxMana, mana, speed, position) {

    fun addExperience(exp: Long) {
        experience += exp
        updateLevel()
    }

    private fun updateLevel() {
        while (experience >= getExpForLevel(level + 1)) {
            level++
        }
    }

    companion object {
        /**
         * Matches Canary / OpenTibia formula:
         * (50 * n^3 - 300 * n^2 + 850 * n - 600) / 3
         */
        fun getExpForLevel(n: Int): Long {
            if (n <= 1) return 0L
            val l = n.toLong()
            return (50L * l * l * l - 300L * l * l + 850L * l - 600L) / 3L
        }
    }
}
