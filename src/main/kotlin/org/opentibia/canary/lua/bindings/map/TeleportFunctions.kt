package org.opentibia.canary.lua.bindings.map

import org.luaj.vm2.LuaValue
import org.opentibia.canary.game.movement.Teleport
import org.opentibia.canary.lua.bindings.core.PositionFunctions
import org.opentibia.canary.lua.bindings.core.luaFunction
import org.opentibia.canary.lua.bindings.core.luaToPosition
import org.opentibia.canary.lua.bindings.core.retrieveUserdata
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.Position

/**
 * Engine seam for the `Teleport` metatable. Mirrors the script environment UID lookup
 * of src/lua/functions/map/teleport_functions.cpp.
 */
interface TeleportOperations {
    /** `ScriptEnvironment::getItemByUID(uid)` (teleport_functions.cpp:29). */
    fun getItemByUid(uid: Int): Teleport?
}

/**
 * Registers the Canary `Teleport` userdata methods:
 *
 *   Teleport(uid)
 *   teleport:getDestination()
 *   teleport:setDestination(position)
 *
 * It also registers the global `Teleport(uid)` class function and the `__eq`
 * metamethod mirroring TeleportFunctions::luaTeleportCreate and
 * `Lua::luaUserdataCompare`.
 *
 * PARITY GAPS (documented, no invented state):
 * - `Teleport(uid)` resolves through [TeleportOperations.getItemByUid]; the port does not
 *   model Canary's script environment UID registry, so the default seam returns null.
 */
object TeleportFunctions {
    const val TELEPORT_TYPE = "Teleport"

    fun install(env: LuaEnvironment, operations: TeleportOperations = UnwiredTeleportOperations) {
        val registry = env.userdata

        env.userdata.registerClass(TELEPORT_TYPE, "Item", luaFunction { args ->
            val teleport = operations.getItemByUid(args.arg(1).optint(0)) ?: return@luaFunction LuaValue.NIL
            env.userdata.wrap(TELEPORT_TYPE, teleport)
        })

        registry.registerMetaMethod(TELEPORT_TYPE, "__eq", luaFunction { args ->
            val teleport = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val other = retrieveUserdata(args, 2) ?: return@luaFunction LuaValue.FALSE
            LuaValue.valueOf(teleport == other)
        })

        registry.registerMethod(TELEPORT_TYPE, "getDestination", luaFunction { args ->
            val teleport = retrieveUserdata(args, 1) as? Teleport ?: return@luaFunction LuaValue.NIL
            env.userdata.wrap(PositionFunctions.POSITION_TYPE, teleport.getDestPos())
        })

        registry.registerMethod(TELEPORT_TYPE, "setDestination", luaFunction { args ->
            val teleport = retrieveUserdata(args, 1) as? Teleport ?: return@luaFunction LuaValue.NIL
            teleport.setDestPos(luaToPosition(args.arg(2)) ?: Position(0, 0, 0))
            LuaValue.TRUE
        })
    }
}

internal object UnwiredTeleportOperations : TeleportOperations {
    override fun getItemByUid(uid: Int): Teleport? =
        throw NotImplementedError("TeleportFunctions.getItemByUid seam is not wired")
}
