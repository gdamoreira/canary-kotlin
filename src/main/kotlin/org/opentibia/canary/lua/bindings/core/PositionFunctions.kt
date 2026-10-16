package org.opentibia.canary.lua.bindings.core

import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.Varargs
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.Position

/**
 * Engine seam for the `Position` metatable. Mirrors the primitives in
 * src/lua/functions/map/position_functions.cpp.
 *
 * Members whose engine state the Kotlin port does not model yet (pathfinding,
 * line-of-sight tiles, zones, sound and distance-effect broadcasts) carry a documented
 * default (null/false) so scripts keep running instead of failing.
 */
interface PositionOperations {
    fun getDistance(position: Position, target: Position): Int
    fun sendMagicEffect(position: Position, magicEffect: Int, spectator: Any?): Boolean
    fun removeMagicEffect(position: Position, magicEffect: Int, spectator: Any?): Boolean

    /**
     * Mirrors `position:getPathTo(positionEx[, minTargetDist = 0[, maxTargetDist = 1[, fullPathSearch = true[, clearSight = true[, maxSearchDist = 0]]]]])`;
     * null means Canary's `false` result because no pathfinder is wired.
     */
    fun getPathTo(
        position: Position,
        target: Position,
        minTargetDist: Int,
        maxTargetDist: Int,
        fullPathSearch: Boolean,
        clearSight: Boolean,
        maxSearchDist: Int,
    ): List<Int>?

    /** Mirrors `position:isSightClear(positionEx[, sameFloor = true])`. */
    fun isSightClear(position: Position, target: Position, sameFloor: Boolean): Boolean

    /** Mirrors `position:getTile()`; null when no map tile is wired. */
    fun getTile(position: Position): Any?

    /** Mirrors `position:getZones()`; null when the position has no tile. */
    fun getZones(position: Position): List<Any>?

    /** Mirrors `position:sendDistanceEffect(positionEx, distanceEffect[, player])`. */
    fun sendDistanceEffect(
        position: Position,
        target: Position,
        distanceEffect: Int,
        spectator: Any?,
    ): Boolean

    /** Mirrors `position:sendSingleSoundEffect(soundId[, actor])`. */
    fun sendSingleSoundEffect(position: Position, soundId: Int, actor: Any?): Boolean

    /** Mirrors `position:sendDoubleSoundEffect(mainSoundId, secondarySoundId[, actor])`. */
    fun sendDoubleSoundEffect(
        position: Position,
        mainSoundId: Int,
        secondarySoundId: Int,
        actor: Any?,
    ): Boolean
}

/**
 * Registers the Canary `Position` userdata methods:
 *
 *   Position([x = 0[, y = 0[, z = 0[, stackpos = 0]]]])
 *   Position([position])
 *   position:getDistance(positionEx)
 *   position:getPathTo(positionEx[, minTargetDist = 0[, maxTargetDist = 1[, fullPathSearch = true[, clearSight = true[, maxSearchDist = 0]]]]])
 *   position:isSightClear(positionEx[, sameFloor = true])
 *   position:getTile()
 *   position:getZones()
 *   position:sendMagicEffect(magicEffect[, player])
 *   position:removeMagicEffect(magicEffect[, player])
 *   position:sendDistanceEffect(positionEx, distanceEffect[, player])
 *   position:sendSingleSoundEffect(soundId[, actor])
 *   position:sendDoubleSoundEffect(mainSoundId, secondarySoundId[, actor])
 *   position:toString()
 *
 * It also registers the global `Position(...)` class function and the `__add`, `__sub`
 * and `__eq` metamethods mirroring PositionFunctions::luaPositionCreate/
 * luaPositionAdd/luaPositionSub/luaPositionCompare. The Canary `stackpos` argument is
 * accepted but not carried by the Kotlin [Position] model.
 */
object PositionFunctions {
    const val POSITION_TYPE = "Position"

