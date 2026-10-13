package org.opentibia.canary.lua.bindings.library

import org.luaj.vm2.LuaValue
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.items.Container
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.utils.BatchUpdate

/**
 * Engine seam behind the `BatchUpdate` userdata class. Mirrors
 * `BatchUpdateFunctions::luaBatchUpdateCreate`/`luaBatchUpdateAdd`
 * (src/lua/functions/core/game/batch_update_functions.cpp:26-57).
 *
 * A `null` return is the C++ not-found branch (`lua_pushnil`/`false`).
 */
interface BatchUpdateOperations {
    /** `BatchUpdate(playerActor)`; null when the argument is not a player. */
    fun create(player: Any?): Any?

    /** `BatchUpdate:add(container)`; null when either userdata is missing. */
    fun add(batchUpdate: Any, container: Any): Boolean?

    fun reportError(message: String) {}
}

/**
 * [BatchUpdateOperations] over the ported [BatchUpdate] engine class
 * (src/utils/batch_update.cpp). `Player` provides the
 * `Player::beginBatchUpdate`/`endBatchUpdate` seam.
 */
class WorldBatchUpdateOperations : BatchUpdateOperations {
    override fun create(player: Any?): Any? =
        (player as? Player)?.let { BatchUpdate(it) }

    override fun add(batchUpdate: Any, container: Any): Boolean? {
        val update = batchUpdate as? BatchUpdate ?: return null
        val target = container as? Container ?: return null
        return update.add(target)
    }
}

/**
 * Registers the `BatchUpdate` userdata class with every registration of
 * `BatchUpdateFunctions::init` (batch_update_functions.cpp:16-24):
 *
 *   BatchUpdate(playerActor)
 *   BatchUpdate:add(container)
 *   BatchUpdate:delete()
 *
 * `delete` is Canary's `Lua::luaSharedPtrGarbageCollection` no-op aside from
 * releasing the shared userdata; the port releases the batch state.
 */
object BatchUpdateFunctions {
    const val BATCH_UPDATE_TYPE = "BatchUpdate"

    fun install(env: LuaEnvironment, operations: BatchUpdateOperations = WorldBatchUpdateOperations()) {
        env.globals.set(BATCH_UPDATE_TYPE, libraryFunction { args ->
            val batchUpdate = operations.create(args.arg(1).touserdata())
            if (batchUpdate == null) {
                operations.reportError("Player not found")
                LuaValue.NIL
            } else {
                env.userdata.wrap(BATCH_UPDATE_TYPE, batchUpdate)
            }
        })

        env.userdata.registerMethod(BATCH_UPDATE_TYPE, "add", libraryFunction { args ->
            val batchUpdate = args.arg(1).touserdata() ?: return@libraryFunction LuaValue.FALSE
            val container = args.arg(2).touserdata() ?: return@libraryFunction LuaValue.FALSE
            val result = operations.add(batchUpdate, container)
            LuaValue.valueOf(result ?: false)
        })

        env.userdata.registerMethod(BATCH_UPDATE_TYPE, "delete", libraryFunction { args ->
            (args.arg(1).touserdata() as? BatchUpdate)?.release()
            LuaValue.NIL
        })
    }
}
