package org.opentibia.canary.creatures

import org.opentibia.canary.combat.CombatType
import org.opentibia.canary.game.movement.Direction
import org.opentibia.canary.map.AStarNode
import org.opentibia.canary.map.AStarNodes
import org.opentibia.canary.map.NavCell
import org.opentibia.canary.map.NavCellFlag
import org.opentibia.canary.map.Position
import kotlin.math.abs
import kotlin.math.max

/**
 * Faithful port of Canary `struct MonsterPathTraits`
 * (src/creatures/monsters/monster_pathfinding.hpp:22-32).
 *
 * [summonInvitedHouseIds] must be kept sorted like the C++ vector because
 * [MonsterPathfinder.canEnter] performs the same `std::ranges::binary_search`.
 */
class MonsterPathTraits {
    val fieldAllowed = BooleanArray(CombatType.COUNT.value)
    val fieldPenalty = BooleanArray(CombatType.COUNT.value)
    val summonInvitedHouseIds = mutableListOf<Int>()
    var canPushItems = false
    var canPushCreatures = false
    var isSummon = false
    var canEnterProtectionZone = false
    var canSeeInvisibility = false
    var moveLocked = false
}

/**
 * Port of Canary `struct MonsterPathRequest`
 * (src/creatures/monsters/monster_pathfinding.hpp:34-40).
 *
 * The C++ request holds a `NavRegionSnapshot`; the Kotlin map does not build
 * navigation snapshots yet, so the snapshot is injected as [radius]
 * (`NavRegionSnapshot::getRadius`) plus [getCell] (`NavRegionSnapshot::getCell`, the
 * injected walkability predicate). [getCell] is expected to return `null` for blocked
 * or non-existent cells and a [NavCell] for candidate cells; [MonsterPathfinder.canEnter]
 * then applies the monster-specific tile contracts to the returned cell.
 */
class MonsterPathRequest(
    val start: Position,
    val target: Position,
    val params: FindPathParams,
    val traits: MonsterPathTraits,
    val radius: Int,
    val getCell: (Position) -> NavCell?,
)

/** `enum class MonsterPathStatus` (src/creatures/monsters/monster_pathfinding.hpp:42-46). */
enum class MonsterPathStatus {
    Found,
    NotFound,
    Cancelled,
}

/**
 * Faithful port of Canary `struct MonsterPathResult`
 * (src/creatures/monsters/monster_pathfinding.hpp:48-56).
 *
 * [directions] is stored in the same order as Canary: from the endpoint back to the
 * start, so callers expand it with `directions.reversed()` (see `Creature::getNextStep`
 * popping `listWalkDir.back()` in src/creatures/creature.cpp:315-324).
 */
class MonsterPathResult {
    var status: MonsterPathStatus = MonsterPathStatus.NotFound
    var endpoint: Position = Position(0, 0, 0)
    val directions = mutableListOf<Direction>()

    /** `MonsterPathResult::found` (monster_pathfinding.hpp:53-55). */
    fun found(): Boolean = status == MonsterPathStatus.Found
}

/** Mutable out-parameter owner mirroring Canary's `int32_t &bestMatchDistance`. */
private class BestMatchDistance(var value: Int)

/**
 * Faithful port of Canary `class MonsterPathfinder`
 * (src/creatures/monsters/monster_pathfinding.hpp:58-68,
 * src/creatures/monsters/monster_pathfinding.cpp:19-396).
 *
 * The A* search runs over the injected [MonsterPathRequest.getCell] predicate and
 * [MonsterPathRequest.radius] instead of a `NavRegionSnapshot`; every other rule —
 * neighbour tables, pruning, node bookkeeping, scoring and path reconstruction — is
 * transcribed from the C++ implementation. The C++ build uses `int_fast32_t` math and
 * `uint16_t` coordinates; the unsigned coordinate wraps performed by
 * `static_cast<uint16_t>` are reproduced with `and 0xFFFF`.
 */
object MonsterPathfinder {
    private const val NORMAL_WALK_COST = 10
    private const val CREATURE_TILE_COST = NORMAL_WALK_COST * 4
    private const val HARMFUL_FIELD_COST = NORMAL_WALK_COST * 18

