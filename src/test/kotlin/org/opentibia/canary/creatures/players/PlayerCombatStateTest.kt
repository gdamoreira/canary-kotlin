package org.opentibia.canary.creatures.players

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.ChaseMode
import org.opentibia.canary.creatures.FightMode
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.players.pvp.PvpMode_t
import org.opentibia.canary.map.Position

/**
 * Transcribes the Player combat/targeting state helpers
 * (src/creatures/players/player.cpp:6196-6228):
 *
 * - `Player::setChaseMode` (player.cpp:6196-6213)
 * - `Player::setFightMode` (player.cpp:6215-6220)
 * - `Player::setSecureMode` (player.cpp:6222-6224)
 * - `Player::setPvpMode` / `getPvpMode` (player.cpp:6226-6232)
 *
 * The `chaseMode` bool is the port's [ChaseMode] enum and `fightMode` is the
 * port's [FightMode] (creatures_definitions.hpp:812-816). `Player::sendStats()`
 * is a protocol seam injected at the call site because the port's [Player]
 * (owned outside this package) has no `sendStats` seam.
 */
class PlayerCombatStateTest {

    private fun newPlayer(id: Long, position: Position) = Player(
        id = id,
        name = "Hero$id",
        maxHealth = 150,
        health = 150,
        position = position,
    ).apply {
        // The attack/follow sight gates run through `Player::canSee`
        // (player.cpp:1402), which is inert without an attached client; these
        // tests model a connected player.
        hasClient = { true }
    }

    @Test
    fun `setChaseMode starts following the attacked creature`() {
        // Player::setChaseMode (player.cpp:6196-6205).
        val player = newPlayer(1, Position(100, 100, 7))
        val target = newPlayer(2, Position(101, 100, 7))
        assertThat(player.setAttackedCreature(target)).isTrue()
        assertThat(player.getFollowCreature()).isNull()

        player.setChaseMode(true)

        assertThat(player.chaseMode).isEqualTo(ChaseMode.Follow)
        assertThat(player.getFollowCreature()).isSameAs(target)
    }

    @Test
    fun `setChaseMode keeps the existing follow creature`() {
        // player.cpp:6202-6205: only follows when no creature is followed yet.
        val player = newPlayer(1, Position(100, 100, 7))
        val first = newPlayer(2, Position(101, 100, 7))
        val second = newPlayer(3, Position(100, 101, 7))
        player.setAttackedCreature(first)
        player.setChaseMode(true)
        player.setAttackedCreature(second)

        player.setChaseMode(true)

        assertThat(player.getFollowCreature()).isSameAs(first)
    }

    @Test
    fun `setChaseMode false cancels the follow and latches cancelNextWalk`() {
        // player.cpp:6206-6210: `setFollowCreature(nullptr)` + cancelNextWalk.
        val player = newPlayer(1, Position(100, 100, 7))
        val target = newPlayer(2, Position(101, 100, 7))
        player.setAttackedCreature(target)
        player.setChaseMode(true)

        player.setChaseMode(false)

        assertThat(player.chaseMode).isEqualTo(ChaseMode.StandStill)
        assertThat(player.getFollowCreature()).isNull()
        assertThat(player.cancelNextWalk).isTrue()
    }

    @Test
    fun `setChaseMode without an attacked creature only flips the mode`() {
        // player.cpp:6206-6210: the else-if requires an attacked creature.
        val player = newPlayer(1, Position(100, 100, 7))

        player.setChaseMode(true)
        player.setChaseMode(false)

        assertThat(player.chaseMode).isEqualTo(ChaseMode.StandStill)
        assertThat(player.cancelNextWalk).isFalse()
    }

    @Test
    fun `attack and follow sight windows use the client viewport`() {
        // Creature::setAttackedCreature (creature.cpp:1011-1030) and
        // Creature::setFollowCreature (creature.cpp:1132-1166) gate on the
        // virtual canSee; for a Player that is ProtocolGame::canSee's
        // +/-8/+/-6 client window, tighter than the creature +/-11 map viewport.
        val player = newPlayer(1, Position(100, 100, 7))
        val insideEast = newPlayer(2, Position(109, 100, 7))
        val outsideEast = newPlayer(3, Position(110, 100, 7))
        val outsideSouth = newPlayer(4, Position(100, 108, 7))

        assertThat(player.setAttackedCreature(insideEast)).isTrue()
        assertThat(player.getAttackedCreature()).isSameAs(insideEast)

        assertThat(player.setAttackedCreature(outsideEast)).isFalse()
        assertThat(player.getAttackedCreature()).isNull()

        assertThat(player.setFollowCreature(outsideSouth)).isFalse()
        assertThat(player.getFollowCreature()).isNull()

        assertThat(player.setFollowCreature(insideEast)).isTrue()
        assertThat(player.getFollowCreature()).isSameAs(insideEast)
    }

    @Test
    fun `setFightMode updates the mode and refreshes stats and skills`() {
        // Player::setFightMode (player.cpp:6215-6220).
        val player = newPlayer(1, Position(100, 100, 7))
        var stats = 0
        var skills = 0
        player.sendSkills = { skills++ }

        player.setFightMode(FightMode.Defense) { stats++ }

        assertThat(player.fightMode).isEqualTo(FightMode.Defense)
        assertThat(stats).isEqualTo(1)
        assertThat(skills).isEqualTo(1)
    }

    @Test
    fun `setSecureMode toggles the secure mode`() {
        // Player::setSecureMode (player.cpp:6222-6224).
        val player = newPlayer(1, Position(100, 100, 7))

        player.setSecureMode(false)

        assertThat(player.secureMode).isFalse()
    }

    @Test
    fun `setPvpMode delegates to the PlayerPvp component`() {
        // Player::setPvpMode / getPvpMode (player.cpp:6226-6232).
        val player = newPlayer(1, Position(100, 100, 7))

        player.setPvpMode(PvpMode_t.PVP_MODE_WHITE_HAND)

        assertThat(player.getPvpMode()).isEqualTo(player.pvpPlayer.getMode())
    }

    @Test
    fun `lastHitIsPlayer resolves the killer and its master`() {
        // Player::lastHitIsPlayer (player.cpp:6747-6758).
        val player = newPlayer(1, Position(100, 100, 7))
        val master = newPlayer(2, Position(101, 100, 7))
        val summon = Monster(id = 3, name = "Summon", maxHealth = 10, health = 10, position = Position(100, 100, 7))
        summon.setMaster(master)

        assertThat(lastHitIsPlayer(null)).isFalse()
        assertThat(lastHitIsPlayer(player)).isTrue()
        assertThat(lastHitIsPlayer(Monster(4, "Rat", 10, 10, position = Position(100, 100, 7)))).isFalse()
        assertThat(lastHitIsPlayer(summon)).isTrue()
    }
}
