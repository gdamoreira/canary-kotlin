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