    private val ALL_NEIGHBORS = arrayOf(
        intArrayOf(-1, 0),
        intArrayOf(0, 1),
        intArrayOf(1, 0),
        intArrayOf(0, -1),
        intArrayOf(-1, -1),
        intArrayOf(1, -1),
        intArrayOf(1, 1),
        intArrayOf(-1, 1),
    )

    /** Indexed by the [Direction] ordinal, exactly like the C++ `Direction` enum values. */
    private val DIRECTION_NEIGHBORS = arrayOf(
        arrayOf(intArrayOf(-1, 0), intArrayOf(0, 1), intArrayOf(1, 0), intArrayOf(1, 1), intArrayOf(-1, 1)),
        arrayOf(intArrayOf(-1, 0), intArrayOf(0, 1), intArrayOf(0, -1), intArrayOf(-1, -1), intArrayOf(-1, 1)),
        arrayOf(intArrayOf(-1, 0), intArrayOf(1, 0), intArrayOf(0, -1), intArrayOf(-1, -1), intArrayOf(1, -1)),
        arrayOf(intArrayOf(0, 1), intArrayOf(1, 0), intArrayOf(0, -1), intArrayOf(1, -1), intArrayOf(1, 1)),
        arrayOf(intArrayOf(1, 0), intArrayOf(0, -1), intArrayOf(-1, -1), intArrayOf(1, -1), intArrayOf(1, 1)),
        arrayOf(intArrayOf(-1, 0), intArrayOf(0, -1), intArrayOf(-1, -1), intArrayOf(1, -1), intArrayOf(-1, 1)),
        arrayOf(intArrayOf(0, 1), intArrayOf(1, 0), intArrayOf(1, -1), intArrayOf(1, 1), intArrayOf(-1, 1)),
        arrayOf(intArrayOf(-1, 0), intArrayOf(0, 1), intArrayOf(-1, -1), intArrayOf(1, 1), intArrayOf(-1, 1)),
    )

    /** `MonsterPathfinder::canEnter` (monster_pathfinding.cpp:69-107). */
    fun canEnter(cell: NavCell, traits: MonsterPathTraits): Boolean {
        if (traits.isSummon && cell.houseId != 0) {
            val invited = traits.summonInvitedHouseIds.binarySearch(cell.houseId) >= 0
            val noFieldPathBlocked = cell.hasFlag(NavCellFlag.FloorChangeWest) && cell.hasFlag(NavCellFlag.NoFieldBlockPath)
            return invited && !cell.hasFlag(NavCellFlag.BlockSolid) && !noFieldPathBlocked
        }
        if (traits.moveLocked || !cell.hasFlag(NavCellFlag.HasGround) || cell.hasFlag(NavCellFlag.FloorChange) || cell.hasFlag(NavCellFlag.Teleport)) {
            return false
        }
        if (cell.hasFlag(NavCellFlag.ProtectionZone) && !traits.canEnterProtectionZone) {
            return false
        }
        if (traits.isSummon && cell.hasFlag(NavCellFlag.WalkableSea)) {
            return false
        }

        if (traits.canPushCreatures && !traits.isSummon) {
            if (cell.hasUnpushableCreature) {
                return false
            }
        } else if (cell.blockingCreatures != 0) {
            return false
        }

        if (cell.hasFlag(NavCellFlag.ImmovableBlockSolid) || cell.hasFlag(NavCellFlag.ImmovableNoFieldBlockPath)) {
            return false
        }
        if ((cell.hasFlag(NavCellFlag.BlockSolid) || cell.hasFlag(NavCellFlag.NoFieldBlockPath)) && !traits.canPushItems) {
            return false
        }

        if (cell.hasFlag(NavCellFlag.HarmfulField)) {
            val combatType = cell.harmfulFieldCombatType
            if (combatType >= traits.fieldAllowed.size || !traits.fieldAllowed[combatType]) {
                return false
            }
        }
        return true
    }

