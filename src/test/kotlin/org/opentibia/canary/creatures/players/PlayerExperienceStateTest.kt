package org.opentibia.canary.creatures.players

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.PlayerFlags_t
import org.opentibia.canary.map.Position

/**
 * Transcribes the Player experience-gain family:
 *
 * - `Creature::getDamageRatio` (creature.cpp:1168-1190)
 * - `Player::getGainedExperience` (player.cpp:6180-6189)
 * - `Player::gainExperience` (player.cpp:6701-6707)
 *
 * `EXPERIENCE_FROM_PLAYERS` (configmanager.cpp:123, default false) and
 * `EXP_FROM_PLAYERS_LEVEL_RANGE` (configmanager.cpp:122, default 75) are
 * injected seams; `Creature::getDamageMap` comes from the [PlayerDeathState]
 * `CountBlock` mirror, and `Player::addExperience(target, exp, sendText)`
 * (player.cpp:3613) is a seam because the host player owns only the reduced
 * one-argument form.
 */
class PlayerExperienceStateTest {

    private val recorder = AddExperienceRecorder()

    private class AddExperienceRecorder {
        var calls = 0
        var target: Creature? = null
        var gainExp: Long = -1
        var sendText = false
    }

    private fun newPlayer(
        experience: Long = 1_000,
        level: Int = 100,
    ) = Player(
        id = 41,
        name = "Experience Hero",
        maxHealth = 150,
        health = 150,
        position = Position(100, 100, 7),
    ).apply {
        this.experience = experience
        this.level = level
        // DEATH_LOSE_PERCENT (configmanager.cpp:245) fixes getLostPercent to
        // 10%, making the expected gain deterministic.
        deathLosePercent = 10
    }

    private fun newAttacker(level: Int = 100) = Player(
        id = 42,
        name = "Attacker",
        maxHealth = 150,
        health = 150,
        position = Position(101, 100, 7),
    ).apply { this.level = level }

    private fun newState(
        player: Player = newPlayer(),
        experienceFromPlayers: Boolean = true,
        levelRange: Int = 75,
        skillLoss: Boolean = true,
        staminaMinutes: Int = 2520,
        damageMap: Map<Long, CountBlock> = emptyMap(),
    ) = PlayerExperienceState(
        player = player,
        experienceFromPlayers = experienceFromPlayers,
        expFromPlayersLevelRange = levelRange,
        getSkillLoss = { skillLoss },
        staminaMinutes = { staminaMinutes },
        getDamageMap = { damageMap },
        addExperience = { target, exp, sendText ->
            recorder.calls++
            recorder.target = target
            recorder.gainExp = exp
            recorder.sendText = sendText
        },
    )

    @Test
    fun `damage ratio splits the damage map by attacker id`() {
        // creature.cpp:1168-1190.
        val attacker = newAttacker()
        val state = newState(
            damageMap = mapOf(
                attacker.id to CountBlock(total = 300),
                99L to CountBlock(total = 700),
            ),
        )

        assertThat(state.getDamageRatio(attacker)).isEqualTo(0.3)
    }

    @Test
    fun `damage ratio ignores zero ids and zero totals`() {
        // creature.cpp:1174-1177: `attackerId == 0 || totalDamageValue == 0`.
        val attacker = newAttacker()
        val state = newState(
            damageMap = mapOf(
                0L to CountBlock(total = 999),
                attacker.id to CountBlock(total = 100),
                98L to CountBlock(total = 0),
            ),
        )

        assertThat(state.getDamageRatio(attacker)).isEqualTo(1.0)
    }

    @Test
    fun `damage ratio is zero when no damage was recorded`() {
        // creature.cpp:1184-1186.
        val state = newState()

        assertThat(state.getDamageRatio(newAttacker())).isZero()
    }

    @Test
    fun `gained experience takes 75 percent of the damage share`() {
        // player.cpp:6180-6189: floor(getLostExperience() * ratio * 0.75).
        // experience 1000 at a 10% loss = 100 lost experience.
        val attacker = newAttacker()
        val state = newState(
            damageMap = mapOf(
                attacker.id to CountBlock(total = 300),
                99L to CountBlock(total = 700),
            ),
        )

        assertThat(state.getGainedExperience(attacker)).isEqualTo(22)
    }

    @Test
    fun `gained experience is zero for the player's own damage`() {
        // player.cpp:6183: `attackerPlayer.get() != this`.
        val player = newPlayer()
        val state = newState(
            player = player,
            damageMap = mapOf(player.id to CountBlock(total = 1_000)),
        )

        assertThat(state.getGainedExperience(player)).isZero()
    }

    @Test
    fun `gained experience respects the level range`() {
        // player.cpp:6183: `abs(attackerLevel - level) <= EXP_FROM_PLAYERS_LEVEL_RANGE`.
        val attacker = newAttacker(level = 176)
        val state = newState(damageMap = mapOf(attacker.id to CountBlock(total = 1_000)))

        assertThat(state.getGainedExperience(attacker)).isZero()

        val inRange = newAttacker(level = 175)
        val inRangeState = newState(damageMap = mapOf(inRange.id to CountBlock(total = 1_000)))
        assertThat(inRangeState.getGainedExperience(inRange)).isEqualTo(75)
    }

    @Test
    fun `gained experience needs player kills and skill loss enabled`() {
        // player.cpp:6181 (EXPERIENCE_FROM_PLAYERS) and 6183 (skillLoss).
        val attacker = newAttacker()
        val damageMap = mapOf(attacker.id to CountBlock(total = 1_000))

        assertThat(newState(experienceFromPlayers = false, damageMap = damageMap).getGainedExperience(attacker)).isZero()
        assertThat(newState(skillLoss = false, damageMap = damageMap).getGainedExperience(attacker)).isZero()
    }

    @Test
    fun `gained experience is computed for players only`() {
        // player.cpp:6182: `attacker->getPlayer()`.
        val monster = Monster(
            id = 43,
            name = "Rat",
            maxHealth = 20,
            health = 20,
            position = Position(101, 100, 7),
        )
        val state = newState(damageMap = mapOf(monster.id to CountBlock(total = 1_000)))

        assertThat(state.getGainedExperience(monster)).isZero()
    }

    @Test
    fun `gainExperience forwards to addExperience with sendText true`() {
        // player.cpp:6701-6707: gate then addExperience(target, gainExp, true).
        val player = newPlayer()
        val target = newAttacker()
        val state = newState(player = player)

        state.gainExperience(500, target)

        assertThat(recorder.calls).isEqualTo(1)
        assertThat(recorder.target).isSameAs(target)
        assertThat(recorder.gainExp).isEqualTo(500)
        assertThat(recorder.sendText).isTrue()
    }

    @Test
    fun `gainExperience gates zero experience empty stamina and blocked players`() {
        // player.cpp:6702: NotGainExperience || gainExp == 0 || staminaMinutes == 0.
        val player = newPlayer()
        val staminaState = newState(player = player, staminaMinutes = 0)
        staminaState.gainExperience(500, null)

        val zeroState = newState(player = player)
        zeroState.gainExperience(0, null)

        player.setFlag(PlayerFlags_t.NotGainExperience)
        val blockedState = newState(player = player)
        blockedState.gainExperience(500, null)

        assertThat(recorder.calls).isZero()
    }
}
