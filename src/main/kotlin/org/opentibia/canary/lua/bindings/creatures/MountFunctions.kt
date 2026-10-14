package org.opentibia.canary.lua.bindings.creatures

import org.luaj.vm2.LuaValue
import org.opentibia.canary.lua.bindings.core.luaFunction
import org.opentibia.canary.lua.bindings.core.retrieveUserdata
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.systems.mount.Mount
import org.opentibia.canary.systems.mount.Mounts

/**
 * Engine seam for the `Mount` metatable. Mirrors the primitives in
 * src/lua/functions/creatures/player/mount_functions.cpp.
 *
 * `create` receives the already-resolved argument (number or string); nullable
 * results mirror the `lua_pushnil` guards.
 */
interface MountOperations {
    /** Mirrors `Mounts::getMountByID`/`getMountByName` behind `Mount(id or name)`. */
    fun create(idOrName: Any): Any?

    fun getName(mount: Any): String?

    fun getId(mount: Any): Int?

    fun getClientId(mount: Any): Int?

    fun getSpeed(mount: Any): Int?
}

/**
 * Registers the Canary `Mount` class and methods:
 *
 *   Mount(id or name)
 *   mount:getName()
 *   mount:getId()
 *   mount:getClientId()
 *   mount:getSpeed()
 *
 * plus `__eq` = `Lua::luaUserdataCompare`, exactly like
 * `MountFunctions::init` (mount_functions.cpp:14-22).
 */
object MountFunctions {
    const val MOUNT_TYPE = "Mount"

    fun install(env: LuaEnvironment, operations: MountOperations = WorldMountOperations()) {
        val registry = env.userdata

        registry.registerClass(MOUNT_TYPE, "", luaFunction { args ->
            val argument = args.arg(1)
            val mount = when {
                argument.isnumber() -> operations.create(argument.toint())
                argument.isstring() -> operations.create(argument.tojstring())
                else -> null
            }
            mount?.let { registry.wrap(MOUNT_TYPE, it) } ?: LuaValue.NIL
        })

        registry.registerMethod(MOUNT_TYPE, "getName", luaFunction { args ->
            val mount = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            luaResult(operations.getName(mount))
        })

        registry.registerMethod(MOUNT_TYPE, "getId", luaFunction { args ->
            val mount = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            luaResult(operations.getId(mount))
        })

        registry.registerMethod(MOUNT_TYPE, "getClientId", luaFunction { args ->
            val mount = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            luaResult(operations.getClientId(mount))
        })

        registry.registerMethod(MOUNT_TYPE, "getSpeed", luaFunction { args ->
            val mount = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            luaResult(operations.getSpeed(mount))
        })

        registerUserdataEquality(env, MOUNT_TYPE)
    }
}

/**
 * Real, non-no-op [MountOperations] backed by the Kotlin
 * [org.opentibia.canary.systems.mount.Mounts] registry, the dependency-injected
 * replacement for `g_game().mounts` (mount_functions.cpp:28-40). Without a wired
 * registry every lookup misses and pushes `nil`.
 */
class WorldMountOperations(
    private val mounts: Mounts? = null,
) : MountOperations {
    override fun create(idOrName: Any): Any? = when (idOrName) {
        is Number -> mounts?.getMountByID(idOrName.toInt())
        is String -> mounts?.getMountByName(idOrName)
        else -> null
    }

    override fun getName(mount: Any): String? = (mount as? Mount)?.name

    override fun getId(mount: Any): Int? = (mount as? Mount)?.id

    override fun getClientId(mount: Any): Int? = (mount as? Mount)?.clientId

    override fun getSpeed(mount: Any): Int? = (mount as? Mount)?.speed
}
