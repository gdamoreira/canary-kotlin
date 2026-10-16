package org.opentibia.canary.lua.bindings.library

import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.Varargs
import org.opentibia.canary.combat.CombatOrigin
import org.opentibia.canary.combat.condition.Condition
import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.items.INDEX_WHEREEVER
import org.opentibia.canary.lua.bindings.core.luaToPosition
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.Position
import kotlin.math.floor
import org.opentibia.canary.lua.bindings.core.pushPosition

/**
 * Engine seam behind the global functions of `GlobalFunctions::init`
 * (src/lua/functions/core/game/global_functions.cpp:29-67).
 *
 * The pure helpers (`systemTime`, `createTable`, `isType`, `rawgetmetatable`,
 * `getFormattedTimeRemaining`, `debugPrint`, `reportError`) are implemented by
 * [GlobalFunctions] itself; every world-touching function delegates here with the
 * documented default of the unwired port.
 *
 * `addEvent`, `stopEvent` and `getWorldTime` are already registered by
 * [org.opentibia.canary.lua.bindings.core.GameFunctions] (Wave 39) and are not
 * registered twice.
 *
 * Parity gaps (documented defaults): the UID registry (`isValidUID`, `isDepot`,
 * `isMovable`, `getDepotId`, `doAddContainerItem`), waypoints, depot lockers, war
 * lists, channel/guild chat sends, map clean/save and the combat engine are not
 * wired into this runtime, so those seam methods read the unwired defaults.
 */
interface GlobalOperations {
    /** `cleanMap()`: `g_game().map.clean()`. */
    fun cleanMap(): Long = 0L

    /** `createCombatArea(area[, extArea])`; null is the C++ area-not-found `false`. */
    fun createCombatArea(area: List<List<Int>>, extendedArea: List<List<Int>>?): Long? = null

    /** `doAddContainerItem(uid, itemid[, count])`. */
    fun doAddContainerItem(uid: Int, itemId: Int, count: Int): Long? = null

    /** `doPlayerAddItem(cid, itemid[, count[, canDropOnMap[, subtype]]])`. */
    fun doPlayerAddItem(player: Any?, itemId: Int, count: Int, canDropOnMap: Boolean, subType: Int): Long? = null

    fun doAreaCombatHealth(
        creature: Any?,
        combatType: Int,
        position: Position,
        areaId: Long,
        min: Int,
        max: Int,
        effect: Int,
        origin: Int,
        instantSpellName: String,
        runeSpellName: String,
    ): Boolean = false

    fun doTargetCombatHealth(
        creature: Any?,
        target: Any,
        combatType: Int,
        min: Int,
        max: Int,
        effect: Int,
        origin: Int,
        instantSpellName: String,
        runeSpellName: String,
    ): Boolean = false

    fun doAreaCombatMana(
        creature: Any?,
        position: Position,
        areaId: Long,
        min: Int,
        max: Int,
        effect: Int,
        origin: Int,
        instantSpellName: String,
        runeSpellName: String,
    ): Boolean = false

    fun doTargetCombatMana(
        creature: Any?,
        target: Any,
        min: Int,
        max: Int,
        effect: Int,
        origin: Int,
        instantSpellName: String,
        runeSpellName: String,
    ): Boolean = false

    fun doAreaCombatCondition(
        creature: Any?,
        position: Position,
        areaId: Long,
        condition: Any,
        effect: Int,
    ): Boolean = false

    fun doTargetCombatCondition(creature: Any?, target: Any, condition: Any, effect: Int): Boolean = false

    fun doAreaCombatDispel(creature: Any?, position: Position, areaId: Long, dispelType: Int, effect: Int): Boolean = false

    fun doTargetCombatDispel(creature: Any?, target: Any, dispelType: Int, effect: Int): Boolean = false

    fun doChallengeCreature(creature: Any, target: Any, targetChangeCooldown: Int): Boolean = false

    /** `getDepotId(uid)`; null is the C++ `false`. */
    fun getDepotId(uid: Int): Long? = null

    /** `getWaypointPositionByName(name)`; null is the C++ `false`. */
    fun getWaypointPositionByName(name: String): Position? = null

    /** `getWorldLight()`: `(level, color)`; the C++ default world light is (0, 0). */
    fun getWorldLight(): Pair<Int, Int> = 0 to 0

    /** `getWorldUpTime()`: `(OTSYS_TIME() - ProtocolStatus::start) / 1000`. */
    fun getWorldUpTime(): Long = 0L

