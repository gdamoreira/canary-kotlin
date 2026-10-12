package org.opentibia.canary.game.movement

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.map.Position

class PositionFunctionsTranscriptTest {

    @Test
    fun `returns expected directions matching Canary position_functions_test`() {
        // Same coords
        assertThat(MovementEngine.getDirectionTo(Position(0, 0, 0), Position(0, 0, 0))).isNull()
        assertThat(MovementEngine.getDirectionTo(Position(100, 100, 100), Position(100, 100, 100))).isNull()

        // Cardinal
        assertThat(MovementEngine.getDirectionTo(Position(125, 1123, 5), Position(125, 1153, 5))).isEqualTo(Direction.South)
        assertThat(MovementEngine.getDirectionTo(Position(5555, 3212, 15), Position(5555, 3211, 15))).isEqualTo(Direction.North)
        assertThat(MovementEngine.getDirectionTo(Position(32132, 65000, 11), Position(31512, 65000, 11))).isEqualTo(Direction.West)
        assertThat(MovementEngine.getDirectionTo(Position(5123, 6554, 7), Position(40000, 6554, 7))).isEqualTo(Direction.East)

        // Diagonal far
        assertThat(MovementEngine.getDirectionTo(Position(25200, 33173, 8), Position(5200, 13173, 7))).isEqualTo(Direction.NorthWest)
        assertThat(MovementEngine.getDirectionTo(Position(22137, 6, 9), Position(22141, 2, 15))).isEqualTo(Direction.NorthEast)
        assertThat(MovementEngine.getDirectionTo(Position(32011, 2197, 1), Position(32135, 2321, 13))).isEqualTo(Direction.SouthEast)
        assertThat(MovementEngine.getDirectionTo(Position(13121, 5213, 5), Position(5213, 13121, 5))).isEqualTo(Direction.SouthWest)

        // Non-diagonal bias (exactDiagonal = false)
        assertThat(MovementEngine.getDirectionTo(Position(123, 122, 0), Position(0, 0, 0), exactDiagonal = false)).isEqualTo(Direction.West)
        assertThat(MovementEngine.getDirectionTo(Position(122, 123, 0), Position(0, 0, 0), exactDiagonal = false)).isEqualTo(Direction.North)
    }
}
