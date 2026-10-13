package org.opentibia.canary.map

/**
 * Faithful port of Canary `enum class NavCellFlag : uint16_t`
 * (src/map/navigation_snapshot.hpp:23-37).
 */
enum class NavCellFlag(val bit: Int) {
    None(0),
    HasGround(1 shl 0),
    ProtectionZone(1 shl 1),
    FloorChange(1 shl 2),
    Teleport(1 shl 3),
    ImmovableBlockSolid(1 shl 4),
    ImmovableNoFieldBlockPath(1 shl 5),
    BlockSolid(1 shl 6),
    NoFieldBlockPath(1 shl 7),
    HarmfulField(1 shl 8),
    WalkableSea(1 shl 9),
    BlockProjectile(1 shl 10),
    FloorChangeWest(1 shl 11),
}

/**
 * Kotlin value-class port of the `uint16_t` flag bitmask held by Canary
 * `struct NavCell` (src/map/navigation_snapshot.hpp:43-56). The behaviour matches
 * `cell.flags = cell.flags | NavCellFlag::X` / `cell.hasFlag(NavCellFlag::X)`.
 */
@JvmInline
value class NavCellFlags private constructor(private val bits: Int) {
    operator fun plus(flag: NavCellFlag): NavCellFlags = NavCellFlags(bits or flag.bit)

    operator fun contains(flag: NavCellFlag): Boolean = (bits and flag.bit) != 0

    companion object {
        /** `NavCellFlag::None` (navigation_snapshot.hpp:24). */
        val NONE = NavCellFlags(0)

        /**
         * Mirrors the C++ `operator|(NavCellFlag, NavCellFlag)` combined with the
         * `NavCell::flags` storage (navigation_snapshot.hpp:39-56).
         */
        fun of(vararg flags: NavCellFlag): NavCellFlags =
            NavCellFlags(flags.fold(0) { bits, flag -> bits or flag.bit })
    }
}

/**
 * Faithful port of Canary `struct NavCell` (src/map/navigation_snapshot.hpp:43-56).
 *
 * The C++ counter fields are `uint8_t` (`blockingCreatures`, `pushableMonsters`) and
 * `uint32_t` (`houseId`); Kotlin stores them as [Int] because the pathfinder only
 * compares and indexes them.
 */
class NavCell {
    var flags: NavCellFlags = NavCellFlags.NONE
    var houseId: Int = 0
    var groundId: Int = 0
    var harmfulFieldCombatType: Int = 0
    var blockingCreatures: Int = 0
    var pushableMonsters: Int = 0
    var hasNonInvisibleCreature: Boolean = false
    var hasUnpushableCreature: Boolean = false

    /** `NavCell::hasFlag` (navigation_snapshot.hpp:53-55). */
    fun hasFlag(flag: NavCellFlag): Boolean = flag in flags
}
