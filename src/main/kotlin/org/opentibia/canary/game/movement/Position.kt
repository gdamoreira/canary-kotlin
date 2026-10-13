package org.opentibia.canary.game.movement

import org.opentibia.canary.map.Position
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * C++-parity position math transcribed from Canary.
 *
 *  - `Position::getOffsetX/Y/Z`, `getDistanceX/Y/Z`, `getDiagonalDistance`,
 *    `getEuclideanDistance` and the two `areInRange` rectangles are static
 *    members of `struct Position` (src/game/movement/position.hpp:33-65).
 *  - `getNextPosition` and `getDirectionTo` are the free functions of
 *    src/utils/tools.cpp:581-690.
 *  - `getRandomDirection` lives in src/game/movement/position.cpp:20-30.
 *
 * Canary's `DIRECTION_NONE` for equal positions maps to `null`, the same
 * convention already used by [MovementEngine.getDirectionTo].
 */

/** `Position::getOffsetX` (src/game/movement/position.hpp:43). */
fun getOffsetX(p1: Position, p2: Position): Int = p1.x - p2.x

/** `Position::getOffsetY` (src/game/movement/position.hpp:46). */
fun getOffsetY(p1: Position, p2: Position): Int = p1.y - p2.y

/** `Position::getOffsetZ` (src/game/movement/position.hpp:49). */
fun getOffsetZ(p1: Position, p2: Position): Int = p1.z - p2.z

/** `Position::getDistanceX` (src/game/movement/position.hpp:53). */
fun getDistanceX(p1: Position, p2: Position): Int = abs(getOffsetX(p1, p2))

/** `Position::getDistanceY` (src/game/movement/position.hpp:56). */
fun getDistanceY(p1: Position, p2: Position): Int = abs(getOffsetY(p1, p2))

/** `Position::getDistanceZ` (src/game/movement/position.hpp:59). */
fun getDistanceZ(p1: Position, p2: Position): Int = abs(getOffsetZ(p1, p2))

/** `Position::getDiagonalDistance` (src/game/movement/position.hpp:62): the larger axis. */
fun getDiagonalDistance(p1: Position, p2: Position): Int = max(getDistanceX(p1, p2), getDistanceY(p1, p2))

/**
 * `Position::getEuclideanDistance` (src/game/movement/position.cpp:14-18): the
 * x/y distance; z is deliberately not part of the formula. The squared sum is
 * evaluated in double precision rather than Canary's `int32_t` (which wraps
 * for coordinates beyond ~46340) with the same in-range result.
 */
fun getEuclideanDistance(p1: Position, p2: Position): Double {
    val dx = getDistanceX(p1, p2).toDouble()
    val dy = getDistanceY(p1, p2).toDouble()
    return sqrt(dx * dx + dy * dy)
}

/** `Position::areInRange<deltax, deltay>` (src/game/movement/position.hpp:33-36). */
fun areInRange(p1: Position, p2: Position, deltax: Int, deltay: Int): Boolean =
    getDistanceX(p1, p2) <= deltax && getDistanceY(p1, p2) <= deltay

/** `Position::areInRange<deltax, deltay, deltaz>` (src/game/movement/position.hpp:38-41). */
fun areInRange(p1: Position, p2: Position, deltax: Int, deltay: Int, deltaz: Int): Boolean =
    getDistanceX(p1, p2) <= deltax && getDistanceY(p1, p2) <= deltay && getDistanceZ(p1, p2) <= deltaz

/**
 * `getNextPosition(Direction, Position)` (src/utils/tools.cpp:581-624): applies
 * the direction delta to a copy of [pos]. `DIRECTION_NONE` does not exist in the
 * Kotlin [Direction] enum, so every enum entry moves (the C++ `default` branch is
 * unreachable for valid directions).
 */
fun getNextPosition(direction: Direction, pos: Position): Position = when (direction) {
    Direction.North -> Position(pos.x, pos.y - 1, pos.z)
    Direction.South -> Position(pos.x, pos.y + 1, pos.z)
    Direction.West -> Position(pos.x - 1, pos.y, pos.z)
    Direction.East -> Position(pos.x + 1, pos.y, pos.z)
    Direction.SouthWest -> Position(pos.x - 1, pos.y + 1, pos.z)
    Direction.NorthWest -> Position(pos.x - 1, pos.y - 1, pos.z)
    Direction.NorthEast -> Position(pos.x + 1, pos.y - 1, pos.z)
    Direction.SouthEast -> Position(pos.x + 1, pos.y + 1, pos.z)
}

/**
 * `getDirectionTo(from, to, exactDiagonalOnly = true)` (src/utils/tools.cpp:626-670).
 *
 * With [exactDiagonalOnly] a diagonal is returned only when `|dx| == |dy|`; the
 * dominant axis wins otherwise. With `false` any quadrant yields its diagonal.
 * `DIRECTION_NONE` is represented as `null`.
 */
fun getDirectionTo(from: Position, to: Position, exactDiagonalOnly: Boolean = true): Direction? {
    if (from == to) {
        return null
    }

    val dx = getOffsetX(from, to)
    val dy = getOffsetY(from, to)

    if (exactDiagonalOnly) {
        val absDx = abs(dx)
        val absDy = abs(dy)

        if (absDx > absDy) {
            return if (dx < 0) Direction.East else Direction.West
        }
        if (absDx < absDy) {
            return if (dy > 0) Direction.North else Direction.South
        }
    }

    if (dx < 0) {
        if (dy < 0) {
            return Direction.SouthEast
        }
        if (dy > 0) {
            return Direction.NorthEast
        }
        return Direction.East
    }

    if (dx > 0) {
        if (dy < 0) {
            return Direction.SouthWest
        }
        if (dy > 0) {
            return Direction.NorthWest
        }
        return Direction.West
    }

    return if (dy > 0) Direction.North else Direction.South
}

/**
 * `Position::getRandomDirection` (src/game/movement/position.cpp:20-30): shuffles
 * the `{ NORTH, WEST, EAST, SOUTH }` list and returns the first entry. Canary's
 * global generator is replaced by an injectable [random] per AGENTS.md 1.1.
 */
fun getRandomDirection(random: Random = Random.Default): Direction =
    listOf(Direction.North, Direction.West, Direction.East, Direction.South)
        .shuffled(random)
        .first()
