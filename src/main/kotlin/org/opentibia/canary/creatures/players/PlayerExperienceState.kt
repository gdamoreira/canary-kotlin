package org.opentibia.canary.creatures.players

import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.PlayerFlags_t
import kotlin.math.abs
import kotlin.math.floor

/**
 * Self-contained mirror of the Player experience-gain family:
 *
 * - `Creature::getDamageRatio` (src/creatures/creature.cpp:1168-1190)
 * - `Player::getGainedExperience` (player.cpp:6180-6189)
 * - `Player::gainExperience` (player.cpp:6701-6707)
 *
 * `Creature::damageMap` (creature.hpp:839) is not on the port's `Creature` yet
 * (see [PlayerDeathState]'s [CountBlock] mirror), so it is an injected map.
 * `EXPERIENCE_FROM_PLAYERS` (configmanager.cpp:123) and
 * `EXP_FROM_PLAYERS_LEVEL_RANGE` (configmanager.cpp:122) replace the
 * `g_configManager()` reads, `Creature::skillLoss` (creature.hpp:932) is a
 * protected flag exposed as a seam, and `Player::addExperience(target, exp,
 * sendText)` (player.cpp:3613) is a seam because the host player owns only the
 * reduced one-argument form.
 *
 * PARITY GAP: the C++ `uint32_t` damage accumulators are widened to [Long] so
 * the ratio cannot overflow, and the reduced host
 * `Player::addExperience(exp)` does not run the Canary rate/message/level-up
 * pipeline.
 */
class PlayerExperienceState(
    /** The host `Player` whose flags, level and experience are read. */
    private val player: Player,
    /** `g_configManager().getBoolean(EXPERIENCE_FROM_PLAYERS)` (configmanager.cpp:123). */
    private val experienceFromPlayers: Boolean = false,
    /** `g_configManager().getNumber(EXP_FROM_PLAYERS_LEVEL_RANGE)` (configmanager.cpp:122). */
    private val expFromPlayersLevelRange: Int = 75,
    /** `Creature::skillLoss` (creature.hpp:932). */
    private val getSkillLoss: () -> Boolean = { true },
    /** `Player::staminaMinutes` (player.hpp:1817). */
    private val staminaMinutes: () -> Int = { 2520 },
    /** `Creature::damageMap` (creature.hpp:839). */
    private val getDamageMap: () -> Map<Long, CountBlock> = { emptyMap() },
    /** `Player::addExperience(target, exp, sendText)` (player.cpp:3613). */
    private val addExperience: (Creature?, Long, Boolean) -> Unit = { _, exp, _ -> player.addExperience(exp) },
) {

    /**
     * `Creature::getDamageRatio` (creature.cpp:1168-1190): the attacker's share
     * of the total damage recorded on the map, skipping the zero id and zero
     * totals.
     */
    fun getDamageRatio(attacker: Creature): Double {
        var totalDamage = 0L
        var attackerDamage = 0L

        for ((attackerId, damageInfo) in getDamageMap()) {
            if (attackerId == 0L || damageInfo.total == 0) {
                continue
            }

            totalDamage += damageInfo.total
            if (attackerId == attacker.id) {
                attackerDamage += damageInfo.total
            }
        }

        if (totalDamage == 0L) {
            return 0.0
        }

        return attackerDamage.toDouble() / totalDamage
    }

    /**
     * `Player::getGainedExperience` (player.cpp:6180-6189): 75% of the lost
     * experience weighted by the attacker's damage share, for a different
     * player within `EXP_FROM_PLAYERS_LEVEL_RANGE` levels.
     */
    fun getGainedExperience(attacker: Creature): Long {
        if (experienceFromPlayers) {
            val attackerPlayer = attacker as? Player
            if (attackerPlayer != null && attackerPlayer !== player && getSkillLoss() &&
                abs(attackerPlayer.level - player.level) <= expFromPlayersLevelRange
            ) {
                return maxOf(0L, floor(player.getLostExperience() * getDamageRatio(attacker) * 0.75).toLong())
            }
        }
        return 0
    }

    /**
     * `Player::gainExperience` (player.cpp:6701-6707): blocked players, zero
     * gains and players without stamina do not advance.
     */
    fun gainExperience(gainExp: Long, target: Creature?) {
        if (player.hasFlag(PlayerFlags_t.NotGainExperience) || gainExp == 0L || staminaMinutes() == 0) {
            return
        }

        addExperience(target, gainExp, true)
    }
}
