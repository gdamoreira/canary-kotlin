package org.opentibia.canary.creatures.players

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.map.Position

/**
 * Transcribes the Player damage-over-time / cleanse hooks of
 * `Player::drainHealth`, `Player::drainMana` and `Player::onCleanseCondition`
 * (src/creatures/players/player.cpp:3540-3552, 6322-6348):
 *
 * - `Player::drainHealth` (player.cpp:3540-3547) plays the pain sound with the
 *   `PLAYER_SOUND_HEALTH_CHANGE` (player.hpp:128) 10% chance, drains through
 *   `Creature::drainHealth` and refreshes the stats.
 * - `Player::drainMana` (player.cpp:3549-3552) goes through
 *   `Creature::drainMana` (creature.cpp:901-907: `onAttacked`, `changeMana`,
 *   `addDamagePoints`) and refreshes the stats.
 * - `Player::onCleanseCondition` (player.cpp:6322-6348) prints the
 *   "(cleanse charm)" party message for the mapped condition types.
 *
 * `g_game().sendSingleSoundEffect` and `Player::sendStats()` are call-site
 * seams; the port's `Creature::drainMana` is not separately modelled, so the
 * three base steps are transcribed inline.
 */
class PlayerCombatHooksStateTest {

    private fun newPlayer(): Player =
        Player(id = 1, name = "Hero", maxHealth = 150, health = 100, maxMana = 200, mana = 50, position = Position(100, 100, 7))

    @Test
    fun `drainHealth plays the sound on a ten percent roll and drains without broadcast`() {
        // Player::drainHealth (player.cpp:3540-3547).
        val player = newPlayer()
        var sounds = 0
        var stats = 0

        player.drainHealth(attacker = null, damage = 30, soundRoll = 5, playSound = { sounds++ }, sendStats = { stats++ })

        assertThat(sounds).isEqualTo(1)
        assertThat(player.health).isEqualTo(70)
        assertThat(stats).isEqualTo(1)
    }

    @Test
    fun `drainHealth stays silent when the pain roll misses`() {
        // player.cpp:3541: `10 >= uniform_random(1, 100)`.
        val player = newPlayer()
        var sounds = 0

        player.drainHealth(attacker = null, damage = 200, soundRoll = 50, playSound = { sounds++ })

        assertThat(sounds).isZero()
        assertThat(player.health).isZero()
    }

    @Test
    fun `drainMana triggers the attacked hook, drains mana and records damage points`() {
        // Player::drainMana (player.cpp:3549-3552) over
        // Creature::drainMana (creature.cpp:901-907).
        val player = newPlayer()
        val attacker = Player(id = 9, name = "Enemy", maxHealth = 10, health = 10, position = Position(101, 100, 7))
        var stats = 0

        player.drainMana(attacker = attacker, manaLoss = 20, sendStats = { stats++ })

        assertThat(player.mana).isEqualTo(30)
        assertThat(player.getDamageMap()[attacker.id]?.total).isEqualTo(20)
        assertThat(stats).isEqualTo(1)
    }

    @Test
    fun `drainMana clamps at zero without an attacker`() {
        // Creature::changeMana (creature.cpp:878-891): negative mana is clamped.
        val player = newPlayer()

        player.drainMana(attacker = null, manaLoss = 500)

        assertThat(player.mana).isZero()
    }

    @Test
    fun `onCleanseCondition prints the mapped condition name`() {
        // Player::onCleanseCondition (player.cpp:6322-6348).
        val player = newPlayer()
        val messages = mutableListOf<String>()
        player.sendTextMessage = { message -> messages.add(message) }

        player.onCleanseCondition(ConditionType.CONDITION_POISON)
        player.onCleanseCondition(ConditionType.CONDITION_FEARED)

        assertThat(messages).containsExactly(
            "You are no longer poisoned. (cleanse charm)",
            "You are no longer feared. (cleanse charm)",
        )
    }

    @Test
    fun `onCleanseCondition ignores unmapped conditions`() {
        // player.cpp:6346: unmapped types fall through silently.
        val player = newPlayer()
        val messages = mutableListOf<String>()
        player.sendTextMessage = { message -> messages.add(message) }

        player.onCleanseCondition(ConditionType.CONDITION_INFIGHT)

        assertThat(messages).isEmpty()
    }
}
