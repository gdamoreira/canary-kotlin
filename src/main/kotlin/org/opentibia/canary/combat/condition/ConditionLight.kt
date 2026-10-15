package org.opentibia.canary.combat.condition

import org.opentibia.canary.io.PropStream
import org.opentibia.canary.io.PropWriteStream

/**
 * `ConditionLight` (`src/creatures/combat/condition.cpp:2758-2879`,
 * `condition.hpp:437-458`).
 *
 * Installs a light with the given level/color and dims it by one level every
 * `ticks / level` interval. `g_game().changeLight` is applied through
 * [ConditionTarget.changeLight].
 *
 * Bounded arithmetic: Canary performs `ticks / lightInfo.level` unguarded in
 * [startCondition] (undefined for level 0); this port clamps the divisor to at
 * least 1, matching the guard its own [addCondition] already uses
 * (`condition.cpp:2802`). For every legal light level (>= 1) the result is
 * identical.
 */
class ConditionLight(
    id: ConditionId,
    type: ConditionType,
    ticks: Int,
    buff: Boolean = false,
    subId: Int = 0,
    initLightlevel: Int,
    initLightcolor: Int,
    now: () -> Long = { System.currentTimeMillis() },
) : Condition(id, type, ticks, buff, subId, false, now) {

    /** `ConditionLight::lightInfo` (`condition.hpp:455`). */
    var lightInfo: LightInfo = LightInfo(initLightlevel, initLightcolor)
        private set

    /** `ConditionLight::internalLightTicks` (`condition.hpp:456`). */
    var internalLightTicks: Int = 0
        private set

    /** `ConditionLight::lightChangeInterval` (`condition.hpp:457`). */
    var lightChangeInterval: Int = 0
        private set

    /** `ConditionLight::startCondition` (`condition.cpp:2761-2771`). */
    override fun startCondition(target: ConditionTarget?): Boolean {
        if (!super.startCondition(target)) {
            return false
        }

        internalLightTicks = 0
        lightChangeInterval = ticks / maxOf(1, lightInfo.level)
        target?.setCreatureLight(lightInfo)
        target?.changeLight()
        return true
    }

    /** `ConditionLight::executeCondition` (`condition.cpp:2773-2788`). */
    override fun executeCondition(target: ConditionTarget?, interval: Int): Boolean {
        internalLightTicks += interval

        if (internalLightTicks >= lightChangeInterval) {
            internalLightTicks = 0
            val creatureLightInfo = target?.getCreatureLight()?.copy()

            if (creatureLightInfo != null && creatureLightInfo.level > 0) {
                creatureLightInfo.level -= 1
                target.setCreatureLight(creatureLightInfo)
                target.changeLight()
            }
        }

        return super.executeCondition(target, interval)
    }

    /** `ConditionLight::endCondition` (`condition.cpp:2790-2793`). */
    override fun endCondition(target: ConditionTarget?) {
        target?.setNormalCreatureLight()
        target?.changeLight()
    }

    /** `ConditionLight::addCondition` (`condition.cpp:2795-2807`). */
    override fun addCondition(target: ConditionTarget?, condition: Condition) {
        if (updateCondition(condition)) {
            setTicks(condition.ticks)

            val incoming = condition as ConditionLight
            lightInfo.level = incoming.lightInfo.level
            lightInfo.color = incoming.lightInfo.color
            lightChangeInterval = ticks / maxOf(1, lightInfo.level)
            internalLightTicks = 0
            target?.setCreatureLight(lightInfo)
            target?.changeLight()
        }
    }

    /** `ConditionLight::setParam` (`condition.cpp:2813-2835`). */
    override fun setParam(param: ConditionParam, value: Int): Boolean {
        val ret = super.setParam(param, value)
        if (ret) {
            return false
        }

        return when (param) {
            ConditionParam.CONDITION_PARAM_LIGHT_LEVEL -> {
                lightInfo.level = maxOf(1, value and 0xFF)
                true
            }

            ConditionParam.CONDITION_PARAM_LIGHT_COLOR -> {
                lightInfo.color = value and 0xFF
                true
            }

            else -> false
        }
    }

    /** `ConditionLight::serialize` (`condition.cpp:2862-2881`). */
    override fun serialize(stream: PropWriteStream) {
        super.serialize(stream)

        stream.writeU8(ConditionAttr.CONDITIONATTR_LIGHTCOLOR.value)
        stream.writeU32(lightInfo.color.toLong() and 0xFFFFFFFFL)

        stream.writeU8(ConditionAttr.CONDITIONATTR_LIGHTLEVEL.value)
        stream.writeU32(lightInfo.level.toLong() and 0xFFFFFFFFL)

        stream.writeU8(ConditionAttr.CONDITIONATTR_LIGHTTICKS.value)
        stream.writeU32(internalLightTicks.toLong() and 0xFFFFFFFFL)

        stream.writeU8(ConditionAttr.CONDITIONATTR_LIGHTINTERVAL.value)
        stream.writeU32(lightChangeInterval.toLong() and 0xFFFFFFFFL)
    }

    /**
     * `ConditionLight::unserializeProp` (`condition.cpp:2837-2860`): the four
     * fields are written directly, without the `setParam` clamping.
     */
    override fun unserializeProp(attr: ConditionAttr, stream: PropStream): Boolean {
        when (attr) {
            ConditionAttr.CONDITIONATTR_LIGHTCOLOR ->
                lightInfo.color = stream.readU32()?.toInt() ?: return false

            ConditionAttr.CONDITIONATTR_LIGHTLEVEL ->
                lightInfo.level = stream.readU32()?.toInt() ?: return false

            ConditionAttr.CONDITIONATTR_LIGHTTICKS ->
                internalLightTicks = stream.readU32()?.toInt() ?: return false

            ConditionAttr.CONDITIONATTR_LIGHTINTERVAL ->
                lightChangeInterval = stream.readU32()?.toInt() ?: return false

            else -> return super.unserializeProp(attr, stream)
        }
        return true
    }

    /** `ConditionLight::clone` (`condition.cpp:2809-2811`). */
    override fun clone(): Condition {
        val copy = ConditionLight(
            id,
            type,
            ticks,
            isBuff,
            subId,
            lightInfo.level,
            lightInfo.color,
            now,
        )
        copy.apply {
            endTime = this@ConditionLight.endTime
            owner = this@ConditionLight.owner
            tickSound = this@ConditionLight.tickSound
            addSound = this@ConditionLight.addSound
            internalLightTicks = this@ConditionLight.internalLightTicks
            lightChangeInterval = this@ConditionLight.lightChangeInterval
        }
        return copy
    }
}
