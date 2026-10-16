package org.opentibia.canary.lua.bindings.core

import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.opentibia.canary.combat.condition.Outfit
import org.opentibia.canary.combat.condition.Condition
import org.opentibia.canary.combat.condition.LightInfo
import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.CreatureIcon
import org.opentibia.canary.creatures.CreatureIconCategory_t
import org.opentibia.canary.creatures.CreatureIconModifications_t
import org.opentibia.canary.creatures.CreatureIconQuests_t
import org.opentibia.canary.creatures.Skulls_t
import org.opentibia.canary.creatures.players.CountBlock
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

    /** `g_game().internalTeleport` reach of `creature:teleportTo` (creature_functions.cpp:868-890). */
    fun teleportTo(creature: Any, position: org.opentibia.canary.map.Position, pushMovement: Boolean): Boolean = false

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

    /** `Creature::isRemoved()` reach of `creature:isRemoved` (creature_functions.cpp:175-186). */
    fun isRemoved(creature: Any): Boolean? = (creature as? Creature)?.isRemoved()

    /** `Creature::isInGhostMode()` reach of `creature:isInGhostMode` (creature_functions.cpp:192-203). */
    fun isInGhostMode(creature: Any): Boolean? = (creature as? Creature)?.isInGhostMode()

    /** `Creature::getDamageMap()` reach of `creature:getDamageMap` (creature_functions.cpp:947-963). */
    fun getDamageMap(creature: Any): Map<Long, CountBlock>? = (creature as? Creature)?.getDamageMap()

    /** `Creature::getSummons()` reach of `creature:getSummons` (creature_functions.cpp:965-983). */
    fun getSummons(creature: Any): List<Any>? = (creature as? Creature)?.getSummons()

    /** `Creature::setMaxHealth` reach of `creature:setMaxHealth` (creature_functions.cpp:615-633). */
    fun setMaxHealth(creature: Any, maxHealth: Int): Boolean {
        val target = creature as? Creature ?: return false
        target.maxHealth = maxHealth
        if (target.health > maxHealth) {
            target.health = maxHealth
        }
        target.addCreatureHealth(target)
        return true
    }

    /** `Creature::setHiddenHealth` reach of `creature:setHiddenHealth` (creature_functions.cpp:635-646). */
    fun setHiddenHealth(creature: Any, hidden: Boolean): Boolean {
        val target = creature as? Creature ?: return false
        target.setHiddenHealth(hidden)
        target.addCreatureHealth(target)
        return true
    }

    /** `Creature::setDropLoot` reach of `creature:setDropLoot` (creature_functions.cpp:478-489). */
    fun setDropLoot(creature: Any, drop: Boolean): Boolean {
        val target = creature as? Creature ?: return false
        target.setDropLoot(drop)
        return true
    }

    /** `Creature::setSkillLoss` reach of `creature:setSkillLoss` (creature_functions.cpp:491-502). */
    fun setSkillLoss(creature: Any, skillLoss: Boolean): Boolean {
        val target = creature as? Creature ?: return false
        target.setSkillLoss(skillLoss)
        return true
    }

    /** `Creature::isDirectionLocked()` reach of `creature:isDirectionLocked` (creature_functions.cpp:671-680). */
    fun isDirectionLocked(creature: Any): Boolean? = (creature as? Creature)?.isDirectionLocked()

    /** `Creature::setDirectionLocked` reach of `creature:setDirectionLocked` (creature_functions.cpp:682-693). */
    fun setDirectionLocked(creature: Any, locked: Boolean): Boolean {
        val target = creature as? Creature ?: return false
        target.setDirectionLocked(locked)
        return true
    }

    /** `Creature::getSkull()` reach of `creature:getSkull` (creature_functions.cpp:700-712). */
    fun getSkull(creature: Any): Int? = (creature as? Creature)?.getSkull()?.value

    /** `Creature::setSkull` reach of `creature:setSkull` (creature_functions.cpp:714-726). */
    fun setSkull(creature: Any, skull: Int): Boolean {
        val target = creature as? Creature ?: return false
        target.setSkull(Skulls_t.entries.firstOrNull { it.value == skull } ?: return false)
        return true
    }

    /** `Creature::getCreatureLight()` reach of `creature:getLight` (creature_functions.cpp:395-407). */
    fun getLight(creature: Any): Pair<Int, Int>? =
        (creature as? Creature)?.getCreatureLight()?.let { it.level to it.color }

    /** `Creature::setCreatureLight` reach of `creature:setLight` (creature_functions.cpp:409-427). */
    fun setLight(creature: Any, color: Int, level: Int): Boolean {
        val target = creature as? Creature ?: return false
        target.setCreatureLight(LightInfo(level = level, color = color))
        return true
    }

    /** `Creature::setFollowCreature` reach of `creature:setFollowCreature` (creature_functions.cpp:337-350). */
    fun setFollowCreature(creature: Any, followed: Any?): Boolean? =
        (creature as? Creature)?.let { it.setFollowCreature(followed as? Creature) }

    /** `g_game().changeSpeed` reach of `creature:changeSpeed` (creature_functions.cpp:463-476); wired by the world. */
    fun changeSpeed(creature: Any, delta: Int): Boolean = false

    /** `Creature::isImmune(conditionType)` reach of `creature:isImmune` (creature_functions.cpp:820-839); wired by the world. */
    fun isImmune(creature: Any, conditionType: Int): Boolean? = null

    /** `g_game().changeLight` reach behind `creature:setLight`; wired by the world. */
    fun changeLight(creature: Any) {
        // No behaviour without a composed world.
    }

    /** `Creature::setIcon(key, icon)` reach of `creature:setIcon` (creature_functions.cpp:1099-1122). */
    fun setIcon(creature: Any, key: String, icon: CreatureIcon): Boolean {
        val target = creature as? Creature ?: return false
        target.setIcon(key, icon)
        return true
    }

    /** `Creature::getIcon(key)` reach of `creature:getIcon` (creature_functions.cpp:1145-1164). */
    fun getIcon(creature: Any, key: String): CreatureIcon? = (creature as? Creature)?.getIcon(key)

    /** `Creature::getIcons()` reach of `creature:getIcons` (creature_functions.cpp:1124-1143). */
    fun getIcons(creature: Any): List<CreatureIcon>? = (creature as? Creature)?.getIcons()

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

    /** `Lua::getPosition` (src/lua/functions/lua_functions_loader.cpp:392-419). */
    private fun luaPosition(value: LuaValue): org.opentibia.canary.map.Position? {
        if (!value.istable()) {
            return null
        }
        return org.opentibia.canary.map.Position(
            value.get("x").toint(),
            value.get("y").toint(),
            value.get("z").toint(),
        )
    }

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

        // `CreatureFunctions::luaCreatureTeleportTo` (creature_functions.cpp:868-890).
        registry.registerMethod(CREATURE_TYPE, "teleportTo", luaFunction { args ->
            val creature = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.FALSE
            val position = luaPosition(args.arg(2)) ?: return@luaFunction LuaValue.FALSE
            LuaValue.valueOf(operations.teleportTo(creature, position, args.arg(3).optboolean(false)))
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
            pushPosition(env, operations.getPosition(creature))
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

        registry.registerMethod(CREATURE_TYPE, "isRemoved", luaFunction { args ->
            val creature = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.isRemoved(creature)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        registry.registerMethod(CREATURE_TYPE, "isInGhostMode", luaFunction { args ->
            val creature = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.isInGhostMode(creature)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        registry.registerMethod(CREATURE_TYPE, "getDamageMap", luaFunction { args ->
            val creature = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val damageMap = operations.getDamageMap(creature) ?: return@luaFunction LuaValue.NIL
            LuaTable().also { table ->
                for ((attackerId, block) in damageMap) {
                    val entry = LuaTable()
                    entry.set("total", LuaValue.valueOf(block.total))
                    entry.set("ticks", LuaValue.valueOf(block.ticks.toDouble()))
                    table.set(LuaValue.valueOf(attackerId.toDouble()), entry)
                }
            }
        })

        registry.registerMethod(CREATURE_TYPE, "getSummons", luaFunction { args ->
            val creature = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val summons = operations.getSummons(creature) ?: return@luaFunction LuaValue.NIL
            LuaTable().also { table ->
                summons.forEachIndexed { index, summon ->
                    table.set(index + 1, env.userdata.wrap(CREATURE_TYPE, summon))
                }
            }
        })

        registry.registerMethod(CREATURE_TYPE, "setMaxHealth", luaFunction { args ->
            val creature = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            LuaValue.valueOf(operations.setMaxHealth(creature, args.arg(2).toint()))
        })

        registry.registerMethod(CREATURE_TYPE, "setHiddenHealth", luaFunction { args ->
            val creature = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            LuaValue.valueOf(operations.setHiddenHealth(creature, args.arg(2).toboolean()))
        })

        registry.registerMethod(CREATURE_TYPE, "isHealthHidden", luaFunction { args ->
            val creature = retrieveUserdata(args, 1) as? Creature
            creature?.let { LuaValue.valueOf(it.isHealthHidden()) } ?: LuaValue.NIL
        })

        registry.registerMethod(CREATURE_TYPE, "setDropLoot", luaFunction { args ->
            val creature = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            LuaValue.valueOf(operations.setDropLoot(creature, args.arg(2).toboolean()))
        })

        registry.registerMethod(CREATURE_TYPE, "setSkillLoss", luaFunction { args ->
            val creature = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            LuaValue.valueOf(operations.setSkillLoss(creature, args.arg(2).toboolean()))
        })

        registry.registerMethod(CREATURE_TYPE, "isDirectionLocked", luaFunction { args ->
            val creature = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.isDirectionLocked(creature)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        registry.registerMethod(CREATURE_TYPE, "setDirectionLocked", luaFunction { args ->
            val creature = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            LuaValue.valueOf(operations.setDirectionLocked(creature, args.arg(2).toboolean()))
        })

        registry.registerMethod(CREATURE_TYPE, "getSkull", luaFunction { args ->
            val creature = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.getSkull(creature)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        registry.registerMethod(CREATURE_TYPE, "setSkull", luaFunction { args ->
            val creature = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            LuaValue.valueOf(operations.setSkull(creature, args.arg(2).toint()))
        })

        registry.registerMethod(CREATURE_TYPE, "getLight", luaFunction { args ->
            val creature = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val light = operations.getLight(creature) ?: return@luaFunction LuaValue.NIL
            LuaValue.varargsOf(LuaValue.valueOf(light.first), LuaValue.valueOf(light.second))
        })

        registry.registerMethod(CREATURE_TYPE, "setLight", luaFunction { args ->
            val creature = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val color = args.arg(2).toint()
            val level = args.arg(3).toint()
            val applied = operations.setLight(creature, color, level)
            if (applied) {
                operations.changeLight(creature)
            }
            LuaValue.valueOf(applied)
        })

        registry.registerMethod(CREATURE_TYPE, "setFollowCreature", luaFunction { args ->
            val creature = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val followed = retrieveUserdata(args, 2)
            operations.setFollowCreature(creature, followed)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        registry.registerMethod(CREATURE_TYPE, "changeSpeed", luaFunction { args ->
            val creature = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            LuaValue.valueOf(operations.changeSpeed(creature, args.arg(2).toint()))
        })

        registry.registerMethod(CREATURE_TYPE, "isImmune", luaFunction { args ->
            val creature = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val condition = retrieveUserdata(args, 2)
            val conditionType = when {
                condition is Condition -> condition.type.value
                args.arg(2).isnumber() -> args.arg(2).toint()
                else -> return@luaFunction LuaValue.NIL
            }
            operations.isImmune(creature, conditionType)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        registry.registerMethod(CREATURE_TYPE, "setIcon", luaFunction { args ->
            val creature = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val key = args.arg(2).tojstring()
            val category = args.arg(3).toint()
            val iconValue = args.arg(4).toint()
            val count = args.arg(5).toint()
            val icon = if (category == CreatureIconCategory_t.Modifications.value) {
                CreatureIcon(CreatureIconModifications_t.entries.firstOrNull { it.value == iconValue } ?: CreatureIconModifications_t.None, count)
            } else {
                CreatureIcon(CreatureIconQuests_t.entries.firstOrNull { it.value == iconValue } ?: CreatureIconQuests_t.None, count)
            }
            LuaValue.valueOf(operations.setIcon(creature, key, icon))
        })

        registry.registerMethod(CREATURE_TYPE, "getIcon", luaFunction { args ->
            val creature = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val icon = operations.getIcon(creature, args.arg(2).tojstring()) ?: return@luaFunction LuaValue.NIL
            if (!icon.isSet()) {
                return@luaFunction LuaValue.NIL
            }
            LuaTable().also { table ->
                table.set("category", LuaValue.valueOf(icon.category.value))
                table.set("icon", LuaValue.valueOf(icon.serialize()))
                table.set("count", LuaValue.valueOf(icon.count))
            }
        })

        registry.registerMethod(CREATURE_TYPE, "getIcons", luaFunction { args ->
            val creature = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val icons = operations.getIcons(creature) ?: return@luaFunction LuaValue.NIL
            LuaTable().also { table ->
                for (icon in icons) {
                    val entry = LuaTable()
                    entry.set("category", LuaValue.valueOf(icon.category.value))
                    entry.set("icon", LuaValue.valueOf(icon.serialize()))
                    entry.set("count", LuaValue.valueOf(icon.count))
                    table.set(icon.category.value, entry)
                }
            }
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
