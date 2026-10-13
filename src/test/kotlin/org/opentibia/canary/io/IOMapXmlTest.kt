package org.opentibia.canary.io

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.opentibia.canary.config.ConfigKeys
import org.opentibia.canary.config.ConfigManager
import org.opentibia.canary.config.ConfigSource
import org.opentibia.canary.creatures.spawns.FakeMonsterFactory
import org.opentibia.canary.creatures.spawns.FakeNpcFactory
import org.opentibia.canary.creatures.spawns.FakeSpawnScheduler
import org.opentibia.canary.creatures.spawns.MonsterType
import org.opentibia.canary.creatures.spawns.NpcType
import org.opentibia.canary.creatures.spawns.SpawnMonster
import org.opentibia.canary.creatures.spawns.SpawnNpc
import org.opentibia.canary.creatures.spawns.monsterTypeLookup
import org.opentibia.canary.creatures.spawns.npcTypeLookup
import org.opentibia.canary.map.GameMap
import java.io.File
import java.nio.file.Path

/**
 * Transcript tests for the XML half of `IOMap` (src/io/iomap.hpp:32-150):
 * `loadHouses`, `loadMonsters`, `loadNpcs` and `loadZones` including the
 * `MAP_NAME`-based file-name fallbacks.
 */
class IOMapXmlTest {

    @TempDir
    lateinit var tempDir: Path

    private val now = { 0L }
    private val scheduler = FakeSpawnScheduler(now)
    private val monsterFactory = FakeMonsterFactory()
    private val npcFactory = FakeNpcFactory()

    private fun config(mapName: String): ConfigManager = ConfigManager(
        keyMap = ConfigKeys.all.associateWith { it.luaIdentifier },
        properties = ConfigSource.of("mapName = $mapName"),
    )

    private fun xmlFile(name: String, content: String): File =
        tempDir.resolve(name).toFile().apply { writeText(content) }

    @Test
    fun `loadHouses loads the map house file into the registry`() {
        val map = GameMap()
        map.houses.addHouse(1)
        map.housefile = xmlFile(
            "world-house.xml",
            """
            <houses>
                <house name="Central Flat 1" houseid="1" entryx="1000" entryy="1001" entryz="7" rent="500" townid="2" size="17" />
            </houses>
            """.trimIndent(),
        ).path

        assertThat(IOMap.loadHouses(map, config("canary"))).isTrue()
        assertThat(map.houses.getHouse(1)?.name).isEqualTo("Central Flat 1")
    }

    @Test
    fun `loadHouses falls back to the MAP_NAME house file name`() {
        val map = GameMap()

        assertThat(IOMap.loadHouses(map, config("missing-map"))).isFalse()
        assertThat(map.housefile).isEqualTo("missing-map-house.xml")
    }

    @Test
    fun `loadZones registers each zone from the zones XML`() {
        val map = GameMap()
        map.zonesfile = xmlFile(
            "world-zones.xml",
            """
            <zones>
                <zone name="arena" zoneid="7" />
                <zone name="boss" zoneid="8" />
            </zones>
            """.trimIndent(),
        ).path

        assertThat(IOMap.loadZones(map, config("canary"))).isTrue()
        assertThat(map.zones.getZone("arena")?.id).isEqualTo(7)
        assertThat(map.zones.getZone("boss")?.id).isEqualTo(8)
    }

    @Test
    fun `loadMonsters loads the monster spawn file into the map`() {
        val map = GameMap()
        val dragon = MonsterType("Dragon", isBlockable = true)
        map.monsterfile = xmlFile(
            "world-monster.xml",
            """
            <monsters>
                <monster centerx="1000" centery="1000" centerz="7" radius="5">
                    <monster name="Dragon" x="0" y="0" spawntime="60"/>
                </monster>
            </monsters>
            """.trimIndent(),
        ).path

        assertThat(
            IOMap.loadMonsters(map, config("canary")) { pos, radius ->
                SpawnMonster(pos, radius, monsterTypeLookup(dragon), monsterFactory, scheduler, now = now)
            },
        ).isTrue()

        assertThat(map.spawnsMonster.isLoaded()).isTrue
        assertThat(map.spawnsMonster.getspawnMonsterList()).hasSize(1)
    }

    @Test
    fun `loadNpcs loads the npc spawn file into the map`() {
        val map = GameMap()
        val alice = NpcType("Alice")
        map.npcfile = xmlFile(
            "world-npc.xml",
            """
            <npcs>
                <npc centerx="500" centery="600" centerz="6" radius="4">
                    <npc name="Alice" x="0" y="0" spawntime="60"/>
                </npc>
            </npcs>
            """.trimIndent(),
        ).path

        assertThat(
            IOMap.loadNpcs(map, config("canary")) { pos, radius ->
                SpawnNpc(pos, radius, npcTypeLookup(alice), npcFactory, scheduler, now = now)
            },
        ).isTrue()

        assertThat(map.spawnsNpc.isLoaded()).isTrue
        assertThat(map.spawnsNpc.getSpawnNpcList()).hasSize(1)
    }
}
