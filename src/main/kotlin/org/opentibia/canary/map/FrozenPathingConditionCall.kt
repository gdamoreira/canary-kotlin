package org.opentibia.canary.map

import org.opentibia.canary.creatures.FindPathParams
import org.opentibia.canary.game.movement.getDistanceX
import org.opentibia.canary.game.movement.getDistanceY
import org.opentibia.canary.game.movement.getOffsetX
import org.opentibia.canary.game.movement.getOffsetY

/** Carrier for the C++ `int32_t &bestMatchDist` out parameter (`src/creatures/creature.cpp:1819`). */
class BestMatchDistance(var value: Int)

/**
 * Port of `class FrozenPathingConditionCall` (src/creatures/creature.hpp:50-63,
 * src/creatures/creature.cpp:1775-1861).
 *
 * The C++ `operator()` reaches `g_game().isSightClear(...)`; per AGENTS.md 1.1 the
 * owning [GameMap] is constructor-injected instead of the global.
 */
class FrozenPathingConditionCall(
    private val targetPos: Position,
    private val map: GameMap,
) {
    /** `FrozenPathingConditionCall::getTargetPos` (src/creatures/creature.hpp:60-62). */
    fun getTargetPos(): Position = targetPos

    /** `FrozenPathingConditionCall::isInRange` (src/creatures/creature.cpp:1775-1816). */
    fun isInRange(startPos: Position, testPos: Position, fpp: FindPathParams): Boolean {
        if (fpp.fullPathSearch) {
            if (testPos.x > targetPos.x + fpp.maxTargetDist) {
                return false
            }

            if (testPos.x < targetPos.x - fpp.maxTargetDist) {
                return false
            }

            if (testPos.y > targetPos.y + fpp.maxTargetDist) {
                return false
            }

            if (testPos.y < targetPos.y - fpp.maxTargetDist) {
                return false
            }
        } else {
            val dx = getOffsetX(startPos, targetPos)

            val dxMax = if (dx >= 0) fpp.maxTargetDist else 0
            if (testPos.x > targetPos.x + dxMax) {
                return false
            }

            val dxMin = if (dx <= 0) fpp.maxTargetDist else 0
            if (testPos.x < targetPos.x - dxMin) {
                return false
            }

            val dy = getOffsetY(startPos, targetPos)

            val dyMax = if (dy >= 0) fpp.maxTargetDist else 0
            if (testPos.y > targetPos.y + dyMax) {
                return false
            }

            val dyMin = if (dy <= 0) fpp.maxTargetDist else 0
            if (testPos.y < targetPos.y - dyMin) {
                return false
            }
        }
        return true
    }

    /** `FrozenPathingConditionCall::operator()` (src/creatures/creature.cpp:1819-1861). */
    operator fun invoke(
        startPos: Position,
        testPos: Position,
        fpp: FindPathParams,
        bestMatchDist: BestMatchDistance,
    ): Boolean {
        if (!isInRange(startPos, testPos, fpp)) {
            return false
        }

        if (fpp.clearSight && !map.isSightClear(testPos, targetPos, true)) {
            return false
        }

        val testDist = maxOf(getDistanceX(targetPos, testPos), getDistanceY(targetPos, testPos))
        if (fpp.maxTargetDist == 1) {
            if (testDist < fpp.minTargetDist || testDist > fpp.maxTargetDist) {
                return false
            }
            return true
        } else if (testDist <= fpp.maxTargetDist) {
            if (testDist < fpp.minTargetDist) {
                return false
            }
            if (testDist == fpp.maxTargetDist) {
                bestMatchDist.value = 0
                return true
            } else if (testDist > bestMatchDist.value) { // not quite what we want, but the best so far
                bestMatchDist.value = testDist
                return true
            }
        }
        return false
    }
}

