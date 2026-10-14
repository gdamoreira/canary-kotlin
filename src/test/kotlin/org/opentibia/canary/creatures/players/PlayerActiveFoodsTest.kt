package org.opentibia.canary.creatures.players

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Transcribes the Player active-food state
 * (src/creatures/players/player.cpp:5672-5690):
 *
 * - `Player::updateFood` (player.cpp:5672-5678)
 * - `Player::getActiveFoods` (player.cpp:5680-5683)
 * - `Player::isFoodActive` (player.cpp:5685-5690)
 *
 * Canary keeps `std::map<uint16_t, uint32_t> m_activeFoods` on `Player`
 * (player.hpp:1677); the state holder is the port's composition seam, mirroring
 * `PlayerConcoctions`. The client food-duration list is sent by
 * `ProtocolGame::sendCyclopediaCharacterMiscStats` (protocolgame.cpp:5761-5766).
 */
class PlayerActiveFoodsTest {

    @Test
    fun `updateFood stores a food duration and erases it at zero`() {
        // Player::updateFood (player.cpp:5672-5678).
        val foods = PlayerActiveFoods()

        foods.updateFood(1234, 60_000)
        assertThat(foods.getActiveFoods()).containsExactlyEntriesOf(mapOf(1234 to 60_000))
        assertThat(foods.isFoodActive(1234)).isTrue()

        foods.updateFood(1234, 0)
        assertThat(foods.getActiveFoods()).isEmpty()
        assertThat(foods.isFoodActive(1234)).isFalse()
    }

    @Test
    fun `updateFood overwrites the remaining time of an active food`() {
        // player.cpp:5677: `m_activeFoods[itemId] = timeLeft`.
        val foods = PlayerActiveFoods()

        foods.updateFood(1234, 60_000)
        foods.updateFood(1234, 3_000)

        assertThat(foods.getActiveFoods()).containsExactlyEntriesOf(mapOf(1234 to 3_000))
    }

    @Test
    fun `getActiveFoods returns every entry and is a snapshot`() {
        // Player::getActiveFoods (player.cpp:5680-5683).
        val foods = PlayerActiveFoods()

        foods.updateFood(1234, 60_000)
        foods.updateFood(4321, 30_000)

        val snapshot = foods.getActiveFoods()
        assertThat(snapshot).containsExactlyInAnyOrderEntriesOf(mapOf(1234 to 60_000, 4321 to 30_000))

        foods.updateFood(1234, 0)
        assertThat(snapshot).containsExactlyInAnyOrderEntriesOf(mapOf(1234 to 60_000, 4321 to 30_000))
    }

    @Test
    fun `isFoodActive is false for an unknown food`() {
        // Player::isFoodActive (player.cpp:5685-5690): missing key returns false.
        assertThat(PlayerActiveFoods().isFoodActive(1234)).isFalse()
    }
}
