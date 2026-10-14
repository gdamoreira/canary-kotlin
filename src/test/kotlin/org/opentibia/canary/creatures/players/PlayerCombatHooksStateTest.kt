package org.opentibia.canary.creatures.players

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.grouping.Party
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
 * The party shared-experience hooks are transcribed too:
 *
 * - `Player::onAttackedCreatureDrainHealth` (player.cpp:6563-6575)
 * - `Player::onTargetCreatureGainHealth` (player.cpp:6577-6594)
 *
 * `g_game().sendSingleSoundEffect` and `Player::sendStats()` are call-site
 * seams; the port's `Creature::drainMana` is not separately modelled, so the
 * three base steps are transcribed inline. `Creature::onAttackedCreatureDrainHealth`
 * (creature.cpp), `Creature::getMonster`/`Monster::isHostile` (monster.cpp:295)
 * and `Creature::getPlayer` are unported host members, so they are injected
 * seams.
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
        // player.cpp:3541: `10 >= uniform_random(1, 100)`. The drain routes
        // through `changeHealth(-damage, false)` (creature.cpp:893-899), so a
        // non-lethal hit leaves the clamped health behind.
        val player = newPlayer()
        var sounds = 0

        player.drainHealth(attacker = null, damage = 60, soundRoll = 50, playSound = { sounds++ })

        assertThat(sounds).isZero()
        assertThat(player.health).isEqualTo(40)
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

    // --- Player::onAttackedCreatureDrainHealth -----------------------------------

    @Test
    fun `onAttackedCreatureDrainHealth runs the base hook without a target`() {
        // player.cpp:6563-6575.
        val player = newPlayer()
        var base = 0
        var ticks = 0

        player.onAttackedCreatureDrainHealth(
            target = null,
            points = 25,
            base = { base++ },
            updatePlayerTicks = { ticks++ },
        )

        assertThat(base).isEqualTo(1)
        assertThat(ticks).isZero()
    }

    @Test
    fun `onAttackedCreatureDrainHealth skips player combat targets`() {
        // `m_party && !Combat::isPlayerCombat(target)` (6567).
        val player = newPlayer()
        Party(player)
        val other = newPlayer()
        var ticks = 0

        player.onAttackedCreatureDrainHealth(
            target = other,
            points = 25,
            isPlayerCombat = { true },
            updatePlayerTicks = { ticks++ },
        )

        assertThat(ticks).isZero()
    }

    @Test
    fun `onAttackedCreatureDrainHealth credits the party for hostile monsters`() {
        // `tmpMonster->isHostile()` (6571-6574).
        val player = newPlayer()
        Party(player)
        val monster = Monster(id = 2, name = "Rat", maxHealth = 20, health = 20, position = Position(101, 100, 7))
        var ticks = 0

        player.onAttackedCreatureDrainHealth(
            target = monster,
            points = 25,
            getMonster = { it as? Monster },
            isHostile = { true },
            updatePlayerTicks = { ticks++ },
        )

        assertThat(ticks).isEqualTo(1)
    }

    @Test
    fun `onAttackedCreatureDrainHealth ignores non-hostile monsters`() {
        // 6571-6572.
        val player = newPlayer()
        Party(player)
        val monster = Monster(id = 2, name = "Rat", maxHealth = 20, health = 20, position = Position(101, 100, 7))
        var ticks = 0

        player.onAttackedCreatureDrainHealth(
            target = monster,
            points = 25,
            getMonster = { it as? Monster },
            isHostile = { false },
            updatePlayerTicks = { ticks++ },
        )

        assertThat(ticks).isZero()
    }

    // --- Player::onTargetCreatureGainHealth --------------------------------------

    @Test
    fun `onTargetCreatureGainHealth requires a party`() {
        // `if (target && m_party)` (6577).
        val player = newPlayer()
        val monster = Monster(id = 2, name = "Rat", maxHealth = 20, health = 20, position = Position(101, 100, 7))
        var ticks = 0

        player.onTargetCreatureGainHealth(
            target = monster,
            points = 30,
            getPlayer = { null },
            updatePlayerTicks = { ticks++ },
        )

        assertThat(ticks).isZero()
    }

    @Test
    fun `onTargetCreatureGainHealth credits a partnered summon master`() {
        // 6579-6593: the target's master player decides the shared-exp credit.
        val leader = newPlayer()
        val member = newPlayer()
        val party = Party(leader)
        party.invitePlayer(member)
        party.joinParty(member)

        val summon = Monster(id = 2, name = "Summon", maxHealth = 20, health = 20, position = Position(101, 100, 7))
        summon.setMaster(member)
        var ticks = 0

        leader.onTargetCreatureGainHealth(
            target = summon,
            points = 30,
            getPlayer = { creature -> if (creature === member) member else null },
            updatePlayerTicks = { ticks++ },
        )

        assertThat(ticks).isEqualTo(1)
    }

    @Test
    fun `onTargetCreatureGainHealth ignores non-partner masters`() {
        // `if (isPartner(tmpPlayer))` (6592).
        val leader = newPlayer()
        val outsider = newPlayer()
        val party = Party(leader)
        party.invitePlayer(outsider)
        party.joinParty(outsider)

        val stranger = newPlayer()
        val summon = Monster(id = 2, name = "Summon", maxHealth = 20, health = 20, position = Position(101, 100, 7))
        summon.setMaster(stranger)
        var ticks = 0

        leader.onTargetCreatureGainHealth(
            target = summon,
            points = 30,
            getPlayer = { creature -> if (creature === stranger) stranger else null },
            updatePlayerTicks = { ticks++ },
        )

        assertThat(ticks).isZero()
    }

    // --- Player::isPartner -------------------------------------------------------

    @Test
    fun `isPartner rejects null and self`() {
        // player.cpp:7790-7795.
        val player = newPlayer()
        Party(player)

        assertThat(player.isPartner(null)).isFalse()
        assertThat(player.isPartner(player)).isFalse()
    }

    @Test
    fun `isPartner requires the same party`() {
        // `m_party == player->m_party` (7794).
        val leader = newPlayer()
        val member = newPlayer()
        val party = Party(leader)
        party.invitePlayer(member)
        party.joinParty(member)

        val outsider = newPlayer()
        Party(outsider)

        assertThat(leader.isPartner(member)).isTrue()
        assertThat(leader.isPartner(outsider)).isFalse()
    }
}
