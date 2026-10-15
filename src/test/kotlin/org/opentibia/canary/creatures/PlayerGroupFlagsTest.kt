package org.opentibia.canary.creatures

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.grouping.Group
import org.opentibia.canary.map.Position

/**
 * Transcript tests for `Player::hasFlag` / `Player::setFlag` /
 * `Player::removeFlag` (src/creatures/players/player.cpp:7853-7863).
 *
 * Canary resolves every flag through the bound `std::shared_ptr<Group>`
 * candidate's `std::array<bool, FlagLast> flags` (`Group`, groups.hpp:14-22);
 * the Kotlin [Group.flags] is the raised-flag set loaded from
 * `data/XML/groups.xml`. The port's per-player set is only the stand-in used
 * when no group is resolved.
 */
class PlayerGroupFlagsTest {

    private fun newPlayer() = Player(
        id = 1,
        name = "Flag Hero",
        maxHealth = 150,
        health = 150,
        position = Position(100, 100, 7),
    )

    @Test
    fun `hasFlag resolves through the bound group flags`() {
        val player = newPlayer()
        player.group = Group(flags = mutableSetOf(PlayerFlags_t.CannotBeMuted))

        assertThat(player.hasFlag(PlayerFlags_t.CannotBeMuted)).isTrue()
        assertThat(player.hasFlag(PlayerFlags_t.CanBroadcast)).isFalse()
    }

    @Test
    fun `hasFlag falls back to the local set only without a group`() {
        val player = newPlayer()
        player.setFlag(PlayerFlags_t.CanBroadcast)

        assertThat(player.hasFlag(PlayerFlags_t.CanBroadcast)).isTrue()
    }

    @Test
    fun `the local set is ignored while a group is resolved`() {
        val player = newPlayer()
        player.setFlag(PlayerFlags_t.CanBroadcast)
        player.group = Group()

        assertThat(player.hasFlag(PlayerFlags_t.CanBroadcast)).isFalse()
    }

    @Test
    fun `setFlag raises the resolved group flag`() {
        val player = newPlayer()
        player.group = Group()

        player.setFlag(PlayerFlags_t.CanBroadcast)

        assertThat(player.hasFlag(PlayerFlags_t.CanBroadcast)).isTrue()
    }

    @Test
    fun `removeFlag clears the resolved group flag`() {
        val player = newPlayer()
        player.group = Group(flags = mutableSetOf(PlayerFlags_t.CanBroadcast))

        player.removeFlag(PlayerFlags_t.CanBroadcast)

        assertThat(player.hasFlag(PlayerFlags_t.CanBroadcast)).isFalse()
    }
}
