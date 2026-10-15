package org.opentibia.canary.combat.condition

/**
 * `ConditionInvisible` (`src/creatures/combat/condition.cpp:2641-2661`,
 * `condition.hpp:287-295`).
 *
 * Hides the creature on start and restores visibility on end. The
 * `g_game().internalCreatureChangeVisible` side effect is applied through
 * [ConditionTarget.internalCreatureChangeVisible].
 */
class ConditionInvisible(
    id: ConditionId,
    type: ConditionType,
    ticks: Int,
    buff: Boolean = false,
    subId: Int = 0,
    now: () -> Long = { System.currentTimeMillis() },
) : ConditionGeneric(id, type, ticks, buff, subId, false, now) {

    /** `ConditionInvisible::startCondition` (`condition.cpp:2644-2651`). */
    override fun startCondition(target: ConditionTarget?): Boolean {
        if (!super.startCondition(target)) {
            return false
        }

        target?.internalCreatureChangeVisible(false)
        return true
    }

    /** `ConditionInvisible::endCondition` (`condition.cpp:2653-2657`). */
    override fun endCondition(target: ConditionTarget?) {
        if (target != null && !target.isInvisible()) {
            target.internalCreatureChangeVisible(true)
        }
    }

    /** `ConditionInvisible::clone` (`condition.cpp:2659-2661`). */
    override fun clone(): Condition {
        val copy = ConditionInvisible(id, type, ticks, isBuff, subId, now)
        copy.apply {
            endTime = this@ConditionInvisible.endTime
            owner = this@ConditionInvisible.owner
            tickSound = this@ConditionInvisible.tickSound
            addSound = this@ConditionInvisible.addSound
        }
        return copy
    }
}
