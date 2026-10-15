package org.opentibia.canary.creatures.spawns

import org.opentibia.canary.config.ConfigManager
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.PlayerFlags_t
import org.opentibia.canary.creatures.npcs.Npcs
import org.opentibia.canary.game.Game
import org.opentibia.canary.io.IOMap
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.Spectators

/**
 * Production composition of the NPC spawn path. Canary splits the workflow
 * between map boot and `GAME_STATE_INIT`:
 *  - `Map::loadMap(loadNpcs=true)` -> `IOMap::loadNpcs` ->
 *    `SpawnsNpc::loadFromXml` (src/map/map.cpp:211-212,
 *    src/io/iomap.hpp:64-73,
 *    src/creatures/npcs/spawns/spawn_npc.cpp:25-98)
 *  - `Game::setGameState(GAME_STATE_INIT)` -> `map.spawnsNpc.startup()`
 *    (src/game/game.cpp:971), which places every block through
 *    `Game::internalPlaceCreature` (spawn_npc.cpp:195, game.cpp:1495)
 *  - `Game::shutdown` -> `map.spawnsNpc.clear()` (src/game/game.cpp:9502)
 *
 * This class owns both halves for the Kotlin server: [load] parses the map's
 * npc spawn XML with a [GameNpcFactory] over the booted `g_npcs()` registry,
 * and [startup]/[shutdown] drive the engine. The engine resolves each block
 * type through the registered `NpcType` (`NpcType::canSpawn` day/night gate
 * folded in, src/creatures/npcs/npcs.cpp:22-32) and its scheduled checks run
 * through [spawnScheduler] (`g_dispatcher()`).
 *
 * PARITY GAPS: the custom-map spawn loops (`map.spawnsNpcCustomMaps`,
 * game.cpp:972-977) need the custom-map arrays the Kotlin [GameMap] does not
 * carry; the `Npc::spawnNpc` weak-pointer removal hook
 * (`Npc::onRemoveCreature` -> `startSpawnNpcCheck`, npc.cpp:615-616) is not
 * bound, so a removed npc only respawns through an explicit
 * `startSpawnNpcCheck`; the `g_npcs()` registry has no datapack Lua
 * registration boot yet, so production composes it from the bootstrap-owned
 * registry.
 */
class NpcSpawnPath(
    private val game: Game,
    private val map: GameMap,
    private val npcTypes: Npcs,
    private val spawnScheduler: SpawnScheduler,
    private val config: ConfigManager,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val npcFactory = GameNpcFactory(game, npcTypes)

    /**
     * `IOMap::loadNpcs` (src/io/iomap.hpp:64-73): resolves the map's spawn
     * file (`Map::npcfile` or the `MAP_NAME` fallback) into `map.spawnsNpc`.
     * Idempotent through the `SpawnsNpc::loadFromXml` `isLoaded()` guard
     * (spawn_npc.cpp:26-28).
     */
    fun load(): Boolean {
        if (map.spawnsNpc.isLoaded()) {
            return true
        }

        return IOMap.loadNpcs(map, config, ::createSpawnNpc)
    }

    /** `SpawnsNpc::startup` (src/creatures/npcs/spawns/spawn_npc.cpp:101-111). */
    fun startup() {
        map.spawnsNpc.startup()
    }

    /** `SpawnsNpc::clear` through the `Game::shutdown` call site (src/game/game.cpp:9502). */
    fun shutdown() {
        map.spawnsNpc.clear()
    }

    /**
     * `SpawnNpc` composition factory for `IOMap::loadNpcs`
     * (`std::make_shared<SpawnNpc>(centerPos, radius)`,
     * src/creatures/npcs/spawns/spawn_npc.cpp:50).
     */
    fun createSpawnNpc(centerPos: Position, radius: Int): SpawnNpc = SpawnNpc(
        centerPos = centerPos,
        radius = radius,
        npcTypeLookup = NpcTypeLookup { name -> lookupNpcType(name) },
        npcFactory = npcFactory,
        scheduler = spawnScheduler,
        now = now,
        findPlayer = ::findPlayer,
        addMagicEffect = game::addMagicEffect,
    )

    /**
     * `SpawnNpc::addNpc`'s `g_npcs().getNpcType` reach
     * (spawn_npc.cpp:313-316) with the `NpcType::canSpawn` day/night gate
     * folded in (src/creatures/npcs/npcs.cpp:22-32).
     */
    private fun lookupNpcType(name: String): NpcType? {
        val registeredType = npcTypes.getNpcType(name) ?: return null
        return NpcType(
            name = registeredType.name,
            canSpawnAt = { pos -> registeredType.canSpawn(pos) },
        )
    }

    /** `SpawnNpc::findPlayer` (src/creatures/npcs/spawns/spawn_npc.cpp:175-181). */
    private fun findPlayer(pos: Position): Boolean =
        Spectators(map).find<Player>(pos).data()
            .filterIsInstance<Player>()
            .any { !it.hasFlag(PlayerFlags_t.IgnoredByNpcs) }
}