    fun install(env: LuaEnvironment, operations: PositionOperations = UnwiredPositionOperations) {
        val registry = env.userdata

        env.userdata.registerClass(POSITION_TYPE, "", luaFunction { args ->
            // `Position([x = 0[, y = 0[, z = 0[, stackpos = 0]]]])` / `Position([position])`
            if (args.narg() == 0) {
                return@luaFunction pushPosition(env, Position(0, 0, 0))
            }

            val first = args.arg(1)
            if (first.istable()) {
                val position = luaToPosition(first) ?: Position(0, 0, 0)
                return@luaFunction pushPosition(env, position, first.get("stackpos").optint(0))
            }

            val position = Position(first.optint(0), args.arg(2).optint(0), args.arg(3).optint(0))
            pushPosition(env, position, args.arg(4).optint(0))
        })

        registry.registerMetaMethod(POSITION_TYPE, "__add", luaFunction { args ->
            positionArithmetic(args, env, subtract = false)
        })

        registry.registerMetaMethod(POSITION_TYPE, "__sub", luaFunction { args ->
            positionArithmetic(args, env, subtract = true)
        })

        registry.registerMetaMethod(POSITION_TYPE, "__eq", luaFunction { args ->
            val position = luaToPosition(args.arg(1)) ?: return@luaFunction LuaValue.NIL
            val other = luaToPosition(args.arg(2)) ?: return@luaFunction LuaValue.FALSE
            LuaValue.valueOf(position == other)
        })

        registry.registerMethod(POSITION_TYPE, "getDistance", luaFunction { args ->
            val position = luaToPosition(args.arg(1)) ?: return@luaFunction LuaValue.NIL
            val target = luaToPosition(args.arg(2)) ?: return@luaFunction LuaValue.NIL
            LuaValue.valueOf(operations.getDistance(position, target))
        })

        registry.registerMethod(POSITION_TYPE, "getPathTo", luaFunction { args ->
            val position = luaToPosition(args.arg(1)) ?: return@luaFunction LuaValue.NIL
            val target = luaToPosition(args.arg(2)) ?: return@luaFunction LuaValue.NIL
            val directions = operations.getPathTo(
                position = position,
                target = target,
                minTargetDist = args.arg(3).optint(0),
                maxTargetDist = args.arg(4).optint(1),
                fullPathSearch = args.arg(5).optboolean(true),
                clearSight = args.arg(6).optboolean(true),
                maxSearchDist = args.arg(7).optint(0),
            ) ?: return@luaFunction LuaValue.FALSE
            LuaTable().also { table ->
                directions.forEachIndexed { index, direction -> table.set(index + 1, LuaValue.valueOf(direction)) }
            }
        })

        registry.registerMethod(POSITION_TYPE, "isSightClear", luaFunction { args ->
            val position = luaToPosition(args.arg(1)) ?: return@luaFunction LuaValue.NIL
            val target = luaToPosition(args.arg(2)) ?: return@luaFunction LuaValue.NIL
            LuaValue.valueOf(operations.isSightClear(position, target, args.arg(3).optboolean(true)))
        })

        registry.registerMethod(POSITION_TYPE, "getTile", luaFunction { args ->
            val position = luaToPosition(args.arg(1)) ?: return@luaFunction LuaValue.NIL
            val tile = operations.getTile(position) ?: return@luaFunction LuaValue.NIL
            env.userdata.wrap(TileFunctions.TILE_TYPE, tile)
        })

        registry.registerMethod(POSITION_TYPE, "getZones", luaFunction { args ->
            val position = luaToPosition(args.arg(1)) ?: return@luaFunction LuaValue.NIL
            val zones = operations.getZones(position) ?: return@luaFunction LuaValue.NIL
            LuaTable().also { table ->
                zones.forEachIndexed { index, zone -> table.set(index + 1, env.userdata.wrap("Zone", zone)) }
            }
        })

        registry.registerMethod(POSITION_TYPE, "sendMagicEffect", luaFunction { args ->
            val position = luaToPosition(args.arg(1)) ?: return@luaFunction LuaValue.NIL
            val magicEffect = args.arg(2).toint()
            val spectator = retrieveUserdata(args, 3)
            LuaValue.valueOf(operations.sendMagicEffect(position, magicEffect, spectator))
        })

        registry.registerMethod(POSITION_TYPE, "removeMagicEffect", luaFunction { args ->
            val position = luaToPosition(args.arg(1)) ?: return@luaFunction LuaValue.NIL
            val magicEffect = args.arg(2).toint()
            val spectator = retrieveUserdata(args, 3)
            LuaValue.valueOf(operations.removeMagicEffect(position, magicEffect, spectator))
        })

        registry.registerMethod(POSITION_TYPE, "sendDistanceEffect", luaFunction { args ->
            val position = luaToPosition(args.arg(1)) ?: return@luaFunction LuaValue.NIL
            val target = luaToPosition(args.arg(2)) ?: return@luaFunction LuaValue.NIL
            val distanceEffect = args.arg(3).toint()
            LuaValue.valueOf(operations.sendDistanceEffect(position, target, distanceEffect, retrieveUserdata(args, 4)))
        })

        registry.registerMethod(POSITION_TYPE, "sendSingleSoundEffect", luaFunction { args ->
            val position = luaToPosition(args.arg(1)) ?: return@luaFunction LuaValue.NIL
            val soundId = args.arg(2).toint()
            LuaValue.valueOf(operations.sendSingleSoundEffect(position, soundId, retrieveUserdata(args, 3)))
        })

        registry.registerMethod(POSITION_TYPE, "sendDoubleSoundEffect", luaFunction { args ->
            val position = luaToPosition(args.arg(1)) ?: return@luaFunction LuaValue.NIL
            val mainSoundId = args.arg(2).toint()
            val secondarySoundId = args.arg(3).toint()
            LuaValue.valueOf(
                operations.sendDoubleSoundEffect(position, mainSoundId, secondarySoundId, retrieveUserdata(args, 4)),
            )
        })

        registry.registerMethod(POSITION_TYPE, "toString", luaFunction { args ->
            val position = luaToPosition(args.arg(1)) ?: return@luaFunction LuaValue.NIL
            LuaValue.valueOf("( ${position.x}, ${position.y}, ${position.z} )")
        })
    }

