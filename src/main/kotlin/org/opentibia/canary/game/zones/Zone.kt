package org.opentibia.canary.game.zones

import io.github.oshai.kotlinlogging.KotlinLogging
import org.opentibia.canary.map.Position

private val logger = KotlinLogging.logger {}

/**
 * `enum ZoneType_t` (`src/items/items_definitions.hpp:479-485`).
 *
 * The numeric values are part of the C++ contract (the enum starts at zero),
 * so they are transcribed explicitly.
 */
enum class ZoneType(val value: Int) {
    ZONE_PROTECTION(0),
    ZONE_NOPVP(1),
    ZONE_PVP(2),
    ZONE_NOLOGOUT(3),
    ZONE_NORMAL(4),
}

/**
 * `enum TileFlags_t` (`src/items/items_definitions.hpp:443-477`), the tile bit
 * layout the zone flag checks read. `TILESTATE_FLOORCHANGE` is the aggregate of
 * the seven floor-change bits, exactly as in C++.
 */
enum class TileFlags(val value: Int) {
    TILESTATE_NONE(0),
    TILESTATE_FLOORCHANGE_DOWN(1 shl 0),
    TILESTATE_FLOORCHANGE_NORTH(1 shl 1),
    TILESTATE_FLOORCHANGE_SOUTH(1 shl 2),
    TILESTATE_FLOORCHANGE_EAST(1 shl 3),
    TILESTATE_FLOORCHANGE_WEST(1 shl 4),
    TILESTATE_FLOORCHANGE_SOUTH_ALT(1 shl 5),
    TILESTATE_FLOORCHANGE_EAST_ALT(1 shl 6),
    TILESTATE_PROTECTIONZONE(1 shl 7),
    TILESTATE_NOPVPZONE(1 shl 8),
    TILESTATE_NOLOGOUT(1 shl 9),
    TILESTATE_PVPZONE(1 shl 10),
    TILESTATE_TELEPORT(1 shl 11),
    TILESTATE_MAGICFIELD(1 shl 12),
    TILESTATE_MAILBOX(1 shl 13),
    TILESTATE_TRASHHOLDER(1 shl 14),
    TILESTATE_BED(1 shl 15),
    TILESTATE_DEPOT(1 shl 16),
    TILESTATE_BLOCKSOLID(1 shl 17),
    TILESTATE_BLOCKPATH(1 shl 18),
    TILESTATE_IMMOVABLEBLOCKSOLID(1 shl 19),
    TILESTATE_IMMOVABLEBLOCKPATH(1 shl 20),
    TILESTATE_IMMOVABLENOFIELDBLOCKPATH(1 shl 21),
    TILESTATE_NOFIELDBLOCKPATH(1 shl 22),
    TILESTATE_SUPPORTS_HANGABLE(1 shl 23),
    TILESTATE_MOVABLE(1 shl 24),
    TILESTATE_ISHORIZONTAL(1 shl 25),
    TILESTATE_ISVERTICAL(1 shl 26),
    TILESTATE_BLOCKPROJECTILE(1 shl 27),
    TILESTATE_HASHEIGHT(1 shl 28),
    TILESTATE_FLOORCHANGE(
        TILESTATE_FLOORCHANGE_DOWN.value or
            TILESTATE_FLOORCHANGE_NORTH.value or
            TILESTATE_FLOORCHANGE_SOUTH.value or
            TILESTATE_FLOORCHANGE_EAST.value or
            TILESTATE_FLOORCHANGE_WEST.value or
            TILESTATE_FLOORCHANGE_SOUTH_ALT.value or
            TILESTATE_FLOORCHANGE_EAST_ALT.value,
    ),
}

/**
 * `Tile::getZoneType()` (`src/items/tile.hpp:188-199`): the zone flag checks
 * combat and movement consume. The precedence order (protection, no-pvp, pvp,
 * no-logout, normal) is part of the C++ behavior.
 */
fun getZoneType(flags: Int): ZoneType {
    if (flags and TileFlags.TILESTATE_PROTECTIONZONE.value != 0) {
        return ZoneType.ZONE_PROTECTION
    }
    if (flags and TileFlags.TILESTATE_NOPVPZONE.value != 0) {
        return ZoneType.ZONE_NOPVP
    }
    if (flags and TileFlags.TILESTATE_PVPZONE.value != 0) {
        return ZoneType.ZONE_PVP
    }
    if (flags and TileFlags.TILESTATE_NOLOGOUT.value != 0) {
        return ZoneType.ZONE_NOLOGOUT
    }
    return ZoneType.ZONE_NORMAL
}

/**
 * `struct Area` (`src/game/zones/zone.hpp:30-91`): an inclusive axis-aligned
 * box over positions. Iteration walks x, then y, then z, exactly like
 * `Area::PositionIterator`.
 */
class Area(val from: Position, val to: Position) : Iterable<Position> {

    /** `Area::intersects(Area other)` (`zone.hpp:39-41`). */
    fun intersects(other: Area): Boolean = intersects(this, other)

