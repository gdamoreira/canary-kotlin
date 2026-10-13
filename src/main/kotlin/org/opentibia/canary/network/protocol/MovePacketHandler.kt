package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.movement.Direction
import org.opentibia.canary.game.movement.MovementEngine
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.lua.runtime.ScriptingDispatch
import org.opentibia.canary.map.GameMap

object MovePacketHandler {

    /**
     * Handles the walk and turn opcodes.
     *
     * Every successful cardinal/diagonal walk moves the player through
     * [MovementEngine.move]. When [luaEnvironment] and [dispatcher] are supplied, the
     * engine also fires the `stepout`/`stepin` Lua move events of the tiles involved
     * (confined to the WorldCommit lane by [ScriptingDispatch]); without them the walk
     * still happens and no Lua event is considered. Turn opcodes never move and fire
     * nothing, as in `ProtocolGame::parseTurn`.
     */
    fun handle(
        player: Player,
        opcode: Int,
        payload: ByteBuf,
        map: GameMap,
        luaEnvironment: LuaEnvironment? = null,
        dispatcher: GameDispatcher? = null,
    ): Boolean {
        val scripting = if (luaEnvironment != null && dispatcher != null) {
            ScriptingDispatch(luaEnvironment, dispatcher)
        } else {
            null
        }

        return when (opcode) {
            // Movement: Cardinal directions
            0x65 -> move(player, Direction.North, map, scripting)
            0x66 -> move(player, Direction.East, map, scripting)
            0x67 -> move(player, Direction.South, map, scripting)
            0x68 -> move(player, Direction.West, map, scripting)

            // Movement: Diagonal directions
            0x6A -> move(player, Direction.NorthEast, map, scripting)
            0x6B -> move(player, Direction.SouthEast, map, scripting)
            0x6C -> move(player, Direction.SouthWest, map, scripting)
            0x6D -> move(player, Direction.NorthWest, map, scripting)

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

    private fun move(
        player: Player,
        direction: Direction,
        map: GameMap,
        scripting: ScriptingDispatch?,
    ): Boolean {
        player.direction = direction
        return MovementEngine.move(player, direction, map, scripting)
    }
}
