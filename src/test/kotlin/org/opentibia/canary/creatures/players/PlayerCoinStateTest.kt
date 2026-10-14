package org.opentibia.canary.creatures.players

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Transcribes the Player store-coin cache
 * (src/creatures/players/player.cpp:7556-7558, 7725-7727):
 *
 * - `Player::setTibiaCoins` (player.cpp:7556-7558)
 * - `Player::setTransferableTibiaCoins` (player.cpp:7725-7727)
 *
 * Canary keeps `coinBalance` and `coinTransferableBalance` as public
 * `uint32_t` fields (player.hpp:1811-1812) written directly by the load path
 * (`iologindata_load_player.cpp:101`) and the store
 * (`protocolgame.cpp:6843`), so the port exposes matching mutable properties.
 */
class PlayerCoinStateTest {

    @Test
    fun `coin balances default to zero`() {
        // player.hpp:1811-1812.
        val state = PlayerCoinState()

        assertThat(state.coinBalance).isZero()
        assertThat(state.coinTransferableBalance).isZero()
    }

    @Test
    fun `setTibiaCoins writes the normal coin balance`() {
        // player.cpp:7556-7558.
        val state = PlayerCoinState()

        state.setTibiaCoins(750)
        assertThat(state.coinBalance).isEqualTo(750)
        assertThat(state.coinTransferableBalance).isZero()
    }

    @Test
    fun `setTransferableTibiaCoins writes the transferable balance`() {
        // player.cpp:7725-7727.
        val state = PlayerCoinState()

        state.setTransferableTibiaCoins(25)
        assertThat(state.coinTransferableBalance).isEqualTo(25)
        assertThat(state.coinBalance).isZero()
    }

    @Test
    fun `negative int32 values wrap into the uint32 field`() {
        // `coinBalance = v` with int32_t v and a uint32_t field (player.hpp:1811).
        val state = PlayerCoinState()

        state.setTibiaCoins(-1)
        state.setTransferableTibiaCoins(-2)

        assertThat(state.coinBalance).isEqualTo(4_294_967_295L)
        assertThat(state.coinTransferableBalance).isEqualTo(4_294_967_294L)
    }

    @Test
    fun `the public fields can be written directly like Canary`() {
        // iologindata_load_player.cpp:101 `player->coinBalance = coins;`.
        val state = PlayerCoinState()

        state.coinBalance = 12_345
        state.coinTransferableBalance = 67

        assertThat(state.coinBalance).isEqualTo(12_345)
        assertThat(state.coinTransferableBalance).isEqualTo(67)
    }
}