    /** `MonsterPathfinder::getTileWalkCost` (monster_pathfinding.cpp:109-119). */
    private fun getTileWalkCost(cell: NavCell, traits: MonsterPathTraits): Int {
        val hasVisibleCreature = if (traits.canSeeInvisibility) cell.blockingCreatures != 0 else cell.hasNonInvisibleCreature
        var cost = if (hasVisibleCreature) CREATURE_TILE_COST else 0
        if (cell.hasFlag(NavCellFlag.HarmfulField)) {
            val combatType = cell.harmfulFieldCombatType
            if (combatType < traits.fieldPenalty.size && traits.fieldPenalty[combatType]) {
                cost += HARMFUL_FIELD_COST
            }
        }
        return cost
    }

    /** `MonsterPathfinder::isInRange` (monster_pathfinding.cpp:121-143). */
    private fun isInRange(request: MonsterPathRequest, testPosition: Position): Boolean {
        val params = request.params
        if (params.fullPathSearch) {
            if (testPosition.x > request.target.x + params.maxTargetDist || testPosition.x < request.target.x - params.maxTargetDist ||
                testPosition.y > request.target.y + params.maxTargetDist || testPosition.y < request.target.y - params.maxTargetDist
            ) {
                return false
            }
        } else {
            val dx = request.start.x - request.target.x
            val dxMax = if (dx >= 0) params.maxTargetDist else 0
            val dxMin = if (dx <= 0) params.maxTargetDist else 0
            if (testPosition.x > request.target.x + dxMax || testPosition.x < request.target.x - dxMin) {
                return false
            }

            val dy = request.start.y - request.target.y
            val dyMax = if (dy >= 0) params.maxTargetDist else 0
            val dyMin = if (dy <= 0) params.maxTargetDist else 0
            if (testPosition.y > request.target.y + dyMax || testPosition.y < request.target.y - dyMin) {
                return false
            }
        }
        return true
    }

    /** `MonsterPathfinder::matchesTarget` (monster_pathfinding.cpp:145-169). */
    private fun matchesTarget(request: MonsterPathRequest, testPosition: Position, bestMatchDistance: BestMatchDistance): Boolean {
        if (!isInRange(request, testPosition)) {
            return false
        }
        if (request.params.clearSight && !isSightClear(request.getCell, testPosition, request.target)) {
            return false
        }

        val testDistance = max(abs(request.target.x - testPosition.x), abs(request.target.y - testPosition.y))
        if (request.params.maxTargetDist == 1) {
            return testDistance >= request.params.minTargetDist && testDistance <= request.params.maxTargetDist
        }
        if (testDistance > request.params.maxTargetDist || testDistance < request.params.minTargetDist) {
            return false
        }
        if (testDistance == request.params.maxTargetDist) {
            bestMatchDistance.value = 0
            return true
        }
        if (testDistance > bestMatchDistance.value) {
            bestMatchDistance.value = testDistance
            return true
        }
        return false
    }