    /** `Area::contains(Position)` (`zone.hpp:43-45`), inclusive on every axis. */
    fun contains(position: Position): Boolean =
        position.x >= from.x && position.x <= to.x &&
            position.y >= from.y && position.y <= to.y &&
            position.z >= from.z && position.z <= to.z

    /** `Area::begin()`/`Area::end()` (`zone.hpp:83-90`). */
    override fun iterator(): Iterator<Position> = PositionIterator(from, this)

    /** `Area::toString()` (`zone.hpp:50-52`), using `Position::toString` formatting. */
    override fun toString(): String =
        "Area(from: ( ${from.x}, ${from.y}, ${from.z} ), to: ( ${to.x}, ${to.y}, ${to.z} ))"

    /**
     * `Area::PositionIterator` (`zone.hpp:54-90`). The cursor advances x first,
     * wraps x to `from.x` and advances y, then wraps y and advances z; the end
     * position is `from.x, from.y, to.z + 1`.
     */
    private class PositionIterator(startPosition: Position, private val area: Area) : Iterator<Position> {
        private var currentPosition = startPosition
        private val endPosition = Position(area.from.x, area.from.y, area.to.z + 1)

        override fun hasNext(): Boolean = currentPosition != endPosition

        override fun next(): Position {
            if (!hasNext()) {
                throw NoSuchElementException()
            }

            val result = currentPosition
            var x = currentPosition.x + 1
            var y = currentPosition.y
            var z = currentPosition.z
            if (x > area.to.x) {
                x = area.from.x
                y++
                if (y > area.to.y) {
                    y = area.from.y
                    z++
                }
            }
            currentPosition = Position(x, y, z)
            return result
        }
    }

    companion object {
        /** `Area::intersects(Area a, Area b)` (`zone.hpp:35-37`), inclusive overlap. */
        fun intersects(a: Area, b: Area): Boolean =
            a.from.x <= b.to.x && a.to.x >= b.from.x &&
                a.from.y <= b.to.y && a.to.y >= b.from.y &&
                a.from.z <= b.to.z && a.to.z >= b.from.z
    }
}

/**
 * Port of `class Zone` (`src/game/zones/zone.hpp:112-199`,
 * `src/game/zones/zone.cpp`).
 *
 * The C++ class deletes its copy constructor and assignment operator; as a
 * Kotlin reference type a `Zone` is shared, never structurally copied, and the
 * registry (`Zone::zones`, `Zone::zonesByID`, `Zone::zonesByPosition`) lives in
 * the instance-owned [Zones] manager instead of static maps (AGENTS.md 1.1).
 *
 * World-mutating C++ members are intentionally not ported yet: the weak
 * creature/item caches (`creatureAdded`/`thingAdded`/...) and the
 * `removePlayers`/`removeMonsters`/`removeNpcs`/`setMonsterVariant` side
 * effects need the game map, dispatcher and spawn registry.
 */
class Zone internal constructor(
    name: String,
    val id: Int,
    private val zones: Zones,
) {
    /** `Zone::name` (`zone.hpp:185`); `Zone::getName()` returns this value. */
    var name: String = name
        internal set

    /** `Zone::positions` (`zone.hpp:187`), a set of unique positions. */
    internal val positions = linkedSetOf<Position>()

    /** `Zone::addArea` (`zone.cpp:53-58`); refreshes the zone after indexing. */
    fun addArea(area: Area) {
        for (position in area) {
            addPosition(position)
        }
        refresh()
    }

    /** `Zone::subtractArea` (`zone.cpp:60-65`); refreshes the zone after unindexing. */
    fun subtractArea(area: Area) {
        for (position in area) {
            removePosition(position)
        }
        refresh()
    }

    /**
     * `Zone::addPosition` (`zone.cpp:67-73`): a duplicate or a zone without a
     * name is stored but stays out of the position index.
     */
    fun addPosition(position: Position) {
        if (!positions.add(position) || name.isEmpty()) {
            return
        }

        zones.indexPosition(this, position)
    }

    /** `Zone::removePosition` (`zone.cpp:75-81`). */
    fun removePosition(position: Position) {
        if (!positions.remove(position) || name.isEmpty()) {
            return
        }

        zones.unindexPosition(this, position)
    }

    /** `Zone::contains` (`zone.cpp:83-85`). */
    fun contains(position: Position): Boolean = positions.contains(position)

    /** `Zone::getPositions` (`zone.cpp:160-166`). */
    fun getPositions(): List<Position> = positions.toList()

    /** `Zone::isStatic` (`zone.hpp:159-161`): map-editor zones carry an id. */
    fun isStatic(): Boolean = id != 0

    /**
     * `Zone::refresh` (`zone.cpp:323-338`): the weak caches are cleared (not
     * ported yet) and every position is handed back to `Map::refreshZones`
     * through the [Zones] seam.
     */
    fun refresh() {
        for (position in getPositions()) {
            zones.refreshZones(position)
        }
        logger.trace { "Refreshed zone '$name'" }
    }
}
