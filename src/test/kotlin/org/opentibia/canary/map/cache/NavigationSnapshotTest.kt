package org.opentibia.canary.map.cache

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.map.NavCell
import org.opentibia.canary.map.NavCellFlag
import org.opentibia.canary.map.NavCellFlags
import org.opentibia.canary.map.Position

/**
 * Transcribes the Canary navigation-snapshot unit tests and the
 * `NavSectorSnapshot`/`NavRegionSnapshot` contract
 * (tests/unit/game/navigation_snapshot_test.cpp:14-35,
 * src/map/navigation_snapshot.cpp:16-44).
 */
class NavigationSnapshotTest {

    private fun emptyCells(): Array<NavCell> = Array(SECTOR_SIZE * SECTOR_SIZE) { NavCell() }

    private fun sectorIndex(x: Int, y: Int): Int = (x / SECTOR_SIZE) or ((y / SECTOR_SIZE) shl 16)

    @Test
    fun `resolves cells only inside the captured region`() {
        val centerX = 100
        val centerY = 100
        val z = 7
        val sectorIndex = sectorIndex(centerX, centerY)

        val cells = emptyCells()
        val expectedCell = cells[(centerX and SECTOR_MASK) * SECTOR_SIZE + ((centerY + 1) and SECTOR_MASK)]
        expectedCell.flags = NavCellFlags.of(NavCellFlag.HasGround, NavCellFlag.BlockSolid)
        expectedCell.groundId = 4526

        val sector = NavSectorSnapshot(sectorIndex, z, 11, 19, cells)
        val region = NavRegionSnapshot(3, Position(centerX, centerY, z), 4, listOf(sector))

        val cell = region.getCell(Position(centerX, centerY + 1, z))

        assertThat(cell).isNotNull()
        assertThat(cell!!.groundId).isEqualTo(4526)
        assertThat(cell.hasFlag(NavCellFlag.HasGround)).isTrue()
        assertThat(cell.hasFlag(NavCellFlag.BlockSolid)).isTrue()
        assertThat(region.getCell(Position(centerX + 5, centerY, z))).isNull()
        assertThat(region.getCell(Position(centerX, centerY, z + 1))).isNull()
    }

    @Test
    fun `stores the sector revision and radius metadata`() {
        val cells = emptyCells()
        val sector = NavSectorSnapshot(0x00060006, 7, 11, 19, cells)
        val region = NavRegionSnapshot(3, Position(100, 100, 7), 4, listOf(sector))

        assertThat(sector.getSectorIndex()).isEqualTo(0x00060006)
        assertThat(sector.getZ()).isEqualTo(7)
        assertThat(sector.getTopologyRevision()).isEqualTo(11)
        assertThat(sector.getOccupancyRevision()).isEqualTo(19)
        assertThat(sector.getCells()).isSameAs(cells)
        assertThat(region.getEpoch()).isEqualTo(3)
        assertThat(region.getCenter()).isEqualTo(Position(100, 100, 7))
        assertThat(region.getRadius()).isEqualTo(4)
        assertThat(region.getSectors()).containsExactly(sector)
    }

    @Test
    fun `indexes the cell array by local x then local y`() {
        val cells = emptyCells()
        cells[4 * SECTOR_SIZE + 9].groundId = 1111
        cells[9 * SECTOR_SIZE + 4].groundId = 2222
        val sector = NavSectorSnapshot(sectorIndex(100, 100), 7, 1, 1, cells)

        assertThat(sector.getCell(100, 105, 7)!!.groundId).isEqualTo(1111)
        assertThat(sector.getCell(105, 100, 7)!!.groundId).isEqualTo(2222)
    }

    @Test
    fun `rejects coordinates from another floor or sector`() {
        val sector = NavSectorSnapshot(sectorIndex(100, 100), 7, 1, 1, emptyCells())

        assertThat(sector.getCell(100, 100, 8)).isNull()
        assertThat(sector.getCell(100, 100, 6)).isNull()
        assertThat(sector.getCell(116, 100, 7)).isNull()
        assertThat(sector.getCell(100, 116, 7)).isNull()
    }

    @Test
    fun `sorts the captured sectors by sector index`() {
        val lowCells = emptyCells()
        lowCells[0].groundId = 100
        val highCells = emptyCells()
        highCells[0].groundId = 200
        val low = NavSectorSnapshot(sectorIndex(16, 16), 7, 1, 1, lowCells)
        val high = NavSectorSnapshot(sectorIndex(80, 80), 7, 1, 1, highCells)

        val region = NavRegionSnapshot(1, Position(80, 80, 7), 80, listOf(high, low))

        assertThat(region.getSectors().map { it.getSectorIndex() })
            .containsExactly(low.getSectorIndex(), high.getSectorIndex())
        assertThat(region.getCell(Position(16, 16, 7))!!.groundId).isEqualTo(100)
        assertThat(region.getCell(Position(80, 80, 7))!!.groundId).isEqualTo(200)
    }

    @Test
    fun `accepts positions at the exact radius and rejects one tile beyond`() {
        val cells = emptyCells()
        val center = Position(100, 100, 7)
        val sector = NavSectorSnapshot(sectorIndex(center.x, center.y), 7, 1, 1, cells)
        val region = NavRegionSnapshot(1, center, 4, listOf(sector))

        assertThat(region.getCell(Position(104, 100, 7))).isNotNull()
        assertThat(region.getCell(Position(100, 104, 7))).isNotNull()
        assertThat(region.getCell(Position(105, 100, 7))).isNull()
        assertThat(region.getCell(Position(100, 95, 7))).isNull()
    }

    @Test
    fun `returns null when the region does not capture the position sector`() {
        val center = Position(100, 100, 7)
        val captured = NavSectorSnapshot(sectorIndex(96, 96), 7, 1, 1, emptyCells())
        val region = NavRegionSnapshot(1, center, 20, listOf(captured))

        assertThat(region.getCell(Position(120, 100, 7))).isNull()
        assertThat(region.getCell(Position(100, 100, 7))).isNotNull()
    }

    @Test
    fun `returns the captured cell instance for a resolvable position`() {
        val cells = emptyCells()
        val center = Position(100, 100, 7)
        val sector = NavSectorSnapshot(sectorIndex(center.x, center.y), 7, 1, 1, cells)
        val region = NavRegionSnapshot(1, center, 4, listOf(sector))

        assertThat(region.getCell(Position(103, 102, 7)))
            .isSameAs(cells[(103 and SECTOR_MASK) * SECTOR_SIZE + (102 and SECTOR_MASK)])
    }
}
