package org.opentibia.canary.combat.condition

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
            internalLightTicks = this@ConditionLight.internalLightTicks
            lightChangeInterval = this@ConditionLight.lightChangeInterval
        }
        return copy
    }
}
