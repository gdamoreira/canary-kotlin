package org.opentibia.canary.creatures.players

import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Player

/**
 * Player drain/cleanse hooks (src/creatures/players/player.cpp:3540-3552,
 * 6322-6348) as extensions over the port's [Player].
 *
 * `g_game().sendSingleSoundEffect` and `Player::sendStats` are call-site seams
 * per AGENTS.md 1.1. The port does not separately model `Creature::drainHealth`
 * and `Creature::drainMana`; their bodies (creature.cpp:893-907) are composed
 * here from the port's `Creature.drainHealth` member, `Creature.mana` and
 * `Creature.addDamagePoints`.
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
 * (`changeHealth(-damage, false)`, creature.cpp:893-899) runs before the stats
 * refresh. [soundRoll] replaces `uniform_random(1, 100)` so tests pin the roll.
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

    drainHealth(damage)
    sendStats()
}

/**
 * `Player::drainMana` (player.cpp:3549-3552) over `Creature::drainMana`
 * (creature.cpp:901-907): `onAttacked`, the clamped mana drain and the
 * attacker damage point, then the stats refresh.
 */
fun Player.drainMana(
    attacker: Creature?,
    manaLoss: Int,
    sendStats: () -> Unit = {},
) {
    onAttacked()
    mana = maxOf(0, mana - manaLoss)

    if (attacker != null) {
        addDamagePoints(attacker, manaLoss)
    }

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
