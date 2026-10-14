package org.opentibia.canary.creatures.players

import org.opentibia.canary.combat.condition.Condition
import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.creatures.Player

/**
 * `Player::getMuteConditions` (player.cpp:9293-9315): the stored conditions
 * that still have ticks left and belong to the mute family
 * (`CONDITION_MUTED`, `CONDITION_CHANNELMUTEDTICKS`, `CONDITION_YELLTICKS`).
 *
 * The condition list already lives on the host [Player] (`Creature.conditions`,
 * creature.hpp:843), so this is an extension like the other stateless Player
 * queries.
 */
fun Player.getMuteConditions(): List<Condition> {
    val muteConditions = mutableListOf<Condition>()

    for (condition in conditions) {
        if (condition.ticks <= 0) {
            continue
        }

        val type = condition.type
        if (type != ConditionType.CONDITION_MUTED &&
            type != ConditionType.CONDITION_CHANNELMUTEDTICKS &&
            type != ConditionType.CONDITION_YELLTICKS
        ) {
            continue
        }

        muteConditions.add(condition)
    }

    return muteConditions
}
