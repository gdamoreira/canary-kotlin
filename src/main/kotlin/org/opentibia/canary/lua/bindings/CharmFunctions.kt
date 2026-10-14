package org.opentibia.canary.lua.bindings

import org.luaj.vm2.LuaError
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.opentibia.canary.lua.bindings.core.luaFunction
import org.opentibia.canary.lua.bindings.core.retrieveUserdata
import org.opentibia.canary.lua.runtime.LuaEnvironment

/**
 * Engine seam for the `Charm` binding group. `Charm(id)` resolves the shared charm
 * through `Game::getCharmList`; the rest of the group is pure data mutation.
 */
interface CharmOperations {
    /** Mirrors `g_game().getCharmList()` by id (`Charm::id`); null when absent. */
    fun charmById(id: Int): Any?
}

/**
 * Port of Canary's `Charm` (src/io/iobestiary.hpp:31-63) as mutated by the `Charm` Lua
 * methods. Every field keeps its Canary name and default (`damageType` is
 * `COMBAT_NONE`, the `SoundEffect_t::SILENCE` value 0 for both sounds).
 */
class CharmData(
    var name: String = "",
    var description: String = "",
    var category: Int = 0,
    var type: Int = 0,
    val points: MutableList<Int> = mutableListOf(),
    var damageType: Int = 255,
    var percent: Double = 0.0,
    val chance: MutableList<Double> = mutableListOf(),
    var cancelMessage: String = "",
    var logMessage: Boolean = false,
    var effect: Int = 0,
    var soundCastEffect: Int = 0,
    var soundImpactEffect: Int = 0,
)

/**
 * Registers the Canary `Charm` class from
 * src/lua/functions/creatures/monster/charm_functions.cpp:
 *
 *   Charm(id)
 *   charm:name([string])
 *   charm:description([string])
 *   charm:category([number])
 *   charm:type([number])
 *   charm:points([table])
 *   charm:damageType([number])
 *   charm:percentage([number])
 *   charm:chance([table])
 *   charm:messageCancel([string])
 *   charm:messageServerLog([bool])
 *   charm:effect([number])
 *   charm:castSound([sound])
 *   charm:impactSound([sound])
 *
 * Fidelity quirks kept from the C++ source:
 *  - `Charm(id)` always evaluates to `nil`: `luaCharmCreate` pushes the shared
 *    userdata and then an unconditional trailing `lua_pushnil`
 *    (charm_functions.cpp:35-50), so the lookup is performed but shadowed;
 *  - `chance(table)` clears the vector and seeds a leading `0.0` before appending the
 *    table values (charm_functions.cpp:161-170).
 */
object CharmFunctions {
    const val CHARM_TYPE = "Charm"

