package org.opentibia.canary.systems.dailyreward

/**
 * Daily Reward enums, ported verbatim from Canary
 * `src/creatures/creatures_definitions.hpp` (`DailyRewardBonus` / `DailyRewardStatus`).
 *
 * The Canary declarations use `uint8_t`; the exact numeric values are preserved here.
 */
enum class DailyRewardBonus(val value: Int) {
    DAILY_REWARD_FIRST(2),

    DAILY_REWARD_HP_REGENERATION(2),
    DAILY_REWARD_MP_REGENERATION(3),
    DAILY_REWARD_STAMINA_REGENERATION(4),
    DAILY_REWARD_DOUBLE_HP_REGENERATION(5),
    DAILY_REWARD_DOUBLE_MP_REGENERATION(6),
    DAILY_REWARD_SOUL_REGENERATION(7),

    DAILY_REWARD_LAST(7),
}

enum class DailyRewardStatus(val value: Int) {
    DAILY_REWARD_COLLECTED(0),
    DAILY_REWARD_NOTCOLLECTED(1),
    DAILY_REWARD_NOTAVAILABLE(2),
}
