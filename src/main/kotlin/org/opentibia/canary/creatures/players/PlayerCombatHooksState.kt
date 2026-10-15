package org.opentibia.canary.creatures.players

import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Player

/**
 * Player drain/cleanse hooks (src/creatures/players/player.cpp:3540-3552,
 * 6322-6348, 6577-6594) as extensions over the port's [Player].
 *
 * `g_game().sendSingleSoundEffect` and `Player::sendStats` are call-site seams
 * per AGENTS.md 1.1. `Player::drainMana` and `Player::onAttackedCreatureDrainHealth`
 * are class overrides on [Player] (creatures/Creature.kt) so they ride the
 * virtual dispatch; the remaining hooks compose [Creature.drainHealth] over
 * the port's `Creature`.
 */

/** `PLAYER_SOUND_HEALTH_CHANGE` (player.hpp:128). */
private const val PLAYER_SOUND_HEALTH_CHANGE = 10

/** `Player::onCleanseCondition` message map (player.cpp:6323-6334). */
private val CLEANSE_CONDITION_MESSAGES = mapOf(
    ConditionType.CONDITION_POISON to "poisoned",
    ConditionType.CONDITION_FIRE to "burning",
    ConditionType.CONDITION_ENERGY to "electrified",
    ConditionType.CONDITION_FREEZING to "freezing",
    ConditionType.CONDITION_CURSED to "cursed",
    ConditionType.CONDITION_DAZZLED to "dazzled",
    ConditionType.CONDITION_BLEEDING to "bleeding",
    ConditionType.CONDITION_PARALYZE to "paralyzed",
    ConditionType.CONDITION_ROOTED to "rooted",
    ConditionType.CONDITION_FEARED to "feared",
)

/**
 * `Player::drainHealth` (player.cpp:3540-3547): the pain sound plays with the
 * `PLAYER_SOUND_HEALTH_CHANGE` percent chance, then `Creature::drainHealth`
 * (`changeHealth(-damage, false)` plus the attacker credit, creature.cpp:893-899)
 * runs before the stats refresh. [soundRoll] replaces
 * `uniform_random(1, 100)` so tests pin the roll.
 */
fun Player.drainHealth(
    attacker: Creature?,
    damage: Int,
    soundChance: Int = PLAYER_SOUND_HEALTH_CHANGE,
    soundRoll: Int = 100,
    playSound: () -> Unit = {},
    sendStats: () -> Unit = {},
) {
    if (soundChance >= soundRoll) {
        playSound()
    }

    drainHealth(attacker, damage)
    sendStats()
}

/**
 * `Player::onCleanseCondition` (player.cpp:6322-6348): the mapped condition
 * types print the `MESSAGE_PARTY` cleanse-charm notice. The message class is
 * folded into the `sendTextMessage` seam like the other ported notices.
 */
fun Player.onCleanseCondition(type: ConditionType) {
    val conditionName = CLEANSE_CONDITION_MESSAGES[type] ?: return
    sendTextMessage("You are no longer $conditionName. (cleanse charm)")
}

/**
 * `Player::onTargetCreatureGainHealth(target, points)` (player.cpp:6577-6594):
 * with a party, the target's (or its master's) player must be a partner for
 * the shared-experience credit. The C++ `isPartner(tmpPlayer)` first branch is
 * dead code because `isPartner(nullptr)` is always false; it is transcribed
 * anyway so the flow stays auditable. `Player::getPlayer()` on an arbitrary
 * creature is unported on the host, so it is the [getPlayer] seam.
 */
fun Player.onTargetCreatureGainHealth(
    /** The `target` argument (player.cpp:6577). */
    target: Creature?,
    /** The `points` argument (player.cpp:6577). */
    points: Int,
    /** `Creature::getPlayer()` (player.cpp:6581, 6585). */
    getPlayer: (Creature) -> Player? = { null },
    /** `Party::updatePlayerTicks(player, points)` (party.cpp:720-725). */
    updatePlayerTicks: (Int) -> Unit = { credited ->
        getParty()?.updatePlayerTicks(this, credited)
    },
) {
    if (target != null && getParty() != null) {
        var tmpPlayer: Player? = null

        if (isPartner(tmpPlayer) && tmpPlayer !== this) {
            tmpPlayer = getPlayer(target)
        } else {
            val targetMaster = target.getMaster()
            if (targetMaster != null) {
                val targetMasterPlayer = getPlayer(targetMaster)
                if (targetMasterPlayer != null) {
                    tmpPlayer = targetMasterPlayer
                }
            }
        }

        if (isPartner(tmpPlayer)) {
            updatePlayerTicks(points)
        }
    }
}
