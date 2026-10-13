package org.opentibia.canary.lua.bindings.events

import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.Varargs
import org.luaj.vm2.lib.VarArgFunction

/**
 * Installs the `on<Callback>(callback)` setter methods registered by the Canary
 * event-function files and resolves the callback assigned through either shape the
 * revscriptsys library supports:
 *
 *   event:onDeath(fn)          -- the registered method
 *   function event.onDeath()   -- a raw field assignment
 *
 * The setter mirrors Canary's `loadScriptId()` contract: it captures the callback and
 * returns `true`, or returns `false` when the argument is not a function. The method
 * itself is never resolved as the callback.
 */
internal class CallbackMethods {
    private val setters = mutableMapOf<String, LuaValue>()
    private val captured = mutableMapOf<String, LuaValue>()

    fun install(table: LuaTable, field: String) {
        val setter = object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                val callback = args.arg(2)
                if (!callback.isfunction()) {
                    return LuaValue.FALSE
                }
                captured[field] = callback
                return LuaValue.TRUE
            }
        }
        setters[field] = setter
        table.set(field, setter)
    }

    /** Returns the callback for [field], or nil when neither shape assigned one. */
    fun resolve(table: LuaValue, field: String): LuaValue =
        captured[field]
            ?: table.get(field).takeIf { it !== setters[field] }
            ?: LuaValue.NIL
}