    /**
     * `PositionFunctions::luaPositionAdd`/`luaPositionSub`
     * (position_functions.cpp:75-103): both operands read as positions and the
     * result carries the first operand's `stackpos`, or the second's when the
     * first is `0`.
     */
    private fun positionArithmetic(args: Varargs, env: LuaEnvironment, subtract: Boolean): LuaValue {
        val first = args.arg(1)
        val position = luaToPosition(first) ?: return LuaValue.NIL

        var stackpos = first.get("stackpos").optint(0)
        val other = if (stackpos == 0) {
            val second = args.arg(2)
            val value = luaToPosition(second) ?: return LuaValue.NIL
            stackpos = second.get("stackpos").optint(0)
            value
        } else {
            luaToPosition(args.arg(2)) ?: return LuaValue.NIL
        }

        val result = if (subtract) {
            Position(position.x - other.x, position.y - other.y, position.z - other.z)
        } else {
            Position(position.x + other.x, position.y + other.y, position.z + other.z)
        }
        return pushPosition(env, result, stackpos)
    }
}

internal object UnwiredPositionOperations : PositionOperations {
    override fun getDistance(position: Position, target: Position): Int =
        throw NotImplementedError("PositionFunctions.getDistance seam is not wired")

    override fun sendMagicEffect(position: Position, magicEffect: Int, spectator: Any?): Boolean =
        throw NotImplementedError("PositionFunctions.sendMagicEffect seam is not wired")

    override fun removeMagicEffect(position: Position, magicEffect: Int, spectator: Any?): Boolean =
        throw NotImplementedError("PositionFunctions.removeMagicEffect seam is not wired")

    override fun getPathTo(
        position: Position,
        target: Position,
        minTargetDist: Int,
        maxTargetDist: Int,
        fullPathSearch: Boolean,
        clearSight: Boolean,
        maxSearchDist: Int,
    ): List<Int>? = throw NotImplementedError("PositionFunctions.getPathTo seam is not wired")

    override fun isSightClear(position: Position, target: Position, sameFloor: Boolean): Boolean =
        throw NotImplementedError("PositionFunctions.isSightClear seam is not wired")

    override fun getTile(position: Position): Any? =
        throw NotImplementedError("PositionFunctions.getTile seam is not wired")

    override fun getZones(position: Position): List<Any>? =
        throw NotImplementedError("PositionFunctions.getZones seam is not wired")

    override fun sendDistanceEffect(
        position: Position,
        target: Position,
        distanceEffect: Int,
        spectator: Any?,
    ): Boolean = throw NotImplementedError("PositionFunctions.sendDistanceEffect seam is not wired")

    override fun sendSingleSoundEffect(position: Position, soundId: Int, actor: Any?): Boolean =
        throw NotImplementedError("PositionFunctions.sendSingleSoundEffect seam is not wired")

    override fun sendDoubleSoundEffect(
        position: Position,
        mainSoundId: Int,
        secondarySoundId: Int,
        actor: Any?,
    ): Boolean = throw NotImplementedError("PositionFunctions.sendDoubleSoundEffect seam is not wired")
}
