package org.opentibia.canary.lua.bindings

import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.Varargs
import org.luaj.vm2.lib.VarArgFunction
import org.opentibia.canary.lua.bindings.events.CallbackMethods
import org.opentibia.canary.lua.registry.CreatureEvents
import org.opentibia.canary.lua.registry.LuaCreatureEvent
import org.opentibia.canary.lua.runtime.LuaEnvironment

/**
 * Registers the global `CreatureEvent(name)` constructor used by revscriptsys.lua
 * scripts:
 *
 *   local playerLogin = CreatureEvent("NamelockLogin")
 *   function playerLogin.onLogin(player) ... end
 *   playerLogin:register()
 *
 * Mirrors CreatureEventFunctions::luaCreateCreatureEvent, `type`, `register` and every
 * `on<Callback>` setter (`onLogin`, `onLogout`, `onThink`, `onPrepareDeath`, `onDeath`,
 * `onKill`, `onAdvance`, `onModalWindow`, `onTextEdit`, `onHealthChange`,
 * `onManaChange`, `onExtendedOpcode`).
 */
object CreatureEventBindings {
    private val CALLBACK_TYPES = linkedMapOf(
        "onLogin" to "login",
        "onLogout" to "logout",
        "onThink" to "think",
        "onPrepareDeath" to "preparedeath",
        "onDeath" to "death",
        "onKill" to "kill",
        "onAdvance" to "advance",
        "onModalWindow" to "modalwindow",
        "onTextEdit" to "textedit",
        "onHealthChange" to "healthchange",
        "onManaChange" to "manachange",
        "onExtendedOpcode" to "extendedopcode"
    )

    fun install(env: LuaEnvironment): CreatureEvents {
        val events = CreatureEvents()

        env.globals.set("CreatureEvent", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                val name = if (args.arg(1).isstring()) args.arg(1).tojstring() else ""
                val self = LuaTable()
                var explicitType = ""
                val callbacks = CallbackMethods()

                self.set("type", object : VarArgFunction() {
                    override fun invoke(a: Varargs): Varargs {
                        explicitType = a.arg(2).tojstring().lowercase()
                        return LuaValue.NONE
                    }
                })

                for (field in CALLBACK_TYPES.keys) {
                    callbacks.install(self, field)
                }

                self.set("register", object : VarArgFunction() {
                    override fun invoke(a: Varargs): Varargs {
                        val owner = a.arg1()
                        var eventType = explicitType
                        var callback = LuaValue.NIL
                        for ((field, type) in CALLBACK_TYPES) {
                            val candidate = callbacks.resolve(owner, field)
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
                        return LuaValue.valueOf(events.register(LuaCreatureEvent(name, eventType, callback)))
                    }
                })

                return self
            }
        })

        return events
    }
}
