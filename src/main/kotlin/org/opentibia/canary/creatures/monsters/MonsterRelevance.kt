package org.opentibia.canary.creatures.monsters

/**
 * Port of `enum class MonsterRelevanceTier`
 * (src/creatures/monsters/monster_relevance.hpp:18-21).
 */
enum class MonsterRelevanceTier {
    Background,
    Visible,
}

/**
 * Port of `struct MonsterRelevanceSnapshot`
 * (src/creatures/monsters/monster_relevance.hpp:23-27). [nearestPlayerDistance]
 * defaults to the `uint16_t` maximum (65535) like the C++ field.
 */
class MonsterRelevanceSnapshot(
    val playerSpectators: Int = 0,
    val nearestPlayerDistance: Int = UShort.MAX_VALUE.toInt(),
    val engagedWithPlayer: Boolean = false,
)

/**
 * Port of `struct MonsterRelevanceState`
 * (src/creatures/monsters/monster_relevance.hpp:29-32). [visibleUntil] is a
 * steady-clock time point in milliseconds; `0` is the default-constructed value.
 */
class MonsterRelevanceState(
    val tier: MonsterRelevanceTier = MonsterRelevanceTier.Background,
    val visibleUntil: Long = 0L,
)

/**
 * Port of `MonsterRelevancePolicy::update`
 * (src/creatures/monsters/monster_relevance.cpp:17-33), with the C++
 * `ENTER_VISIBLE_DISTANCE` (10) and `EXIT_VISIBLE_DISTANCE` (12) hysteresis
 * bounds (monster_relevance.cpp:13-14).
 *
 * A monster is Visible on the same update it is engaged or has a spectator
 * within 10 tiles; an already-Visible monster remains Visible with a spectator
 * within 12 tiles. Every remains-visible update refreshes `visibleUntil` by
 * [visibleHold]; otherwise a Visible monster stays visible until its stored
 * `visibleUntil` passes, then drops to Background and clears the hold value.
 *
 * C++ uses `std::chrono::steady_clock::time_point` and
 * `std::chrono::milliseconds`; here [now] and [visibleHold] are monotonic
 * (steady-clock) milliseconds to preserve the C++ clock semantics.
 */
object MonsterRelevancePolicy {
    private const val ENTER_VISIBLE_DISTANCE = 10
    private const val EXIT_VISIBLE_DISTANCE = 12
    private const val DEFAULT_VISIBLE_HOLD_MILLIS = 3_000L

    fun update(
        current: MonsterRelevanceState,
        snapshot: MonsterRelevanceSnapshot,
        now: Long,
        visibleHold: Long = DEFAULT_VISIBLE_HOLD_MILLIS,
    ): MonsterRelevanceState {
        val entersVisible = snapshot.engagedWithPlayer ||
            (snapshot.playerSpectators > 0 && snapshot.nearestPlayerDistance <= ENTER_VISIBLE_DISTANCE)
        val remainsVisible = entersVisible ||
            (current.tier == MonsterRelevanceTier.Visible &&
                snapshot.playerSpectators > 0 &&
                snapshot.nearestPlayerDistance <= EXIT_VISIBLE_DISTANCE)

        return when {
            remainsVisible -> MonsterRelevanceState(MonsterRelevanceTier.Visible, now + visibleHold)
            current.tier == MonsterRelevanceTier.Visible && now < current.visibleUntil ->
                MonsterRelevanceState(MonsterRelevanceTier.Visible, current.visibleUntil)
            else -> MonsterRelevanceState(MonsterRelevanceTier.Background, 0L)
        }
    }
}
