package org.opentibia.canary.combat

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.map.Position

/**
 * Transcribes the `AreaCombat`/`MatrixArea` area data
 * (`src/creatures/combat/combat.cpp:2807-2586`, `combat.hpp:107-176`) built by
 * the pre-made areas of `data/scripts/lib/register_spells.lua`.
 */
class AreaCombatTest {

    private fun position(x: Int, y: Int): Position = Position(x, y, 7)

    // AREA_SQUARE1X1 (register_spells.lua:488-492).
    private val square1x1: List<Int> = listOf(
        1, 1, 1,
        1, 3, 1,
        1, 1, 1,
    )

    // AREA_WAVE4 (register_spells.lua:56-61).
    private val wave4: List<Int> = listOf(
        1, 1, 1, 1, 1,
        0, 1, 1, 1, 0,
        0, 1, 1, 1, 0,
        0, 0, 3, 0, 0,
    )

    @Test
    fun `setupArea builds the north matrix from the flattened area list`() {
        val area = AreaCombat()
        area.setupArea(square1x1, rows = 3)

        val north = area.getArea(position(100, 100), position(100, 99))!!
        assertThat(north.rows).isEqualTo(3)
        assertThat(north.cols).isEqualTo(3)
        assertThat(north.centerY).isEqualTo(1)
        assertThat(north.centerX).isEqualTo(1)

        for (row in 0 until 3) {
            for (col in 0 until 3) {
                assertThat(north.getValue(row, col)).isTrue()
            }
        }
    }

    @Test
    fun `createArea keeps the centre marker and the wave shape`() {
        val area = AreaCombat()
        area.setupArea(wave4, rows = 4)

        val north = area.getArea(position(100, 100), position(100, 99))!!
        assertThat(north.rows).isEqualTo(4)
        assertThat(north.cols).isEqualTo(5)
        assertThat(north.centerY).isEqualTo(3)
        assertThat(north.centerX).isEqualTo(2)

        assertThat(north.getValue(0, 0)).isTrue()
        assertThat(north.getValue(1, 0)).isFalse()
        assertThat(north.getValue(3, 2)).isTrue()
        assertThat(north.getValue(3, 1)).isFalse()
    }

    @Test
    fun `getArea rotates the matrix by the target offset`() {
        val area = AreaCombat()
        area.setupArea(wave4, rows = 4)

        // dx > 0 selects the ROTATE90 matrix.
        val east = area.getArea(position(100, 100), position(101, 100))!!
        assertThat(east.rows).isEqualTo(10)
        assertThat(east.cols).isEqualTo(10)
        assertThat(east.centerY).isEqualTo(4)
        assertThat(east.centerX).isEqualTo(4)
        // Input (y=0, x=0) maps to output[y+2][7-y] = (2, 7).
        assertThat(east.getValue(2, 7)).isTrue()
        assertThat(east.getValue(2, 2)).isFalse()

        // dy < 0 selects the untransformed NORTH matrix.
        val north = area.getArea(position(100, 100), position(100, 99))!!
        assertThat(north.rows).isEqualTo(4)
        assertThat(north.getValue(0, 0)).isTrue()
    }

    @Test
    fun `setupArea by radius selects the template rings`() {
        val area = AreaCombat()
        area.setupArea(radius = 1)

        val centerOnly = area.getArea(position(100, 100), position(100, 99))!!
        assertThat(centerOnly.rows).isEqualTo(13)
        assertThat(centerOnly.cols).isEqualTo(13)
        assertThat(centerOnly.centerY).isEqualTo(6)
        assertThat(centerOnly.centerX).isEqualTo(6)
        assertThat(centerOnly.getValue(6, 6)).isTrue()
        assertThat(centerOnly.getValue(6, 5)).isFalse()
        assertThat(centerOnly.getValue(0, 6)).isFalse()

        val radiusThree = AreaCombat()
        radiusThree.setupArea(radius = 3)
        val three = radiusThree.getArea(position(100, 100), position(100, 99))!!
        assertThat(three.getValue(6, 6)).isTrue()
        assertThat(three.getValue(5, 6)).isTrue()
        assertThat(three.getValue(4, 6)).isFalse()

        val radiusEight = AreaCombat()
        radiusEight.setupArea(radius = 8)
        val eight = radiusEight.getArea(position(100, 100), position(100, 99))!!
        assertThat(eight.getValue(0, 6)).isTrue()
        assertThat(eight.getValue(0, 0)).isFalse()
    }