    fun install(env: LuaEnvironment, operations: CharmOperations = WorldCharmOperations()) {
        val registry = env.userdata

        registry.registerClass(CHARM_TYPE, "", luaFunction { args ->
            if (args.arg(1).isnumber()) {
                operations.charmById(args.arg(1).toint())
            }
            LuaValue.NIL
        })

        registry.registerMethod(CHARM_TYPE, "name", luaFunction { args ->
            val charm = retrieveUserdata(args, 1) as? CharmData ?: return@luaFunction LuaValue.NIL
            if (args.narg() == 1) {
                LuaValue.valueOf(charm.name)
            } else {
                charm.name = args.arg(2).tojstring()
                LuaValue.TRUE
            }
        })

        registry.registerMethod(CHARM_TYPE, "description", luaFunction { args ->
            val charm = retrieveUserdata(args, 1) as? CharmData ?: return@luaFunction LuaValue.NIL
            if (args.narg() == 1) {
                LuaValue.valueOf(charm.description)
            } else {
                charm.description = args.arg(2).tojstring()
                LuaValue.TRUE
            }
        })

        registry.registerMethod(CHARM_TYPE, "category", luaFunction { args ->
            val charm = retrieveUserdata(args, 1) as? CharmData ?: return@luaFunction LuaValue.NIL
            if (args.narg() == 1) {
                LuaValue.valueOf(charm.category)
            } else {
                charm.category = args.arg(2).toint()
                LuaValue.TRUE
            }
        })

        registry.registerMethod(CHARM_TYPE, "type", luaFunction { args ->
            val charm = retrieveUserdata(args, 1) as? CharmData ?: return@luaFunction LuaValue.NIL
            if (args.narg() == 1) {
                LuaValue.valueOf(charm.type)
            } else {
                charm.type = args.arg(2).toint()
                LuaValue.TRUE
            }
        })

        registry.registerMethod(CHARM_TYPE, "points", luaFunction { args ->
            val charm = retrieveUserdata(args, 1) as? CharmData ?: return@luaFunction LuaValue.NIL
            if (args.narg() == 1) {
                numberTable(charm.points)
            } else if (args.arg(2).istable()) {
                charm.points.clear()
                tableNumbers(args.arg(2).checktable()).forEach { charm.points += it.toInt() }
                LuaValue.TRUE
            } else {
                throw LuaError("Expected a table for points.")
            }
        })

        registry.registerMethod(CHARM_TYPE, "damageType", luaFunction { args ->
            val charm = retrieveUserdata(args, 1) as? CharmData ?: return@luaFunction LuaValue.NIL
            if (args.narg() == 1) {
                LuaValue.valueOf(charm.damageType)
            } else {
                charm.damageType = args.arg(2).toint()
                LuaValue.TRUE
            }
        })

        registry.registerMethod(CHARM_TYPE, "percentage", luaFunction { args ->
            val charm = retrieveUserdata(args, 1) as? CharmData ?: return@luaFunction LuaValue.NIL
            if (args.narg() == 1) {
                LuaValue.valueOf(charm.percent)
            } else {
                charm.percent = args.arg(2).tofloat().toDouble()
                LuaValue.TRUE
            }
        })

        registry.registerMethod(CHARM_TYPE, "chance", luaFunction { args ->
            val charm = retrieveUserdata(args, 1) as? CharmData ?: return@luaFunction LuaValue.NIL
            if (args.narg() == 1) {
                numberTable(charm.chance)
            } else if (args.arg(2).istable()) {
                charm.chance.clear()
                charm.chance += 0.0
                tableNumbers(args.arg(2).checktable()).forEach { charm.chance += it }
                LuaValue.TRUE
            } else {
                throw LuaError("Expected a table for chance.")
            }
        })

        registry.registerMethod(CHARM_TYPE, "messageCancel", luaFunction { args ->
            val charm = retrieveUserdata(args, 1) as? CharmData ?: return@luaFunction LuaValue.NIL
            if (args.narg() == 1) {
                LuaValue.valueOf(charm.cancelMessage)
            } else {
                charm.cancelMessage = args.arg(2).tojstring()
                LuaValue.TRUE
            }
        })

        registry.registerMethod(CHARM_TYPE, "messageServerLog", luaFunction { args ->
            val charm = retrieveUserdata(args, 1) as? CharmData ?: return@luaFunction LuaValue.NIL
            if (args.narg() == 1) {
                LuaValue.valueOf(charm.logMessage)
            } else {
                charm.logMessage = args.arg(2).toboolean()
                LuaValue.TRUE
            }
        })

        registry.registerMethod(CHARM_TYPE, "effect", luaFunction { args ->
            val charm = retrieveUserdata(args, 1) as? CharmData ?: return@luaFunction LuaValue.NIL
            if (args.narg() == 1) {
                LuaValue.valueOf(charm.effect)
            } else {
                charm.effect = args.arg(2).toint()
                LuaValue.TRUE
            }
        })

        registry.registerMethod(CHARM_TYPE, "castSound", luaFunction { args ->
            val charm = retrieveUserdata(args, 1) as? CharmData ?: return@luaFunction LuaValue.NIL
            if (args.narg() == 1) {
                LuaValue.valueOf(charm.soundCastEffect)
            } else {
                charm.soundCastEffect = args.arg(2).toint()
                LuaValue.TRUE
            }
        })

        registry.registerMethod(CHARM_TYPE, "impactSound", luaFunction { args ->
            val charm = retrieveUserdata(args, 1) as? CharmData ?: return@luaFunction LuaValue.NIL
            if (args.narg() == 1) {
                LuaValue.valueOf(charm.soundImpactEffect)
            } else {
                charm.soundImpactEffect = args.arg(2).toint()
                LuaValue.TRUE
            }
        })
    }

    /** Reads the numeric sequence part of a Lua table, mirroring `lua_next` over it. */
    private fun tableNumbers(table: LuaTable): List<Double> =
        (1..table.length()).mapNotNull { index ->
            val value = table.get(index)
            if (value.isnumber()) value.todouble() else null
        }

    private fun numberTable(values: List<*>): LuaTable =
        LuaTable().also { table ->
            values.forEachIndexed { index, value ->
                table.set(
                    index + 1,
                    when (value) {
                        is Int -> LuaValue.valueOf(value)
                        is Double -> LuaValue.valueOf(value)
                        else -> LuaValue.valueOf(value.toString())
                    },
                )
            }
        }
}

/**
 * [CharmOperations] seam resolving `Charm(id)` through the optional charm catalog,
 * mirroring `g_game().getCharmList()`; an absent catalog misses (and, due to the C++
 * stack quirk, the constructor answers `nil` either way).
 */
class WorldCharmOperations(
    private val charmsById: Map<Int, Any> = emptyMap(),
) : CharmOperations {
    override fun charmById(id: Int): Any? = charmsById[id]
}
