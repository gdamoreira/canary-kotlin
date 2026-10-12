package org.opentibia.canary.systems.dailyreward

/**
 * Engine state holder for the Daily Reward system, ported from Canary:
 *  - `Player::setDailyReward` / `Player::isDailyReward`
 *    (`src/creatures/players/player.cpp`, `src/creatures/players/player.hpp`)
 *  - the `daily-reward` KV scope used by the C++ engine
 *    (`src/creatures/combat/condition.cpp`, `src/server/network/protocol/protocolgame.cpp`,
 *    `src/creatures/players/components/player_title.cpp`)
 *
 * Only the C++ engine primitives are modelled here. The claim/reward policy,
 * streak advancement/reset and the "already claimed today" guard live in Lua
 * (`data/modules/scripts/daily_reward/daily_reward.lua`,
 * `data/libs/systems/daily_reward.lua`) and are intentionally not ported.
 */
class PlayerDailyReward(
    val kv: DailyRewardKV = DailyRewardKV(),
) {
    @set:JvmName("setIsDailyReward")
    var isDailyReward: Int = DailyRewardStatus.DAILY_REWARD_NOTCOLLECTED.value

    fun setDailyReward(reward: Int) {
        this.isDailyReward = reward
    }
}

/**
 * Minimal port of Canary's `KV` / `ScopedKV` (`src/kv/kv.hpp`) for the numeric
 * entries the Daily Reward engine reads and writes. A scoped handle composes
 * keys as `"prefix.key"` exactly like `ScopedKV::buildKey`, and shares the same
 * underlying store as its root.
 */
class DailyRewardKV private constructor(
    private val store: MutableMap<String, Long>,
    private val prefix: String,
) {
    constructor() : this(mutableMapOf(), "")

    fun set(key: String, value: Long) {
        store[buildKey(key)] = value
    }

    fun get(key: String): Long? = store[buildKey(key)]

    fun remove(key: String) {
        store.remove(buildKey(key))
    }

    fun scoped(scope: String): DailyRewardKV = DailyRewardKV(store, buildKey(scope))

    private fun buildKey(key: String): String =
        if (prefix.isEmpty()) key else "$prefix.$key"
}
