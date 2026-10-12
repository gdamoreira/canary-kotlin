package org.opentibia.canary.game.movement

import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position

enum class Direction(val byteValue: Byte, val dx: Int, val dy: Int) {
    North(0, 0, -1),
    East(1, 1, 0),
    South(2, 0, 1),
    West(3, -1, 0),
    SouthWest(4, -1, 1),
    SouthEast(5, 1, 1),
    NorthWest(6, -1, -1),
    NorthEast(7, 1, -1);

    fun offset(pos: Position): Position {
        return Position(pos.x + dx, pos.y + dy, pos.z)
    }
}

object MovementEngine {

    fun calculateStepDuration(creatureSpeed: Int, groundSpeed: Int = 100): Long {
        if (creatureSpeed <= 0) return Long.MAX_VALUE
        return (groundSpeed.toLong() * 1000L) / creatureSpeed.toLong()
    }

    fun move(creature: Creature, direction: Direction, map: GameMap): Boolean {
        val targetPos = direction.offset(creature.position)
        val targetTile = map.getTile(targetPos) ?: return false

        creature.position = targetPos
        return true
    }
}
