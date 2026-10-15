package org.opentibia.canary.systems.raid

import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.monsters.MonsterForge
import org.opentibia.canary.creatures.monsters.MonsterPipelinesHost
import org.opentibia.canary.creatures.spawns.MonsterSpawnPath
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.Tile

/**
 * Production [RaidSpawner] bridge over the composed world. Canary reaches the
 * raid primitives through globals; per AGENTS.md 1.1 they are injected here:
 *
 *  - [createMonster] -> `Monster::createMonster(name)`
 *    (src/creatures/monsters/monster.cpp:82-88) through the composed
 *    [MonsterSpawnPath] over the booted `g_monsters()` registry;
 *  - [placeCreature] -> `g_game().placeCreature(creature, pos, extendedPos,
 *    force)` (src/game/game.cpp:1524), including the `Creature::addList()` ->
 *    `Game::addMonster` registration (game.cpp:1513, 11671) that
 *    `MonsterSpawnPath.placeCreature` ports for non-engine callers;
 *  - [getTile] -> `g_game().map.getTile(x, y, z)` wrapped as a [RaidTile]
 *    view for [AreaSpawnEvent] (`raids.cpp:560-570`).
 *
 * The [RaidMonster] view returned by [createMonster] keeps the placed monster's
 * identity, so the event tail (`monster->setForgeMonster(false)` and
 * `monster->onSpawn(position)`, src/lua/creature/raids.cpp:404-408) dispatches
 * through the world host of the real monster. Without a [monsterHost]
 * (embedded/test roots) the placement stays live and the forge/spawn hooks
 * degrade to no-ops.
 *
 * The bridge is composed by the server root next to `Raids`; it holds the
 * world-owned [MonsterSpawnPath], [GameMap] and [MonsterPipelinesHost], so a
 * raid event can never spawn through a stale or foreign monster factory
 * (AGENTS.md deferred-callback lifetime safety).
 */
class GameRaidSpawner(
    private val spawnPath: MonsterSpawnPath,
    private val map: GameMap,
    private val monsterHost: MonsterPipelinesHost? = null,
) : RaidSpawner {
    override fun createMonster(name: String): RaidMonster? {
        val monster = spawnPath.createMonster(name) ?: return null
        return GameRaidMonster(monster, monsterHost)
    }

    override fun placeCreature(
        monster: RaidMonster,
        position: Position,
        extendedPos: Boolean,
        force: Boolean,
    ): Boolean {
        val hosted = monster as? GameRaidMonster ?: return false
        return spawnPath.placeCreature(hosted.monster, position, extendedPos, force)
    }

    override fun getTile(x: Int, y: Int, z: Int): RaidTile? {
        val tile = map.getTile(Position(x, y, z)) ?: return null
        return GameRaidTile(tile)
    }
}

/**
 * The real [Monster] behind a raid [RaidMonster] view. The hooks transcribe
 * `Monster::setForgeMonster(bool)` (src/creatures/monsters/monster.cpp:3899-3901)
 * and `Monster::onSpawn(position)` (monster.cpp:627-648) through the world host
 * entry of the placed monster; every lookup re-resolves the entry by id, so a
 * removed or replaced monster becomes a no-op (AGENTS.md deferred-callback
 * lifetime safety).
 */
class GameRaidMonster(
    val monster: Monster,
    private val monsterHost: MonsterPipelinesHost?,
) : RaidMonster(monster.name) {
    override fun setForgeMonster(value: Boolean) {
        monsterHost?.typeStateOf(monster.id)?.let { state -> MonsterForge.setForgeMonster(state, value) }
    }

    override fun onSpawn(position: Position) {
        monsterHost?.onSpawn(monster.id, position)
    }
}

/** `g_game().map.getTile(x, y, z)` view used by [AreaSpawnEvent] (`raids.cpp:560`). */
class GameRaidTile(private val tile: Tile) : RaidTile {
    override val position: Position get() = tile.position

    /** `Tile::isMovableBlocking()` (src/items/tile.cpp). */
    override fun isMovableBlocking(): Boolean = tile.isMovableBlocking()

    /** `Tile::hasFlag(TileFlags_t)` (src/items/tile.hpp:296). */
    override fun hasFlag(flag: Int): Boolean = tile.hasFlag(flag)

    /** `Tile::getTopCreature()` `nullptr` check (raids.cpp:568); any creature blocks. */
    override val hasTopCreature: Boolean get() = tile.getTopCreature() != null
}
