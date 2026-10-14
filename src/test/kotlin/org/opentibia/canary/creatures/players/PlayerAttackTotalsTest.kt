package org.opentibia.canary.creatures.players

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.FightMode
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.map.Position

/**
 * Transcribes the combat-total formulas
 * (src/creatures/players/player.cpp:616-646):
 *
 * - `Player::attackTotal` (player.cpp:616-641)
 * - `Player::attackRawTotal` (player.cpp:643-646)
 *
 * The C++ computes the fight-mode factor as `1.2f * equipment` (float) and
 * applies `std::floor` before scaling by `(skill + 4) / 28.`; the `uint16_t`
 * return truncates the double result. All three traits are pinned here.
 */
class PlayerAttackTotalsTest {

    private fun newPlayer(fightMode: FightMode): Player =
        Player(id = 1, name = "Hero", maxHealth = 150, health = 150, position = Position(100, 100, 7)).apply {
            this.fightMode = fightMode
        }

    @Test
    fun `attackTotal applies the fight mode equipment factor`() {
        // Player::attackTotal (player.cpp:616-641).
        assertThat(newPlayer(FightMode.Attack).attackTotal(0, 10, 24)).isEqualTo(12)
        assertThat(newPlayer(FightMode.Balanced).attackTotal(0, 10, 24)).isEqualTo(10)
        assertThat(newPlayer(FightMode.Defense).attackTotal(0, 10, 24)).isEqualTo(6)
    }

    @Test
    fun `attackTotal floors the equipment factor before scaling the skill`() {
        // `fightFactor = std::floor(fightFactor)` (player.cpp:638).
        assertThat(newPlayer(FightMode.Defense).attackTotal(0, 11, 28)).isEqualTo(6)
        assertThat(newPlayer(FightMode.Attack).attackTotal(1, 10, 10)).isEqualTo(7)
    }

    @Test
    fun `attackTotal adds the flat bonus after the scaled equipment`() {
        // 5 + (floor(12.0) * 0.5) = 11.
        assertThat(newPlayer(FightMode.Attack).attackTotal(5, 10, 10)).isEqualTo(11)
    }

    @Test
    fun `attackRawTotal scales the equipment without the fight mode factor`() {
        // Player::attackRawTotal (player.cpp:643-646).
        assertThat(newPlayer(FightMode.Defense).attackRawTotal(3, 10, 10)).isEqualTo(8)
        assertThat(newPlayer(FightMode.Attack).attackRawTotal(0, 11, 38)).isEqualTo(16)
        assertThat(newPlayer(FightMode.Attack).attackRawTotal(2, 0, 24)).isEqualTo(2)
    }
}
