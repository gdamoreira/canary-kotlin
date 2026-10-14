package org.opentibia.canary.lua.bindings.events

import org.luaj.vm2.LuaError
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.Varargs
import org.luaj.vm2.lib.VarArgFunction
import org.opentibia.canary.lua.registry.EventCallbackType
import org.opentibia.canary.lua.registry.LuaEventCallback
import org.opentibia.canary.lua.runtime.LuaEnvironment

/**
 * Registers the `EventCallback` constructor plus its `type`/`register` methods and the
 * `luaEventCallbackLoad` loader exposed through `Game.getEventCallbacks()`, mirroring
 * `src/lua/functions/events/event_callback_functions.cpp`.
 *
 *   local callback = EventCallback("PlayerLookWatcher")
 *   Game.getEventCallbacks().playerOnLook(callback, function(player) ... end)
 *   callback:type("playerOnLook")
 *   callback:register()
 *
 * The instance carries its state in fields (`__name`, `__callback`, `__type`, ...).
 *
 * PARITY GAP: Canary registers `EventCallback` as shared userdata whose metatable
 * revscriptsys.lua augments with an `EventCallbackNewIndex` hook. That hook logs through
 * the not-yet-ported `logger` global, so the Kotlin instance is a plain table and
 * `register()` resolves a raw `function callback.<enumName>` field itself.
 */
object EventCallbackFunctions {
    private const val NAME_FIELD = "__name"
    private const val CALLBACK_FIELD = "__callback"
    private const val TYPE_FIELD = "__type"
    private const val SCRIPT_TYPE_NAME_FIELD = "__scriptTypeName"
    private const val SKIP_DUPLICATION_FIELD = "__skipDuplicationCheck"

    /**
     * Installs the bindings for [env] and returns the `luaEventCallbackLoad` table
     * `Game.getEventCallbacks()` must expose (every enum name except `none`, matching
     * `GameFunctions::luaGameGetEventCallbacks`).
     */
    fun install(env: LuaEnvironment): Map<String, LuaValue> {
        val typeMethod = object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                val self = args.arg1()
                val typeName = args.arg(2).tojstring()
                val resolved = EventCallbackType.fromName(typeName) ?: return LuaValue.FALSE
                self.set(TYPE_FIELD, LuaValue.valueOf(resolved.name))
                self.set(SCRIPT_TYPE_NAME_FIELD, LuaValue.valueOf(typeName))
                return LuaValue.TRUE
            }
        }

        val registerMethod = object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                val self = args.arg1()
                val (callback, type) = resolveCallback(self)
                if (!callback.isfunction() || type == null) {
                    return LuaValue.NONE
                }
                val entry = LuaEventCallback(
                    name = self.get(NAME_FIELD).tojstring(),
                    type = type,
                    scriptTypeName = self.get(SCRIPT_TYPE_NAME_FIELD).tojstring(),
                    skipDuplicationCheck = self.get(SKIP_DUPLICATION_FIELD).toboolean(),
                    callback = callback,
                )
                if (env.eventCallbacks.isCallbackRegistered(entry)) {
                    return LuaValue.NONE
                }
                env.eventCallbacks.addCallback(entry)
                return LuaValue.TRUE
            }
        }

        env.userdata.registerClass("EventCallback", "", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                val name = args.arg(1)
                if (!name.isstring() || name.tojstring().isEmpty()) {
                    throw LuaError("Invalid callback name")
                }
                val self = LuaTable()
                self.set("type", typeMethod)
                self.set("register", registerMethod)
                self.set(NAME_FIELD, LuaValue.valueOf(name.tojstring()))
                self.set(SKIP_DUPLICATION_FIELD, LuaValue.valueOf(args.arg(2).optboolean(false)))
                self.set(CALLBACK_FIELD, LuaValue.NIL)
                self.set(TYPE_FIELD, LuaValue.NIL)
                self.set(SCRIPT_TYPE_NAME_FIELD, LuaValue.valueOf(""))
                return self
            }
        })

        return EventCallbackType.entries
            .filter { it != EventCallbackType.none }
            .associate { it.name to loader }
    }

    /**
     * Mirrors `EventCallbackFunctions::luaEventCallbackLoad`: captures the callback
     * passed by revscriptsys (`func(self, value)`) as the instance script id.
     */
    private val loader = object : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs {
            val self = args.arg(1)
            val callback = args.arg(2)
            if (!self.istable() || !callback.isfunction()) {
                return LuaValue.NONE
            }
            self.set(CALLBACK_FIELD, callback)
            return LuaValue.TRUE
        }
    }

    /**
     * Resolves the instance callback and type: the loader/explicit `type()` pair first,
     * then a raw callback field named after an enum value (the shape a script produces
     * when the revscriptsys metatable hook is bypassed).
     */
    private fun resolveCallback(self: LuaValue): Pair<LuaValue, EventCallbackType?> {
        val callback = self.get(CALLBACK_FIELD)
        val typeName = self.get(TYPE_FIELD)
        if (callback.isfunction() && typeName.isstring()) {
            return callback to EventCallbackType.fromName(typeName.tojstring())
        }
        for (type in EventCallbackType.entries) {
            if (type == EventCallbackType.none) {
                continue
            }
            val field = self.get(type.name)
            if (field.isfunction()) {
                return field to type
            }
        }
        return LuaValue.NIL to null
    }
}
