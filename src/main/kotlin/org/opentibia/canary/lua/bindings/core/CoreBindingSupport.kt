package org.opentibia.canary.lua.bindings.core

import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.Varargs
import org.luaj.vm2.lib.VarArgFunction
import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.Npc
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.Item
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.Tile

/**
 * Shared plumbing for the core binding libraries. Keeps the per-group files focused on
 * Canary method signatures and delegates actual gameplay work to the engine seams.
 */
internal fun luaFunction(fn: (Varargs) -> Varargs): VarArgFunction = object : VarArgFunction() {
    override fun invoke(args: Varargs): Varargs = fn(args)
}

internal fun retrieveUserdata(args: Varargs, index: Int): Any? = args.arg(index).touserdata()

/**
 * `Lua::luaUserdataCompare` (`lua_functions_loader.cpp:910-913`): equal only when both
 * operands wrap the same engine instance, exactly like the C++ raw-pointer comparison.
 * Shared by every class that registers the metamethod so LuaJ's "same `__eq`
 * function" rule accepts cross-class comparisons like Canary's single C function.
 */
internal val luaUserdataCompare: LuaValue = luaFunction { args ->
    val left = args.arg(1).touserdata()
    val right = args.arg(2).touserdata()
    LuaValue.valueOf(left != null && right != null && left === right)
}

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
    items.size == 1 -> wrapItemUserdata(env, items[0])
    else -> LuaTable().also { table ->
        items.forEachIndexed { index, item -> table.set(index + 1, wrapItemUserdata(env, item)) }
    }
}

/**
 * Reads a position argument the way Canary's `Lua::getPosition` does: a `Position`
 * userdata or a table carrying `x`/`y`/`z` fields. Returns null for any other shape.
 */
internal fun luaToPosition(value: LuaValue): Position? {
    val userdata = value.touserdata()
    if (userdata is Position) {
        return userdata
    }
    if (!value.istable()) {
        return null
    }
    return Position(value.get("x").optint(0), value.get("y").optint(0), value.get("z").optint(0))
}

/**
 * Mirrors `Lua::setCreatureMetatable`: picks the runtime creature metatable and falls
 * back to the shared `Creature` one.
 */
internal fun wrapCreatureUserdata(env: LuaEnvironment, value: Any): LuaValue {
    val typeName = when (value) {
        is Player -> "Player"
        is Monster -> "Monster"
        is Npc -> "Npc"
        else -> "Creature"
    }
    return env.userdata.wrap(typeName, value)
}

/** Mirrors `Lua::setItemMetatable`: containers get the `Container` metatable. */
internal fun wrapItemUserdata(env: LuaEnvironment, value: Any): LuaValue =
    env.userdata.wrap(if (value is Container) "Container" else "Item", value)

/** Mirrors `Lua::pushCylinder`: items, creatures and tiles become their Canary userdata. */
internal fun wrapCylinderUserdata(env: LuaEnvironment, value: Any): LuaValue = when (value) {
    is Item -> wrapItemUserdata(env, value)
    is Creature -> wrapCreatureUserdata(env, value)
    is Tile -> env.userdata.wrap("Tile", value)
    else -> LuaValue.NIL
}
