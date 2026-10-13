package org.opentibia.canary.creatures.monsters

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.map.Position

/**
 * Transcribes Canary:
 * - `MonsterCombatIntentionEvaluator::evaluate`
 *   (src/creatures/monsters/monster_combat_intention.cpp:16-42)
 * - `tests/unit/game/monster_combat_intention_test.cpp`
 */
class MonsterCombatIntentionTest {

    private val origin = Position(100, 100, 7)

    private fun spell(
        index: Int,
        range: Int = 0,
        enabled: Boolean = true,
        melee: Boolean = false,
    ) = MonsterCombatSpellGeometry(index = index, range = range, enabled = enabled, melee = melee)

    @Test
    fun `returns enabled in-range indices in source order`() {
        val result = MonsterCombatIntentionEvaluator.evaluate(
            MonsterCombatIntentionRequest(
                origin = origin,
                target = Position(103, 102, 7),
                spells = listOf(
                    spell(0, range = 1),
                    spell(1, range = 3),
                    spell(2, range = 0),
                    spell(3, range = 8, enabled = false),
                ),
            )
        )

        assertThat(result.geometricallyEligibleSpellIndices).containsExactly(1, 2)
        assertThat(result.canceled).isFalse()
    }

    @Test
    fun `excludes melee while fleeing`() {
        val result = MonsterCombatIntentionEvaluator.evaluate(
            MonsterCombatIntentionRequest(
                origin = origin,
                target = Position(101, 100, 7),
                fleeing = true,
                spells = listOf(
                    spell(4, range = 1, melee = true),
                    spell(5, range = 1, melee = false),
                ),
            )
        )

        assertThat(result.geometricallyEligibleSpellIndices).containsExactly(5)
    }

    @Test
    fun `rejects different floors without canceling`() {
        val result = MonsterCombatIntentionEvaluator.evaluate(
            MonsterCombatIntentionRequest(
                origin = origin,
                target = Position(100, 100, 8),
                spells = listOf(spell(1, range = 0)),
            )
        )

        assertThat(result.geometricallyEligibleSpellIndices).isEmpty()
        assertThat(result.canceled).isFalse()
    }

    @Test
    fun `reports cancellation before any evaluation`() {
        val result = MonsterCombatIntentionEvaluator.evaluate(
            MonsterCombatIntentionRequest(
                origin = origin,
                target = origin,
                spells = listOf(spell(1, range = 0)),
            ),
            stopRequested = { true },
        )

        assertThat(result.canceled).isTrue()
        assertThat(result.geometricallyEligibleSpellIndices).isEmpty()
    }

    @Test
    fun `cancellation during the spell walk discards collected indices`() {
        var checks = 0

        val result = MonsterCombatIntentionEvaluator.evaluate(
            MonsterCombatIntentionRequest(
                origin = origin,
                target = Position(101, 100, 7),
                spells = listOf(spell(0, range = 0), spell(1, range = 0)),
            ),
            stopRequested = { ++checks >= 3 },
        )

        assertThat(result.canceled).isTrue()
        assertThat(result.geometricallyEligibleSpellIndices).isEmpty()
    }
}
