package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.Game
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
     * With a composed [Game] every walk enters `Game::playerMove` (game.cpp:4282):
     * the queued step runs through `Creature::addEventWalk`/`onCreatureWalk` and
     * `Game::internalMoveCreature`, so the tiles/sectors update, the spectator
     * `sendCreatureMove` (0x6D) confirms the step and the Lua `stepout`/`stepin`
     * events fire through the tile notifications. Without a composed world the
     * decode-only fallback moves through [MovementEngine.move]; when
     * [luaEnvironment] and [dispatcher] are supplied it also fires the Lua move
     * events itself. Turn opcodes never move and fire nothing, as in
     * `ProtocolGame::parseTurn`.
     */
    fun handle(
        player: Player,
        opcode: Int,
        payload: ByteBuf,
        map: GameMap,
        luaEnvironment: LuaEnvironment? = null,
        dispatcher: GameDispatcher? = null,
        game: Game? = null,
    ): Boolean {
        val scripting = if (luaEnvironment != null && dispatcher != null) {
            ScriptingDispatch(luaEnvironment, dispatcher)
        } else {
            null
        }

        return when (opcode) {
            // Stop auto walk: 0x69 playerStopAutoWalk
            0x69 -> {
                // `Game::playerStopAutoWalk` (game.cpp:4567-4574) latches
                // `Player::stopWalk`'s cancel; the queued walk aborts at the
                // next pump tick (`Creature::onCreatureWalk`, creature.cpp:259).
                if (game != null) {
                    game.playerStopAutoWalk(player.id)
                }
                true
            }

            // Movement: Cardinal directions
            0x65 -> move(player, Direction.North, map, scripting, game)
            0x66 -> move(player, Direction.East, map, scripting, game)
            0x67 -> move(player, Direction.South, map, scripting, game)
            0x68 -> move(player, Direction.West, map, scripting, game)

            // Movement: Diagonal directions
            0x6A -> move(player, Direction.NorthEast, map, scripting, game)
            0x6B -> move(player, Direction.SouthEast, map, scripting, game)
            0x6C -> move(player, Direction.SouthWest, map, scripting, game)
            0x6D -> move(player, Direction.NorthWest, map, scripting, game)

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
        game: Game?,
    ): Boolean {
        if (game != null) {
            // `ProtocolGame::parseWalk` -> `Game::playerMove` (game.cpp:4282):
            // the queued walk runs through `Creature::onCreatureWalk` and
            // `Game::internalMoveCreature`, which moves the creature on the
            // map, updates the sectors and confirms the step to the client
            // (`sendCreatureMove`, 0x6D).
            game.playerMove(player.id, direction)
            return true
        }

        // Decode-only fallback (no composed world): the direct engine step.
        player.direction = direction
        return MovementEngine.move(player, direction, map, scripting)
    }
}
