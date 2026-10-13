package org.opentibia.canary.creatures.players

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.Town

/**
 * Transcribes the Player town/login-position state
 * (src/creatures/players/player.cpp:8307-8326; player.hpp:1713, 635-637):
 *
 * - `Player::getTown` / `setTown` (player.cpp:8307-8312)
 * - `Player::getTemplePosition` (player.cpp:8319-8326)
 * - `Player::getLoginPosition` (player.hpp:635-637)
 *
 * `Player::loginPosition` is a zero `Position` by default and is written
 * directly by `Player::death` (`loginPosition = town->getTemplePosition()`,
 * player.cpp:4040).
 */
class PlayerTownStateTest {

    @Test
    fun `town round trips`() {
        // Player::getTown / setTown (player.cpp:8307-8312).
        val state = PlayerTownState()

        assertThat(state.getTown()).isNull()

        val town = Town(1)
        state.setTown(town)
        assertThat(state.getTown()).isSameAs(town)

        state.setTown(null)
        assertThat(state.getTown()).isNull()
    }

    @Test
    fun `temple position follows the town`() {
        // Player::getTemplePosition (player.cpp:8319-8326).
        val state = PlayerTownState()

        assertThat(state.getTemplePosition()).isEqualTo(Position(0, 0, 0))

        val town = Town(1)
        town.setTemplePos(Position(100, 200, 7))
        state.setTown(town)

        assertThat(state.getTemplePosition()).isEqualTo(Position(100, 200, 7))
    }

    @Test
    fun `login position defaults to zero and is replaceable`() {
        // player.hpp:1713 `Position loginPosition;`.
        val state = PlayerTownState()

        assertThat(state.getLoginPosition()).isEqualTo(Position(0, 0, 0))

        state.loginPosition = Position(50, 60, 7)
        assertThat(state.getLoginPosition()).isEqualTo(Position(50, 60, 7))
    }
}
