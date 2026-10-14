package org.opentibia.canary.creatures.players

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Transcribes the Player experience-boost family
 * (src/creatures/players/player.cpp:3203-3273):
 *
 * - `Player::getBaseXpGain` / `setBaseXpGain` (player.cpp:3203-3209)
 * - `Player::getVoucherXpBoost` / `setVoucherXpBoost` (player.cpp:3211-3217)
 * - `Player::getGrindingXpBoost` / `setGrindingXpBoost` (player.cpp:3219-3229)
 * - `Player::getDisplayGrindingXpBoost` (player.cpp:3223-3225)
 * - `Player::getXpBoostPercent` / `setXpBoostPercent` (player.cpp:3231, 3243-3246)
 * - `Player::getDisplayXpBoostPercent` (player.cpp:3235-3241)
 * - `Player::getStaminaXpBoost` / `setStaminaXpBoost` (player.cpp:3248-3254)
 * - `Player::getXpBoostTime` / `setXpBoostTime` (player.cpp:3256-3273)
 *
 * Field defaults are the C++ member initializers (player.hpp:1813, 1820-1824):
 * `baseXpGain = 100`, `staminaXpBoost = 100`, every other boost zero.
 */
class PlayerXpBoostStateTest {

    private var statsSent = 0

    private fun newState() = PlayerXpBoostState(sendStats = { statsSent++ })

    @Test
    fun `defaults mirror the Canary member initializers`() {
        val state = newState()

        assertThat(state.getBaseXpGain()).isEqualTo(100)
        assertThat(state.getVoucherXpBoost()).isZero()
        assertThat(state.getGrindingXpBoost()).isZero()
        assertThat(state.getXpBoostPercent()).isZero()
        assertThat(state.getStaminaXpBoost()).isEqualTo(100)
        assertThat(state.getXpBoostTime()).isZero()
    }

    @Test
    fun `setters keep the uint16 parameter semantics`() {
        // The C++ setters take a `uint16_t` (player.cpp:3207, 3215, 3227, 3252),
        // so an out-of-range Int call wraps and the std::min is a no-op.
        val state = newState()

        state.setBaseXpGain(65_535)
        state.setVoucherXpBoost(65_535)
        state.setGrindingXpBoost(65_535)
        state.setStaminaXpBoost(65_535)

        assertThat(state.getBaseXpGain()).isEqualTo(65_535)
        assertThat(state.getVoucherXpBoost()).isEqualTo(65_535)
        assertThat(state.getGrindingXpBoost()).isEqualTo(65_535)
        assertThat(state.getStaminaXpBoost()).isEqualTo(65_535)

        state.setBaseXpGain(70_000)
        state.setVoucherXpBoost(70_000)
        state.setGrindingXpBoost(70_000)
        state.setStaminaXpBoost(70_000)

        assertThat(state.getBaseXpGain()).isEqualTo(4_464)
        assertThat(state.getVoucherXpBoost()).isEqualTo(4_464)
        assertThat(state.getGrindingXpBoost()).isEqualTo(4_464)
        assertThat(state.getStaminaXpBoost()).isEqualTo(4_464)
    }

    @Test
    fun `display grinding boost scales by baseXpGain over 100`() {
        // std::clamp<uint16_t>(grindingXpBoost * (baseXpGain / 100), 0, max)
        // (player.cpp:3223-3225); the division is integer division.
        val state = newState()
        state.setGrindingXpBoost(50)

        assertThat(state.getDisplayGrindingXpBoost()).isEqualTo(50)

        state.setBaseXpGain(150)
        assertThat(state.getDisplayGrindingXpBoost()).isEqualTo(50)

        state.setBaseXpGain(200)
        assertThat(state.getDisplayGrindingXpBoost()).isEqualTo(100)
    }

    @Test
    fun `display grinding boost saturates at the uint16 maximum`() {
        // player.cpp:3224 clamps the widened product back into uint16.
        val state = newState()
        state.setBaseXpGain(200)
        state.setGrindingXpBoost(65_535)

        assertThat(state.getDisplayGrindingXpBoost()).isEqualTo(65_535)
    }

    @Test
    fun `display xp boost percent is zero while no boost time remains`() {
        // player.cpp:3235-3241: the xpBoostTime == 0 gate.
        val state = newState()
        state.setXpBoostPercent(50)

        assertThat(state.getXpBoostPercent()).isEqualTo(50)
        assertThat(state.getDisplayXpBoostPercent()).isZero()

        state.setXpBoostTime(3600)
        assertThat(state.getDisplayXpBoostPercent()).isEqualTo(50)
    }

    @Test
    fun `setXpBoostPercent sends stats and the display scales by baseXpGain`() {
        // player.cpp:3243-3246.
        val state = newState()

        state.setXpBoostPercent(60)
        assertThat(statsSent).isEqualTo(1)

        state.setXpBoostTime(3600)
        state.setBaseXpGain(200)
        assertThat(state.getDisplayXpBoostPercent()).isEqualTo(120)
    }

    @Test
    fun `setXpBoostTime caps boosts at twelve hours`() {
        // player.cpp:3258-3263: timeLeft > 12 * 3600 is clamped to 43200.
        val state = newState()

        state.setXpBoostTime(43_200)
        assertThat(state.getXpBoostTime()).isEqualTo(43_200)

        state.setXpBoostTime(50_000)
        assertThat(state.getXpBoostTime()).isEqualTo(43_200)
    }

    @Test
    fun `setXpBoostTime sends stats only when the boost state flips`() {
        // player.cpp:3257, 3265-3267: hadXpBoost != (xpBoostTime > 0).
        val state = newState()

        state.setXpBoostTime(0)
        assertThat(statsSent).isZero()

        state.setXpBoostTime(60)
        assertThat(statsSent).isEqualTo(1)

        state.setXpBoostTime(120)
        assertThat(statsSent).isEqualTo(1)

        state.setXpBoostTime(0)
        assertThat(statsSent).isEqualTo(2)
    }
}
