package org.opentibia.canary.lua.bindings.library

import org.luaj.vm2.LuaValue
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.ContainerBatchActor
import org.opentibia.canary.lua.runtime.LuaEnvironment
import java.lang.ref.WeakReference
import java.util.IdentityHashMap

/**
 * Port of Canary's `BatchUpdate` (`src/utils/batch_update.hpp:23-48`,
 * `batch_update.cpp:23-63`): the per-actor cache of containers currently being
 * batched. [add] mirrors the duplicate/alive check, [release] mirrors the C++
 * destructor's `endBatchUpdate` cascade.
 *
 * Parity gaps: `Player::beginBatchUpdate`/`endBatchUpdate` are not modeled by the
 * Kotlin player, so the actor-side begin/end calls are skipped; the container-side
 * `Container::beginBatchUpdate`/`endBatchUpdate` calls are applied, and the actor is
 * only handed to `Container::endBatchUpdate` when it implements [ContainerBatchActor].
 */
class BatchUpdate(actor: Any?) {
    private val actorRef: WeakReference<Any> = WeakReference(actor)
    private val cached = mutableListOf<WeakReference<Container>>()
    private val cachedLookup = IdentityHashMap<Container, WeakReference<Container>>()

    /** `BatchUpdate::add` (`batch_update.cpp:41-62`). */
    fun add(container: Container?): Boolean {
        if (container == null) {
            return false
        }

        val existing = cachedLookup[container]
        if (existing?.get() != null) {
            return false
        }
        if (existing != null) {
            cachedLookup.remove(container)
        }

        cachedLookup[container] = WeakReference(container)
        cached.add(WeakReference(container))
        container.beginBatchUpdate()
        return true
    }

    /** `BatchUpdate::~BatchUpdate` (`batch_update.cpp:31-39`). */
    fun release() {
        val actor = actorRef.get() as? ContainerBatchActor
        for (containerRef in cached) {
            containerRef.get()?.endBatchUpdate(actor)
        }
        cached.clear()
        cachedLookup.clear()
    }
}

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

/** [BatchUpdateOperations] over the ported [BatchUpdate] engine class. */
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
