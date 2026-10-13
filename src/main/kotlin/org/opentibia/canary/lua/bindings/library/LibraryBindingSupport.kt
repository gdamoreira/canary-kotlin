package org.opentibia.canary.lua.bindings.library

import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.Varargs
import org.luaj.vm2.lib.VarArgFunction

/**
 * Shared plumbing for the Wave 40 library bindings under `bindings/library`.
 *
 * These helpers reproduce the small `Lua::*` accessors the C++ library bindings use
 * (`Lua::getString`, `Lua::getNumber`, `Lua::getBoolean`, `Lua::isTable`) so every
 * `*Functions` object in this package can keep its Lua-visible contract exact.
 */
internal fun libraryFunction(fn: (Varargs) -> Varargs): VarArgFunction =
    object : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs = fn(args)
    }

/**
 * Mirrors `Lua::getString`: `lua_tolstring` accepts strings and numbers and converts
 * the value in place, any other type resolves to the empty string.
 */
internal fun luaArgString(args: Varargs, index: Int): String {
    val value = args.argOrNil(index)
    return if (value.isstring() || value.isnumber()) value.tojstring() else ""
}

/** Mirrors `Lua::getNumber<T>` with [default] when the argument is not a number. */
internal fun luaArgDouble(args: Varargs, index: Int, default: Double = 0.0): Double =
    if (args.argOrNil(index).isnumber()) args.arg(index).todouble() else default

/** Mirrors `Lua::getNumber<T>` with [default] when the argument is not a number. */
internal fun luaArgInt(args: Varargs, index: Int, default: Int = 0): Int =
    if (args.argOrNil(index).isnumber()) args.arg(index).toint() else default

/** Mirrors `Lua::getNumber<uint64_t>`; keeps the double result for large values. */
internal fun luaArgLong(args: Varargs, index: Int, default: Long = 0L): Long =
    if (args.argOrNil(index).isnumber()) args.arg(index).tolong() else default

/** Mirrors `Lua::getNumber<uint8_t>`: the C++ narrowing wraps modulo 256. */
internal fun luaArgUint8(args: Varargs, index: Int, default: Int = 0): Int =
    if (args.argOrNil(index).isnumber()) args.arg(index).toint() and 0xFF else default

/** Mirrors `Lua::getBoolean(value, default)`. */
internal fun luaArgBoolean(args: Varargs, index: Int, default: Boolean): Boolean =
    if (args.argOrNil(index).isboolean()) args.argOrNil(index).toboolean() else default

/** Reads a positive argument index without throwing when the stack is short. */
private fun Varargs.argOrNil(index: Int): LuaValue =
    if (index <= narg()) arg(index) else LuaValue.NIL

/**
 * Iterates a Lua table with `lua_next` semantics: [action] receives each key/value pair
 * and the cursor advances by the returned key, exactly like Canary's
 * `lua_pushnil`/`lua_next` loops (metrics attributes, KV maps, config tables).
 */
internal fun forEachTableEntry(table: LuaTable, action: (LuaValue, LuaValue) -> Unit) {
    var cursor = LuaValue.NIL
    while (true) {
        val entry = table.next(cursor)
        val key = entry.arg1()
        if (key.isnil()) {
            return
        }
        action(key, entry.arg(2))
        cursor = key
    }
}

/** Builds a 1-indexed Lua array from [values]. */
internal fun luaArrayOf(values: List<LuaValue>): LuaTable =
    LuaTable().also { table ->
        values.forEachIndexed { index, value -> table.set(index + 1, value) }
    }
