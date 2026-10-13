package org.opentibia.canary.lua.bindings.items

import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.opentibia.canary.lua.bindings.core.luaFunction
import org.opentibia.canary.lua.bindings.core.retrieveUserdata
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.systems.imbuement.BaseImbuement
import org.opentibia.canary.systems.imbuement.CategoryImbuement
import org.opentibia.canary.systems.imbuement.Imbuement

/**
 * Engine seam for the `Imbuement` metatable. Mirrors the `g_imbuements()` lookups of
 * src/lua/functions/items/imbuement_functions.cpp.
 */
interface ImbuementOperations {
    /** `g_imbuements().getImbuement(id)` (imbuement_functions.cpp:33). */
    fun getImbuement(id: Int): Imbuement?

    /** `g_imbuements().getBaseByID(imbuement->getBaseID())` (imbuement_functions.cpp:95). */
    fun getBase(imbuement: Imbuement): BaseImbuement?

    /** `g_imbuements().getCategoryByID(imbuement->getCategory())` (imbuement_functions.cpp:120). */
    fun getCategory(imbuement: Imbuement): CategoryImbuement?
}

/**
 * Registers the Canary `Imbuement` userdata methods:
 *
 *   Imbuement(id)
 *   imbuement:getName()
 *   imbuement:getId()
 *   imbuement:getItems()
 *   imbuement:getBase()
 *   imbuement:getCategory()
 *   imbuement:isPremium()
 *   imbuement:getElementDamage()
 *   imbuement:getCombatType()
 *
 * It also registers the global `Imbuement(id)` class function and the `__eq` metamethod
 * mirroring ImbuementFunctions::luaCreateImbuement and `Lua::luaUserdataCompare`.
 *
 * `getItems` keeps Canary's exact shape: a table keyed by item id whose values are
 * `{ itemid = ..., count = ... }` tables (imbuement_functions.cpp:76-82). `getBase`
 * renders the `id`/`name`/`price`/`protection`/`percent`/`removeCost`/`duration` fields
 * and `getCategory` the `id`/`name` fields, exactly like the C++ `setField` calls.
 */
object ImbuementFunctions {
    const val IMBUEMENT_TYPE = "Imbuement"

    fun install(env: LuaEnvironment, operations: ImbuementOperations = UnwiredImbuementOperations) {
        val registry = env.userdata

        env.globals.set(IMBUEMENT_TYPE, luaFunction { args ->
            val imbuement = operations.getImbuement(args.arg(1).optint(0)) ?: return@luaFunction LuaValue.NIL
            env.userdata.wrap(IMBUEMENT_TYPE, imbuement)
        })

        registry.registerMethod(IMBUEMENT_TYPE, "__eq", luaFunction { args ->
            val imbuement = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val other = retrieveUserdata(args, 2) ?: return@luaFunction LuaValue.FALSE
            LuaValue.valueOf(imbuement == other)
        })

        registry.registerMethod(IMBUEMENT_TYPE, "getName", luaFunction { args ->
            val imbuement = retrieveUserdata(args, 1) as? Imbuement ?: return@luaFunction LuaValue.NIL
            LuaValue.valueOf(imbuement.name)
        })

        registry.registerMethod(IMBUEMENT_TYPE, "getId", luaFunction { args ->
            val imbuement = retrieveUserdata(args, 1) as? Imbuement ?: return@luaFunction LuaValue.NIL
            LuaValue.valueOf(imbuement.id)
        })

        registry.registerMethod(IMBUEMENT_TYPE, "getItems", luaFunction { args ->
            val imbuement = retrieveUserdata(args, 1) as? Imbuement ?: return@luaFunction LuaValue.NIL
            LuaTable().also { table ->
                for ((itemId, count) in imbuement.items) {
                    val entry = LuaTable()
                    entry.set("itemid", LuaValue.valueOf(itemId))
                    entry.set("count", LuaValue.valueOf(count))
                    table.set(itemId, entry)
                }
            }
        })

        registry.registerMethod(IMBUEMENT_TYPE, "getBase", luaFunction { args ->
            val imbuement = retrieveUserdata(args, 1) as? Imbuement ?: return@luaFunction LuaValue.NIL
            val base = operations.getBase(imbuement) ?: return@luaFunction LuaValue.NIL
            LuaTable().also { table ->
                table.set("id", LuaValue.valueOf(base.id))
                table.set("name", LuaValue.valueOf(base.name))
                table.set("price", LuaValue.valueOf(base.price))
                table.set("protection", LuaValue.valueOf(base.protectionPrice))
                table.set("percent", LuaValue.valueOf(base.percent))
                table.set("removeCost", LuaValue.valueOf(base.removeCost))
                table.set("duration", LuaValue.valueOf(base.duration))
            }
        })

        registry.registerMethod(IMBUEMENT_TYPE, "getCategory", luaFunction { args ->
            val imbuement = retrieveUserdata(args, 1) as? Imbuement ?: return@luaFunction LuaValue.NIL
            val category = operations.getCategory(imbuement) ?: return@luaFunction LuaValue.NIL
            LuaTable().also { table ->
                table.set("id", LuaValue.valueOf(category.id))
                table.set("name", LuaValue.valueOf(category.name))
            }
        })

        registry.registerMethod(IMBUEMENT_TYPE, "isPremium", luaFunction { args ->
            val imbuement = retrieveUserdata(args, 1) as? Imbuement ?: return@luaFunction LuaValue.NIL
            LuaValue.valueOf(imbuement.premium)
        })

        registry.registerMethod(IMBUEMENT_TYPE, "getElementDamage", luaFunction { args ->
            val imbuement = retrieveUserdata(args, 1) as? Imbuement ?: return@luaFunction LuaValue.NIL
            LuaValue.valueOf(imbuement.elementDamage)
        })

        registry.registerMethod(IMBUEMENT_TYPE, "getCombatType", luaFunction { args ->
            val imbuement = retrieveUserdata(args, 1) as? Imbuement ?: return@luaFunction LuaValue.NIL
            LuaValue.valueOf(imbuement.combatType.value)
        })
    }
}

internal object UnwiredImbuementOperations : ImbuementOperations {
    override fun getImbuement(id: Int): Imbuement? =
        throw NotImplementedError("ImbuementFunctions.getImbuement seam is not wired")

    override fun getBase(imbuement: Imbuement): BaseImbuement? =
        throw NotImplementedError("ImbuementFunctions.getBase seam is not wired")

    override fun getCategory(imbuement: Imbuement): CategoryImbuement? =
        throw NotImplementedError("ImbuementFunctions.getCategory seam is not wired")
}
