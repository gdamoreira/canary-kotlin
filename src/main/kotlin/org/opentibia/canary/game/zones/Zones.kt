package org.opentibia.canary.game.zones

import io.github.oshai.kotlinlogging.KotlinLogging
import org.opentibia.canary.map.Position

private val logger = KotlinLogging.logger {}

/**
 * Instance-owned port of the Canary zone registry: the static
 * `Zone::zones` / `Zone::zonesByID` / `Zone::zonesByPosition` maps and the
 * static `Zone::addZone` / `getZone` / `getZones` / `refreshAll` / `clearZones`
 * operations (`src/game/zones/zone.hpp:163-173`, `src/game/zones/zone.cpp:28-51,
 * 144-158, 215-252`).
 *
 * AGENTS.md 1.1: the C++ statics act as a global singleton; the Kotlin engine
 * is constructor-owned and lives on the single-threaded world dispatcher
 * (AGENTS.md 1.2). [mapRefreshZones] is the `g_game().map.refreshZones(position)`
 * seam `Zone::refresh` calls (`src/map/map.cpp:369-380`).
 */
class Zones(
    private val mapRefreshZones: (Position) -> Unit = {},
) {
    /** `Zone::zones` (`zone.hpp:197`), keyed by name. */
    private val zones = linkedMapOf<String, Zone>()

    /** `Zone::zonesByID` (`zone.hpp:198`). */
    private val zonesById = linkedMapOf<Int, Zone>()

    /** `Zone::zonesByPosition` (`zone.hpp:199`). */
    private val zonesByPosition = linkedMapOf<Position, MutableList<Zone>>()

    /**
     * `Zone::addZone` (`zone.cpp:28-51`). Returns `null` for the reserved
     * `default` name and for an already registered name (the C++ `nullZone`).
     * When [zoneId] is already known the new name is linked to the existing
     * zone and its positions are indexed.
     */
    fun addZone(name: String, zoneId: Int = 0): Zone? {
        if (name == "default") {
            logger.error { "Zone name $name is reserved" }
            return null
        }
        if (zoneId != 0 && zonesById.containsKey(zoneId)) {
            logger.trace { "[Zone::addZone] Found with ID $zoneId while adding $name, linking them together..." }
            val zone = zonesById.getValue(zoneId)
            zone.name = name
            zones[name] = zone
            indexPositions(zone)
            return zone
        }

        if (zones.containsKey(name)) {
            logger.error { "Zone $name already exists" }
            return null
        }

        val zone = Zone(name, zoneId, this)
        zones[name] = zone
        if (zoneId != 0) {
            zonesById[zoneId] = zone
        }
        return zone
    }

    /** `Zone::getZone(const std::string &name)` (`zone.cpp:144-146`). */
    fun getZone(name: String): Zone? = zones[name]

    /**
     * `Zone::getZone(uint32_t zoneID)` (`zone.cpp:148-158`): id `0` is the
     * dynamic-zone marker and returns `null`; an unknown id materializes and
     * caches an unnamed zone.
     */
    fun getZone(zoneId: Int): Zone? {
        if (zoneId == 0) {
            return null
        }
        zonesById[zoneId]?.let { return it }

        val zone = Zone("", zoneId, this)
        zonesById[zoneId] = zone
        return zone
    }

    /** `Zone::getZones(Position)` (`zone.cpp:230-242`). */
    fun getZones(position: Position): List<Zone> = zonesByPosition[position]?.toList() ?: emptyList()

    /** `Zone::getZones()` (`zone.cpp:244-252`), the registered (named) zones. */
    fun getZones(): List<Zone> = zones.values.toList()

    /** `Zone::refreshAll()` (`zone.hpp:168-172`). */
    fun refreshAll() {
        for (zone in zones.values) {
            zone.refresh()
        }
    }

    /**
     * `Zone::clearZones()` (`zone.cpp:215-228`): dynamic zones are unindexed
     * and refreshed, then the name registry is rebuilt from the static
     * (id-carrying) zones.
     */
    fun clearZones() {
        for (zone in zones.values) {
            // do not clear zones loaded from the map (id > 0)
            if (zone.isStatic()) {
                continue
            }
            unindexPositions(zone)
            zone.refresh()
        }
        zones.clear()
        for (zone in zonesById.values) {
            zones[zone.name] = zone
        }
    }

    /** `Zone::indexPosition` (`zone.cpp:87-96`), de-duplicated by zone identity. */
    internal fun indexPosition(zone: Zone, position: Position) {
        val zonesAtPosition = zonesByPosition.getOrPut(position) { mutableListOf() }
        if (zonesAtPosition.any { it === zone }) {
            return
        }
        zonesAtPosition.add(zone)
    }

    /** `Zone::unindexPosition` (`zone.cpp:108-123`). */
    internal fun unindexPosition(zone: Zone, position: Position) {
        val zonesAtPosition = zonesByPosition[position] ?: return
        zonesAtPosition.removeAll { it === zone }
        if (zonesAtPosition.isEmpty()) {
            zonesByPosition.remove(position)
        }
    }

    /** `Zone::indexPositions` (`zone.cpp:98-106`); unnamed zones are skipped. */
    internal fun indexPositions(zone: Zone) {
        if (zone.name.isEmpty()) {
            return
        }

        for (position in zone.positions) {
            indexPosition(zone, position)
        }
    }

    /** `Zone::unindexPositions` (`zone.cpp:125-129`). */
    internal fun unindexPositions(zone: Zone) {
        for (position in zone.positions) {
            unindexPosition(zone, position)
        }
    }

    /**
     * Invocation seam for `Map::refreshZones` (`src/map/map.cpp:369-380`), used
     * by [Zone.refresh]. The map/tile side re-attaches this position's zones to
     * its tile; that wiring lives outside the zone engine.
     */
    internal fun refreshZones(position: Position) {
        mapRefreshZones(position)
    }
}