    fun isDepot(uid: Int): Boolean = false

    fun isInWar(player: Any, targetPlayer: Any): Boolean = false

    fun isMovable(uid: Int): Boolean = false

    fun isValidUID(uid: Int): Boolean = false

    fun saveServer(): Boolean = false

    fun sendChannelMessage(channelId: Int, type: Int, message: String): Boolean = false

    fun sendGuildChannelMessage(guildId: Int, type: Int, message: String): Boolean = false

    /** `Lua::reportErrorFunc`. */
    fun reportError(message: String) {}

    /** `getTimeNow()` (`src/utils/tools.cpp`); injectable for deterministic formatting. */
    fun timeNowSeconds(): Long = System.currentTimeMillis() / 1000
}

/**
 * Registers the global functions and variables of `GlobalFunctions::init`
 * (global_functions.cpp:29-67):
 *
 *   cleanMap() / createCombatArea(area[, extArea]) / debugPrint(text)
 *   doAddContainerItem(uid, itemid[, count]) / doPlayerAddItem(cid, itemid, ...)
 *   doAreaCombatHealth / doAreaCombatMana / doAreaCombatCondition / doAreaCombatDispel
 *   doTargetCombatHealth / doTargetCombatMana / doTargetCombatCondition / doTargetCombatDispel
 *   doChallengeCreature(cid, target[, targetChangeCooldown = 6000])
 *   getDepotId(uid) / getWaypointPositionByName(name) / getWorldLight() / getWorldUpTime()
 *   isDepot(uid) / isInWar(cid, target) / isMovable(uid) / isValidUID(uid)
 *   saveServer() / sendChannelMessage(channelId, type, message)
 *   sendGuildChannelMessage(guildId, type, message)
 *   INDEX_WHEREEVER / VIRTUAL_PARENT
 *   isType(derived, base) / rawgetmetatable(metatableName) / createTable(arrayLength, keyLength)
 *   systemTime() / getFormattedTimeRemaining(time) / reportError(errorDescription)
 *
 * `createCombatArea` fetches the area id from [GlobalOperations] instead of the C++
 * `LuaEnvironment` area table; the C++ `EVENT_ID_LOADING` guard is not modeled
 * (PARITY GAP). `isType` walks the `__index` metatable chain instead of the C++
 * parent-count/hash fields of `Lua::registerClass` (PARITY GAP).
 * `rawgetmetatable` auto-vivifies through `UserdataRegistry::metatable` like the
 * compat shim the revscript Lua libraries expect, instead of the C++
 * `luaL_getmetatable` nil-for-unknown behavior (PARITY GAP).
 */
object GlobalFunctions {
    fun install(env: LuaEnvironment, operations: GlobalOperations = object : GlobalOperations {}) {
        registerWorldFunctions(env, operations)
        registerPureFunctions(env, operations)

        env.globals.set("INDEX_WHEREEVER", LuaValue.valueOf(INDEX_WHEREEVER))
        env.globals.set("VIRTUAL_PARENT", LuaValue.TRUE)
    }

    private fun registerWorldFunctions(env: LuaEnvironment, operations: GlobalOperations) {
        env.globals.set("cleanMap", libraryFunction {
            LuaValue.valueOf(operations.cleanMap().toDouble())
        })

        env.globals.set("createCombatArea", libraryFunction { args ->
            val extended = if (args.narg() >= 2) {
                luaToArea(args.arg(2)) ?: return@libraryFunction LuaValue.FALSE
            } else {
                null
            }
            val area = luaToArea(args.arg(1))
            val areaId = if (area == null) null else operations.createCombatArea(area, extended)
            if (areaId == null) LuaValue.FALSE else LuaValue.valueOf(areaId.toDouble())
        })

        env.globals.set("debugPrint", libraryFunction { args ->
            operations.reportError(luaArgString(args, args.narg()))
            LuaValue.NONE
        })

        env.globals.set("doAddContainerItem", libraryFunction { args ->
            val result = operations.doAddContainerItem(
                luaArgInt(args, 1),
                luaArgInt(args, 2),
                luaArgInt(args, 3, 1),
            )
            if (result == null) LuaValue.FALSE else LuaValue.valueOf(result.toDouble())
        })

        env.globals.set("doPlayerAddItem", libraryFunction { args ->
            val result = operations.doPlayerAddItem(
                args.arg(1).touserdata(),
                luaArgInt(args, 2),
                luaArgInt(args, 3, 1),
                luaArgBoolean(args, 4, true),
                luaArgInt(args, 5, 1),
            )
            if (result == null) LuaValue.FALSE else LuaValue.valueOf(result.toDouble())
        })

        installAreaCombat(env, operations)
        installTargetCombat(env, operations)
        installMiscWorldGlobals(env, operations)
    }

