package org.opentibia.canary.creatures.players

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.PlayerFlags_t
import org.opentibia.canary.creatures.grouping.Party
import org.opentibia.canary.creatures.monsters.MonsterType
import org.opentibia.canary.map.Position

/**
 * Transcribes the Player damage-over-time / cleanse hooks of
 * `Player::drainHealth`, `Player::drainMana` and `Player::onCleanseCondition`
 * (src/creatures/players/player.cpp:3540-3552, 6322-6348):
 *
 * - `Player::drainHealth` (player.cpp:3540-3547) plays the pain sound with the
 *   `PLAYER_SOUND_HEALTH_CHANGE` (player.hpp:128) 10% chance, drains through
 *   `Creature::drainHealth` and refreshes the stats.
 * - `Player::drainMana` (player.cpp:3549-3552) is the `Creature::drainMana`
 *   override (creature.cpp:901-907: `onAttacked`, `changeMana`,
 *   `addDamagePoints`) with the stats refresh.
 * - `Player::onCleanseCondition` (player.cpp:6322-6348) prints the
 *   "(cleanse charm)" party message for the mapped condition types.
 *
 * The party shared-experience hooks are transcribed too:
 *
 * - `Player::onAttackedCreatureDrainHealth` (player.cpp:6563-6575) is a
 *   `Creature::onAttackedCreatureDrainHealth` override on the [Player] class,
 *   so the credit rides the virtual dispatch.
 * - `Player::onTargetCreatureGainHealth` (player.cpp:6577-6594) is the
 *   `Creature::onTargetCreatureGainHealth` override on the [Player] class, so
 *   `Creature::gainHealth`'s `healer->onTargetCreatureGainHealth` call
 *   (creature.cpp:889) reaches the party credit.
 *
 * `g_game().sendSingleSoundEffect` and `Player::sendStats()` are call-site
 * seams; `Monster::isHostile` (monster.cpp:295-297) resolves through the
 * player's `monsterTypeProvider` (`g_monsters().getMonsterType`).
 */
class PlayerCombatHooksStateTest {

    private fun newPlayer(id: Long = 1, name: String = "Hero"): Player =
        Player(id = id, name = name, maxHealth = 150, health = 100, maxMana = 200, mana = 50, position = Position(100, 100, 7))

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
    fun `drainHealth credits the attacker on the damage map`() {
        // Player::drainHealth -> Creature::drainHealth(attacker, damage)
        // (creature.cpp:893-899) -> onAttackedCreatureDrainHealth.
        val player = newPlayer()
        val attacker = newPlayer(id = 9, name = "Enemy")

        player.drainHealth(attacker = attacker, damage = 30)

        assertThat(player.health).isEqualTo(70)
        assertThat(player.getDamageMap()[9L]?.total).isEqualTo(30)
        assertThat(player.lastHitCreatureId).isEqualTo(9L)
    }

    @Test
    fun `drainMana triggers the attacked hook, drains mana and records damage points`() {
        // Player::drainMana (player.cpp:3549-3552) over
        // Creature::drainMana (creature.cpp:901-907). The virtual
        // `Player::changeMana` (player.cpp:6765-6772) refreshes the stats once
        // and `Player::drainMana`'s own tail refreshes them again, exactly as
        // the C++ flow does.
        val player = newPlayer()
        val attacker = newPlayer(id = 9, name = "Enemy")
        var stats = 0
        player.sendStats = { stats++ }

        player.drainMana(attacker, 20)

        assertThat(player.mana).isEqualTo(30)
        assertThat(player.getDamageMap()[attacker.id]?.total).isEqualTo(20)
        assertThat(stats).isEqualTo(2)
    }

