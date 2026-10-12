package org.opentibia.canary.combat.condition

/**
 * `ConditionRegeneration` (`src/creatures/combat/condition.cpp:1423-1598`,
 * `condition.hpp:215-243`).
 *
 * Ticks two independent accumulators: health every [healthTicks] ms and mana
 * every [manaTicks] ms. The daily-reward quest storage `daily-reward/streak`
 * doubles the gain inside a protection zone and gates regeneration there.
 * `g_configManager().getFloat(RATE_SPELL_COOLDOWN)` is injected as
 * [rateSpellCooldown].
 */
class ConditionRegeneration(
    id: ConditionId,
    type: ConditionType,
    ticks: Int,
    buff: Boolean = false,
    subId: Int = 0,
    now: () -> Long = { System.currentTimeMillis() },
    private val rateSpellCooldown: Double = 1.0,
) : ConditionGeneric(id, type, ticks, buff, subId, false, now) {

    /** `ConditionRegeneration::internalHealthTicks` (`condition.hpp:236`). */
    private var internalHealthTicks: Int = 0

    /** `ConditionRegeneration::internalManaTicks` (`condition.hpp:237`). */
    private var internalManaTicks: Int = 0

    /** `ConditionRegeneration::healthTicks` (`condition.hpp:239`), default 1000. */
    var healthTicks: Int = 1000
        private set

    /** `ConditionRegeneration::manaTicks` (`condition.hpp:240`), default 1000. */
    var manaTicks: Int = 1000
        private set

    /** `ConditionRegeneration::healthGain` (`condition.hpp:241`). */
    var healthGain: Int = 0
        private set

    /** `ConditionRegeneration::manaGain` (`condition.hpp:242`). */
    var manaGain: Int = 0
        private set

    /** `ConditionRegeneration::startCondition` (`condition.cpp:1426-1435`). */
    override fun startCondition(target: ConditionTarget?): Boolean {
        if (!super.startCondition(target)) {
            return false
        }

        target?.player?.sendStats()
        return true
    }

    /** `ConditionRegeneration::endCondition` (`condition.cpp:1437-1441`). */
    override fun endCondition(target: ConditionTarget?) {
        target?.player?.sendStats()
    }

    /** `ConditionRegeneration::addCondition` (`condition.cpp:1443-1459`). */
    override fun addCondition(target: ConditionTarget?, condition: Condition) {
        if (updateCondition(condition)) {
            setTicks(condition.ticks)

            val conditionRegen = condition as ConditionRegeneration
            healthTicks = conditionRegen.healthTicks
            manaTicks = conditionRegen.manaTicks
            healthGain = conditionRegen.healthGain
            manaGain = conditionRegen.manaGain
        }

        target?.player?.sendStats()
    }

    /** `ConditionRegeneration::executeCondition` (`condition.cpp:1490-1549`). */
    override fun executeCondition(target: ConditionTarget?, interval: Int): Boolean {
        internalHealthTicks += interval
        internalManaTicks += interval

        val player = target?.player
        var dailyStreak = 0
        if (player != null) {
            val streak = target.getStorageValue(STORAGE_STREAK)
            if (streak != null) {
                dailyStreak = streak.toInt()
            }
        }

        if (target != null &&
            (target.getZoneType() != ZoneType.ZONE_PROTECTION || dailyStreak >= DAILY_REWARD_HP_REGENERATION)
        ) {
            if (internalHealthTicks >= getHealthTicks(target)) {
                internalHealthTicks = 0

                if (target.getZoneType() == ZoneType.ZONE_PROTECTION && dailyStreak >= DAILY_REWARD_DOUBLE_HP_REGENERATION) {
                    target.addHealth(healthGain * 2)
                } else {
                    target.addHealth(healthGain)
                }
                // The result is only consumed by the buff heal message
                // (`condition.cpp:1511-1533`), which is presentation-only and
                // requires the spectator list.
            }
        }

        if (target != null &&
            (target.getZoneType() != ZoneType.ZONE_PROTECTION || dailyStreak >= DAILY_REWARD_MP_REGENERATION)
        ) {
            if (internalManaTicks >= getManaTicks(target)) {
                internalManaTicks = 0
                if (target.getZoneType() == ZoneType.ZONE_PROTECTION && dailyStreak >= DAILY_REWARD_DOUBLE_MP_REGENERATION) {
                    target.addMana(manaGain * 2)
                } else {
                    target.addMana(manaGain)
                }
            }
        }

        return super.executeCondition(target, interval)
    }

    /** `ConditionRegeneration::setParam` (`condition.cpp:1551-1574`). */
    override fun setParam(param: ConditionParam, value: Int): Boolean {
        val ret = super.setParam(param, value)

        return when (param) {
            ConditionParam.CONDITION_PARAM_HEALTHGAIN -> {
                healthGain = value
                true
            }

            ConditionParam.CONDITION_PARAM_HEALTHTICKS -> {
                healthTicks = value
                true
            }

            ConditionParam.CONDITION_PARAM_MANAGAIN -> {
                manaGain = value
                true
            }

            ConditionParam.CONDITION_PARAM_MANATICKS -> {
                manaTicks = value
                true
            }

            else -> ret
        }
    }

    /** `ConditionRegeneration::getHealthTicks` (`condition.cpp:1576-1584`). */
    fun getHealthTicks(target: ConditionTarget): Int {
        if (target.player != null && isBuff) {
            return (healthTicks / rateSpellCooldown).toInt()
        }
        return healthTicks
    }

    /** `ConditionRegeneration::getManaTicks` (`condition.cpp:1586-1594`). */
    fun getManaTicks(target: ConditionTarget): Int {
        if (target.player != null && isBuff) {
            return (manaTicks / rateSpellCooldown).toInt()
        }
        return manaTicks
    }

    /** `ConditionRegeneration::clone` (`condition.cpp:1596-1598`). */
    override fun clone(): Condition {
        val copy = ConditionRegeneration(id, type, ticks, isBuff, subId, now, rateSpellCooldown)
        copy.apply {
            endTime = this@ConditionRegeneration.endTime
            owner = this@ConditionRegeneration.owner
            healthTicks = this@ConditionRegeneration.healthTicks
            manaTicks = this@ConditionRegeneration.manaTicks
            healthGain = this@ConditionRegeneration.healthGain
            manaGain = this@ConditionRegeneration.manaGain
        }
        return copy
    }

    companion object {
        /** `DailyRewardBonus` (`src/creatures/creatures_definitions.hpp:577-588`). */
        const val DAILY_REWARD_HP_REGENERATION = 2
        const val DAILY_REWARD_MP_REGENERATION = 3
        const val DAILY_REWARD_DOUBLE_HP_REGENERATION = 5
        const val DAILY_REWARD_DOUBLE_MP_REGENERATION = 6

        /** `Player::kv()->scoped("daily-reward")->get("streak")` (condition.cpp:1496). */
        const val STORAGE_STREAK = "streak"
    }
}
