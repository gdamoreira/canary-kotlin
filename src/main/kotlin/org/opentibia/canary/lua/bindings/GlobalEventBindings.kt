package org.opentibia.canary.lua.bindings

import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.Varargs
import org.luaj.vm2.lib.VarArgFunction
import org.opentibia.canary.lua.registry.GlobalEvents
import org.opentibia.canary.lua.registry.LuaGlobalEvent
import org.opentibia.canary.lua.runtime.LuaEnvironment

/**
 * Registers the global `GlobalEvent(name)` constructor used by revscriptsys.lua
 * scripts:
 *
 *   local onlineRecord = GlobalEvent("OnlineRecord")
 *   function onlineRecord.onRecord(current, old) ... end
 *   onlineRecord:register()
 *
 * Mirrors GlobalEventFunctions::luaCreateGlobalEvent, `time`/`interval`, and the
 * revscriptsys `on<Callback>` -> `type` mapping.
 */
object GlobalEventBindings {
    private val CALLBACK_TYPES = linkedMapOf(
        "onThink" to "",
        "onTime" to "",
        "onStartup" to "startup",
        "onShutdown" to "shutdown",
        "onRecord" to "record",
        "onPeriodChange" to "periodchange",
        "onSave" to "save"
    )

    fun install(env: LuaEnvironment): GlobalEvents {
        val events = GlobalEvents()

        env.globals.set("GlobalEvent", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                val name = if (args.arg(1).isstring()) args.arg(1).tojstring() else ""
                val self = LuaTable()
                var explicitType = ""
                var interval = 0

                self.set("type", object : VarArgFunction() {
                    override fun invoke(a: Varargs): Varargs {
                        explicitType = a.arg(2).tojstring().lowercase()
                        return LuaValue.NONE
                    }
                })

                self.set("time", object : VarArgFunction() {
                    override fun invoke(a: Varargs): Varargs {
                        val parts = a.arg(2).tojstring().split(":").mapNotNull { it.toIntOrNull() }
                        val hour = parts.getOrNull(0) ?: return LuaValue.FALSE
                        if (hour < 0 || hour > 23) {
                            return LuaValue.FALSE
                        }
                        interval = hour shl 16
                        explicitType = "timer"
                        return LuaValue.TRUE
                    }
                })

                self.set("interval", object : VarArgFunction() {
                    override fun invoke(a: Varargs): Varargs {
                        interval = a.arg(2).checkint()
                        return LuaValue.NONE
                    }
                })

                self.set("register", object : VarArgFunction() {
                    override fun invoke(a: Varargs): Varargs {
                        val owner = a.arg1()
                        var eventType = explicitType
                        var callback = LuaValue.NIL
                        for ((field, type) in CALLBACK_TYPES) {
                            val candidate = owner.get(field)
                            if (candidate.isfunction()) {
                                callback = candidate
                                if (eventType.isEmpty()) {
                                    eventType = type
                                }
                                break
                            }
                        }
                        if (!callback.isfunction()) {
                            return LuaValue.FALSE
                        }
                        return LuaValue.valueOf(events.register(LuaGlobalEvent(name, eventType, interval, callback)))
                    }
                })

                return self
            }
        })

        return events
    }
}
