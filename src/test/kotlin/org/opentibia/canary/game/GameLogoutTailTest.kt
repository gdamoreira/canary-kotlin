package org.opentibia.canary.game

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.grouping.Party
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.items.Item
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.social.guild.Guild

/**
 * Wave 66 C1 transcript tests for the logout tail of `Game::removeCreature`
 * (src/game/game.cpp:1551): `Player::onRemoveCreature(creature, isLogout=true)`
 * (src/creatures/players/player.cpp:12318) leaves the party
 * (`Party::leaveParty(player, true)`, party.cpp:169), erases the online guild
 * entry (`Guild::removeMember`, guild.cpp:21) and saves the player through
 * `g_saveManager().savePlayer(player)` (player.cpp:12353), while the ported
 * `Creature::removeList()` step (game.cpp:1602 -> `Player::removeList`,
 * player.cpp:4511 -> `Game::removePlayer`, game.cpp:11631) unregisters the
 * session from the game registry.
 */
class GameLogoutTailTest {

    private val center = Position(1000, 1000, 7)

    private fun newGame(): Game = Game(GameMap(), GameDispatcher())

    private fun Game.groundTile(position: Position = center) {
        map.getOrCreateTile(position).ground = Item(id = 100)
    }

    private fun player(id: Long = 1, name: String = "Knight"): Player =
        Player(id = id, name = name, maxHealth = 150, health = 150, position = center)

    private fun guild(id: Long = 1, name: String = "Red Rose"): Guild = Guild(id, name)

    @Test
    fun `logout tail runs for a placed player and unregisters the session`() {
        val game = newGame()
        game.groundTile()
        val player = player()
        val guild = guild()
        val party = Party(leader = player)
        player.setGuild(guild)
        var saves = 0
        player.savePlayer = { saves++ }
        assertThat(game.internalPlaceCreature(player, center)).isTrue()
        game.addPlayer(player)

        assertThat(game.removeCreature(player, isLogout = true)).isTrue()

        assertThat(guild.getMembersOnline()).isEmpty()
        assertThat(player.getParty()).isNull()
        assertThat(party.getLeader()).isNull()
        assertThat(saves).isEqualTo(1)
        assertThat(player.isRemoved()).isTrue()
        assertThat(game.getPlayerByID(1L)).isNull()
    }

    @Test
    fun `logout tail runs for an unplaced session player`() {
        // The port registers session players through `Game::addPlayer` instead
        // of `internalPlaceCreature`'s `Creature::addList` (PARITY GAP), so a
        // logged-out session has no parent tile; the logout tail must still run.
        val game = newGame()
        val player = player()
        val guild = guild()
        val party = Party(leader = player)
        player.setGuild(guild)
        var saves = 0
        player.savePlayer = { saves++ }
        game.addPlayer(player)

        assertThat(game.removeCreature(player, isLogout = true)).isTrue()

        assertThat(guild.getMembersOnline()).isEmpty()
        assertThat(player.getParty()).isNull()
        assertThat(party.getLeader()).isNull()
        assertThat(saves).isEqualTo(1)
        assertThat(player.isRemoved()).isTrue()
        assertThat(game.getPlayerByID(1L)).isNull()
    }

    @Test
    fun `a non-logout removal keeps the guild and party but still saves the player`() {
        val game = newGame()
        game.groundTile()
        val player = player()
        val guild = guild()
        val party = Party(leader = player)
        player.setGuild(guild)
        var saves = 0
        player.savePlayer = { saves++ }
        assertThat(game.internalPlaceCreature(player, center)).isTrue()
        game.addPlayer(player)

        assertThat(game.removeCreature(player, isLogout = false)).isTrue()

        assertThat(guild.getMembersOnline()).containsExactly(player)
        assertThat(player.getParty()).isSameAs(party)
        assertThat(saves).isEqualTo(1)
        assertThat(game.getPlayerByID(1L)).isNull()
    }

    @Test
    fun `relog after logout keeps a single guild online entry`() {
        val game = newGame()
        val guild = guild()
        game.addGuild(guild)
        val first = player()
        first.setGuild(guild)
        game.addPlayer(first)

        assertThat(game.removeCreature(first, isLogout = true)).isTrue()

        val second = player()
        second.setGuild(guild)
        game.addPlayer(second)

        assertThat(guild.getMembersOnline()).containsExactly(second)
        assertThat(guild.getMemberCountOnline()).isEqualTo(1L)
    }
}
