package org.opentibia.canary.combat

import org.opentibia.canary.game.movement.Direction
import org.opentibia.canary.map.Position
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** `MatrixOperation_t` (`src/creatures/creatures_definitions.hpp:62-69`). */
enum class MatrixOperation {
    MATRIXOPERATION_COPY,
    MATRIXOPERATION_MIRROR,
    MATRIXOPERATION_FLIP,
    MATRIXOPERATION_ROTATE90,
    MATRIXOPERATION_ROTATE180,
    MATRIXOPERATION_ROTATE270,
}

/**
 * `MatrixArea` (`combat.hpp:107-146`, `combat.cpp:2807-2870`).
 *
 * Boolean cell matrix with a marked centre; `getValue`/`setValue` bounds-check
 * exactly like the C++ accessors.
 */
class MatrixArea(val rows: Int, val cols: Int) {

    private val data: Array<BooleanArray> = Array(rows) { BooleanArray(cols) }

    /** `MatrixArea::getCenter` y component (`combat.hpp:131`). */
    var centerY: Int = 0
        private set

    /** `MatrixArea::getCenter` x component (`combat.hpp:131`). */
    var centerX: Int = 0
        private set

    /** `MatrixArea::setValue` (`combat.cpp:2849-2856`). */
    fun setValue(row: Int, col: Int, value: Boolean) {
        if (row < 0 || col < 0 || row >= rows || col >= cols) {
            throw IndexOutOfBoundsException("MatrixArea($rows, $cols) access ($row, $col)")
        }
        data[row][col] = value
    }

    /** `MatrixArea::getValue` (`combat.cpp:2858-2860`). */
    fun getValue(row: Int, col: Int): Boolean {
        if (row < 0 || col < 0 || row >= rows || col >= cols) {
            throw IndexOutOfBoundsException("MatrixArea($rows, $cols) access ($row, $col)")
        }
        return data[row][col]
    }

    /** `MatrixArea::setCenter` (`combat.cpp:2862-2865`); `y` first like C++. */
    fun setCenter(y: Int, x: Int) {
        centerY = y
        centerX = x
    }

    /** `MatrixArea::clone` (`combat.cpp:2845-2847`). */
    fun clone(): MatrixArea {
        val copy = MatrixArea(rows, cols)
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                copy.data[row][col] = data[row][col]
            }
        }
        copy.centerY = centerY
        copy.centerX = centerX
        return copy
    }
}

/**
 * `AreaCombat` (`combat.hpp:148-176`, `combat.cpp:2268-2586`).
 *
 * The tile enumeration (`AreaCombat::getList`, `combat.cpp:2289-2325`) needs
 * the game map and sight line; it stays with the world system (PARITY GAP).
 * `getArea` is private in Canary and exposed here so the spell/combat systems
 * and tests can inspect the selected direction matrix.
 */
class AreaCombat {

    private val areas: Array<MatrixArea?> = arrayOfNulls(Direction.entries.size)

    private var hasExtArea: Boolean = false

    /** `AreaCombat::clear` (`combat.cpp:2268-2270`). */
    fun clear() {
        areas.fill(null)
    }

    /** `AreaCombat::clone` (`combat.cpp:2272-2283`). */
    fun clone(): AreaCombat {
        val copy = AreaCombat()
        copy.hasExtArea = hasExtArea
        for (index in areas.indices) {
            areas[index]?.let { copy.areas[index] = it.clone() }
        }
        return copy
    }

    /** `AreaCombat::setupArea(list, rows)` (`combat.cpp:2470-2488`). */
    fun setupArea(list: List<Int>, rows: Int) {
        val northArea = createArea(list, rows)

        val maxOutput = max(northArea.cols, northArea.rows) * 2

        val southArea = MatrixArea(maxOutput, maxOutput)
        copyArea(northArea, southArea, MatrixOperation.MATRIXOPERATION_ROTATE180)

        val eastArea = MatrixArea(maxOutput, maxOutput)
        copyArea(northArea, eastArea, MatrixOperation.MATRIXOPERATION_ROTATE90)

        val westArea = MatrixArea(maxOutput, maxOutput)
        copyArea(northArea, westArea, MatrixOperation.MATRIXOPERATION_ROTATE270)

        areas[Direction.North.ordinal] = northArea
        areas[Direction.South.ordinal] = southArea
        areas[Direction.East.ordinal] = eastArea
        areas[Direction.West.ordinal] = westArea
    }

