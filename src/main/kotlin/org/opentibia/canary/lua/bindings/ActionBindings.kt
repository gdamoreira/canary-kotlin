package org.opentibia.canary.lua.bindings

import org.luaj.vm2.LuaError
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.Varargs
import org.luaj.vm2.lib.VarArgFunction
import org.luaj.vm2.lib.ZeroArgFunction
import org.opentibia.canary.lua.registry.LuaAction
import org.opentibia.canary.lua.runtime.LuaEnvironment

/**
 * Registers the global `Action()` constructor used by revscriptsys.lua scripts:
 *
 *   local a = Action()
 *   a:id(3483)
 *   function a.onUse(player, item, fromPosition, target, toPosition, isHotkey) ... end
 *   a:register()
 */
object ActionBindings {
    fun install(env: LuaEnvironment) {
        val actions = env.actions

        env.globals.set("Action", object : ZeroArgFunction() {
            override fun call(): LuaValue {
                val self = LuaTable()
                var actionId = 0
                var allowFarUse = false

                self.set("id", object : VarArgFunction() {
                    override fun invoke(args: Varargs): Varargs {
                        actionId = args.arg(2).toint()
                        return LuaValue.NONE
                    }
                })

                self.set("allowFarUse", object : VarArgFunction() {
                    override fun invoke(args: Varargs): Varargs {
                        allowFarUse = args.arg(2).toboolean()
                        return LuaValue.NONE
                    }
                })

                self.set("register", object : VarArgFunction() {
                    override fun invoke(args: Varargs): Varargs {
                        if (actionId <= 0) {
                            throw LuaError("Action:register() called without a valid id")
                        }
                        val onUse = args.arg1().get("onUse")
                        if (!onUse.isfunction()) {
                            throw LuaError("Action:register() called without an onUse function")
                        }
                        actions.register(LuaAction(actionId, allowFarUse, onUse))
                        return LuaValue.valueOf(actionId)
                    }
                })

                return self
            }
        })
    }
}
