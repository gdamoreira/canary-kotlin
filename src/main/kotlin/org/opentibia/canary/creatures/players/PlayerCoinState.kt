package org.opentibia.canary.creatures.players

/**
 * Mirror of the Player store-coin cache
 * (src/creatures/players/player.cpp:7556-7558, 7725-7727).
 *
 * Canary keeps `coinBalance` and `coinTransferableBalance` as private
 * `uint32_t` fields (player.hpp:1811-1812, private section from 1585) read by
 * the store protocol through `friend class ProtocolGame` (protocolgame.cpp:6821)
 * and written directly by the load path through `friend class IOLoginData`
 * (`iologindata_load_player.cpp:101`) as well as through
 * `Player::setTibiaCoins`/`Player::setTransferableTibiaCoins`. The Lua
 * `player:getTibiaCoins` accessor reads the account instead
 * (player_functions.cpp:3209-3222), so no player-level getter exists in
 * Canary and none is invented here.
 *
 * The state is exposed as an injectable holder so the host player can delegate
 * without a global (AGENTS.md 1.1); the `internal` setters stand in for the
 * Canary friend access (store writes and the load path).
 */
class PlayerCoinState(
    /** `Player::coinBalance` (player.hpp:1811); `uint32_t`. */
    coinBalance: Long = 0,
    /** `Player::coinTransferableBalance` (player.hpp:1812); `uint32_t`. */
    coinTransferableBalance: Long = 0,
) {

    /**
     * `Player::coinBalance` (player.hpp:1811, private; read by
     * `ProtocolGame::sendResourcesBalance`, protocolgame.cpp:6821).
     */
    var coinBalance: Long = coinBalance and UINT32_MAX
        internal set

    /**
     * `Player::coinTransferableBalance` (player.hpp:1812, private; friend
     * access from the store and load paths).
     */
    var coinTransferableBalance: Long = coinTransferableBalance and UINT32_MAX
        internal set

    /** `Player::setTibiaCoins(v)` (player.cpp:7556-7558): `coinBalance = v`. */
    fun setTibiaCoins(v: Int) {
        coinBalance = v.toLong() and UINT32_MAX
    }

    /** `Player::setTransferableTibiaCoins(v)` (player.cpp:7725-7727). */
    fun setTransferableTibiaCoins(v: Int) {
        coinTransferableBalance = v.toLong() and UINT32_MAX
    }

    companion object {
        /** `std::numeric_limits<uint32_t>::max()` (player.hpp:1811-1812). */
        private const val UINT32_MAX = 0xFFFF_FFFFL
    }
}
