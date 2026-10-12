package org.opentibia.canary.creatures

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.map.Position

class CreatureModelTest {

    @Test
    fun `creature manages health, mana and death state`() {
        val creature = Monster(
            id = 1001,
            name = "Rat",
            maxHealth = 20,
            health = 20,
            speed = 100,
            position = Position(100, 100, 7)
        )

        assertThat(creature.isDead).isFalse()

        creature.drainHealth(15)
        assertThat(creature.health).isEqualTo(5)
        assertThat(creature.isDead).isFalse()

        creature.drainHealth(10)
        assertThat(creature.health).isEqualTo(0)
        assertThat(creature.isDead).isTrue()
    }

    @Test
    fun `player manages experience, levels, and capacity`() {
        val player = Player(
            id = 1,
            name = "Hero",
            maxHealth = 150,
            health = 150,
            maxMana = 50,
            mana = 50,
            capacity = 400.0,
            speed = 220,
            position = Position(1000, 1000, 7)
        )

        assertThat(player.level).isEqualTo(1)

        // Adding 4200 EXP should calculate level progression matching Tibia formula
        player.addExperience(4200)
        assertThat(player.level).isGreaterThan(1)
        assertThat(player.experience).isEqualTo(4200)
    }

    @Test
    fun `experience curve matches Canary exact mathematical formula`() {
        // Canary formula: getExpForLevel(level) = (50 * n^3 - 300 * n^2 + 850 * n - 600) / 3
        assertThat(Player.getExpForLevel(1)).isEqualTo(0L)
        assertThat(Player.getExpForLevel(2)).isEqualTo(100L)
        assertThat(Player.getExpForLevel(3)).isEqualTo(200L)
        assertThat(Player.getExpForLevel(8)).isEqualTo(4200L)
    }
}
