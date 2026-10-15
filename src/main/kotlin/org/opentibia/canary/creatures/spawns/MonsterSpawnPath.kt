package org.opentibia.canary.creatures.spawns

import org.opentibia.canary.config.ConfigKeys
import org.opentibia.canary.config.ConfigManager
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.PlayerFlags_t
import org.opentibia.canary.creatures.monsters.Monsters
import org.opentibia.canary.game.Game
import org.opentibia.canary.game.scheduling.DispatcherLane
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.io.IOMap
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.Spectators
import kotlin.random.Random

/**
 * Production composition of the monster spawn path. Canary splits the workflow
 * between map boot and `GAME_STATE_INIT`:
 *  - `Map::loadMap(loadMonsters=true)` -> `IOMap::loadMonsters` ->
 *    `SpawnsMonster::loadFromXML` (src/map/map.cpp:199-201,
 *    src/io/iomap.hpp:32-46,
 *    src/creatures/monsters/spawns/spawn_monster.cpp:29-117)
 *  - `Game::setGameState(GAME_STATE_INIT)` -> `map.spawnsMonster.startup()`
 *    (src/game/game.cpp:970), which places every block through
 *    `Game::internalPlaceCreature` (spawn_monster.cpp:201, game.cpp:1495)
 *  - `Game::shutdown` -> `map.spawnsMonster.clear()` (src/game/game.cpp:9502)
 *
 * This class owns both halves for the Kotlin server: [load] parses the map's
 * spawn XML with a [GameMonsterFactory] over the booted `g_monsters()` registry,
 * and [startup]/[shutdown] drive the engine. The engine resolves each block type
 * through the registered `MonsterType` (`isBlockable`, `isBoss()` and the
 * day/night `canSpawn` gate, src/creatures/monsters/monsters.cpp:35-45) and its
 * scheduled checks run through [spawnScheduler] (`g_dispatcher()`).
 *
 * PARITY GAPS: the zone monster-variant lookup of `SpawnMonster::addMonster`
 * (`Zone::getMonsterVariant`, spawn_monster.cpp:365-371) and the
 * `g_eventsScheduler().getSpawnMonsterSchedule()` read are not wired: the
 * event schedule defaults to the engine's 100.
 */
