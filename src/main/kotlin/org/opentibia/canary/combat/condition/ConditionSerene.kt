package org.opentibia.canary.combat.condition

/**
 * `ConditionSerene` (`src/creatures/combat/condition.cpp:664-696`,
 * `condition.hpp:101-149`).
 *
 * Monk serenity state: toggles the client serenity flag via
 * `Player::sendMonkData(MonkData_t::Serenity, ...)` on start (`0x01`) and end
 * (`0x00`). The add-sound side effect is presentation-only and omitted.
 */
class ConditionSerene(
    id: ConditionId,
    type: ConditionType,
    ticks: Int,
    buff: Boolean = false,
    subId: Int = 0,
    now: () -> Long = { System.currentTimeMillis() },
) : Condition(id, type, ticks, buff, subId, false, now) {

    /** `ConditionSerene::startCondition` (`condition.cpp:667-672`). */
    override fun startCondition(target: ConditionTarget?): Boolean {
        target?.player?.sendMonkData(MonkData.Serenity, 0x01)
        return super.startCondition(target)
    }

    /** `ConditionSerene::executeCondition` (`condition.cpp:674-676`). */
    override fun executeCondition(target: ConditionTarget?, interval: Int): Boolean =
        super.executeCondition(target, interval)

    /** `ConditionSerene::endCondition` (`condition.cpp:678-682`). */
    override fun endCondition(target: ConditionTarget?) {
        target?.player?.sendMonkData(MonkData.Serenity, 0x00)
    }

    /** `ConditionSerene::addCondition` (`condition.cpp:684-692`). */
    override fun addCondition(target: ConditionTarget?, condition: Condition) {
        if (updateCondition(condition)) {
            setTicks(condition.ticks)
        }
    }

    /** `ConditionSerene::clone` (`condition.cpp:694-696`). */
    override fun clone(): Condition {
        val copy = ConditionSerene(id, type, ticks, isBuff, subId, now)
        copy.apply {
            endTime = this@ConditionSerene.endTime
            owner = this@ConditionSerene.owner
        }
        return copy
    }
}
