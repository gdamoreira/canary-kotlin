package org.opentibia.canary.lua.bindings.core

import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.Varargs
import org.luaj.vm2.lib.VarArgFunction
import org.opentibia.canary.lua.runtime.LuaEnvironment

/**
 * Shared plumbing for the core binding libraries. Keeps the per-group files focused on
 * Canary method signatures and delegates actual gameplay work to the engine seams.
 */
internal fun luaFunction(fn: (Varargs) -> Varargs): VarArgFunction = object : VarArgFunction() {
    override fun invoke(args: Varargs): Varargs = fn(args)
}

internal fun retrieveUserdata(args: Varargs, index: Int): Any? = args.arg(index).touserdata()

/**
 * Reads a Lua argument into a raw attribute value, mirroring Canary's
 * `ItemFunctions::luaItemSetAttribute` value handling (boolean, integer, float, string).
 */
internal fun luaToAttributeValue(value: LuaValue): Any? = when {
    value.isnil() -> null
    value.isboolean() -> value.toboolean()
    value.isnumber() -> if (value.isint()) value.toint() else value.todouble()
    value.isstring() -> value.tojstring()
    else -> null
}

/**
 * Converts a seam attribute result back to Lua, mirroring Canary's
 * `ItemFunctions::luaItemGetAttribute` (integer attributes as numbers, string attributes
 * as strings, absent attributes as nil).
 */
internal fun attributeValueToLua(value: Any?): LuaValue = when (value) {
    null -> LuaValue.NIL
    is Int -> LuaValue.valueOf(value)
    is Long -> LuaValue.valueOf(value.toDouble())
    is Double -> LuaValue.valueOf(value)
    is Float -> LuaValue.valueOf(value.toDouble())
    is Boolean -> LuaValue.valueOf(value)
    is String -> LuaValue.valueOf(value)
    else -> LuaValue.valueOf(value.toString())
}

/**
 * Mirrors Canary's item-return convention: a single item is returned as its userdata,
 * multiple items as a 1-indexed table, and none as nil.
 */
internal fun wrapItems(env: LuaEnvironment, items: List<Any>): LuaValue = when {
    items.isEmpty() -> LuaValue.NIL
    items.size == 1 -> env.userdata.wrap("Item", items[0])
    else -> LuaTable().also { table ->
        items.forEachIndexed { index, item -> table.set(index + 1, env.userdata.wrap("Item", item)) }
    }
}
