package org.opentibia.canary.lua.bindings.core

import org.luaj.vm2.LuaValue
import org.opentibia.canary.creatures.Creature
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

    /**
     * `Creature::isMoveLocked()` (src/creatures/creature.hpp:189-191) behind
     * `CreatureFunctions::luaCreatureIsMoveLocked`
     * (src/lua/functions/creatures/creature_functions.cpp:648-657).
     *
     * Domain-backed default: the port's [Creature] owns the flag, so the
     * composed `WorldCreatureOperations` inherits the production behavior
     * without a duplicate implementation (the wave-67 D2 ownership excludes
     * the `lua/runtime` package).
     */
    fun isMoveLocked(creature: Any): Boolean = (creature as? Creature)?.isMoveLocked() ?: false

    /**
     * `Creature::setMoveLocked(bool)` (src/creatures/creature.hpp:192-194)
     * behind `CreatureFunctions::luaCreatureSetMoveLocked`
     * (src/lua/functions/creatures/creature_functions.cpp:659-669).
     */
    fun setMoveLocked(creature: Any, moveLocked: Boolean): Boolean {
        val target = creature as? Creature ?: return false
        target.setMoveLocked(moveLocked)
        return true
    }

    /** `Creature::registerCreatureEvent` (creature.cpp:1674-1693); null without a receiver. */
    fun registerEvent(creature: Any, name: String): Boolean?

    /** `Creature::unregisterCreatureEvent` (creature.cpp:1695-1724); null without a receiver. */
    fun unregisterEvent(creature: Any, name: String): Boolean?
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
 *   creature:isMoveLocked()
 *   creature:setMoveLocked(moveLocked)
 *   creature:registerEvent(name)
 *   creature:unregisterEvent(name)
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

        registry.registerMethod(CREATURE_TYPE, "isMoveLocked", luaFunction { args ->
            val creature = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            LuaValue.valueOf(operations.isMoveLocked(creature))
        })

        registry.registerMethod(CREATURE_TYPE, "setMoveLocked", luaFunction { args ->
            val creature = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val moveLocked = args.arg(2).toboolean()
            LuaValue.valueOf(operations.setMoveLocked(creature, moveLocked))
        })

        registry.registerMethod(CREATURE_TYPE, "registerEvent", luaFunction { args ->
            val creature = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.registerEvent(creature, args.arg(2).tojstring())?.let { LuaValue.valueOf(it) }
                ?: LuaValue.NIL
        })

        registry.registerMethod(CREATURE_TYPE, "unregisterEvent", luaFunction { args ->
            val creature = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.unregisterEvent(creature, args.arg(2).tojstring())?.let { LuaValue.valueOf(it) }
                ?: LuaValue.NIL
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

    override fun registerEvent(creature: Any, name: String): Boolean =
        throw NotImplementedError("CreatureFunctions.registerEvent seam is not wired")

    override fun unregisterEvent(creature: Any, name: String): Boolean =
        throw NotImplementedError("CreatureFunctions.unregisterEvent seam is not wired")
}
