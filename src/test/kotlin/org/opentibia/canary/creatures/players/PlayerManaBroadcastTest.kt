package org.opentibia.canary.creatures.players

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.PlayerFlags_t
import org.opentibia.canary.creatures.grouping.Party
import org.opentibia.canary.game.Game
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.items.Item
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position

/**
 * Transcript tests for `Player::changeMana` (src/creatures/players/player.cpp:6765-6772):
 *
 * - the `HasInfiniteMana` flag gates the `Creature::changeMana`
 *   (creature.cpp:869-875) clamp;
 * - `g_game().addPlayerMana(*this)` (game.cpp:9326-9331) broadcasts the party
 *   mana percent unconditionally, even behind the infinite-mana flag;
 * - `sendStats()` (player.cpp:2347) refreshes the client stats.
 *
 * The `g_game()` reach is the per-player [Player.addPlayerMana] seam, bound by
 * `Game::internalPlaceCreature` once the player enters the world
 * (game.cpp:1495); an unplaced player keeps the inert default.
 */
class PlayerManaBroadcastTest {

    private val center = Position(1000, 1000, 7)

    private fun newPlayer(
        id: Long = 1,
        name: String = "Hero",
        mana: Int = 50,
        maxMana: Int = 200,
    ): Player = Player(
        id = id,
        name = name,
        maxHealth = 150,
        health = 100,
        maxMana = maxMana,
        mana = mana,
        position = center,
    )

    @Test
    fun `changeMana broadcasts through addPlayerMana and refreshes the stats`() {
        // player.cpp:6765-6772: gate, `g_game().addPlayerMana`, `sendStats`.
        val player = newPlayer()
        val broadcasts = mutableListOf<Player>()
        player.addPlayerMana = { broadcasts.add(it) }
        var stats = 0
        player.sendStats = { stats++ }

        player.changeMana(30)

        assertThat(player.mana).isEqualTo(80)
        assertThat(broadcasts).containsExactly(player)
        assertThat(stats).isEqualTo(1)
    }

    @Test
    fun `changeMana clamps a restore at the maximum mana`() {
        // Creature::changeMana (creature.cpp:869-875): `mana += min(delta, max - mana)`.
        val player = newPlayer()

        player.changeMana(500)

        assertThat(player.mana).isEqualTo(200)
    }

    @Test
    fun `changeMana clamps a drain at zero`() {
        // Creature::changeMana (creature.cpp:874): `mana = max(0, mana + delta)`.
        val player = newPlayer(mana = 10)

        player.changeMana(-500)

        assertThat(player.mana).isZero()
    }

    @Test
    fun `the HasInfiniteMana flag skips the mana change but still broadcasts`() {
        // player.cpp:6766-6771: the flag only gates `Creature::changeMana`; the
        // `addPlayerMana` broadcast and `sendStats` run unconditionally.
        val player = newPlayer()
        player.setFlag(PlayerFlags_t.HasInfiniteMana)
        val broadcasts = mutableListOf<Player>()
        player.addPlayerMana = { broadcasts.add(it) }
        var stats = 0
        player.sendStats = { stats++ }

        player.changeMana(-500)

        assertThat(player.mana).isEqualTo(50)
        assertThat(broadcasts).containsExactly(player)
        assertThat(stats).isEqualTo(1)
    }

    @Test
    fun `Player getPlayer returns itself`() {
        // `Player::getPlayer()` (player.cpp:9102-9104) returns the player.
        val player = newPlayer()

        assertThat(player.getPlayer()).isSameAs(player)
    }

    @Test
    fun `internalPlaceCreature binds the player mana broadcast to the party`() {
        // `Game::internalPlaceCreature` (game.cpp:1495) wires the placed
        // Player's `g_game()` reach; `Game::addPlayerMana` fans the mana
        // percent out through `Party::updatePlayerMana` (party.cpp:846-863).
        val game = Game(GameMap(), GameDispatcher())
        game.map.getOrCreateTile(center).ground = Item(id = 100)
        val member = Player(
            id = 2,
            name = "Member",
            maxHealth = 150,
            health = 100,
            maxMana = 200,
            mana = 50,
            position = center,
        )
        game.addPlayer(member)
        val leader = Player(
            id = 1,
            name = "Leader",
            maxHealth = 150,
            health = 100,
            maxMana = 200,
            mana = 50,
            position = center,
        )
        game.addPlayer(leader)
        val party = Party(leader)
        party.invitePlayer(member)
        party.joinParty(member)
        assertThat(game.internalPlaceCreature(leader, center)).isTrue()
        val broadcasts = mutableListOf<Triple<Party, Player, Int>>()
        game.partyUpdatePlayerMana = { partyTarget, target, percent ->
            broadcasts.add(Triple(partyTarget, target, percent))
        }

        leader.changeMana(30)

        assertThat(leader.mana).isEqualTo(80)
        assertThat(broadcasts).containsExactly(Triple(party, leader, 40))
    }
}