    @Test
    fun `setupArea by length and spread forms a cone`() {
        val area = AreaCombat()
        area.setupArea(length = 4, spread = 2)

        val north = area.getArea(position(100, 100), position(100, 99))!!
        assertThat(north.rows).isEqualTo(4)
        assertThat(north.cols).isEqualTo(5)
        assertThat(north.centerY).isEqualTo(3)
        assertThat(north.centerX).isEqualTo(2)

        // The C++ cone widens for the first `spread` rows before narrowing.
        assertThat(north.getValue(0, 0)).isTrue()
        assertThat(north.getValue(1, 0)).isTrue()
        assertThat(north.getValue(2, 0)).isFalse()
        assertThat(north.getValue(3, 0)).isFalse()
        assertThat(north.getValue(3, 1)).isTrue()
        assertThat(north.getValue(3, 2)).isTrue()
    }

    @Test
    fun `setupArea with no spread forms a straight line`() {
        val area = AreaCombat()
        area.setupArea(length = 4, spread = 0)

        val north = area.getArea(position(100, 100), position(100, 99))!!
        assertThat(north.rows).isEqualTo(4)
        assertThat(north.cols).isEqualTo(1)
        assertThat(north.centerY).isEqualTo(3)
        assertThat(north.centerX).isEqualTo(0)
        assertThat(north.getValue(0, 0)).isTrue()
        assertThat(north.getValue(3, 0)).isTrue()
    }

    // AREADIAGONAL_WAVE4 (register_spells.lua:124-130).
    private val diagonalWave4: List<Int> = listOf(
        0, 0, 0, 0, 1, 0,
        0, 0, 0, 1, 1, 0,
        0, 0, 1, 1, 1, 0,
        0, 1, 1, 1, 1, 0,
        1, 1, 1, 1, 1, 0,
    )

    @Test
    fun `setupExtArea builds the four diagonal quadrants`() {
        val area = AreaCombat()
        area.setupExtArea(diagonalWave4, rows = 5)

        val northWest = area.getArea(position(100, 100), position(99, 99))!!
        assertThat(northWest.rows).isEqualTo(5)
        assertThat(northWest.cols).isEqualTo(6)
        assertThat(northWest.getValue(4, 0)).isTrue()
        assertThat(northWest.getValue(0, 5)).isFalse()

        val northEast = area.getArea(position(100, 100), position(101, 99))!!
        assertThat(northEast.rows).isEqualTo(12)
        assertThat(northEast.getValue(4, 5)).isTrue()

        val southWest = area.getArea(position(100, 100), position(99, 101))!!
        assertThat(southWest.rows).isEqualTo(12)
        assertThat(southWest.getValue(0, 0)).isTrue()

        val southEast = area.getArea(position(100, 100), position(101, 101))!!
        assertThat(southEast.rows).isEqualTo(12)
        assertThat(southEast.getValue(0, 11)).isTrue()
    }

    @Test
    fun `clear drops every direction matrix`() {
        val area = AreaCombat()
        area.setupArea(square1x1, rows = 3)

        area.clear()

        assertThat(area.getArea(position(100, 100), position(100, 99))).isNull()
    }

    @Test
    fun `clone deep copies the matrices`() {
        val area = AreaCombat()
        area.setupArea(square1x1, rows = 3)
        val copy = area.clone()

        val north = copy.getArea(position(100, 100), position(100, 99))!!
        assertThat(north.getValue(0, 0)).isTrue()
        assertThat(north).isNotSameAs(area.getArea(position(100, 100), position(100, 99)))
    }
}
