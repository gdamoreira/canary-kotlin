package org.opentibia.canary.combat.condition

/**
 * `ConditionSpellCooldown` (`src/creatures/combat/condition.cpp:2885-2917`,
 * `condition.hpp:460-468`).
 *
 * Generic cooldown that notifies the player with `sendSpellCooldown(subId, ticks)`
 * when a non-zero `subId` keys the spell and the remaining duration is positive.
 */
class ConditionSpellCooldown(
    id: ConditionId,
    type: ConditionType,
    ticks: Int,
    buff: Boolean = false,
    subId: Int = 0,
    now: () -> Long = { System.currentTimeMillis() },
) : ConditionGeneric(id, type, ticks, buff, subId, false, now) {

    /** `ConditionSpellCooldown::startCondition` (`condition.cpp:2905-2917`). */
    override fun startCondition(target: ConditionTarget?): Boolean {
        if (!super.startCondition(target)) {
            return false
        }

        if (subId != 0 && ticks > 0) {
            target?.player?.sendSpellCooldown(subId, ticks)
        }
        return true
    }

    /** `ConditionSpellCooldown::addCondition` (`condition.cpp:2885-2896`). */
    override fun addCondition(target: ConditionTarget?, condition: Condition) {
        if (updateCondition(condition)) {
            setTicks(condition.ticks)

            if (subId != 0 && ticks > 0) {
                target?.player?.sendSpellCooldown(subId, ticks)
            }
        }
    }

    /** `ConditionSpellCooldown::clone` (`condition.cpp:2898-2900`). */
    override fun clone(): Condition {
        val copy = ConditionSpellCooldown(id, type, ticks, isBuff, subId, now)
        copy.apply {
            endTime = this@ConditionSpellCooldown.endTime
            owner = this@ConditionSpellCooldown.owner
        }
        return copy
    }
}
