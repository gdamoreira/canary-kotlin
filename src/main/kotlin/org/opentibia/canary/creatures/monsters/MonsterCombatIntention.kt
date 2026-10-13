package org.opentibia.canary.creatures.monsters

import org.opentibia.canary.map.Position
import kotlin.math.abs
import kotlin.math.max

/**
 * Port of `struct MonsterCombatSpellGeometry`
 * (src/creatures/monsters/monster_combat_intention.hpp:20-25).
 */
class MonsterCombatSpellGeometry(
    val index: Int = 0,
    val range: Int = 0,
    val enabled: Boolean = false,
    val melee: Boolean = false,
)

/**
 * Port of `struct MonsterCombatIntentionRequest`
 * (src/creatures/monsters/monster_combat_intention.hpp:27-32).
 */
class MonsterCombatIntentionRequest(
    val origin: Position = Position(0, 0, 0),
    val target: Position = Position(0, 0, 0),
    val fleeing: Boolean = false,
    val spells: List<MonsterCombatSpellGeometry> = emptyList(),
)

/**
 * Port of `struct MonsterCombatIntentionResult`
 * (src/creatures/monsters/monster_combat_intention.hpp:34-37).
 */
class MonsterCombatIntentionResult {
    var geometricallyEligibleSpellIndices: List<Int> = emptyList()
    var canceled: Boolean = false
}

/**
 * Port of `MonsterCombatIntentionEvaluator::evaluate`
 * (src/creatures/monsters/monster_combat_intention.cpp:16-42).
 *
 * Returns the spell indices whose geometry admits casting at the target on the same
 * floor: enabled spells that are not melee while fleeing and whose range is zero
 * (unlimited) or at least the Chebyshev distance between origin and target. The
 * result keeps source order. [stopRequested] replaces the C++ `std::stop_token`;
 * when it trips before the evaluation the result is canceled, and when it trips
 * mid-walk any already-collected indices are discarded before returning canceled.
 */
object MonsterCombatIntentionEvaluator {
    fun evaluate(
        request: MonsterCombatIntentionRequest,
        stopRequested: () -> Boolean = { false },
    ): MonsterCombatIntentionResult {
        val result = MonsterCombatIntentionResult()
        if (stopRequested()) {
            result.canceled = true
            return result
        }
        if (request.origin.z != request.target.z) {
            return result
        }

        val distance = max(
            abs(request.origin.x - request.target.x),
            abs(request.origin.y - request.target.y),
        )

        val eligible = ArrayList<Int>(request.spells.size)
        for (spell in request.spells) {
            if (stopRequested()) {
                result.geometricallyEligibleSpellIndices = emptyList()
                result.canceled = true
                return result
            }
            if (!spell.enabled || (spell.melee && request.fleeing)) {
                continue
            }
            if (spell.range == 0 || distance <= spell.range) {
                eligible.add(spell.index)
            }
        }
        result.geometricallyEligibleSpellIndices = eligible
        return result
    }
}
