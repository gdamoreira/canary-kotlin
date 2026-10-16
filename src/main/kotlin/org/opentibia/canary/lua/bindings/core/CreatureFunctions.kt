package org.opentibia.canary.lua.bindings.core

import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.opentibia.canary.combat.condition.Outfit
import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.Position

/**
 * Engine seam for the `Creature` metatable family. Mirrors the primitives in
 * src/lua/functions/creatures/creature_functions.cpp.
 */
interface CreatureOperations {
    fun getId(creature: Any): Long

    /** `Creature::removeIcon(key)` reach of `creature:removeIcon` (creature_functions.cpp:1166-1178). */
    fun removeIcon(creature: Any, key: String): Boolean = false

    /** `Creature::clearIcons()` reach of `creature:clearIcons` (creature_functions.cpp:1180-1192). */
    fun clearIcons(creature: Any): Boolean = false

    /** `Creature::getCurrentOutfit` reach of `getOutfit` (creature_functions.cpp:716-726). */
    fun getCurrentOutfit(creature: Any): Outfit = Outfit()

    /** `Creature::setDefaultOutfit` reach of `setOutfit` (creature_functions.cpp:728-748). */
    fun setDefaultOutfit(creature: Any, outfit: Outfit): Boolean = false
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

    /** Mirrors `Lua::pushOutfit` (src/lua/functions/lua_functions_loader.cpp:722). */
    private fun pushOutfit(outfit: Outfit): LuaTable =
        LuaTable().also { table ->
            table.set("lookType", LuaValue.valueOf(outfit.lookType))
            table.set("lookTypeEx", LuaValue.valueOf(outfit.lookTypeEx))
            table.set("lookHead", LuaValue.valueOf(outfit.lookHead))
            table.set("lookBody", LuaValue.valueOf(outfit.lookBody))
            table.set("lookLegs", LuaValue.valueOf(outfit.lookLegs))
            table.set("lookFeet", LuaValue.valueOf(outfit.lookFeet))
            table.set("lookAddons", LuaValue.valueOf(outfit.lookAddons))
            table.set("lookMount", LuaValue.valueOf(outfit.lookMount))
            table.set("lookMountHead", LuaValue.valueOf(outfit.lookMountHead))
            table.set("lookMountBody", LuaValue.valueOf(outfit.lookMountBody))
            table.set("lookMountLegs", LuaValue.valueOf(outfit.lookMountLegs))
            table.set("lookMountFeet", LuaValue.valueOf(outfit.lookMountFeet))
            table.set("lookFamiliarsType", LuaValue.valueOf(outfit.lookFamiliarsType))
            table.set("lookWing ", LuaValue.valueOf(outfit.lookWing))
            table.set("lookAura", LuaValue.valueOf(outfit.lookAura))
            table.set("lookEffect ", LuaValue.valueOf(outfit.lookEffect))
            table.set("lookShader ", LuaValue.valueOf(outfit.lookShader))
        }

    /** Mirrors `Lua::getOutfit` (src/lua/functions/lua_functions_loader.cpp:439). */
    private fun luaToOutfit(value: LuaValue): Outfit? {
        if (!value.istable()) {
            return null
        }
        return Outfit(
            lookMountFeet = value.get("lookMountFeet").toint(),
            lookMountLegs = value.get("lookMountLegs").toint(),
            lookMountBody = value.get("lookMountBody").toint(),
            lookMountHead = value.get("lookMountHead").toint(),
            lookFamiliarsType = value.get("lookFamiliarsType").toint(),
            lookMount = value.get("lookMount").toint(),
            lookAddons = value.get("lookAddons").toint(),
            lookFeet = value.get("lookFeet").toint(),
            lookLegs = value.get("lookLegs").toint(),
            lookBody = value.get("lookBody").toint(),
            lookHead = value.get("lookHead").toint(),
            lookTypeEx = value.get("lookTypeEx").toint(),
            lookType = value.get("lookType").toint(),
            lookWing = value.get("lookShader ").toint(),
            lookAura = value.get("lookAura").toint(),
            lookEffect = value.get("lookEffect ").toint(),
            lookShader = value.get("lookShader ").toint(),
        )
    }
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

        // `CreatureFunctions::luaCreatureRemoveIcon` (creature_functions.cpp:1166-1178).
        registry.registerMethod(CREATURE_TYPE, "removeIcon", luaFunction { args ->
            val creature = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.FALSE
            LuaValue.valueOf(operations.removeIcon(creature, args.arg(2).tojstring()))
        })

        // `CreatureFunctions::luaCreatureClearIcons` (creature_functions.cpp:1180-1192).
        registry.registerMethod(CREATURE_TYPE, "clearIcons", luaFunction { args ->
            val creature = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.FALSE
            LuaValue.valueOf(operations.clearIcons(creature))
        })

        // `CreatureFunctions::luaCreatureGetOutfit` (creature_functions.cpp:716-726):
        // `Lua::pushOutfit` over `Creature::getCurrentOutfit`.
        registry.registerMethod(CREATURE_TYPE, "getOutfit", luaFunction { args ->
            val creature = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            pushOutfit(operations.getCurrentOutfit(creature))
        })

        // `CreatureFunctions::luaCreatureSetOutfit` (creature_functions.cpp:728-748).
        registry.registerMethod(CREATURE_TYPE, "setOutfit", luaFunction { args ->
            val creature = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val outfit = luaToOutfit(args.arg(2)) ?: return@luaFunction LuaValue.NIL
            operations.setDefaultOutfit(creature, outfit)
            LuaValue.TRUE
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
