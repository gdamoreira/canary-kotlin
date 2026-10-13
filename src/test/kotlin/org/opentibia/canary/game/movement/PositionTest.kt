package org.opentibia.canary.game.movement

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.map.Position
import kotlin.random.Random

/**
 * Transcript of Canary's position math:
 *  - `Position::getOffsetX/Y/Z`, `getDistanceX/Y/Z`, `getDiagonalDistance`,
 *    `getEuclideanDistance`, `areInRange` (src/game/movement/position.hpp:33-65)
 *  - `getNextPosition`, `getDirectionTo` (src/utils/tools.cpp:581-690)
 *  - `getRandomDirection` (src/game/movement/position.cpp:20-30)
 *
 * The `getDirectionTo` cases mirror Canary's own unit test fixture
 * `kGetDirectionToTestCases` (tests/unit/utils/position_functions_test.cpp:34-54).
 */
class PositionTest {

    @Test
    fun `computes offsets as p1 minus p2 matching Position getOffsetX Y Z`() {
        val p1 = Position(104, 99, 6)
        val p2 = Position(100, 107, 8)

        assertThat(getOffsetX(p1, p2)).isEqualTo(4)
        assertThat(getOffsetY(p1, p2)).isEqualTo(-8)
        assertThat(getOffsetZ(p1, p2)).isEqualTo(-2)

        assertThat(getOffsetX(p2, p1)).isEqualTo(-4)
        assertThat(getOffsetY(p2, p1)).isEqualTo(8)
        assertThat(getOffsetZ(p2, p1)).isEqualTo(2)
    }

    @Test
    fun `computes absolute distances and the larger-axis diagonal distance`() {
        val p1 = Position(104, 99, 6)
        val p2 = Position(100, 107, 8)

        assertThat(getDistanceX(p1, p2)).isEqualTo(4)
        assertThat(getDistanceY(p1, p2)).isEqualTo(8)
        assertThat(getDistanceZ(p1, p2)).isEqualTo(2)
        assertThat(getDiagonalDistance(p1, p2)).isEqualTo(8)
        assertThat(getDiagonalDistance(p2, p1)).isEqualTo(8)
    }

    @Test
    fun `computes the euclidean distance over x and y only`() {
        assertThat(getEuclideanDistance(Position(0, 0, 0), Position(0, 0, 0))).isZero()
        assertThat(getEuclideanDistance(Position(0, 0, 0), Position(3, 4, 0))).isEqualTo(5.0)
        assertThat(getEuclideanDistance(Position(3, 4, 0), Position(0, 0, 0))).isEqualTo(5.0)

        // `Position::getEuclideanDistance` ignores z, exactly like the C++ body.
        assertThat(getEuclideanDistance(Position(0, 0, 0), Position(3, 4, 7))).isEqualTo(5.0)
    }

    @Test
    fun `areInRange checks the dx dy rectangle and ignores z in the two-axis overload`() {
        val center = Position(100, 100, 7)

        assertThat(areInRange(center, Position(101, 101, 7), 1, 1)).isTrue()
        assertThat(areInRange(center, Position(99, 99, 7), 1, 1)).isTrue()
        assertThat(areInRange(center, Position(102, 100, 7), 1, 1)).isFalse()
        assertThat(areInRange(center, Position(100, 102, 7), 1, 1)).isFalse()

        // Canary's Position::areInRange<deltax, deltay> never compares z.
        assertThat(areInRange(center, Position(101, 101, 12), 1, 1)).isTrue()
        // The trade/use rectangle `areInRange<7, 5>` (game.cpp:4865).
        assertThat(areInRange(center, Position(107, 105, 7), 7, 5)).isTrue()
        assertThat(areInRange(center, Position(108, 105, 7), 7, 5)).isFalse()
    }

    @Test
    fun `areInRange checks the dz height in the three-axis overload`() {
        val center = Position(100, 100, 7)

        assertThat(areInRange(center, Position(101, 101, 8), 1, 1, 1)).isTrue()
        assertThat(areInRange(center, Position(101, 101, 9), 1, 1, 1)).isFalse()
        assertThat(areInRange(center, Position(101, 101, 6), 1, 1, 1)).isTrue()
        // `areInRange<30, 30, 1>` party range (party.cpp:639).
        assertThat(areInRange(center, Position(130, 130, 8), 30, 30, 1)).isTrue()
        assertThat(areInRange(center, Position(130, 130, 9), 30, 30, 1)).isFalse()
    }

