package org.opentibia.canary.creatures

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.monsters.MonsterDeathHost
import org.opentibia.canary.map.Position

/**
 * Transcript tests for the `Creature` damage-map queries the death pipeline
 * reads:
 *
 * - `Creature::getDamageRatio` (src/creatures/creature.cpp:1168-1190);
 * - `Creature::getGainedExperience` (creature.cpp:1192-1194) over the virtual
 *   `Creature::getLostExperience` (creature.hpp:960-962);
 * - `Creature::hasBeenAttacked` (creature.cpp:830-836) against the injected
 *   `PZ_LOCKED` window.
 */
class CreatureDamageMapTest {

    private val pos = Position(100, 100, 7)

    private fun monster(id: Long = 1): Monster =
        Monster(id = id, name = "Rat", maxHealth = 100, health = 100, position = pos)

    private fun player(id: Long): Player =
        Player(id = id, name = "Hero$id", maxHealth = 150, health = 150, position = pos)

    @Test
    fun `getDamageRatio weighs each attacker's share and skips empty entries`() {
        val dying = monster()
        val first = player(10)
        val second = player(11)

        assertThat(dying.getDamageRatio(first)).isZero()

        dying.addDamagePoints(first, 300)
        dying.addDamagePoints(second, 100)

        assertThat(dying.getDamageRatio(first)).isEqualTo(0.75)
        assertThat(dying.getDamageRatio(second)).isEqualTo(0.25)
    }

    @Test
    fun `getGainedExperience floors the ratio times the lost experience`() {
        val dying = monster()
        dying.monsterDeathHost = MonsterDeathHost(experience = 100)
        val attacker = player(10)
        dying.addDamagePoints(attacker, 100)

        assertThat(dying.getGainedExperience(attacker)).isEqualTo(100L)

        val partial = monster(id = 2)
        partial.monsterDeathHost = MonsterDeathHost(experience = 99)
        partial.addDamagePoints(attacker, 100)

        assertThat(partial.getGainedExperience(attacker)).isEqualTo(99L)
    }

    @Test
    fun `hasBeenAttacked expires after the pz locked window`() {
        val dying = monster()
        val attacker = player(10)
        dying.now = { 0L }
        dying.addDamagePoints(attacker, 100)

        dying.now = { 59_999L }
        assertThat(dying.hasBeenAttacked(10)).isTrue()

        dying.now = { 60_000L }
        assertThat(dying.hasBeenAttacked(10)).isTrue()

        dying.now = { 60_001L }
        assertThat(dying.hasBeenAttacked(10)).isFalse()
        assertThat(dying.hasBeenAttacked(99)).isFalse()
    }
}