    /**
     * `MonsterPathfinder::isSightClear` (monster_pathfinding.cpp:171-258).
     *
     * [navigation] is the injected cell provider (`NavRegionSnapshot::getCell`); the
     * Bresenham-style walk keeps Canary's `uint16_t` accumulator wrap semantics.
     */
    fun isSightClear(navigation: (Position) -> NavCell?, from: Position, to: Position): Boolean {
        if (from.z != to.z) {
            return false
        }
        if (abs(from.x - to.x) <= 1 && abs(from.y - to.y) <= 1) {
            return true
        }

        var start = from
        var destination = to
        var distanceX = abs(start.x - destination.x)
        var distanceY = abs(start.y - destination.y)
        if (start.y == destination.y) {
            val delta = if (start.x < destination.x) 0x0001 else 0xFFFF
            while (--distanceX > 0) {
                start = Position((start.x + delta) and 0xFFFF, start.y, start.z)
                if (isProjectileBlocked(navigation, start)) {
                    return false
                }
            }
        } else if (start.x == destination.x) {
            val delta = if (start.y < destination.y) 0x0001 else 0xFFFF
            while (--distanceY > 0) {
                start = Position(start.x, (start.y + delta) and 0xFFFF, start.z)
                if (isProjectileBlocked(navigation, start)) {
                    return false
                }
            }
        } else {
            var errorAdjustment: Int
            var errorAccumulator = 0
            var deltaX = 0x0001
            var deltaY = 0x0001

            if (distanceY > distanceX) {
                errorAdjustment = ((distanceX shl 16) / distanceY) and 0xFFFF
                if (start.y > destination.y) {
                    val swapX = start.x
                    val swapY = start.y
                    start = Position(destination.x, destination.y, start.z)
                    destination = Position(swapX, swapY, destination.z)
                }
                if (start.x > destination.x) {
                    deltaX = 0xFFFF
                    errorAccumulator = (errorAccumulator - errorAdjustment) and 0xFFFF
                }

                while (--distanceY > 0) {
                    var xIncrease = 0
                    val previousError = errorAccumulator
                    errorAccumulator = (errorAccumulator + errorAdjustment) and 0xFFFF
                    if (errorAccumulator <= previousError) {
                        xIncrease = deltaX
                    }
                    val testPosition = Position((start.x + xIncrease) and 0xFFFF, (start.y + deltaY) and 0xFFFF, start.z)
                    if (isProjectileBlocked(navigation, testPosition)) {
                        return abs(start.x - destination.x) <= 1 && abs(start.y - destination.y) <= 1
                    }
                    start = testPosition
                }
            } else {
                errorAdjustment = ((distanceY shl 16) / distanceX) and 0xFFFF
                if (start.x > destination.x) {
                    val swapX = start.x
                    val swapY = start.y
                    start = Position(destination.x, destination.y, start.z)
                    destination = Position(swapX, swapY, destination.z)
                }
                if (start.y > destination.y) {
                    deltaY = 0xFFFF
                    errorAccumulator = (errorAccumulator - errorAdjustment) and 0xFFFF
                }

                while (--distanceX > 0) {
                    var yIncrease = 0
                    val previousError = errorAccumulator
                    errorAccumulator = (errorAccumulator + errorAdjustment) and 0xFFFF
                    if (errorAccumulator <= previousError) {
                        yIncrease = deltaY
                    }
                    val testPosition = Position((start.x + deltaX) and 0xFFFF, (start.y + yIncrease) and 0xFFFF, start.z)
                    if (isProjectileBlocked(navigation, testPosition)) {
                        return abs(start.x - destination.x) <= 1 && abs(start.y - destination.y) <= 1
                    }
                    start = testPosition
                }
            }
        }
        return true
    }