    @Test
    fun `getNextPosition applies the eight direction deltas`() {
        val start = Position(100, 100, 7)

        assertThat(getNextPosition(Direction.North, start)).isEqualTo(Position(100, 99, 7))
        assertThat(getNextPosition(Direction.South, start)).isEqualTo(Position(100, 101, 7))
        assertThat(getNextPosition(Direction.West, start)).isEqualTo(Position(99, 100, 7))
        assertThat(getNextPosition(Direction.East, start)).isEqualTo(Position(101, 100, 7))
        assertThat(getNextPosition(Direction.SouthWest, start)).isEqualTo(Position(99, 101, 7))
        assertThat(getNextPosition(Direction.NorthWest, start)).isEqualTo(Position(99, 99, 7))
        assertThat(getNextPosition(Direction.NorthEast, start)).isEqualTo(Position(101, 99, 7))
        assertThat(getNextPosition(Direction.SouthEast, start)).isEqualTo(Position(101, 101, 7))
        assertThat(getNextPosition(Direction.East, Position(250, 250, 8)).z).isEqualTo(8)
    }

    @Test
    fun `getDirectionTo matches every Canary position_functions_test case`() {
        // (from, to, expected, expectedForExactDiagonal, description) from
        // tests/unit/utils/position_functions_test.cpp:34-54.
        val cases = listOf(
            Case(Position(0, 0, 0), Position(0, 0, 0), null, null, "origin"),
            Case(Position(100, 100, 100), Position(100, 100, 100), null, null, "same coords"),
            Case(Position(125, 1123, 5), Position(125, 1153, 5), Direction.South, Direction.South, "south"),
            Case(Position(5555, 3212, 15), Position(5555, 3211, 15), Direction.North, Direction.North, "north"),
            Case(Position(32132, 65000, 11), Position(31512, 65000, 11), Direction.West, Direction.West, "west"),
            Case(Position(5123, 6554, 7), Position(40000, 6554, 7), Direction.East, Direction.East, "east"),
            Case(Position(25200, 33173, 8), Position(5200, 13173, 7), Direction.NorthWest, Direction.NorthWest, "northwest far"),
            Case(Position(22137, 6, 9), Position(22141, 2, 15), Direction.NorthEast, Direction.NorthEast, "northeast far"),
            Case(Position(32011, 2197, 1), Position(32135, 2321, 13), Direction.SouthEast, Direction.SouthEast, "southeast far"),
            Case(Position(13121, 5213, 5), Position(5213, 13121, 5), Direction.SouthWest, Direction.SouthWest, "southwest far"),
            Case(Position(123, 122, 0), Position(0, 0, 0), Direction.NorthWest, Direction.West, "northwest bias west"),
            Case(Position(122, 123, 0), Position(0, 0, 0), Direction.NorthWest, Direction.North, "northwest bias north"),
            Case(Position(0, 122, 0), Position(123, 0, 0), Direction.NorthEast, Direction.East, "northeast bias east"),
            Case(Position(0, 123, 0), Position(122, 0, 0), Direction.NorthEast, Direction.North, "northeast bias north"),
            Case(Position(0, 0, 0), Position(123, 122, 0), Direction.SouthEast, Direction.East, "southeast bias east"),
            Case(Position(0, 0, 0), Position(122, 123, 0), Direction.SouthEast, Direction.South, "southeast bias south"),
            Case(Position(123, 0, 0), Position(0, 122, 0), Direction.SouthWest, Direction.West, "southwest bias west"),
            Case(Position(122, 0, 0), Position(0, 123, 0), Direction.SouthWest, Direction.South, "southwest bias south"),
        )

        for (case in cases) {
            assertThat(getDirectionTo(case.from, case.to, exactDiagonalOnly = false))
                .describedAs("${case.description} (exactDiagonalOnly = false)")
                .isEqualTo(case.expected)
            assertThat(getDirectionTo(case.from, case.to))
                .describedAs("${case.description} (exactDiagonalOnly = true)")
                .isEqualTo(case.expectedForExactDiagonal)
        }
    }

    @Test
    fun `getRandomDirection only yields the four cardinal directions`() {
        val cardinal = setOf(Direction.North, Direction.West, Direction.East, Direction.South)
        val sampled = (1..200).map { seed -> getRandomDirection(Random(seed)) }

        assertThat(sampled).isSubsetOf(cardinal)
        assertThat(sampled.toSet()).isEqualTo(cardinal)
    }

    private data class Case(
        val from: Position,
        val to: Position,
        val expected: Direction?,
        val expectedForExactDiagonal: Direction?,
        val description: String,
    )
}
