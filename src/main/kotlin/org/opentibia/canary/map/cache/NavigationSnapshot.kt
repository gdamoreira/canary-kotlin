package org.opentibia.canary.map.cache

import org.opentibia.canary.map.NavCell
import org.opentibia.canary.map.Position
import kotlin.math.abs

/** `SECTOR_SIZE` (`src/map/map_const.hpp:23`). Must be a power of 2. */
internal const val SECTOR_SIZE: Int = 16

/** `SECTOR_MASK` (`src/map/map_const.hpp:24`). */
internal const val SECTOR_MASK: Int = SECTOR_SIZE - 1

/**
 * Port of `class NavSectorSnapshot` (src/map/navigation_snapshot.hpp:58-87,
 * src/map/navigation_snapshot.cpp:16-27): an immutable floor-sector capture
 * `SECTOR_SIZE * SECTOR_SIZE` cells indexed column-major by
 * `(x & SECTOR_MASK) * SECTOR_SIZE + (y & SECTOR_MASK)`.
 */
class NavSectorSnapshot(
    private val sectorIndex: Int,
    private val z: Int,
    private val topologyRevision: Long,
    private val occupancyRevision: Long,
    private val cells: Array<NavCell>,
) {

    /** `NavSectorSnapshot::getCell` (navigation_snapshot.cpp:19-27). */
    fun getCell(x: Int, y: Int, requestedZ: Int): NavCell? {
        val requestedSector = (x / SECTOR_SIZE) or ((y / SECTOR_SIZE) shl 16)
        if (requestedZ != z || requestedSector != sectorIndex) {
            return null
        }

        val index = (x and SECTOR_MASK) * SECTOR_SIZE + (y and SECTOR_MASK)
        return cells[index]
    }

    /** `NavSectorSnapshot::getSectorIndex` (navigation_snapshot.hpp:65-67). */
    fun getSectorIndex(): Int = sectorIndex

    /** `NavSectorSnapshot::getZ` (navigation_snapshot.hpp:68-70). */
    fun getZ(): Int = z

    /** `NavSectorSnapshot::getTopologyRevision` (navigation_snapshot.hpp:71-73). */
    fun getTopologyRevision(): Long = topologyRevision

    /** `NavSectorSnapshot::getOccupancyRevision` (navigation_snapshot.hpp:74-76). */
    fun getOccupancyRevision(): Long = occupancyRevision

    /** `NavSectorSnapshot::getCells` (navigation_snapshot.hpp:77-79). */
    fun getCells(): Array<NavCell> = cells
}

/**
 * Port of `class NavRegionSnapshot` (src/map/navigation_snapshot.hpp:89-113,
 * src/map/navigation_snapshot.cpp:29-44).
 *
 * The C++ constructor sorts the sector list by sector index
 * (`std::ranges::sort`, navigation_snapshot.cpp:31); the Kotlin port sorts
 * eagerly in the constructor so `getCell` binary-searches the same layout.
 */
class NavRegionSnapshot(
    private val epoch: Long,
    private val center: Position,
    private val radius: Int,
    sectors: List<NavSectorSnapshot>,
) {

    private val sectors: List<NavSectorSnapshot> = sectors.sortedBy { it.getSectorIndex() }

    /** `NavRegionSnapshot::getCell` (navigation_snapshot.cpp:34-44). */
    fun getCell(position: Position): NavCell? {
        if (position.z != center.z ||
            abs(position.x - center.x) > radius ||
            abs(position.y - center.y) > radius
        ) {
            return null
        }

        val sectorIndex = (position.x / SECTOR_SIZE) or ((position.y / SECTOR_SIZE) shl 16)
        val index = sectors.binarySearchBy(sectorIndex) { it.getSectorIndex() }
        if (index < 0) {
            return null
        }

        return sectors[index].getCell(position.x, position.y, position.z)
    }

    /** `NavRegionSnapshot::getEpoch` (navigation_snapshot.hpp:96-98). */
    fun getEpoch(): Long = epoch

    /** `NavRegionSnapshot::getCenter` (navigation_snapshot.hpp:99-101). */
    fun getCenter(): Position = center

    /** `NavRegionSnapshot::getRadius` (navigation_snapshot.hpp:102-104). */
    fun getRadius(): Int = radius

    /** `NavRegionSnapshot::getSectors` (navigation_snapshot.hpp:105-107). */
    fun getSectors(): List<NavSectorSnapshot> = sectors
}