    /** `AreaCombat::setupArea(length, spread)` (`combat.cpp:2490-2522`). */
    fun setupArea(length: Int, spread: Int) {
        val list = mutableListOf<Int>()

        val rows = length
        var cols = 1

        if (spread != 0) {
            cols = ((length - (length % spread)) / spread) * 2 + 1
        }

        var colSpread = cols

        for (y in 1..rows) {
            val mincol = cols - colSpread + 1
            val maxcol = cols - (cols - colSpread)

            for (x in 1..cols) {
                if (y == rows && x == ((cols - (cols % 2)) / 2) + 1) {
                    list.add(3)
                } else if (x in mincol..maxcol) {
                    list.add(1)
                } else {
                    list.add(0)
                }
            }

            if (spread > 0 && y % spread == 0) {
                --colSpread
            }
        }

        setupArea(list, rows)
    }

    /** `AreaCombat::setupArea(radius)` (`combat.cpp:2524-2556`). */
    fun setupArea(radius: Int) {
        val template = arrayOf(
            intArrayOf(0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0, 8, 8, 7, 8, 8, 0, 0, 0, 0),
            intArrayOf(0, 0, 0, 8, 7, 6, 6, 6, 7, 8, 0, 0, 0),
            intArrayOf(0, 0, 8, 7, 6, 5, 5, 5, 6, 7, 8, 0, 0),
            intArrayOf(0, 8, 7, 6, 5, 4, 4, 4, 5, 6, 7, 8, 0),
            intArrayOf(0, 8, 6, 5, 4, 3, 2, 3, 4, 5, 6, 8, 0),
            intArrayOf(8, 7, 6, 5, 4, 2, 1, 2, 4, 5, 6, 7, 8),
            intArrayOf(0, 8, 6, 5, 4, 3, 2, 3, 4, 5, 6, 8, 0),
            intArrayOf(0, 8, 7, 6, 5, 4, 4, 4, 5, 6, 7, 8, 0),
            intArrayOf(0, 0, 8, 7, 6, 5, 5, 5, 6, 7, 8, 0, 0),
            intArrayOf(0, 0, 0, 8, 7, 6, 6, 6, 7, 8, 0, 0, 0),
            intArrayOf(0, 0, 0, 0, 8, 8, 7, 8, 8, 0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 0, 0, 0),
        )

        val list = mutableListOf<Int>()

        for (row in template) {
            for (cell in row) {
                if (cell == 1) {
                    list.add(3)
                } else if (cell in 1..radius) {
                    list.add(1)
                } else {
                    list.add(0)
                }
            }
        }

        setupArea(list, 13)
    }

    /** `AreaCombat::setupExtArea` (`combat.cpp:2558-2586`). */
    fun setupExtArea(list: List<Int>, rows: Int) {
        if (list.isEmpty()) {
            return
        }

        hasExtArea = true

        val nwArea = createArea(list, rows)

        val maxOutput = max(nwArea.cols, nwArea.rows) * 2

        val neArea = MatrixArea(maxOutput, maxOutput)
        copyArea(nwArea, neArea, MatrixOperation.MATRIXOPERATION_MIRROR)

        val swArea = MatrixArea(maxOutput, maxOutput)
        copyArea(nwArea, swArea, MatrixOperation.MATRIXOPERATION_FLIP)

        val seArea = MatrixArea(maxOutput, maxOutput)
        copyArea(swArea, seArea, MatrixOperation.MATRIXOPERATION_MIRROR)

        areas[Direction.NorthWest.ordinal] = nwArea
        areas[Direction.SouthWest.ordinal] = swArea
        areas[Direction.NorthEast.ordinal] = neArea
        areas[Direction.SouthEast.ordinal] = seArea
    }

