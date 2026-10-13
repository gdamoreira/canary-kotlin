package org.opentibia.canary.game.zones

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.map.Position

/**
 * Transcription of the Canary zone definition, membership and flag behavior
 * (`src/game/zones/zone.cpp`, `src/game/zones/zone.hpp`, `src/items/tile.hpp`).
 */
class ZoneTest {

    @Test
    fun `area contains positions inclusively on every axis`() {
        val area = Area(Position(100, 100, 7), Position(105, 105, 8))

        assertThat(area.contains(Position(100, 100, 7))).isTrue()
        assertThat(area.contains(Position(105, 105, 8))).isTrue()
        assertThat(area.contains(Position(99, 100, 7))).isFalse()
        assertThat(area.contains(Position(106, 105, 8))).isFalse()
        assertThat(area.contains(Position(100, 99, 7))).isFalse()
        assertThat(area.contains(Position(100, 100, 6))).isFalse()
        assertThat(area.contains(Position(100, 100, 9))).isFalse()
    }

    @Test
    fun `area intersects matches Canary inclusive overlap rules`() {
        val area = Area(Position(100, 100, 7), Position(105, 105, 7))

        assertThat(area.intersects(Area(Position(105, 105, 7), Position(110, 110, 7)))).isTrue()
        assertThat(area.intersects(Area(Position(106, 100, 7), Position(110, 105, 7)))).isFalse()
        assertThat(area.intersects(Area(Position(100, 106, 7), Position(105, 110, 7)))).isFalse()
        assertThat(area.intersects(Area(Position(100, 100, 8), Position(105, 105, 8)))).isFalse()
        assertThat(Area.intersects(area, Area(Position(99, 99, 7), Position(100, 100, 7)))).isTrue()
    }

    @Test
    fun `area iteration walks x fastest then y then z`() {
        val area = Area(Position(1, 1, 7), Position(2, 2, 8))

        assertThat(area.toList()).containsExactly(
            Position(1, 1, 7),
            Position(2, 1, 7),
            Position(1, 2, 7),
            Position(2, 2, 7),
            Position(1, 1, 8),
            Position(2, 1, 8),
            Position(1, 2, 8),
            Position(2, 2, 8),
        )
    }

    @Test
    fun `area toString mirrors the Canary format`() {
        val area = Area(Position(1, 2, 3), Position(4, 5, 6))

        assertThat(area.toString()).isEqualTo("Area(from: ( 1, 2, 3 ), to: ( 4, 5, 6 ))")
    }

    @Test
    fun `zone addArea stores every position and indexes them`() {
        val zones = Zones()
        val zone = zones.addZone("quest")!!

        zone.addArea(Area(Position(10, 10, 7), Position(11, 11, 7)))

        assertThat(zone.getPositions()).containsExactlyInAnyOrder(
            Position(10, 10, 7),
            Position(11, 10, 7),
            Position(10, 11, 7),
            Position(11, 11, 7),
        )
        assertThat(zones.getZones(Position(10, 11, 7))).containsExactly(zone)
        assertThat(zones.getZones(Position(12, 12, 7))).isEmpty()
    }

    @Test
    fun `zone addPosition keeps a single entry and index for repeated positions`() {
        val zones = Zones()
        val zone = zones.addZone("quest")!!
        val position = Position(10, 10, 7)

        zone.addPosition(position)
        zone.addPosition(position)

        assertThat(zone.getPositions()).containsExactly(position)
        assertThat(zones.getZones(position)).containsExactly(zone)
    }

    @Test
    fun `zone subtractArea removes only the contained positions`() {
        val zones = Zones()
        val zone = zones.addZone("quest")!!
        zone.addArea(Area(Position(10, 10, 7), Position(11, 11, 7)))

        zone.subtractArea(Area(Position(10, 10, 7), Position(10, 10, 7)))

        assertThat(zone.getPositions()).containsExactlyInAnyOrder(
            Position(11, 10, 7),
            Position(10, 11, 7),
            Position(11, 11, 7),
        )
        assertThat(zones.getZones(Position(10, 10, 7))).isEmpty()
    }

    @Test
    fun `zone contains reflects the stored positions`() {
        val zones = Zones()
        val zone = zones.addZone("quest")!!
        zone.addPosition(Position(10, 10, 7))

        assertThat(zone.contains(Position(10, 10, 7))).isTrue()
        assertThat(zone.contains(Position(10, 10, 8))).isFalse()
    }

