package org.opentibia.canary.lua.bindings.core

import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.opentibia.canary.lua.runtime.LuaEnvironment

/**
 * Engine seam for the global `Game` table. Mirrors the primitives used by
 * `GameFunctions::luaGameCreateItem` in
 * src/lua/functions/core/game/game_functions.cpp.
 */
interface GameOperations {
    /** Resolves an item name to its id, returning 0 when unknown. */
    fun itemIdByName(name: String): Int

    /** Creates [count] of the item, returning the created instances (empty on failure). */
    fun createItem(itemId: Int, count: Int): List<Any>
}

/**
 * Registers the Canary global `Game` table (Lua::registerTable(L, "Game")) with its
 * `createItem` method:
 *
 *   Game.createItem(itemId or name[, count[, position]])
 *
 * Only the engine primitive is bound here; the world-mutating work is delegated to
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
    }
}

internal object UnwiredGameOperations : GameOperations {
    override fun itemIdByName(name: String): Int =
        throw NotImplementedError("GameFunctions.itemIdByName seam is not wired")

    override fun createItem(itemId: Int, count: Int): List<Any> =
        throw NotImplementedError("GameFunctions.createItem seam is not wired")
}