    private fun installAreaCombat(env: LuaEnvironment, operations: GlobalOperations) {
        env.globals.set("doAreaCombatHealth", libraryFunction { args ->
            if (!isCasterArgument(args, 1)) {
                operations.reportError("Creature not found")
                return@libraryFunction LuaValue.FALSE
            }
            val creature = caster(args, 1)
            LuaValue.valueOf(
                operations.doAreaCombatHealth(
                    creature,
                    luaArgInt(args, 2),
                    luaToPosition(args.arg(3)) ?: Position(0, 0, 0),
                    luaArgLong(args, 4),
                    luaArgInt(args, 5),
                    luaArgInt(args, 6),
                    luaArgInt(args, 7),
                    luaArgInt(args, 8, CombatOrigin.SPELL.value),
                    luaArgString(args, 9),
                    luaArgString(args, 10),
                ),
            )
        })

        env.globals.set("doAreaCombatMana", libraryFunction { args ->
            if (!isCasterArgument(args, 1)) {
                operations.reportError("Creature not found")
                return@libraryFunction LuaValue.FALSE
            }
            val creature = caster(args, 1)
            LuaValue.valueOf(
                operations.doAreaCombatMana(
                    creature,
                    luaToPosition(args.arg(2)) ?: Position(0, 0, 0),
                    luaArgLong(args, 3),
                    luaArgInt(args, 4),
                    luaArgInt(args, 5),
                    luaArgInt(args, 6),
                    luaArgInt(args, 7, CombatOrigin.SPELL.value),
                    luaArgString(args, 8),
                    luaArgString(args, 9),
                ),
            )
        })

        env.globals.set("doAreaCombatCondition", libraryFunction { args ->
            if (!isCasterArgument(args, 1)) {
                operations.reportError("Creature not found")
                return@libraryFunction LuaValue.FALSE
            }
            val creature = caster(args, 1)
            val condition = args.arg(4).touserdata() as? Condition
            if (condition == null) {
                operations.reportError("Condition not found")
                return@libraryFunction LuaValue.FALSE
            }
            LuaValue.valueOf(
                operations.doAreaCombatCondition(
                    creature,
                    luaToPosition(args.arg(2)) ?: Position(0, 0, 0),
                    luaArgLong(args, 3),
                    condition,
                    luaArgInt(args, 5),
                ),
            )
        })

        env.globals.set("doAreaCombatDispel", libraryFunction { args ->
            if (!isCasterArgument(args, 1)) {
                operations.reportError("Creature not found")
                return@libraryFunction LuaValue.FALSE
            }
            val creature = caster(args, 1)
            LuaValue.valueOf(
                operations.doAreaCombatDispel(
                    creature,
                    luaToPosition(args.arg(2)) ?: Position(0, 0, 0),
                    luaArgLong(args, 3),
                    luaArgInt(args, 4),
                    luaArgInt(args, 5),
                ),
            )
        })
    }

