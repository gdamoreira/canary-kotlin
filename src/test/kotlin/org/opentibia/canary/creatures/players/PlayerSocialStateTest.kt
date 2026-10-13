package org.opentibia.canary.creatures.players

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.account.Account
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.monsters.Faction
import org.opentibia.canary.map.Position

/**
 * Transcribes the Player social/VIP helpers
 * (src/creatures/players/player.cpp:6234-6240, 7395-7401, 7544-7554):
 *
 * - `Player::getFaction` / `setFaction` (player.cpp:6234-6240)
 * - `Player::getGuildNick` / `setGuildNick` (player.cpp:7395-7401)
 * - `Player::getPremiumDays` (player.cpp:7544-7546)
 * - `Player::getPremiumLastDay` (player.cpp:7548-7550)
 * - `Player::isVip` (player.cpp:7552-7554)
 *
 * `Player::guildNick` defaults to the empty string (player.hpp:1708) and
 * `Player::faction` defaults to `FACTION_PLAYER` (player.hpp:1858).
 * `VIP_SYSTEM_ENABLED` defaults to false (configmanager.cpp:168).
 */
class PlayerSocialStateTest {

    private fun newPlayer() = Player(
        id = 7,
        name = "Social Hero",
        maxHealth = 150,
        health = 150,
        position = Position(100, 100, 7),
    )

    @Test
    fun `guild nick defaults to empty and round trips`() {
        // player.hpp:1708 / player.cpp:7395-7401.
        val state = PlayerSocialState()

        assertThat(state.getGuildNick()).isEmpty()

        state.setGuildNick("The Legend")
        assertThat(state.getGuildNick()).isEqualTo("The Legend")
    }

    @Test
    fun `faction defaults to FACTION_PLAYER and round trips`() {
        // player.hpp:1858 / player.cpp:6234-6240.
        val state = PlayerSocialState()

        assertThat(state.getFaction()).isEqualTo(Faction.Player)

        state.setFaction(Faction.Lion)
        assertThat(state.getFaction()).isEqualTo(Faction.Lion)
    }

    @Test
    fun `premium helpers read the account`() {
        // Player::getPremiumDays / getPremiumLastDay (player.cpp:7544-7550).
        val player = newPlayer()
        val now = 1_000_000L
        player.account = Account(
            id = 1,
            name = "acc",
            premiumLastDay = now + PREMIUM_DAYS * 86_400,
        )

        assertThat(player.getPremiumDays(now)).isEqualTo(PREMIUM_DAYS)
        assertThat(player.getPremiumLastDay()).isEqualTo(now + PREMIUM_DAYS * 86_400)
    }

    @Test
    fun `premium helpers are zero without an account`() {
        // The C++ dereferences `account`; the port keeps the safe default.
        val player = newPlayer()

        assertThat(player.getPremiumDays(1_000_000L)).isZero()
        assertThat(player.getPremiumLastDay()).isZero()
    }

    @Test
    fun `getAccountId reads the account and falls back to zero`() {
        // Player::getAccountId (player.cpp:12631-12633).
        val player = newPlayer()

        assertThat(player.getAccountId()).isZero()

        player.account = Account(id = 77, name = "acc")
        assertThat(player.getAccountId()).isEqualTo(77)
    }

    @Test
    fun `isVip requires the enabled system and premium time`() {
        // Player::isVip (player.cpp:7552-7554).
        val player = newPlayer()
        val now = 2_000_000L
        player.account = Account(id = 1, name = "acc", premiumLastDay = now + 86_400)

        assertThat(player.isVip(vipSystemEnabled = false, now = now)).isFalse()
        assertThat(player.isVip(vipSystemEnabled = true, now = now)).isTrue()

        player.account = null
        assertThat(player.isVip(vipSystemEnabled = true, now = now)).isFalse()
    }

    private companion object {
        const val PREMIUM_DAYS = 5
    }
}
