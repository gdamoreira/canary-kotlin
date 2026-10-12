package org.opentibia.canary.combat

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.map.Position

class CombatEngineTest {

    @Test
    fun `calculates physical damage bounds matching Canary weapon formula`() {
        // Canary formula: maxDamage = (2 * (attack * (skill + 5.8) / 25) + (level / 5))
        val maxDmg = CombatEngine.calculateMaxPhysicalDamage(level = 50, skill = 70, attack = 35)
        assertThat(maxDmg).isGreaterThan(0)

        // For skill 70, attack 35, level 50:
        // attackTerm = 35 * 75.8 / 25 = 106.12 -> * 2 = 212.24
        // levelTerm = 50 / 5 = 10 -> total max = ~222
        assertThat(maxDmg).isBetween(215, 230)
    }

    @Test
    fun `applies armor reduction within expected range`() {
        // Canary armor formula: reduction is between (armor / 2) and (armor - 1)
        val rawDamage = 100
        val armor = 20

        val mitigated = CombatEngine.applyArmorReduction(rawDamage, armor)
        // With armor 20: reduction is between 10 and 19 -> damage should be 81..90
        assertThat(mitigated).isBetween(80, 91)
    }

    @Test
    fun `executes combat attack and drains target health`() {
        val attacker = Player(
            id = 1,
            name = "Knight",
            maxHealth = 200,
            health = 200,
            speed = 220,
            position = Position(100, 100, 7),
            level = 20
        )

        val target = Monster(
            id = 1001,
            name = "Troll",
            maxHealth = 200,
            health = 200,
            speed = 100,
            position = Position(101, 100, 7) // adjacent
        )

        val seedRandom = kotlin.random.Random(42)
        val damageDealt = CombatEngine.executeMeleeAttack(attacker, target, weaponAttack = 25, skill = 40, targetArmor = 5, random = seedRandom)
        assertThat(damageDealt).isGreaterThan(0)
        assertThat(target.health).isEqualTo(200 - damageDealt)
    }
}
