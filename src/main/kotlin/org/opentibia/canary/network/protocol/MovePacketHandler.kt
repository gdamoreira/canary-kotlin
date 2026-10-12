package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.movement.Direction
import org.opentibia.canary.game.movement.MovementEngine
import org.opentibia.canary.map.GameMap

object MovePacketHandler {

    fun handle(player: Player, opcode: Int, payload: ByteBuf, map: GameMap): Boolean {
        return when (opcode) {
            // Movement: Cardinal directions
            0x65 -> {
                player.direction = Direction.North
                MovementEngine.move(player, Direction.North, map)
            }
            0x66 -> {
                player.direction = Direction.East
                MovementEngine.move(player, Direction.East, map)
            }
            0x67 -> {
                player.direction = Direction.South
                MovementEngine.move(player, Direction.South, map)
            }
            0x68 -> {
                player.direction = Direction.West
                MovementEngine.move(player, Direction.West, map)
            }

            // Movement: Diagonal directions
            0x6A -> {
                player.direction = Direction.NorthEast
                MovementEngine.move(player, Direction.NorthEast, map)
            }
            0x6B -> {
                player.direction = Direction.SouthEast
                MovementEngine.move(player, Direction.SouthEast, map)
            }
            0x6C -> {
                player.direction = Direction.SouthWest
                MovementEngine.move(player, Direction.SouthWest, map)
            }
            0x6D -> {
                player.direction = Direction.NorthWest
                MovementEngine.move(player, Direction.NorthWest, map)
            }

            // Spot Turns
            0x6F -> {
                player.direction = Direction.North
                true
            }
            0x70 -> {
                player.direction = Direction.East
                true
            }
            0x71 -> {
                player.direction = Direction.South
                true
            }
            0x72 -> {
                player.direction = Direction.West
                true
            }

            else -> false
        }
    }
}
