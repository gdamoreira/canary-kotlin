package org.opentibia.canary.io

import io.github.oshai.kotlinlogging.KotlinLogging
import org.opentibia.canary.config.ConfigKey
import org.opentibia.canary.config.ConfigKeys
import org.opentibia.canary.config.ConfigManager
import org.opentibia.canary.creatures.spawns.SpawnMonster
import org.opentibia.canary.creatures.spawns.SpawnNpc
import org.opentibia.canary.io.pipeline.SpawnXmlLoader
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import java.io.File

/**
 * Port of the XML loader half of `IOMap` (src/io/iomap.hpp:32-150):
 * `loadMonsters`, `loadZones`, `loadNpcs` and `loadHouses`, including the
 * `MAP_NAME`-based file-name fallbacks. The `*Custom` variants need the
 * custom-map arrays the Kotlin [GameMap] does not carry yet (PARITY GAP).
 *
 * `loadMap` itself lives in [org.opentibia.canary.io.otbm.OtbmMapLoader];
 * `parseMapDataAttributes`/`parseTileArea`/`parseTowns`/`parseWaypoints` are
 * ported there and remain to be split out.
 */
object IOMap {

    private val logger = KotlinLogging.logger {}

    private val mapNameKey: ConfigKey = requireNotNull(ConfigKeys.byName("MAP_NAME"))

    private fun mapName(config: ConfigManager): String =
        config.getString(mapNameKey, mapNameKey.defaultValue as String)

    /** `IOMap::loadMonsters` (src/io/iomap.hpp:32-41). */
    fun loadMonsters(
        map: GameMap,
        config: ConfigManager,
        spawnFactory: (Position, Int) -> SpawnMonster,
    ): Boolean {
        if (map.monsterfile.isEmpty()) {
            // OTBM file doesn't tell us about the monsterfile,
            // Lets guess it is mapname-monster.xml.
            map.monsterfile = mapName(config) + "-monster.xml"
        }

        val spawns = SpawnXmlLoader.loadMonsters(File(map.monsterfile), spawnFactory = spawnFactory)
            ?: return false
        map.spawnsMonster = spawns
        logger.info { "Loaded ${spawns.getspawnMonsterList().size} monster spawn blocks from ${map.monsterfile}" }
        return true
    }

    /** `IOMap::loadNpcs` (src/io/iomap.hpp:64-73). */
    fun loadNpcs(
        map: GameMap,
        config: ConfigManager,
        spawnFactory: (Position, Int) -> SpawnNpc,
    ): Boolean {
        if (map.npcfile.isEmpty()) {
            // OTBM file doesn't tell us about the npcfile,
            // Lets guess it is mapname-npc.xml.
            map.npcfile = mapName(config) + "-npc.xml"
        }

        val spawns = SpawnXmlLoader.loadNpcs(File(map.npcfile), spawnFactory = spawnFactory)
            ?: return false
        map.spawnsNpc = spawns
        logger.info { "Loaded ${spawns.getspawnNpcList().size} npc spawn blocks from ${map.npcfile}" }
        return true
    }

    /** `IOMap::loadHouses` (src/io/iomap.hpp:80-89). */
    fun loadHouses(map: GameMap, config: ConfigManager): Boolean {
        if (map.housefile.isEmpty()) {
            // OTBM file doesn't tell us about the housefile,
            // Lets guess it is mapname-house.xml.
            map.housefile = mapName(config) + "-house.xml"
        }

        return map.houses.loadHousesXML(map.housefile)
    }

    /** `IOMap::loadZones` (src/io/iomap.hpp:48-57). */
    fun loadZones(map: GameMap, config: ConfigManager): Boolean {
        if (map.zonesfile.isEmpty()) {
            // OTBM file doesn't tell us about the zonesfile,
            // Lets guess it is mapname-zone.xml.
            map.zonesfile = mapName(config) + "-zones.xml"
        }

        return map.zones.loadFromXml(map.zonesfile)
    }
}
