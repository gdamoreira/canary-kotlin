package org.opentibia.canary.creatures

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.account.Account
import org.opentibia.canary.creatures.grouping.Group
import org.opentibia.canary.map.Position

class PlayerAccessTest {

    private val now = 1_700_000_000L

    private fun newPlayer() = Player(
        id = 9,
        name = "Access Hero",
        maxHealth = 150,
        health = 150,
        position = Position(100, 100, 7),
    )

    private fun premiumAccount(remainingDays: Int) = Account(
        id = 1,
        name = "premium-hero",
        passwordHash = "hash",
    ).setPremiumDays(remainingDays, now)

    @Test
    fun `account with remaining premium days yields a premium player`() {
        val player = newPlayer()
        player.account = premiumAccount(10)

        assertThat(player.isPremium(now)).isTrue()
    }

    @Test
    fun `expired premium yields a non-premium player`() {
        val player = newPlayer()
        player.account = premiumAccount(0)

        assertThat(player.isPremium(now)).isFalse()
    }

    @Test
    fun `access group grants isAccessPlayer`() {
        val player = newPlayer()
        player.group = Group(id = 6, access = true)

        assertThat(player.isAccessPlayer()).isTrue()
    }

    @Test
    fun `player without an account is not premium`() {
        assertThat(newPlayer().isPremium(now)).isFalse()
    }

    @Test
    fun `FREE_PREMIUM config forces premium without an account`() {
        val player = newPlayer()
        player.freePremium = true

        assertThat(player.isPremium(now)).isTrue()
    }
}
