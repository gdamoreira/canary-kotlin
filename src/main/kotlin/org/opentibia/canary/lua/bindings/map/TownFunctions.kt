package org.opentibia.canary.lua.bindings.map

import org.luaj.vm2.LuaValue
import org.opentibia.canary.lua.bindings.core.PositionFunctions
import org.opentibia.canary.lua.bindings.core.luaFunction
import org.opentibia.canary.lua.bindings.core.retrieveUserdata
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.Town

/**
 * Engine seam for the `Town` metatable. Mirrors the `g_game().map.towns` lookups of
 * src/lua/functions/map/town_functions.cpp.
 */
interface TownOperations {
    /** `g_game().map.towns.getTown(id)` (town_functions.cpp:29). */
    fun getTown(id: Int): Town?

    /** `g_game().map.towns.getTown(name)` (town_functions.cpp:31). */
    fun getTown(name: String): Town?
}

/**
 * Registers the Canary `Town` userdata methods:
 *
 *   Town(id or name)
 *   town:getId()
 *   town:getName()
 *   town:getTemplePosition()
 *
 * It also registers the global `Town(id or name)` class function and the `__eq`
 * metamethod mirroring TownFunctions::luaTownCreate and `Lua::luaUserdataCompare`.
 */
object TownFunctions {
    const val TOWN_TYPE = "Town"

    fun install(env: LuaEnvironment, operations: TownOperations = UnwiredTownOperations) {
        val registry = env.userdata

        env.globals.set(TOWN_TYPE, luaFunction { args ->
            val first = args.arg(1)
            val town = when {
                first.isnumber() -> operations.getTown(first.toint())
                first.isstring() -> operations.getTown(first.tojstring())
                else -> null
            } ?: return@luaFunction LuaValue.NIL
            env.userdata.wrap(TOWN_TYPE, town)
        })

        registry.registerMethod(TOWN_TYPE, "__eq", luaFunction { args ->
            val town = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val other = retrieveUserdata(args, 2) ?: return@luaFunction LuaValue.FALSE
            LuaValue.valueOf(town == other)
        })

        registry.registerMethod(TOWN_TYPE, "getId", luaFunction { args ->
            val town = retrieveUserdata(args, 1) as? Town ?: return@luaFunction LuaValue.NIL
            LuaValue.valueOf(town.getID())
        })

        registry.registerMethod(TOWN_TYPE, "getName", luaFunction { args ->
            val town = retrieveUserdata(args, 1) as? Town ?: return@luaFunction LuaValue.NIL
            LuaValue.valueOf(town.getName())
        })

        registry.registerMethod(TOWN_TYPE, "getTemplePosition", luaFunction { args ->
            val town = retrieveUserdata(args, 1) as? Town ?: return@luaFunction LuaValue.NIL
            env.userdata.wrap(PositionFunctions.POSITION_TYPE, town.getTemplePosition())
        })
    }
}

internal object UnwiredTownOperations : TownOperations {
    override fun getTown(id: Int): Town? =
        throw NotImplementedError("TownFunctions.getTown(id) seam is not wired")

    override fun getTown(name: String): Town? =
        throw NotImplementedError("TownFunctions.getTown(name) seam is not wired")
}
