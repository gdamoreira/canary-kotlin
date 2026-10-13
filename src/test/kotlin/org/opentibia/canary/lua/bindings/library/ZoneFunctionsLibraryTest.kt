package org.opentibia.canary.lua.bindings.library

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.game.zones.Zone
import org.opentibia.canary.game.zones.Zones
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.Position

/**
 * Transcript tests for the `Zone` userdata class, mirroring `ZoneFunctions`
 * (src/lua/functions/core/game/zone_functions.cpp) over the ported `Zones` registry.
 */
class ZoneFunctionsLibraryTest {

    private fun environment(operations: ZoneOperations = WorldZoneOperations(Zones())): LuaEnvironment =
        LuaEnvironment().apply { initState() }.also { ZoneFunctions.install(it, operations) }

    @Test
    fun `Zone creates or reuses a named zone`() {
        val environment = environment()

        environment.loadChunk(
            """
            zone = Zone('wave40')
            name = zone:getName()
            again = Zone('wave40')
            same = zone == again
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("zone").isuserdata()).isTrue()
        assertThat(environment.getGlobal("name").tojstring()).isEqualTo("wave40")
        assertThat(environment.getGlobal("same").toboolean()).isTrue()
    }

    @Test
    fun `addArea and subtractArea index every position of the box`() {
        val environment = environment()

        environment.loadChunk(
            """
            zone = Zone('box')
            zone:addArea({x = 10, y = 10, z = 7}, {x = 11, y = 11, z = 7})
            added = #zone:getPositions()
            zone:subtractArea({x = 10, y = 10, z = 7}, {x = 11, y = 11, z = 7})
            removed = #zone:getPositions()
            refreshed = zone:refresh()
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("added").toint()).isEqualTo(4)
        assertThat(environment.getGlobal("removed").toint()).isEqualTo(0)
        assertThat(environment.getGlobal("refreshed").isnil()).isTrue()
    }

    @Test
    fun `remove destination defaults to the origin and is script writable`() {
        val environment = environment()

        environment.loadChunk(
            """
            zone = Zone('dest')
            default = zone:getRemoveDestination()
            zone:setRemoveDestination({x = 5, y = 6, z = 7})
            updated = zone:getRemoveDestination()
            """.trimIndent(),
        )

        val default = environment.getGlobal("default").touserdata() as Position
        assertThat(default).isEqualTo(Position(0, 0, 0))
        val updated = environment.getGlobal("updated").touserdata() as Position
        assertThat(updated).isEqualTo(Position(5, 6, 7))
    }

    @Test
    fun `the static lookups consult the shared registry`() {
        val zones = Zones()
        val environment = environment(WorldZoneOperations(zones))
        zones.addZone("one")
        zones.addZone("two")

        environment.loadChunk(
            """
            byName = Zone.getByName('two')
            name = byName:getName()
            all = Zone.getAll()
            count = #all
            missing = Zone.getByName('missing')
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("name").tojstring()).isEqualTo("two")
        assertThat(environment.getGlobal("count").toint()).isEqualTo(2)
        assertThat(environment.getGlobal("missing").isnil()).isTrue()
    }

    @Test
    fun `getByPosition returns the zones of the tile or nil`() {
        val zones = Zones()
        val zone = zones.addZone("tile-zone")!!
        val operations = object : ZoneOperations by WorldZoneOperations(zones) {
            override fun getByPosition(position: Position): List<Zone>? =
                if (position == Position(10, 10, 7)) listOf(zone) else null
        }
        val environment = environment(operations)

        environment.loadChunk(
            """
            found = Zone.getByPosition({x = 10, y = 10, z = 7})
            count = #found
            missing = Zone.getByPosition({x = 1, y = 1, z = 1})
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("count").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("missing").isnil()).isTrue()
    }

    @Test
    fun `creature getters return empty tables when the caches are not wired`() {
        val environment = environment()

        environment.loadChunk(
            """
            zone = Zone('empty')
            players = #zone:getPlayers()
            creatures = #zone:getCreatures()
            items = #zone:getItems()
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("players").toint()).isEqualTo(0)
        assertThat(environment.getGlobal("creatures").toint()).isEqualTo(0)
        assertThat(environment.getGlobal("items").toint()).isEqualTo(0)
    }

    @Test
    fun `setMonsterVariant rejects the empty variant like the C++ guard`() {
        val environment = environment()

        environment.loadChunk(
            """
            zone = Zone('variant')
            empty = zone:setMonsterVariant('')
            unsupported = zone:setMonsterVariant('greedy')
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("empty").toboolean()).isFalse()
        assertThat(environment.getGlobal("unsupported").toboolean()).isFalse()
    }
}
