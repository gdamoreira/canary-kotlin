package org.opentibia.canary.creatures.players

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Transcribes the bosstiary player counter block
 * (src/creatures/players/player.cpp:12673-12727):
 *
 * - `Player::setBossPoints` (player.cpp:12673-12675)
 * - `Player::addBossPoints` (player.cpp:12677-12679)
 * - `Player::removeBossPoints` (player.cpp:12681-12683)
 * - `Player::getBossPoints` (player.cpp:12685-12687)
 * - `Player::setSlotBossId` (player.cpp:12695-12704)
 * - `Player::getSlotBossId` (player.cpp:12706-12712)
 * - `Player::addRemoveTime` (player.cpp:12714-12716)
 * - `Player::setRemoveBossTime` (player.cpp:12718-12720)
 * - `Player::getRemoveTimes` (player.cpp:12722-12724)
 *
 * Types follow the C++ `Player` members: `uint32_t bossPoints`, the two
 * `uint32_t bossIdSlot` fields and `uint8_t bossRemoveTimes` (player.hpp:1731).
 * The protocol send of `setSlotBossId` is the injected callback.
 */
class PlayerBossPointsStateTest {

    @Test
    fun `boss points start at zero`() {
        // `uint32_t bossPoints = 0` (player.hpp:1731).
        assertThat(PlayerBossPointsState().getBossPoints()).isZero()
    }

    @Test
    fun `setBossPoints stores the amount in the unsigned 32-bit range`() {
        // Player::setBossPoints (player.cpp:12673-12675).
        val state = PlayerBossPointsState()
        state.setBossPoints(12)
        assertThat(state.getBossPoints()).isEqualTo(12L)

        state.setBossPoints(0x1_0000_0002L)
        assertThat(state.getBossPoints()).isEqualTo(2L)
    }

    @Test
    fun `addBossPoints wraps at the uint32 boundary`() {
        // Player::addBossPoints (player.cpp:12677-12679), `uint32_t +=`.
        val state = PlayerBossPointsState()
        state.addBossPoints(5)
        state.addBossPoints(7)
        assertThat(state.getBossPoints()).isEqualTo(12L)

        state.setBossPoints(0xFFFF_FFFFL)
        state.addBossPoints(2)
        assertThat(state.getBossPoints()).isEqualTo(1L)
    }

    @Test
    fun `removeBossPoints subtracts and wraps like the unsigned C++ expression`() {
        // Player::removeBossPoints (player.cpp:12681-12683):
        // `bossPoints = std::max<uint32_t>(0, bossPoints - amount)`. The
        // `uint32_t` difference wraps when the amount exceeds the balance.
        val state = PlayerBossPointsState()
        state.setBossPoints(10)
        state.removeBossPoints(4)
        assertThat(state.getBossPoints()).isEqualTo(6L)

        state.setBossPoints(10)
        state.removeBossPoints(20)
        assertThat(state.getBossPoints()).isEqualTo(0xFFFF_FFF6L)
    }

    @Test
    fun `setSlotBossId writes slot one for slotId one and slot two otherwise`() {
        // Player::setSlotBossId (player.cpp:12695-12704).
        val state = PlayerBossPointsState()
        var sends = 0

        state.setSlotBossId(1, 300) { sends++ }
        assertThat(state.getSlotBossId(1)).isEqualTo(300L)

        state.setSlotBossId(2, 400) { sends++ }
        assertThat(state.getSlotBossId(2)).isEqualTo(400L)

        state.setSlotBossId(3, 500) { sends++ }
        assertThat(state.getSlotBossId(3)).isEqualTo(500L)

        assertThat(sends).isEqualTo(3)
    }

    @Test
    fun `setSlotBossId truncates the boss id to uint32`() {
        // `uint32_t bossId` (player.cpp:12695).
        val state = PlayerBossPointsState()
        state.setSlotBossId(1, 0x1_0000_0064L)

        assertThat(state.getSlotBossId(1)).isEqualTo(100L)
    }

    @Test
    fun `remove times start at zero and increment with a uint8 wrap`() {
        // Player::addRemoveTime (player.cpp:12714-12716).
        val state = PlayerBossPointsState()
        assertThat(state.getRemoveTimes()).isZero()

        state.addRemoveTime()
        state.addRemoveTime()
        assertThat(state.getRemoveTimes()).isEqualTo(2)

        state.setRemoveBossTime(255)
        state.addRemoveTime()
        assertThat(state.getRemoveTimes()).isZero()
    }

    @Test
    fun `setRemoveBossTime truncates to uint8`() {
        // Player::setRemoveBossTime (player.cpp:12718-12720).
        val state = PlayerBossPointsState()
        state.setRemoveBossTime(7)
        assertThat(state.getRemoveTimes()).isEqualTo(7)

        state.setRemoveBossTime(256)
        assertThat(state.getRemoveTimes()).isZero()
    }
}
