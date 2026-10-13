package org.opentibia.canary.game.zones

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.map.Position

/**
 * Transcription of the Canary `Zone` registry behavior
 * (`src/game/zones/zone.cpp`, `src/game/zones/zone.hpp`).
 */
class ZonesTest {

    @Test
    fun `addZone refuses the reserved default name`() {
        val zones = Zones()

        assertThat(zones.addZone("default")).isNull()
        assertThat(zones.getZone("default")).isNull()
    }

    @Test
    fun `addZone refuses duplicate names and keeps the original zone`() {
        val zones = Zones()
        val original = zones.addZone("alpha")!!

        assertThat(zones.addZone("alpha")).isNull()
        assertThat(zones.getZone("alpha")).isSameAs(original)
    }

    @Test
    fun `addZone registers zones by name and by id`() {
        val zones = Zones()
        val zone = zones.addZone("boss-room", 9)!!

        assertThat(zones.getZone("boss-room")).isSameAs(zone)
        assertThat(zones.getZone(9)).isSameAs(zone)
        assertThat(zone.name).isEqualTo("boss-room")
        assertThat(zone.id).isEqualTo(9)
    }

    @Test
    fun `addZone links an existing id to the new name and indexes its positions`() {
        val zones = Zones()
        val zone = zones.getZone(9)!!
        val position = Position(50, 50, 7)
        zone.addPosition(position) // empty name: stored, but not indexed

        assertThat(zones.getZones(position)).isEmpty()

        val linked = zones.addZone("boss-room", 9)

        assertThat(linked).isSameAs(zone)
        assertThat(zone.name).isEqualTo("boss-room")
        assertThat(zones.getZone("boss-room")).isSameAs(zone)
        assertThat(zones.getZones(position)).containsExactly(zone)
    }

    @Test
    fun `linking a duplicate id renames the zone and keeps the old alias`() {
        val zones = Zones()
        val zone = zones.addZone("first", 5)!!

        assertThat(zones.addZone("second", 5)).isSameAs(zone)
        assertThat(zone.name).isEqualTo("second")
        assertThat(zones.getZone("first")).isSameAs(zone)
        assertThat(zones.getZone("second")).isSameAs(zone)
    }

    @Test
    fun `getZone by id returns null for zero and caches the zone created for unknown ids`() {
        val zones = Zones()

        assertThat(zones.getZone(0)).isNull()

        val created = zones.getZone(42)!!
        assertThat(created.id).isEqualTo(42)
        assertThat(created.name).isEmpty()
        assertThat(zones.getZone(42)).isSameAs(created)
    }

    @Test
    fun `getZones by position returns every zone indexed there`() {
        val zones = Zones()
        val first = zones.addZone("first")!!
        val second = zones.addZone("second")!!
        val position = Position(20, 20, 7)
        first.addPosition(position)
        second.addPosition(position)

        assertThat(zones.getZones(position)).containsExactlyInAnyOrder(first, second)
        assertThat(zones.getZones(Position(21, 21, 7))).isEmpty()
    }

    @Test
    fun `getZones returns only name registered zones`() {
        val zones = Zones()
        val named = zones.addZone("named")!!
        zones.getZone(42) // id-only zones live in the id map alone

        assertThat(zones.getZones()).containsExactly(named)
    }

    @Test
    fun `removePosition unindexes the zone at that position`() {
        val zones = Zones()
        val zone = zones.addZone("quest")!!
        val position = Position(20, 20, 7)
        zone.addPosition(position)

        zone.removePosition(position)

        assertThat(zone.contains(position)).isFalse()
        assertThat(zone.getPositions()).isEmpty()
        assertThat(zones.getZones(position)).isEmpty()
    }

    @Test
    fun `refreshAll refreshes every name registered zone`() {
        val refreshed = mutableListOf<Position>()
        val zones = Zones { refreshed.add(it) }
        val first = zones.addZone("first")!!
        val second = zones.addZone("second")!!
        val firstPosition = Position(1, 1, 7)
        val secondPosition = Position(2, 2, 7)
        first.addPosition(firstPosition)
        second.addPosition(secondPosition)

        zones.refreshAll()

        assertThat(refreshed).containsExactlyInAnyOrder(firstPosition, secondPosition)
    }

    @Test
    fun `zone refresh notifies the map seam for every position`() {
        val refreshed = mutableListOf<Position>()
        val zones = Zones { refreshed.add(it) }
        val zone = zones.addZone("quest")!!
        val position = Position(30, 30, 7)
        zone.addPosition(position)

        zone.refresh()

        assertThat(refreshed).containsExactly(position)
    }

    @Test
    fun `clearZones drops dynamic zones and keeps static zones indexed`() {
        val refreshed = mutableListOf<Position>()
        val zones = Zones { refreshed.add(it) }
        val dynamicPosition = Position(10, 10, 7)
        val staticPosition = Position(20, 20, 7)
        val dynamic = zones.addZone("dynamic")!!
        val static = zones.addZone("static", 3)!!
        dynamic.addPosition(dynamicPosition)
        static.addPosition(staticPosition)

        zones.clearZones()

        assertThat(zones.getZone("dynamic")).isNull()
        assertThat(zones.getZone("static")).isSameAs(static)
        assertThat(zones.getZones(dynamicPosition)).isEmpty()
        assertThat(zones.getZones(staticPosition)).containsExactly(static)
        assertThat(refreshed).containsExactly(dynamicPosition)
    }
}
