package org.opentibia.canary.creatures.monsters

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Transcribes Canary:
 * - `MonsterRelevancePolicy::update`
 *   (src/creatures/monsters/monster_relevance.cpp:17-33)
 * - `tests/unit/game/monster_relevance_test.cpp`
 *
 * Time points and holds are steady-clock milliseconds, matching the C++
 * `MonsterRelevancePolicy::Clock` (`std::chrono::steady_clock`).
 */
class MonsterRelevanceTest {

    private val now = 10_000L

    @Test
    fun `enters immediately and uses distance hysteresis`() {
        var state = MonsterRelevancePolicy.update(
            MonsterRelevanceState(),
            MonsterRelevanceSnapshot(playerSpectators = 1, nearestPlayerDistance = 10),
            now,
        )
        assertThat(state.tier).isEqualTo(MonsterRelevanceTier.Visible)

        state = MonsterRelevancePolicy.update(
            state,
            MonsterRelevanceSnapshot(playerSpectators = 1, nearestPlayerDistance = 12),
            now + 1_000L,
        )
        assertThat(state.tier).isEqualTo(MonsterRelevanceTier.Visible)

        val background = MonsterRelevancePolicy.update(
            MonsterRelevanceState(),
            MonsterRelevanceSnapshot(playerSpectators = 1, nearestPlayerDistance = 12),
            now,
        )
        assertThat(background.tier).isEqualTo(MonsterRelevanceTier.Background)
    }

    @Test
    fun `holds visibility then expires without spectators`() {
        var state = MonsterRelevancePolicy.update(
            MonsterRelevanceState(),
            MonsterRelevanceSnapshot(engagedWithPlayer = true),
            now,
            3_000L,
        )
        assertThat(state.tier).isEqualTo(MonsterRelevanceTier.Visible)

        state = MonsterRelevancePolicy.update(state, MonsterRelevanceSnapshot(), now + 2_999L, 3_000L)
        assertThat(state.tier).isEqualTo(MonsterRelevanceTier.Visible)

        state = MonsterRelevancePolicy.update(state, MonsterRelevanceSnapshot(), now + 3_000L, 3_000L)
        assertThat(state.tier).isEqualTo(MonsterRelevanceTier.Background)
        assertThat(state.visibleUntil).isZero()
    }

    @Test
    fun `player engagement overrides distance`() {
        val state = MonsterRelevancePolicy.update(
            MonsterRelevanceState(),
            MonsterRelevanceSnapshot(nearestPlayerDistance = 100, engagedWithPlayer = true),
            now,
        )

        assertThat(state.tier).isEqualTo(MonsterRelevanceTier.Visible)
    }

    @Test
    fun `default hold keeps the monster visible for three seconds`() {
        val state = MonsterRelevancePolicy.update(
            MonsterRelevanceState(),
            MonsterRelevanceSnapshot(engagedWithPlayer = true),
            now,
        )

        assertThat(state.visibleUntil).isEqualTo(now + 3_000L)
    }

    @Test
    fun `player engagement refreshes the hold window`() {
        val visible = MonsterRelevancePolicy.update(
            MonsterRelevanceState(),
            MonsterRelevanceSnapshot(engagedWithPlayer = true),
            now,
            3_000L,
        )

        val refreshed = MonsterRelevancePolicy.update(
            visible,
            MonsterRelevanceSnapshot(engagedWithPlayer = true),
            now + 2_000L,
            3_000L,
        )

        assertThat(refreshed.tier).isEqualTo(MonsterRelevanceTier.Visible)
        assertThat(refreshed.visibleUntil).isEqualTo(now + 5_000L)
    }

    @Test
    fun `visible monster beyond exit distance keeps only the remaining hold`() {
        val visible = MonsterRelevancePolicy.update(
            MonsterRelevanceState(),
            MonsterRelevanceSnapshot(engagedWithPlayer = true),
            now,
            3_000L,
        )

        val held = MonsterRelevancePolicy.update(
            visible,
            MonsterRelevanceSnapshot(playerSpectators = 1, nearestPlayerDistance = 13),
            now + 1_000L,
            3_000L,
        )
        assertThat(held.tier).isEqualTo(MonsterRelevanceTier.Visible)
        assertThat(held.visibleUntil).isEqualTo(now + 3_000L)

        val expired = MonsterRelevancePolicy.update(
            held,
            MonsterRelevanceSnapshot(playerSpectators = 1, nearestPlayerDistance = 13),
            now + 3_000L,
            3_000L,
        )
        assertThat(expired.tier).isEqualTo(MonsterRelevanceTier.Background)
        assertThat(expired.visibleUntil).isZero()
    }
}
