package org.opentibia.canary.creatures.players

import org.opentibia.canary.combat.condition.Condition
import org.opentibia.canary.combat.condition.ConditionDamage
import org.opentibia.canary.combat.condition.ConditionId
import org.opentibia.canary.combat.condition.ConditionParam
import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.Slots_t
import org.opentibia.canary.items.Item
import org.opentibia.canary.spells.WorldType

/**
 * The condition-facing Player hooks
 * (src/creatures/players/player.cpp:3146-3158, 6436-6466):
 *
 * - `Player::updateRegeneration` (player.cpp:3146-3158)
 * - `Player::onCombatRemoveCondition` (player.cpp:6436-6466)
 *
 * The port's [Player] owns the vocation registry and the condition store, so
 * these are extension functions. `g_game().getWorldType()`,
 * `g_game().internalRemoveItem` and the C++ `uniform_random` are call-site
 * seams per AGENTS.md 1.1; the default world type models the unwired
 * `g_game()` and never destroys items.
 */

/**
 * `Player::updateRegeneration` (player.cpp:3146-3158): re-stamps the default
 * regeneration condition with the vocation's health/mana gain amounts and
 * ticks.
 */
fun Player.updateRegeneration() {
    val vocation = vocations?.getVocation(vocationId) ?: return
    val condition = getCondition(ConditionType.CONDITION_REGENERATION, ConditionId.CONDITIONID_DEFAULT) ?: return

    condition.setParam(ConditionParam.CONDITION_PARAM_HEALTHGAIN, vocation.getHealthGainAmount())
    condition.setParam(ConditionParam.CONDITION_PARAM_HEALTHTICKS, vocation.getHealthGainTicks())
    condition.setParam(ConditionParam.CONDITION_PARAM_MANAGAIN, vocation.getManaGainAmount())
    condition.setParam(ConditionParam.CONDITION_PARAM_MANATICKS, vocation.getManaGainTicks())
}

/**
 * `Player::onCombatRemoveCondition` (player.cpp:6436-6466).
 *
 * A positive condition id is an equipment-slot condition: in a PVP-enforced
 * world the equipped item is destroyed with a 25% `uniform_random(1, 100)`
 * roll. The default (non-item) branch re-arms the condition to the next
 * action tick rounded down to `EVENT_CREATURE_THINK_INTERVAL`, removing it
 * when the tick count would be negative or the condition is paralysis and the
 * player cannot act.
 */
fun Player.onCombatRemoveCondition(
    condition: Condition?,
    /** `g_game().getWorldType()` (player.cpp:6443). */
    worldType: () -> WorldType = { WorldType.NO_PVP },
    /** `g_game().internalRemoveItem(item)` (player.cpp:6451). */
    removeItem: (Item) -> Unit = {},
    /** `uniform_random(1, 100)` (player.cpp:6449). */
    uniformRandom: (Int, Int) -> Int = { min, max ->
        org.opentibia.canary.combat.uniformRandom(min, max, java.util.Random())
    },
) {
    if (condition == null) {
        return
    }

    if (condition.id.value > 0) {
        // Means the condition is from an item, id == slot.
        if (worldType() == WorldType.PVP_ENFORCED) {
            val slot = Slots_t.fromValue(condition.id.value) ?: return
            val item = getInventoryItem(slot)
            if (item != null && 25 >= uniformRandom(1, 100)) {
                removeItem(item)
            }
        }
        return
    }

    if (!canDoAction()) {
        val delay = getNextActionTime()
        val ticks = delay - (delay % ConditionDamage.EVENT_CREATURE_THINK_INTERVAL)
        if (ticks < 0 || condition.type == ConditionType.CONDITION_PARALYZE) {
            removeCondition(condition)
        } else {
            condition.setTicks(ticks.toInt())
        }
    } else {
        removeCondition(condition)
    }
}
