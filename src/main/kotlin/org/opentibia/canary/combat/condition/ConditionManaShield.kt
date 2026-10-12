package org.opentibia.canary.combat.condition

/**
 * `ConditionManaShield` (`src/creatures/combat/condition.cpp:1604-1683`,
 * `condition.hpp:245-264`).
 *
 * Sets the creature's mana shield pool on start and clears it on end. The
 * stacking path (`condition.cpp:1630-1643`) ends the previous shield first.
 */
class ConditionManaShield(
    id: ConditionId,
    type: ConditionType,
    ticks: Int,
    buff: Boolean = false,
    subId: Int = 0,
    now: () -> Long = { System.currentTimeMillis() },
) : Condition(id, type, ticks, buff, subId, false, now) {

    /** `ConditionManaShield::manaShield` (`condition.hpp:263`). */
    var manaShield: Int = 0
        private set

    /** `ConditionManaShield::startCondition` (`condition.cpp:1607-1620`). */
    override fun startCondition(target: ConditionTarget?): Boolean {
        if (!super.startCondition(target)) {
            return false
        }

        target?.setManaShield(manaShield)
        target?.setMaxManaShield(manaShield)
        target?.player?.sendStats()
        return true
    }

    /** `ConditionManaShield::endCondition` (`condition.cpp:1622-1628`). */
    override fun endCondition(target: ConditionTarget?) {
        target?.setManaShield(0)
        target?.setMaxManaShield(0)
        target?.player?.sendStats()
    }

    /** `ConditionManaShield::addCondition` (`condition.cpp:1630-1643`). */
    override fun addCondition(target: ConditionTarget?, condition: Condition) {
        endCondition(target)
        setTicks(condition.ticks)

        val conditionManaShield = condition as ConditionManaShield
        manaShield = conditionManaShield.manaShield
        target?.setManaShield(manaShield)
        target?.setMaxManaShield(manaShield)
        target?.player?.sendStats()
    }

    /** `ConditionManaShield::setParam` (`condition.cpp:1659-1669`). */
    override fun setParam(param: ConditionParam, value: Int): Boolean {
        val ret = super.setParam(param, value)

        return when (param) {
            ConditionParam.CONDITION_PARAM_MANASHIELD -> {
                manaShield = value
                true
            }

            else -> ret
        }
    }

    /** `ConditionManaShield::getIcons` (`condition.cpp:1675-1683`). */
    override fun getIcons(): Set<PlayerIcon> {
        val icons = super.getIcons().toMutableSet()
        if (manaShield != 0) {
            icons.add(PlayerIcon.NewManaShield)
        } else {
            icons.add(PlayerIcon.ManaShield)
        }
        return icons
    }

    /** `ConditionManaShield::clone` (`condition.cpp:1671-1673`). */
    override fun clone(): Condition {
        val copy = ConditionManaShield(id, type, ticks, isBuff, subId, now)
        copy.apply {
            endTime = this@ConditionManaShield.endTime
            owner = this@ConditionManaShield.owner
            manaShield = this@ConditionManaShield.manaShield
        }
        return copy
    }
}
