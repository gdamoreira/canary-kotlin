package org.opentibia.canary.creatures

import org.opentibia.canary.game.movement.Direction
import org.opentibia.canary.game.movement.MovementEngine
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position

class Spawner(
    val monsterName: String,
    val centerPos: Position,
    val radius: Int,
    val respawnSeconds: Int,
    private val map: GameMap
) {
    var spawnedMonster: Monster? = null

    fun spawn(): Monster? {
        val targetTile = map.getTile(centerPos) ?: return null
        val monster = Monster(
            id = System.currentTimeMillis(),
            name = monsterName,
            maxHealth = 25,
            health = 25,
            speed = 100,
            position = centerPos
        )
        spawnedMonster = monster
        return monster
    }
}

class SpawnManager(private val map: GameMap) {
    private val spawners = mutableListOf<Spawner>()

    fun addSpawner(monsterName: String, centerPos: Position, radius: Int, respawnSeconds: Int): Spawner {
        val spawner = Spawner(monsterName, centerPos, radius, respawnSeconds, map)
        spawners.add(spawner)
        return spawner
    }
}

object MonsterAiEngine {

    fun think(monster: Monster, potentialTargets: List<Player>, map: GameMap) {
        val nearestTarget = potentialTargets.minByOrNull { it.position.getDistanceTo(monster.position) } ?: return
        val distance = monster.position.getDistanceTo(nearestTarget.position)

        if (distance > 1) {
            val dx = nearestTarget.position.x - monster.position.x
            val dy = nearestTarget.position.y - monster.position.y

            val direction = when {
                dy < 0 -> Direction.North
                dy > 0 -> Direction.South
                dx > 0 -> Direction.East
                dx < 0 -> Direction.West
                else -> null
            }

            if (direction != null) {
                MovementEngine.move(monster, direction, map)
            }
        }
    }
}
