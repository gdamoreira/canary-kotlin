package org.opentibia.canary.lua.bindings.core

import org.luaj.vm2.LuaValue
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.Position

/**
 * Engine seam for the `Position` metatable. Mirrors the primitives in
 * src/lua/functions/map/position_functions.cpp.
 */
interface PositionOperations {
    fun getDistance(position: Position, target: Position): Int
    fun sendMagicEffect(position: Position, magicEffect: Int, spectator: Any?): Boolean
    fun removeMagicEffect(position: Position, magicEffect: Int, spectator: Any?): Boolean
}

/**
 * Registers the Canary `Position` userdata methods:
 *
 *   position:getDistance(positionEx)
 *   position:sendMagicEffect(magicEffect[, player])
 *   position:removeMagicEffect(magicEffect[, player])
 *   position:toString()
 */
object PositionFunctions {
    const val POSITION_TYPE = "Position"

    fun install(env: LuaEnvironment, operations: PositionOperations = UnwiredPositionOperations) {
        val registry = env.userdata

        registry.registerMethod(POSITION_TYPE, "getDistance", luaFunction { args ->
            val position = retrieveUserdata(args, 1) as? Position ?: return@luaFunction LuaValue.NIL
            val target = retrieveUserdata(args, 2) as? Position ?: return@luaFunction LuaValue.NIL
            LuaValue.valueOf(operations.getDistance(position, target))
        })

        registry.registerMethod(POSITION_TYPE, "sendMagicEffect", luaFunction { args ->
            val position = retrieveUserdata(args, 1) as? Position ?: return@luaFunction LuaValue.NIL
            val magicEffect = args.arg(2).toint()
            val spectator = retrieveUserdata(args, 3)
            LuaValue.valueOf(operations.sendMagicEffect(position, magicEffect, spectator))
        })

        registry.registerMethod(POSITION_TYPE, "removeMagicEffect", luaFunction { args ->
            val position = retrieveUserdata(args, 1) as? Position ?: return@luaFunction LuaValue.NIL
            val magicEffect = args.arg(2).toint()
            val spectator = retrieveUserdata(args, 3)
            LuaValue.valueOf(operations.removeMagicEffect(position, magicEffect, spectator))
        })

        registry.registerMethod(POSITION_TYPE, "toString", luaFunction { args ->
            val position = retrieveUserdata(args, 1) as? Position ?: return@luaFunction LuaValue.NIL
            LuaValue.valueOf("( ${position.x}, ${position.y}, ${position.z} )")
        })
    }
}

internal object UnwiredPositionOperations : PositionOperations {
    override fun getDistance(position: Position, target: Position): Int =
        throw NotImplementedError("PositionFunctions.getDistance seam is not wired")

    override fun sendMagicEffect(position: Position, magicEffect: Int, spectator: Any?): Boolean =
        throw NotImplementedError("PositionFunctions.sendMagicEffect seam is not wired")

    override fun removeMagicEffect(position: Position, magicEffect: Int, spectator: Any?): Boolean =
        throw NotImplementedError("PositionFunctions.removeMagicEffect seam is not wired")
}
