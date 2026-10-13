package org.opentibia.canary.lua.runtime

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.game.movement.Teleport
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.Town
import org.opentibia.canary.map.Towns
import org.opentibia.canary.systems.imbuement.Imbuements

/**
 * Transcript tests proving the real (non-no-op) map/item binding seams installed by
 * [WorldLuaOperations] delegate to the Kotlin domain objects: `House`, `Town`,
 * `Teleport` and `Imbuement` through the exact Canary Lua names.
 */
class WorldMapItemsBindingsTest {

    private fun environment(world: WorldLuaOperations): LuaEnvironment =
        LuaEnvironment().apply { initState() }.also { world.install(it) }

    @Test
    fun `WorldLuaOperations wires the House Town Teleport and Imbuement globals`() {
        val map = GameMap()
        map.getOrCreateTile(Position(10, 20, 7))
        map.houses.addHouse(1).apply {
            name = "Central Plaza"
            owner = 7
            entryPos = Position(10, 20, 7)
            size = 10
            addTile(Position(10, 20, 7))
        }
        val towns = Towns().apply {
            addTown(
                1,
                Town(1).apply {
                    setName("Thais")
                    setTemplePos(Position(100, 100, 7))
                },
            )
        }
        val imbuements = Imbuements().apply {
            assertThat(loadFromXml(IMBUEMENT_XML)).isTrue()
        }
        val teleport = Teleport(1387, Position(50, 60, 7))
        val world = WorldLuaOperations(
            gameMap = map,
            towns = towns,
            imbuements = imbuements,
            teleportByUid = { uid -> if (uid == 7) teleport else null },
        )

        val environment = environment(world)
        environment.loadChunk(
            """
            houseName = House(1):getName()
            houseTileCount = House(1):getTileCount()
            townName = Town('Thais'):getName()
            townId = Town(1):getId()
            destination = Teleport(7):getDestination():toString()
            imbuementName = Imbuement(1):getName()
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("houseName").tojstring()).isEqualTo("Central Plaza")
        assertThat(environment.getGlobal("houseTileCount").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("townName").tojstring()).isEqualTo("Thais")
        assertThat(environment.getGlobal("townId").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("destination").tojstring()).isEqualTo("( 50, 60, 7 )")
        assertThat(environment.getGlobal("imbuementName").tojstring()).isEqualTo("Scorch")
    }

    @Test
    fun `WorldLuaOperations returns nil for the map and item lookups it cannot resolve`() {
        val world = WorldLuaOperations()
        val environment = environment(world)

        environment.loadChunk(
            """
            house = House(1)
            town = Town('Thais')
            teleport = Teleport(1)
            imbuement = Imbuement(1)
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("house").isnil()).isTrue()
        assertThat(environment.getGlobal("town").isnil()).isTrue()
        assertThat(environment.getGlobal("teleport").isnil()).isTrue()
        assertThat(environment.getGlobal("imbuement").isnil()).isTrue()
    }

    private companion object {
        const val IMBUEMENT_XML = """
            <imbuements>
                <base id="1" name="Basic" price="5000" protectionPrice="10000" removecost="15000" duration="72000" percent="90" />
                <category id="0" name="Elemental Damage" agressive="1" />
                <imbuement base="1" iconid="1" premium="1" category="0" name="Scorch">
                    <item key="item" value="9636" count="25" />
                    <effect key="effect" type="damage" combat="fire" value="10" />
                </imbuement>
            </imbuements>
        """
    }
}