    private fun installTargetCombat(env: LuaEnvironment, operations: GlobalOperations) {
        env.globals.set("doTargetCombatHealth", libraryFunction { args ->
            if (!isCasterArgument(args, 1)) {
                operations.reportError("Creature not found")
                return@libraryFunction LuaValue.FALSE
            }
            val creature = caster(args, 1)
            val target = creatureTarget(args, 2) ?: return@libraryFunction LuaValue.FALSE
            LuaValue.valueOf(
                operations.doTargetCombatHealth(
                    creature,
                    target,
                    luaArgInt(args, 3),
                    luaArgInt(args, 4),
                    luaArgInt(args, 5),
                    luaArgInt(args, 6),
                    luaArgInt(args, 7, CombatOrigin.SPELL.value),
                    luaArgString(args, 9),
                    luaArgString(args, 10),
                ),
            )
        })

        env.globals.set("doTargetCombatMana", libraryFunction { args ->
            if (!isCasterArgument(args, 1)) {
                operations.reportError("Creature not found")
                return@libraryFunction LuaValue.FALSE
            }
            val creature = caster(args, 1)
            val target = creatureTarget(args, 2) ?: return@libraryFunction LuaValue.FALSE
            LuaValue.valueOf(
                operations.doTargetCombatMana(
                    creature,
                    target,
                    luaArgInt(args, 3),
                    luaArgInt(args, 4),
                    luaArgInt(args, 5),
                    luaArgInt(args, 6, CombatOrigin.SPELL.value),
                    luaArgString(args, 7),
                    luaArgString(args, 8),
                ),
            )
        })

        env.globals.set("doTargetCombatCondition", libraryFunction { args ->
            if (!isCasterArgument(args, 1)) {
                operations.reportError("Creature not found")
                return@libraryFunction LuaValue.FALSE
            }
            val creature = caster(args, 1)
            val target = creatureTarget(args, 2) ?: return@libraryFunction LuaValue.FALSE
            val condition = args.arg(3).touserdata() as? Condition
            if (condition == null) {
                operations.reportError("Condition not found")
                return@libraryFunction LuaValue.FALSE
            }
            LuaValue.valueOf(operations.doTargetCombatCondition(creature, target, condition, luaArgInt(args, 4)))
        })

        env.globals.set("doTargetCombatDispel", libraryFunction { args ->
            if (!isCasterArgument(args, 1)) {
                operations.reportError("Creature not found")
                return@libraryFunction LuaValue.FALSE
            }
            val creature = caster(args, 1)
            val target = creatureTarget(args, 2) ?: return@libraryFunction LuaValue.FALSE
            LuaValue.valueOf(
                operations.doTargetCombatDispel(creature, target, luaArgInt(args, 3), luaArgInt(args, 4)),
            )
        })
    }

    private fun installMiscWorldGlobals(env: LuaEnvironment, operations: GlobalOperations) {
        env.globals.set("doChallengeCreature", libraryFunction { args ->
            val creature = creatureTarget(args, 1) ?: return@libraryFunction LuaValue.FALSE
            val target = creatureTarget(args, 2) ?: return@libraryFunction LuaValue.FALSE
            LuaValue.valueOf(
                operations.doChallengeCreature(creature, target, luaArgInt(args, 3, 6000)),
            )
        })

        env.globals.set("getDepotId", libraryFunction { args ->
            val depotId = operations.getDepotId(luaArgInt(args, args.narg()))
            if (depotId == null) LuaValue.FALSE else LuaValue.valueOf(depotId.toDouble())
        })

        env.globals.set("getWaypointPositionByName", libraryFunction { args ->
            val position = operations.getWaypointPositionByName(luaArgString(args, args.narg()))
            if (position == null) LuaValue.FALSE else pushPosition(env, position)
        })

        env.globals.set("getWorldLight", libraryFunction {
            val (level, color) = operations.getWorldLight()
            LuaValue.varargsOf(LuaValue.valueOf(level), LuaValue.valueOf(color))
        })

        env.globals.set("getWorldUpTime", libraryFunction {
            LuaValue.valueOf(operations.getWorldUpTime().toDouble())
        })

        env.globals.set("isDepot", libraryFunction { args ->
            LuaValue.valueOf(operations.isDepot(luaArgInt(args, args.narg())))
        })

        env.globals.set("isMovable", libraryFunction { args ->
            LuaValue.valueOf(operations.isMovable(luaArgInt(args, args.narg())))
        })

        env.globals.set("isValidUID", libraryFunction { args ->
            LuaValue.valueOf(operations.isValidUID(luaArgInt(args, args.narg())))
        })

        env.globals.set("isInWar", libraryFunction { args ->
            val player = args.arg(1).touserdata() as? Player
            if (player == null) {
                operations.reportError("Player not found - Player")
                return@libraryFunction LuaValue.FALSE
            }
            val target = args.arg(2).touserdata() as? Player
            if (target == null) {
                operations.reportError("Player not found - TargetPlayer")
                return@libraryFunction LuaValue.FALSE
            }
            LuaValue.valueOf(operations.isInWar(player, target))
        })

        env.globals.set("saveServer", libraryFunction {
            LuaValue.valueOf(operations.saveServer())
        })

        env.globals.set("sendChannelMessage", libraryFunction { args ->
            LuaValue.valueOf(
                operations.sendChannelMessage(luaArgInt(args, 1), luaArgInt(args, 2), luaArgString(args, 3)),
            )
        })

        env.globals.set("sendGuildChannelMessage", libraryFunction { args ->
            LuaValue.valueOf(
                operations.sendGuildChannelMessage(luaArgInt(args, 1), luaArgInt(args, 2), luaArgString(args, 3)),
            )
        })
    }