    /**
     * `MonsterPathfinder::find` (monster_pathfinding.cpp:260-396).
     *
     * [stopRequested] replaces the C++ `std::stop_token`; the default empty token can
     * never cancel.
     */
    fun find(request: MonsterPathRequest, stopRequested: () -> Boolean = { false }): MonsterPathResult {
        val result = MonsterPathResult()
        result.endpoint = request.start
        if (request.start.z != request.target.z || request.params.maxSearchDist <= 0 || request.params.maxSearchDist > request.radius) {
            return result
        }
        if (stopRequested()) {
            result.status = MonsterPathStatus.Cancelled
            return result
        }

        val startCell = request.getCell(request.start) ?: return result

        var position = request.start
        var endpoint = Position(0, 0, 0)
        val nodes = AStarNodes(position.x, position.y, getTileWalkCost(startCell, request.traits))
        val bestMatch = BestMatchDistance(0)
        val startDistanceX = abs(request.target.x - position.x)
        val startDistanceY = abs(request.target.y - position.y)
        var directionCount = 0
        var found: AStarNode? = null

        while (true) {
            if (stopRequested()) {
                result.status = MonsterPathStatus.Cancelled
                return result
            }

            val node = nodes.getBestNode()
            if (node == null) {
                if (found != null) {
                    break
                }
                return result
            }

            val x = node.x
            val y = node.y
            position = Position(x, y, position.z)
            if (matchesTarget(request, position, bestMatch)) {
                found = node
                endpoint = position
                if (bestMatch.value == 0) {
                    break
                }
            }

            ++directionCount
            var neighborCount = 8
            var neighbors = ALL_NEIGHBORS
            val parent = node.parent
            if (parent != null) {
                val offsetX = parent.x - x
                val offsetY = parent.y - y
                val incomingDirection = when {
                    offsetY == 0 -> if (offsetX == -1) Direction.West else Direction.East
                    offsetX == 0 -> if (offsetY == -1) Direction.North else Direction.South
                    offsetY == -1 -> if (offsetX == -1) Direction.NorthWest else Direction.NorthEast
                    else -> if (offsetX == -1) Direction.SouthWest else Direction.SouthEast
                }
                neighbors = DIRECTION_NEIGHBORS[incomingDirection.ordinal]
                neighborCount = 5
            }

            val currentCost = node.f
            for (index in 0 until neighborCount) {
                position = Position((x + neighbors[index][0]) and 0xFFFF, (y + neighbors[index][1]) and 0xFFFF, position.z)
                if (abs(request.start.x - position.x) > request.params.maxSearchDist || abs(request.start.y - position.y) > request.params.maxSearchDist) {
                    continue
                }
                if (request.params.keepDistance && !isInRange(request, position)) {
                    continue
                }

                var extraCost: Int
                val neighborNode = nodes.getNodeByPosition(position.x, position.y)
                if (neighborNode != null) {
                    extraCost = neighborNode.c
                } else {
                    val cell = request.getCell(position)
                    if (cell == null || (position != request.start && !canEnter(cell, request.traits))) {
                        continue
                    }
                    extraCost = getTileWalkCost(cell, request.traits)
                }

                val newCost = currentCost + AStarNodes.getMapWalkCost(node, position) + extraCost
                if (neighborNode != null) {
                    if (neighborNode.f <= newCost) {
                        continue
                    }
                    neighborNode.f = newCost
                    neighborNode.parent = node
                    nodes.openNode(neighborNode)
                } else {
                    val distanceX = abs(request.target.x - position.x)
                    val distanceY = abs(request.target.y - position.y)
                    val heuristic = ((distanceX - startDistanceX) * 8) + ((distanceY - startDistanceY) * 8) + (max(distanceX, distanceY) * 8)
                    if (!nodes.createOpenNode(node, position.x, position.y, newCost, heuristic, extraCost)) {
                        if (found != null) {
                            break
                        }
                        return result
                    }
                }
            }
            nodes.closeNode(node)
        }

        val foundNode: AStarNode = found

        result.endpoint = endpoint
        val directions = ArrayList<Direction>(directionCount)
        var previousX = endpoint.x
        var previousY = endpoint.y
        var current = foundNode.parent
        while (current != null) {
            val dx = current.x - previousX
            val dy = current.y - previousY
            previousX = current.x
            previousY = current.y
            appendDirection(directions, dx, dy)
            current = current.parent
        }
        result.directions.addAll(directions)
        result.status = MonsterPathStatus.Found
        return result
    }

    /** `isProjectileBlocked` (monster_pathfinding.cpp:39-42). */
    private fun isProjectileBlocked(navigation: (Position) -> NavCell?, position: Position): Boolean {
        val cell = navigation(position)
        return cell == null || cell.hasFlag(NavCellFlag.BlockProjectile)
    }

    /** `appendDirection` (monster_pathfinding.cpp:44-66). */
    private fun appendDirection(directions: MutableList<Direction>, dx: Int, dy: Int) {
        if (dx == 1) {
            when (dy) {
                1 -> directions.add(Direction.NorthWest)
                -1 -> directions.add(Direction.SouthWest)
                else -> directions.add(Direction.West)
            }
        } else if (dx == -1) {
            when (dy) {
                1 -> directions.add(Direction.NorthEast)
                -1 -> directions.add(Direction.SouthEast)
                else -> directions.add(Direction.East)
            }
        } else if (dy == 1) {
            directions.add(Direction.North)
        } else if (dy == -1) {
            directions.add(Direction.South)
        }
    }
}
