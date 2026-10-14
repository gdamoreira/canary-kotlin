package org.opentibia.canary.lua.bindings.core

import org.luaj.vm2.LuaValue
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.Position

/**
 * Engine seam for the `Creature` metatable family. Mirrors the primitives in
 * src/lua/functions/creatures/creature_functions.cpp.
 */
interface CreatureOperations {
    fun getId(creature: Any): Long
    fun getName(creature: Any): String
    fun getHealth(creature: Any): Int
    fun getMaxHealth(creature: Any): Int
    fun setHealth(creature: Any, health: Int): Boolean
    fun addHealth(creature: Any, healthChange: Int, combatType: Int): Boolean
    fun getPosition(creature: Any): Position
    fun getDirection(creature: Any): Int
    fun setDirection(creature: Any, direction: Int): Boolean
}

/**
 * Registers the Canary `Creature` userdata methods:
 *
 *   creature:getId()
 *   creature:getName()
 *   creature:getHealth()
 *   creature:getMaxHealth()
 *   creature:setHealth(health)
 *   creature:addHealth(healthChange, combatType)
 *   creature:getPosition()
 *   creature:getDirection()
 *   creature:setDirection(direction)
 *
 * Canary shares the `Creature` class with `Player`, `Monster` and `Npc` through
 * `Lua::registerSharedClass(..., "Creature", ...)`; the derived classes inherit these
 * methods through the class-table `__index` chain, so they are registered on
 * `Creature` only.
 */
object CreatureFunctions {
    const val CREATURE_TYPE = "Creature"

    /** Classes sharing `Lua::luaUserdataCompare` (`creature_functions.cpp:23-26`). */
    val CREATURE_TYPES = listOf(CREATURE_TYPE, "Player", "Monster", "Npc")

    fun install(env: LuaEnvironment, operations: CreatureOperations = UnwiredCreatureOperations) {
        val registry = env.userdata
        registry.registerClass(CREATURE_TYPE)

        CREATURE_TYPES.forEach { registry.registerMetaMethod(it, "__eq", luaUserdataCompare) }

        registry.registerMethod(CREATURE_TYPE, "getId", luaFunction { args ->
            val creature = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            LuaValue.valueOf(operations.getId(creature).toDouble())
        })

        registry.registerMethod(CREATURE_TYPE, "getName", luaFunction { args ->
            val creature = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            LuaValue.valueOf(operations.getName(creature))
        })

        registry.registerMethod(CREATURE_TYPE, "getHealth", luaFunction { args ->
            val creature = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            LuaValue.valueOf(operations.getHealth(creature))
        })

        registry.registerMethod(CREATURE_TYPE, "getMaxHealth", luaFunction { args ->
            val creature = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            LuaValue.valueOf(operations.getMaxHealth(creature))
        })

        registry.registerMethod(CREATURE_TYPE, "setHealth", luaFunction { args ->
            val creature = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val health = args.arg(2).toint()
            LuaValue.valueOf(operations.setHealth(creature, health))
        })

        registry.registerMethod(CREATURE_TYPE, "addHealth", luaFunction { args ->
            val creature = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val healthChange = args.arg(2).toint()
            val combatType = args.arg(3).optint(0)
            LuaValue.valueOf(operations.addHealth(creature, healthChange, combatType))
        })

        registry.registerMethod(CREATURE_TYPE, "getPosition", luaFunction { args ->
            val creature = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            env.userdata.wrap("Position", operations.getPosition(creature))
        })

        registry.registerMethod(CREATURE_TYPE, "getDirection", luaFunction { args ->
            val creature = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            LuaValue.valueOf(operations.getDirection(creature))
        })

        registry.registerMethod(CREATURE_TYPE, "setDirection", luaFunction { args ->
            val creature = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val direction = args.arg(2).toint()
            LuaValue.valueOf(operations.setDirection(creature, direction))
        })
    }
}

internal object UnwiredCreatureOperations : CreatureOperations {
    override fun getId(creature: Any): Long =
        throw NotImplementedError("CreatureFunctions.getId seam is not wired")

    override fun getName(creature: Any): String =
        throw NotImplementedError("CreatureFunctions.getName seam is not wired")

    override fun getHealth(creature: Any): Int =
        throw NotImplementedError("CreatureFunctions.getHealth seam is not wired")

    override fun getMaxHealth(creature: Any): Int =
        throw NotImplementedError("CreatureFunctions.getMaxHealth seam is not wired")

    override fun setHealth(creature: Any, health: Int): Boolean =
        throw NotImplementedError("CreatureFunctions.setHealth seam is not wired")

    override fun addHealth(creature: Any, healthChange: Int, combatType: Int): Boolean =
        throw NotImplementedError("CreatureFunctions.addHealth seam is not wired")

    override fun getPosition(creature: Any): Position =
        throw NotImplementedError("CreatureFunctions.getPosition seam is not wired")

    override fun getDirection(creature: Any): Int =
        throw NotImplementedError("CreatureFunctions.getDirection seam is not wired")

    override fun setDirection(creature: Any, direction: Int): Boolean =
        throw NotImplementedError("CreatureFunctions.setDirection seam is not wired")
}
