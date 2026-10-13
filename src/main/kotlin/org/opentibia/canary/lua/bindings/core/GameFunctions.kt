package org.opentibia.canary.lua.bindings.core

import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.Position

/**
 * Engine seam for the global `Game` table. Mirrors the primitives used by
 * `GameFunctions::luaGame*` in src/lua/functions/core/game/game_functions.cpp and, for
 * the timer/time globals, `GlobalFunctions::luaAddEvent`/`luaStopEvent`/`luaGetWorldTime`
 * in src/lua/functions/core/game/global_functions.cpp.
 *
 * Members whose state the Kotlin domain does not model yet carry a documented default
 * (empty list, 0, false or nil) so scripts keep running instead of failing.
 */
interface GameOperations {
    /** Resolves an item name to its id, returning 0 when unknown. */
    fun itemIdByName(name: String): Int

    /** Creates [count] of the item, returning the created instances (empty on failure). */
    fun createItem(itemId: Int, count: Int): List<Any>

    /** Mirrors `Game.getSpectators`; empty when no world spectator index is wired. */
    fun getSpectators(
        position: Position,
        multifloor: Boolean,
        onlyPlayers: Boolean,
        minRangeX: Int,
        maxRangeX: Int,
        minRangeY: Int,
        maxRangeY: Int,
    ): List<Any> = emptyList()

    /** Mirrors `Game.getPlayers`; empty when no online-player index is wired. */
    fun getPlayers(): List<Any> = emptyList()

    /** Mirrors `Game.getGameState` (Canary's default `GAME_STATE_STARTUP` is 0). */
    fun getGameState(): Int = 0

    /** Mirrors `Game.getMonsterCount`; 0 when no monster index is wired. */
    fun getMonsterCount(): Int = 0

    /** Mirrors `Game.getPlayerCount`; 0 when no player index is wired. */
    fun getPlayerCount(): Int = 0

    /** Mirrors `Game.getNpcCount`; 0 when no npc index is wired. */
    fun getNpcCount(): Int = 0

    /** Mirrors `Game.getExperienceForLevel`; 0 when the level formula is not wired. */
    fun getExperienceForLevel(level: Int): Long = 0L

    /** Mirrors `Game.createMonster`; null when the monster catalog/placing is not wired. */
    fun createMonster(name: String, position: Position, extended: Boolean, force: Boolean, master: Any?): Any? = null

    /** Mirrors `Game.createNpc`; null when the npc catalog/placing is not wired. */
    fun createNpc(name: String, position: Position, extended: Boolean, force: Boolean): Any? = null

    /** Mirrors `Game.getOfflinePlayer(name or id)`; null when no persistence lookup is wired. */
    fun getOfflinePlayer(nameOrId: Any): Any? = null

    /** Mirrors `Game.getNormalizedPlayerName`; null when no name service is wired. */
    fun getNormalizedPlayerName(name: String, isNewName: Boolean): String? = null

    /** Mirrors the global `getWorldTime`; 0 when no world-light clock is wired. */
    fun getWorldTime(): Int = 0

    /**
     * Mirrors the global `addEvent(callback, delay, ...)`: returns the scheduled event id,
     * or 0 when scheduling is unavailable (the binding then returns `false`).
     */
    fun addEvent(delay: Int, callback: LuaValue, parameters: List<LuaValue>): Int = 0

    /** Mirrors the global `stopEvent(eventid)`; false when the event is unknown or unsupported. */
    fun stopEvent(eventId: Int): Boolean = false
}

/**
 * Registers the Canary global `Game` table (Lua::registerTable(L, "Game")) with:
 *
 *   Game.createItem(itemId or name[, count[, position]])
 *   Game.getSpectators(position[, multifloor = false[, onlyPlayers = false[, minRangeX = 0[, maxRangeX = 0[, minRangeY = 0[, maxRangeY = 0]]]]]])
 *   Game.getPlayers()
 *   Game.getGameState()
 *   Game.getMonsterCount()
 *   Game.getPlayerCount()
 *   Game.getNpcCount()
 *   Game.getExperienceForLevel(level)
 *   Game.createMonster(monsterName, position[, extended = false[, force = false[, master = nil]]])
 *   Game.createNpc(npcName, position[, extended = false[, force = false]]])
 *   Game.getOfflinePlayer(name or id)
 *   Game.getNormalizedPlayerName(name[, isNewName = false])
 *
 * Canary registers the timer/time helpers as globals (`GlobalFunctions::init`), not as
 * `Game` methods, so they are kept as globals here too, with their exact names:
 *
 *   getWorldTime()
 *   addEvent(callback, delay, ...)
 *   stopEvent(eventid)
 *
 * Only the engine primitives are bound here; the world work is delegated to
 * [GameOperations].
 */
object GameFunctions {
    const val GAME_GLOBAL = "Game"