    @Test
    fun `drainMana clamps at zero without an attacker`() {
        // Creature::changeMana (creature.cpp:869-875): negative mana is clamped.
        val player = newPlayer()

        player.drainMana(null, 500)

        assertThat(player.mana).isZero()
        assertThat(player.getDamageMap()).isEmpty()
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
    fun `onAttackedCreatureDrainHealth stamps the base damage map without a party`() {
        // Creature::onAttackedCreatureDrainHealth (creature.cpp:1277-1283)
        // through the Player override (player.cpp:6563-6565).
        val player = newPlayer()
        val monster = Monster(id = 2, name = "Rat", maxHealth = 20, health = 20, position = Position(101, 100, 7))

        player.onAttackedCreatureDrainHealth(monster, 25)

        assertThat(monster.getDamageMap()[player.id]?.total).isEqualTo(25)
        assertThat(monster.lastHitCreatureId).isEqualTo(player.id)
    }

    @Test
    fun `onAttackedCreatureDrainHealth ignores a null target`() {
        // player.cpp:6565 `if (target)`.
        val player = newPlayer()

        player.onAttackedCreatureDrainHealth(null, 25)
    }

    @Test
    fun `onAttackedCreatureDrainHealth credits the party tick for a hostile monster`() {
        // `tmpMonster->isHostile()` -> Party::updatePlayerTicks (6571-6574).
        val leader = newPlayer()
        val member = newPlayer(id = 2, name = "Member")
        val party = Party(leader)
        party.invitePlayer(member)
        party.joinParty(member)
        member.setFlag(PlayerFlags_t.NotGainInFight)
        assertThat(party.isSharedExperienceEnabled()).isFalse()

        val monster = Monster(id = 3, name = "Rat", maxHealth = 100, health = 100, position = Position(101, 100, 7))
        monster.drainHealth(leader, 25)

        assertThat(monster.getDamageMap()[leader.id]?.total).isEqualTo(25)
        assertThat(party.isSharedExperienceEnabled()).isTrue()
    }

    @Test
    fun `onAttackedCreatureDrainHealth ignores non-hostile monsters`() {
        // `Monster::isHostile()` (monster.cpp:295-297) over
        // `m_monsterType->info.isHostile`.
        val leader = newPlayer()
        val member = newPlayer(id = 2, name = "Member")
        val party = Party(leader)
        party.invitePlayer(member)
        party.joinParty(member)
        member.setFlag(PlayerFlags_t.NotGainInFight)
        leader.monsterTypeProvider = { name -> MonsterType(name).apply { info.isHostile = false } }

        val monster = Monster(id = 3, name = "Rat", maxHealth = 100, health = 100, position = Position(101, 100, 7))
        monster.drainHealth(leader, 25)

        assertThat(party.isSharedExperienceEnabled()).isFalse()
    }

    @Test
    fun `onAttackedCreatureDrainHealth skips player combat targets`() {
        // `m_party && !Combat::isPlayerCombat(target)` (6567).
        val leader = newPlayer()
        val member = newPlayer(id = 2, name = "Member")
        val party = Party(leader)
        party.invitePlayer(member)
        party.joinParty(member)
        member.setFlag(PlayerFlags_t.NotGainInFight)

        val victim = newPlayer(id = 3, name = "Victim")
        victim.drainHealth(leader, 25)

        assertThat(victim.getDamageMap()[leader.id]?.total).isEqualTo(25)
        assertThat(party.isSharedExperienceEnabled()).isFalse()
    }

    // --- Player::onTargetCreatureGainHealth --------------------------------------

    @Test
    fun `gainHealth rides the virtual dispatch into the Player party credit`() {
        // Creature::gainHealth (creature.cpp:886-891) calls
        // `healer->onTargetCreatureGainHealth(target, healthGain)`, so the
        // Player override (player.cpp:6577-6594) runs through the summon's
        // master and credits `Party::updatePlayerTicks`.
        val leader = newPlayer()
        val member = newPlayer(id = 2, name = "Member")
        val party = Party(leader)
        party.invitePlayer(member)
        party.joinParty(member)
        member.setFlag(PlayerFlags_t.NotGainInFight)
        assertThat(party.isSharedExperienceEnabled()).isFalse()

        val summon = Monster(id = 3, name = "Summon", maxHealth = 100, health = 50, position = Position(101, 100, 7))
        summon.setMaster(member)

        summon.gainHealth(leader, 30)

        assertThat(summon.health).isEqualTo(80)
        assertThat(party.isSharedExperienceEnabled()).isTrue()
    }

    @Test
    fun `gainHealth requires a healer party`() {
        // `if (target && m_party)` (player.cpp:6577).
        val member = newPlayer(id = 2, name = "Member")
        val party = Party(member)
        val healer = newPlayer(id = 9, name = "Healer")
        val summon = Monster(id = 3, name = "Summon", maxHealth = 100, health = 50, position = Position(101, 100, 7))
        summon.setMaster(member)

        summon.gainHealth(healer, 30)

        assertThat(summon.health).isEqualTo(80)
        assertThat(party.isSharedExperienceEnabled()).isFalse()
    }

    @Test
    fun `gainHealth ignores non-partner masters`() {
        // `if (isPartner(tmpPlayer))` (player.cpp:6592).
        val leader = newPlayer()
        val member = newPlayer(id = 2, name = "Member")
        val party = Party(leader)
        party.invitePlayer(member)
        party.joinParty(member)
        member.setFlag(PlayerFlags_t.NotGainInFight)

        val stranger = newPlayer(id = 9, name = "Stranger")
        val summon = Monster(id = 3, name = "Summon", maxHealth = 100, health = 50, position = Position(101, 100, 7))
        summon.setMaster(stranger)

        summon.gainHealth(leader, 30)

        assertThat(party.isSharedExperienceEnabled()).isFalse()
    }

    @Test
    fun `gainHealth leaves a direct party-member target to the master path`() {
        // player.cpp:6579-6581: `isPartner(tmpPlayer)` is evaluated with a null
        // `tmpPlayer`, so the first branch can never reassign the target
        // player; only the master branch (6582-6588) credits.
        val leader = newPlayer()
        val member = newPlayer(id = 2, name = "Member")
        val party = Party(leader)
        party.invitePlayer(member)
        party.joinParty(member)
        member.setFlag(PlayerFlags_t.NotGainInFight)

        member.gainHealth(leader, 30)

        assertThat(member.health).isEqualTo(130)
        assertThat(party.isSharedExperienceEnabled()).isFalse()
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
