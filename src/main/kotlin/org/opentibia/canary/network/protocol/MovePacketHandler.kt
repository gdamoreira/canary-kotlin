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

            // Auto walk (click to move): 0x64 parseAutoWalk
            0x64 -> {
                val path = parseAutoWalk(payload)
                if (game != null && path != null) {
                    game.playerAutoWalk(player.id, path)
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

            // Spot Turns: `Game::playerTurn` (game.cpp:7187) through the event
            // and callback vetoes, then `Game::internalCreatureTurn`.
            0x6F -> turn(player, Direction.North, game)
            0x70 -> turn(player, Direction.East, game)
            0x71 -> turn(player, Direction.South, game)
            0x72 -> turn(player, Direction.West, game)

            else -> false
        }
    }

    /**
     * `ProtocolGame::parseAutoWalk` (protocolgame.cpp:2449-2470): a direction
     * count followed by that many client direction bytes. The list is stored
     * reversed because `Creature::getNextStep` consumes from the back
     * (creature.cpp:315-324), so the client's first direction walks first.
     */
    private fun parseAutoWalk(payload: ByteBuf): List<Direction>? {
        if (!payload.isReadable) {
            return null
        }

        val numdirs = payload.readUnsignedByte().toInt()
        if (numdirs == 0 || payload.readableBytes() != numdirs) {
            return null
        }

        val path = MutableList<Direction?>(numdirs) { null }
        for (i in 0 until numdirs) {
            val direction = translateAutoWalkDirection(payload.readUnsignedByte().toInt()) ?: return null
            path[numdirs - i - 1] = direction
        }
        return path.filterNotNull()
    }

    /** `ProtocolGame::translateAutoWalkDirection` (protocolgame.cpp:528-546). */
    private fun translateAutoWalkDirection(rawDirection: Int): Direction? = when (rawDirection) {
        1 -> Direction.East
        2 -> Direction.NorthEast
        3 -> Direction.North
        4 -> Direction.NorthWest
        5 -> Direction.West
        6 -> Direction.SouthWest
        7 -> Direction.South
        8 -> Direction.SouthEast
        else -> null
    }

    private fun turn(player: Player, direction: Direction, game: Game?): Boolean {
        if (game != null) {
            // `ProtocolGame::parseTurn` -> `Game::playerTurn` (game.cpp:7187):
            // the event and callback vetoes run, then `internalCreatureTurn`
            // broadcasts the turn to the spectators.
            game.playerTurn(player.id, direction)
            return true
        }

        // Decode-only fallback (no composed world): the local spot turn.
        player.direction = direction
        return true
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