    /**
     * `AreaCombat::getArea` (`combat.cpp:2408-2436`): selects the matrix by the
     * target offset, the four diagonals when an extended area was registered.
     */
    fun getArea(centerPos: Position, targetPos: Position): MatrixArea? {
        val dx = targetPos.x - centerPos.x
        val dy = targetPos.y - centerPos.y

        var direction = when {
            dx < 0 -> Direction.West
            dx > 0 -> Direction.East
            dy < 0 -> Direction.North
            else -> Direction.South
        }

        if (hasExtArea) {
            if (dx < 0 && dy < 0) {
                direction = Direction.NorthWest
            } else if (dx > 0 && dy < 0) {
                direction = Direction.NorthEast
            } else if (dx < 0 && dy > 0) {
                direction = Direction.SouthWest
            } else if (dx > 0 && dy > 0) {
                direction = Direction.SouthEast
            }
        }

        return areas[direction.ordinal]
    }

    /** `AreaCombat::createArea` (`combat.cpp:2438-2468`). */
    private fun createArea(list: List<Int>, rows: Int): MatrixArea {
        val cols = if (rows == 0) 0 else list.size / rows

        val area = MatrixArea(rows, cols)

        var x = 0
        var y = 0

        for (value in list) {
            if (value == 1 || value == 3) {
                area.setValue(y, x, true)
            }

            if (value == 2 || value == 3) {
                area.setCenter(y, x)
            }

            ++x

            if (cols == x) {
                x = 0
                ++y
            }
        }

        return area
    }

    /** `AreaCombat::copyArea` (`combat.cpp:2327-2406`). */
    private fun copyArea(input: MatrixArea, output: MatrixArea, operation: MatrixOperation) {
        if (operation == MatrixOperation.MATRIXOPERATION_COPY) {
            for (y in 0 until input.rows) {
                for (x in 0 until input.cols) {
                    output.setValue(y, x, input.getValue(y, x))
                }
            }

            output.setCenter(input.centerY, input.centerX)
        } else if (operation == MatrixOperation.MATRIXOPERATION_MIRROR) {
            for (y in 0 until input.rows) {
                var rx = 0
                for (x in input.cols - 1 downTo 0) {
                    output.setValue(y, rx++, input.getValue(y, x))
                }
            }

            output.setCenter(input.centerY, (input.rows - 1) - input.centerX)
        } else if (operation == MatrixOperation.MATRIXOPERATION_FLIP) {
            for (x in 0 until input.cols) {
                var ry = 0
                for (y in input.rows - 1 downTo 0) {
                    output.setValue(ry++, x, input.getValue(y, x))
                }
            }

            output.setCenter((input.cols - 1) - input.centerY, input.centerX)
        } else {
            val rotateCenterX = (output.cols / 2) - 1
            val rotateCenterY = (output.rows / 2) - 1

            val angle = when (operation) {
                MatrixOperation.MATRIXOPERATION_ROTATE90 -> 90
                MatrixOperation.MATRIXOPERATION_ROTATE180 -> 180
                MatrixOperation.MATRIXOPERATION_ROTATE270 -> 270
                else -> 0
            }

            val angleRad = Math.PI * angle / 180.0

            val a = cos(angleRad)
            val b = -sin(angleRad)
            val c = sin(angleRad)
            val d = cos(angleRad)

            for (x in 0 until input.cols) {
                for (y in 0 until input.rows) {
                    val newX = x - input.centerX
                    val newY = y - input.centerY

                    val rotatedX = Math.round(newX * a + newY * b).toInt()
                    val rotatedY = Math.round(newX * c + newY * d).toInt()

                    output.setValue(rotatedY + rotateCenterY, rotatedX + rotateCenterX, input.getValue(y, x))
                }
            }

            output.setCenter(rotateCenterY, rotateCenterX)
        }
    }
}