    fun install(env: LuaEnvironment, operations: GameOperations = UnwiredGameOperations) {
        val game = (env.globals.get(GAME_GLOBAL) as? LuaTable)
            ?: LuaTable().also { env.globals.set(GAME_GLOBAL, it) }

        game.set("createItem", luaFunction { args ->
            val idValue = args.arg(1)
            val itemId = if (idValue.isnumber()) idValue.toint() else operations.itemIdByName(idValue.checkjstring())
            if (itemId == 0) {
                return@luaFunction LuaValue.NIL
            }
            val count = args.arg(2).optint(1)
            wrapItems(env, operations.createItem(itemId, count))
        })

        game.set("getSpectators", luaFunction { args ->
            val position = luaToPosition(args.arg(1)) ?: return@luaFunction LuaTable()
            val spectators = operations.getSpectators(
                position = position,
                multifloor = args.arg(2).optboolean(false),
                onlyPlayers = args.arg(3).optboolean(false),
                minRangeX = args.arg(4).optint(0),
                maxRangeX = args.arg(5).optint(0),
                minRangeY = args.arg(6).optint(0),
                maxRangeY = args.arg(7).optint(0),
            )
            creatureTable(env, spectators)
        })

        game.set("getPlayers", luaFunction { args ->
            val players = operations.getPlayers()
            LuaTable().also { table ->
                players.forEachIndexed { index, player -> table.set(index + 1, env.userdata.wrap("Player", player)) }
            }
        })

        game.set("getGameState", luaFunction { LuaValue.valueOf(operations.getGameState()) })

        game.set("getMonsterCount", luaFunction { LuaValue.valueOf(operations.getMonsterCount()) })

        game.set("getPlayerCount", luaFunction { LuaValue.valueOf(operations.getPlayerCount()) })

        game.set("getNpcCount", luaFunction { LuaValue.valueOf(operations.getNpcCount()) })

        game.set("getExperienceForLevel", luaFunction { args ->
            val level = args.arg(1).toint()
            if (level == 0) {
                return@luaFunction LuaValue.NIL
            }
            LuaValue.valueOf(operations.getExperienceForLevel(level).toDouble())
        })

        game.set("createMonster", luaFunction { args ->
            val name = args.arg(1).checkjstring()
            val position = luaToPosition(args.arg(2)) ?: return@luaFunction LuaValue.NIL
            val monster = operations.createMonster(
                name = name,
                position = position,
                extended = args.arg(3).optboolean(false),
                force = args.arg(4).optboolean(false),
                master = retrieveUserdata(args, 5),
            ) ?: return@luaFunction LuaValue.NIL
            env.userdata.wrap("Monster", monster)
        })

        game.set("createNpc", luaFunction { args ->
            val name = args.arg(1).checkjstring()
            val position = luaToPosition(args.arg(2)) ?: return@luaFunction LuaValue.NIL
            val npc = operations.createNpc(
                name = name,
                position = position,
                extended = args.arg(3).optboolean(false),
                force = args.arg(4).optboolean(false),
            ) ?: return@luaFunction LuaValue.NIL
            env.userdata.wrap("Npc", npc)
        })

        game.set("getOfflinePlayer", luaFunction { args ->
            val argument = args.arg(1)
            val nameOrId: Any = when {
                argument.isnumber() -> argument.toint()
                argument.isstring() -> argument.tojstring()
                else -> return@luaFunction LuaValue.NIL
            }
            val player = operations.getOfflinePlayer(nameOrId) ?: return@luaFunction LuaValue.NIL
            env.userdata.wrap("Player", player)
        })

        game.set("getNormalizedPlayerName", luaFunction { args ->
            val name = args.arg(1).checkjstring()
            val normalized = operations.getNormalizedPlayerName(name, args.arg(2).optboolean(false))
            normalized?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        env.globals.set("getWorldTime", luaFunction { LuaValue.valueOf(operations.getWorldTime()) })

        env.globals.set("addEvent", luaFunction { args ->
            val callback = args.arg(1)
            if (!callback.isfunction()) {
                return@luaFunction LuaValue.FALSE
            }
            val delay = maxOf(100, args.arg(2).optint(0))
            val parameters = (3..args.narg()).map { args.arg(it) }
            val eventId = operations.addEvent(delay, callback, parameters)
            if (eventId == 0) LuaValue.FALSE else LuaValue.valueOf(eventId)
        })

        env.globals.set("stopEvent", luaFunction { args ->
            LuaValue.valueOf(operations.stopEvent(args.arg(1).toint()))
        })
    }

    private fun creatureTable(env: LuaEnvironment, creatures: List<Any>): LuaTable =
        LuaTable().also { table ->
            creatures.forEachIndexed { index, creature ->
                table.set(index + 1, wrapCreatureUserdata(env, creature))
            }
        }
}

internal object UnwiredGameOperations : GameOperations {
    override fun itemIdByName(name: String): Int =
        throw NotImplementedError("GameFunctions.itemIdByName seam is not wired")

    override fun createItem(itemId: Int, count: Int): List<Any> =
        throw NotImplementedError("GameFunctions.createItem seam is not wired")
}
