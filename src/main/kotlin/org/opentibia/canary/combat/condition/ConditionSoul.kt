package org.opentibia.canary.combat.condition

import org.opentibia.canary.io.PropStream
import org.opentibia.canary.io.PropWriteStream

/**
 * `ConditionSoul` (`src/creatures/combat/condition.cpp:1689-1755`,
 * `condition.hpp:266-285`).
 *
 * Grants `soulGain` every [soulTicks] ms to players outside a protection zone.
 */
class ConditionSoul(
    id: ConditionId,
    type: ConditionType,
    ticks: Int,
    buff: Boolean = false,
    subId: Int = 0,
    now: () -> Long = { System.currentTimeMillis() },
) : ConditionGeneric(id, type, ticks, buff, subId, false, now) {

    /** `ConditionSoul::internalSoulTicks` (`condition.hpp:282`). */
    private var internalSoulTicks: Int = 0

    /** `ConditionSoul::soulTicks` (`condition.hpp:283`). */
    var soulTicks: Int = 0
        private set

    /** `ConditionSoul::soulGain` (`condition.hpp:284`). */
    var soulGain: Int = 0
        private set

    /** `ConditionSoul::addCondition` (`condition.cpp:1692-1701`). */
    override fun addCondition(target: ConditionTarget?, condition: Condition) {
        if (updateCondition(condition)) {
            setTicks(condition.ticks)

            val conditionSoul = condition as ConditionSoul
            soulTicks = conditionSoul.soulTicks
            soulGain = conditionSoul.soulGain
        }
    }

    /** `ConditionSoul::executeCondition` (`condition.cpp:1722-1735`). */
    override fun executeCondition(target: ConditionTarget?, interval: Int): Boolean {
        internalSoulTicks += interval

        val player = target?.player
        if (player != null && target.getZoneType() != ZoneType.ZONE_PROTECTION) {
            if (internalSoulTicks >= soulTicks) {
                internalSoulTicks = 0
                target.changeSoul(soulGain)
            }
        }

        return super.executeCondition(target, interval)
    }

    /** `ConditionSoul::setParam` (`condition.cpp:1737-1751`). */
    override fun setParam(param: ConditionParam, value: Int): Boolean {
        val ret = super.setParam(param, value)

        return when (param) {
            ConditionParam.CONDITION_PARAM_SOULGAIN -> {
                soulGain = value
                true
            }

            ConditionParam.CONDITION_PARAM_SOULTICKS -> {
                soulTicks = value
                true
            }

            else -> ret
        }
    }

    /** `ConditionSoul::serialize` (`condition.cpp:1712-1720`). */
    override fun serialize(stream: PropWriteStream) {
        super.serialize(stream)

        stream.writeU8(ConditionAttr.CONDITIONATTR_SOULGAIN.value)
        stream.writeU32(soulGain.toLong() and 0xFFFFFFFFL)

        stream.writeU8(ConditionAttr.CONDITIONATTR_SOULTICKS.value)
        stream.writeU32(soulTicks.toLong() and 0xFFFFFFFFL)
    }

    /** `ConditionSoul::unserializeProp` (`condition.cpp:1703-1710`). */
    override fun unserializeProp(attr: ConditionAttr, stream: PropStream): Boolean {
        when (attr) {
            ConditionAttr.CONDITIONATTR_SOULGAIN ->
                soulGain = (stream.readU32()?.toInt() ?: return false)

            ConditionAttr.CONDITIONATTR_SOULTICKS ->
                soulTicks = (stream.readU32()?.toInt() ?: return false)

            else -> return super.unserializeProp(attr, stream)
        }
        return true
    }

    /** `ConditionSoul::clone` (`condition.cpp:1753-1755`). */
    override fun clone(): Condition {
        val copy = ConditionSoul(id, type, ticks, isBuff, subId, now)
        copy.apply {
            endTime = this@ConditionSoul.endTime
            owner = this@ConditionSoul.owner
            tickSound = this@ConditionSoul.tickSound
            addSound = this@ConditionSoul.addSound
            soulTicks = this@ConditionSoul.soulTicks
            soulGain = this@ConditionSoul.soulGain
        }
        return copy
    }
}