class MonsterSpawnPath(
    private val game: Game,
    private val map: GameMap,
    private val monsterTypes: Monsters,
    private val spawnScheduler: SpawnScheduler,
    private val config: ConfigManager,
    private val now: () -> Long = System::currentTimeMillis,
    private val random: Random = Random.Default,
    private val rateSpawn: Int = configNumber(config, "RATE_SPAWN", 1),
    private val eventsSpawnMonsterSchedule: Int = 100,
    monsterPerfTestForceActive: Boolean = configBoolean(config, "MONSTER_PERF_TEST_FORCE_ACTIVE", false),
    /**
     * `Monster::onSpawn(position)` dispatch seam threaded into every block
     * engine (src/creatures/monsters/spawns/spawn_monster.cpp:220). The
     * composition root binds the world host (`MonsterPipelinesHost.onSpawn`);
     * the inert default keeps the path world-free (embedded/test use).
     */
    private val onMonsterSpawn: (Monster, Position) -> Unit = { _, _ -> },
) {
    private val monsterFactory = GameMonsterFactory(game, monsterTypes, monsterPerfTestForceActive)

    /**
     * `IOMap::loadMonsters` (src/io/iomap.hpp:32-46): resolves the map's spawn
     * file (`Map::monsterfile` or the `MAP_NAME` fallback) into
     * `map.spawnsMonster`. Idempotent through the `SpawnsMonster::loadFromXML`
     * `isLoaded()` guard (spawn_monster.cpp:30-32).
     */
    fun load(): Boolean {
        if (map.spawnsMonster.isLoaded()) {
            return true
        }

        return IOMap.loadMonsters(map, config, ::createSpawnMonster)
    }

    /** `SpawnsMonster::startup` (src/creatures/monsters/spawns/spawn_monster.cpp:122-132). */
    fun startup() {
        map.spawnsMonster.startup()
    }

    /**
     * `Monster::createMonster(name)` (src/creatures/monsters/monster.cpp:82-88)
     * over the path's [GameMonsterFactory]: resolves the booted `g_monsters()`
     * type and constructs the monster without placing it. `GameFunctions::
     * luaGameCreateMonster` (game_functions.cpp:589) and the raid events
     * (src/lua/creature/raids.cpp:395) create through this entry and then place
     * with [placeCreature].
     */
    fun createMonster(name: String): Monster? = monsterFactory.createMonster(name)

    /**
     * `Game::placeCreature(creature, pos, extendedPos, force)`
     * (src/game/game.cpp:1524) plus the `Creature::addList()` tail of
     * `Game::internalPlaceCreature` (game.cpp:1513, `Monster::addList` ->
     * `Game::addMonster`, game.cpp:11671) that registers the placed monster in
     * the world monster index. The Kotlin `Game.internalPlaceCreature`
     * documents that monster registration as a PARITY GAP and
     * [GameMonsterFactory.create] compensates for the engine path; this
     * placement facade applies the same registration for the Lua/raid callers,
     * so `Game::getMonsterByID` resolves them like Canary.
     */
    fun placeCreature(monster: Monster, position: Position, extendedPos: Boolean, force: Boolean): Boolean {
        if (!game.placeCreature(monster, position, extendedPos, force)) {
            return false
        }
        game.addMonster(monster)
        return true
    }

    /** `SpawnsMonster::clear` through the `Game::shutdown` call site (src/game/game.cpp:9502). */
    fun shutdown() {
        map.spawnsMonster.clear()
    }

    /**
     * `SpawnMonster` composition factory for `IOMap::loadMonsters`
     * (`std::make_shared<SpawnMonster>(centerPos, radius)`,
     * src/creatures/monsters/spawns/spawn_monster.cpp:49). The boosted monster
     * name is read when each block is created, like the
     * `g_game().getBoostedMonsterName()` read of the per-block
     * `SpawnMonster::addMonster` (spawn_monster.cpp:379).
     */
    fun createSpawnMonster(centerPos: Position, radius: Int): SpawnMonster = SpawnMonster(
        centerPos = centerPos,
        radius = radius,
        monsterTypeLookup = MonsterTypeLookup { name -> lookupMonsterType(name) },
        monsterFactory = monsterFactory,
        scheduler = spawnScheduler,
        now = now,
        findPlayer = ::findPlayer,
        random = random,
        rateSpawn = rateSpawn,
        boostedMonsterName = game.getBoostedMonsterName(),
        eventsSpawnMonsterSchedule = eventsSpawnMonsterSchedule,
        addMagicEffect = game::addMagicEffect,
        onMonsterSpawn = onMonsterSpawn,
    )

    /**
     * `SpawnMonster::addMonster`'s `g_monsters().getMonsterType` reach
     * (spawn_monster.cpp:365-368) with the `MonsterType::canSpawn` day/night
     * gate folded in (src/creatures/monsters/monsters.cpp:35-45).
     */
    private fun lookupMonsterType(name: String): MonsterType? {
        val registeredType = monsterTypes.getMonsterType(name, silent = true) ?: return null
        return MonsterType(
            name = registeredType.name,
            isBlockable = registeredType.info.isBlockable,
            isBoss = registeredType.isBoss(),
            canSpawnAt = { pos -> registeredType.canSpawn(pos, game.gameIsDay()) },
        )
    }

    /** `SpawnMonster::findPlayer` (src/creatures/monsters/spawns/spawn_monster.cpp:184-187). */
    private fun findPlayer(pos: Position): Boolean =
        Spectators(map).find<Player>(pos).data()
            .filterIsInstance<Player>()
            .any { !it.hasFlag(PlayerFlags_t.IgnoredByMonsters) }
}

/**
 * `g_dispatcher()` adapter for the spawn engine: its checks are scheduled on
 * `DispatcherLane::Maintenance` (spawn_monster.cpp:170-178, 299-305). The
 * cancel handle re-resolves the event by id (`Dispatcher::stopEvent`,
 * src/game/scheduling/dispatcher.hpp:176) so no dispatcher is captured across
 * the deferred boundary (AGENTS.md deferred-callback lifetime safety).
 */
fun dispatcherSpawnScheduler(dispatcher: GameDispatcher): SpawnScheduler = SpawnScheduler { delayMs, task ->
    val scheduled = dispatcher.schedule(delayMs, DispatcherLane.Maintenance) { task() }
    SpawnTask { scheduled.cancel() }
}

/** `g_configManager().getNumber(name, default)` typed view for the spawn rates. */
private fun configNumber(config: ConfigManager, name: String, default: Int): Int {
    val key = ConfigKeys.byName(name) ?: return default
    return config.getNumber(key, key.defaultValue as Int)
}

/** `g_configManager().getBoolean(name, default)` typed view for the spawn flags. */
private fun configBoolean(config: ConfigManager, name: String, default: Boolean): Boolean {
    val key = ConfigKeys.byName(name) ?: return default
    return config.getBoolean(key, key.defaultValue as Boolean)
}