    private fun registerPureFunctions(env: LuaEnvironment, operations: GlobalOperations) {
        env.globals.set("isType", libraryFunction { args ->
            LuaValue.valueOf(isTypeOf(args.arg(1), args.arg(2)))
        })

        env.globals.set("rawgetmetatable", libraryFunction { args ->
            env.userdata.metatable(luaArgString(args, 1))
        })

        env.globals.set("createTable", libraryFunction { args ->
            LuaTable(luaArgInt(args, 1), luaArgInt(args, 2))
        })

        env.globals.set("systemTime", libraryFunction {
            LuaValue.valueOf(System.currentTimeMillis().toDouble())
        })

        env.globals.set("getFormattedTimeRemaining", libraryFunction { args ->
            LuaValue.valueOf(
                formatTimeRemaining(luaArgLong(args, 1), operations.timeNowSeconds()),
            )
        })

        env.globals.set("reportError", libraryFunction { args ->
            operations.reportError(luaArgString(args, 1))
            args.arg(1)
        })
    }

    /**
     * `GlobalFunctions::getArea` (`global_functions.cpp:901-922`): a table of rows,
     * each row a table of numbers; an empty table is invalid.
     */
    private fun luaToArea(value: LuaValue): List<List<Int>>? {
        if (!value.istable()) {
            return null
        }
        val table = value.checktable()
        val rows = mutableListOf<List<Int>>()
        var failed = false
        var cursor = LuaValue.NIL
        while (!failed) {
            val entry = table.next(cursor)
            val key = entry.arg1()
            if (key.isnil()) {
                break
            }
            val rowValue = entry.arg(2)
            if (!rowValue.istable()) {
                failed = true
                break
            }
            val rowTable = rowValue.checktable()
            val cells = mutableListOf<Int>()
            var rowCursor = LuaValue.NIL
            while (true) {
                val cell = rowTable.next(rowCursor)
                val cellKey = cell.arg1()
                if (cellKey.isnil()) {
                    break
                }
                if (!cell.arg(2).isnumber()) {
                    failed = true
                    break
                }
                cells += cell.arg(2).toint()
                rowCursor = cellKey
            }
            if (failed) {
                break
            }
            rows += cells
            cursor = key
        }
        return if (failed || rows.isEmpty()) null else rows
    }

    /** `GlobalFunctions::luaIsType` (`global_functions.cpp:844-867`) over `__index` chains. */
    private fun isTypeOf(derived: LuaValue, base: LuaValue): Boolean {
        val derivedMeta = derived.getmetatable() ?: return false
        val baseMeta = base.getmetatable() ?: return false
        val visited = mutableSetOf<LuaValue>()
        var current: LuaValue? = derivedMeta
        while (current != null && visited.add(current)) {
            if (current == baseMeta) {
                return true
            }
            val index = current.get("__index")
            current = if (index.istable()) index else null
        }
        return false
    }

    /**
     * The `cid` argument of the combat functions: a `Creature` userdata, or the numeric
     * `0` the C++ functions accept as a null caster.
     */
    private fun isCasterArgument(args: Varargs, index: Int): Boolean {
        val value = args.arg(index)
        return value.touserdata() is Creature || (value.isnumber() && value.toint() == 0)
    }

    private fun caster(args: Varargs, index: Int): Creature? = args.arg(index).touserdata() as? Creature

    private fun creatureTarget(args: Varargs, index: Int): Any? =
        args.arg(index).touserdata()?.takeIf { it is Creature }

    /** Port of `getFormattedTimeRemaining` (`src/utils/tools.cpp:2007-2034`). */
    internal fun formatTimeRemaining(time: Long, nowSeconds: Long): String {
        val timeRemaining = time - nowSeconds
        val days = floor(timeRemaining / 86400.0).toInt()
        if (days > 1) {
            return "$days days"
        }

        val hours = floor((timeRemaining % 86400) / 3600.0).toInt()
        val minutes = floor((timeRemaining % 3600) / 60.0).toInt()
        val seconds = (timeRemaining % 60).toInt()

        val output = StringBuilder()
        if (hours == 0 && minutes == 0 && seconds > 0) {
            output.append(" less than 1 minute")
        } else {
            if (hours > 0) {
                output.append(hours).append(" hour").append(if (hours != 1) "s" else "")
            }
            if (minutes > 0) {
                if (hours > 0) {
                    output.append(" and ")
                }
                output.append(minutes).append(" minute").append(if (minutes != 1) "s" else "")
            }
        }
        return output.toString()
    }
}
