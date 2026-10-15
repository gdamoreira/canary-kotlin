package org.opentibia.canary.creatures.players

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.PlayerFlags_t
import org.opentibia.canary.creatures.grouping.Party
import org.opentibia.canary.map.Position

/**
 * Transcribes the Player experience-gain hook
 * (src/creatures/players/player.cpp:6709-6722):
 *
 * - `Player::onGainExperience` (player.cpp:6709-6722)
 *
 * `Player::onGainSharedExperience` (player.cpp:6724-6726) and
 * `Player::gainExperience` (player.cpp:6701-6707) already live on the host
 * (`creatures/Creature.kt:2969`, `players/PlayerExperienceState.kt:95`).
 * `Creature::onGainExperience` is not ported and is a call-site seam.
 */
class PlayerExperienceHooksStateTest {

    private val baseCalls = mutableListOf<Pair<Long, Creature?>>()
    private val gainCalls = mutableListOf<Pair<Long, Creature?>>()
    private val sharedCalls = mutableListOf<Pair<Long, Creature?>>()

    private fun newPlayer(): Player =
        Player(id = 41, name = "Hunter", maxHealth = 150, health = 150, position = Position(100, 100, 7))

    private fun Player.onGain(
        gainExp: Long,
        target: Creature? = null,
        sharedActive: (Party) -> Boolean = { false },
        sharedEnabled: (Party) -> Boolean = { false },
    ) {
        onGainExperience(
            gainExp = gainExp,
            target = target,
            isSharedExperienceActive = sharedActive,
            isSharedExperienceEnabled = sharedEnabled,
            shareExperience = { _, exp, creature -> sharedCalls.add(exp to creature) },
            creatureOnGainExperience = { exp, creature -> baseCalls.add(exp to creature) },
            gainExperience = { exp, creature -> gainCalls.add(exp to creature) },
        )
    }

    @Test
    fun `onGainExperience is blocked by the NotGainExperience flag`() {
        val player = newPlayer()
        player.setFlag(PlayerFlags_t.NotGainExperience)

        player.onGain(500)

        assertThat(baseCalls).isEmpty()
        assertThat(gainCalls).isEmpty()
    }

    @Test
    fun `onGainExperience routes monster kills through the active shared party`() {
        // player.cpp:6713: `target && !target->getPlayer() && ...`; a player
        // target (target->getPlayer() != null) never routes through the
        // shared-experience sink.
        val player = newPlayer()
        val leader = newPlayer()
        player.setParty(Party(leader))
        val monster = Monster(id = 2, name = "Rat", maxHealth = 20, health = 20, position = Position(101, 100, 7))

        player.onGain(500, monster, sharedActive = { true }, sharedEnabled = { true })

        assertThat(sharedCalls).containsExactly(500L to monster)
        assertThat(baseCalls).isEmpty()
        assertThat(gainCalls).isEmpty()
    }

    @Test
    fun `onGainExperience keeps a player target on the base pipeline`() {
        // `!target->getPlayer()` (player.cpp:6713) is false for a player.
        val player = newPlayer()
        val leader = newPlayer()
        player.setParty(Party(leader))
        val playerTarget = newPlayer()

        player.onGain(500, playerTarget, sharedActive = { true }, sharedEnabled = { true })

        assertThat(sharedCalls).isEmpty()
        assertThat(baseCalls).containsExactly(500L to playerTarget)
        assertThat(gainCalls).containsExactly(500L to playerTarget)
    }

    @Test
    fun `onGainExperience forwards to the creature base and gain pipeline`() {
        val player = newPlayer()
        val monster = newPlayer()

        player.onGain(500, monster)

        assertThat(baseCalls).containsExactly(500L to monster)
        assertThat(gainCalls).containsExactly(500L to monster)
    }

    @Test
    fun `onGainExperience forwards without a target`() {
        val player = newPlayer()

        player.onGain(700)

        assertThat(baseCalls).containsExactly(700L to null)
        assertThat(gainCalls).containsExactly(700L to null)
    }
}