    @Test
    fun `zone isStatic only when it carries a non zero id`() {
        val zones = Zones()

        assertThat(zones.addZone("dynamic")!!.isStatic()).isFalse()
        assertThat(zones.addZone("static", 4)!!.isStatic()).isTrue()
    }

    @Test
    fun `zone addArea refreshes every indexed position`() {
        val refreshed = mutableListOf<Position>()
        val zones = Zones { refreshed.add(it) }
        val zone = zones.addZone("quest")!!

        zone.addArea(Area(Position(10, 10, 7), Position(11, 10, 7)))

        assertThat(refreshed).containsExactly(Position(10, 10, 7), Position(11, 10, 7))
    }

    @Test
    fun `zone subtractArea refreshes the remaining positions`() {
        val refreshed = mutableListOf<Position>()
        val zones = Zones { refreshed.add(it) }
        val zone = zones.addZone("quest")!!
        zone.addPosition(Position(10, 10, 7))
        zone.addPosition(Position(11, 10, 7))
        refreshed.clear()

        zone.subtractArea(Area(Position(10, 10, 7), Position(10, 10, 7)))

        assertThat(refreshed).containsExactly(Position(11, 10, 7))
    }

    @Test
    fun `tile flag constants mirror the Canary bit layout`() {
        assertThat(TileFlags.TILESTATE_NONE.value).isEqualTo(0)
        assertThat(TileFlags.TILESTATE_PROTECTIONZONE.value).isEqualTo(1 shl 7)
        assertThat(TileFlags.TILESTATE_NOPVPZONE.value).isEqualTo(1 shl 8)
        assertThat(TileFlags.TILESTATE_NOLOGOUT.value).isEqualTo(1 shl 9)
        assertThat(TileFlags.TILESTATE_PVPZONE.value).isEqualTo(1 shl 10)
        assertThat(TileFlags.TILESTATE_FLOORCHANGE.value).isEqualTo(0x7F)
        assertThat(TileFlags.TILESTATE_HASHEIGHT.value).isEqualTo(1 shl 28)
    }

    @Test
    fun `tile flags resolve to the Canary zone type`() {
        assertThat(getZoneType(TileFlags.TILESTATE_NONE.value)).isEqualTo(ZoneType.ZONE_NORMAL)
        assertThat(getZoneType(TileFlags.TILESTATE_PROTECTIONZONE.value)).isEqualTo(ZoneType.ZONE_PROTECTION)
        assertThat(getZoneType(TileFlags.TILESTATE_NOPVPZONE.value)).isEqualTo(ZoneType.ZONE_NOPVP)
        assertThat(getZoneType(TileFlags.TILESTATE_PVPZONE.value)).isEqualTo(ZoneType.ZONE_PVP)
        assertThat(getZoneType(TileFlags.TILESTATE_NOLOGOUT.value)).isEqualTo(ZoneType.ZONE_NOLOGOUT)
    }

    @Test
    fun `tile zone type keeps Canary precedence when several flags are set`() {
        val all = TileFlags.TILESTATE_PROTECTIONZONE.value or
            TileFlags.TILESTATE_NOPVPZONE.value or
            TileFlags.TILESTATE_PVPZONE.value or
            TileFlags.TILESTATE_NOLOGOUT.value

        assertThat(getZoneType(all)).isEqualTo(ZoneType.ZONE_PROTECTION)
        assertThat(getZoneType(all and TileFlags.TILESTATE_PROTECTIONZONE.value.inv())).isEqualTo(ZoneType.ZONE_NOPVP)
        assertThat(
            getZoneType(
                all and
                    TileFlags.TILESTATE_PROTECTIONZONE.value.inv() and
                    TileFlags.TILESTATE_NOPVPZONE.value.inv(),
            ),
        ).isEqualTo(ZoneType.ZONE_PVP)
        assertThat(getZoneType(TileFlags.TILESTATE_NOLOGOUT.value)).isEqualTo(ZoneType.ZONE_NOLOGOUT)
    }

    @Test
    fun `zone type mirrors the Canary enum values`() {
        assertThat(ZoneType.ZONE_PROTECTION.value).isEqualTo(0)
        assertThat(ZoneType.ZONE_NOPVP.value).isEqualTo(1)
        assertThat(ZoneType.ZONE_PVP.value).isEqualTo(2)
        assertThat(ZoneType.ZONE_NOLOGOUT.value).isEqualTo(3)
        assertThat(ZoneType.ZONE_NORMAL.value).isEqualTo(4)
    }
}
