package org.opentibia.canary.combat.condition

/**
 * `ConditionSpellGroupCooldown` (`src/creatures/combat/condition.cpp:2923-2955`,
 * `condition.hpp:470-478`).
 *
 * Generic cooldown that notifies the player with
 * `sendSpellGroupCooldown(static_cast<SpellGroup_t>(subId), ticks)` when a
 * non-zero `subId` keys the spell group and the remaining duration is positive.
 */
class ConditionSpellGroupCooldown(
    id: ConditionId,
    type: ConditionType,
    ticks: Int,
    buff: Boolean = false,
    subId: Int = 0,
    now: () -> Long = { System.currentTimeMillis() },
) : ConditionGeneric(id, type, ticks, buff, subId, false, now) {

    /** `ConditionSpellGroupCooldown::startCondition` (`condition.cpp:2943-2955`). */
    override fun startCondition(target: ConditionTarget?): Boolean {
        if (!super.startCondition(target)) {
            return false
        }

        if (subId != 0 && ticks > 0) {
            target?.player?.sendSpellGroupCooldown(SpellGroup.fromValue(subId), ticks)
        }
        return true
    }

    /** `ConditionSpellGroupCooldown::addCondition` (`condition.cpp:2923-2934`). */
    override fun addCondition(target: ConditionTarget?, condition: Condition) {
        if (updateCondition(condition)) {
            setTicks(condition.ticks)

            if (subId != 0 && ticks > 0) {
                target?.player?.sendSpellGroupCooldown(SpellGroup.fromValue(subId), ticks)
            }
        }
    }

    /** `ConditionSpellGroupCooldown::clone` (`condition.cpp:2936-2938`). */
    override fun clone(): Condition {
        val copy = ConditionSpellGroupCooldown(id, type, ticks, isBuff, subId, now)
        copy.apply {
            endTime = this@ConditionSpellGroupCooldown.endTime
            owner = this@ConditionSpellGroupCooldown.owner
            tickSound = this@ConditionSpellGroupCooldown.tickSound
            addSound = this@ConditionSpellGroupCooldown.addSound
        }
        return copy
    }
}
