package org.opentibia.canary.lua.bindings

import org.luaj.vm2.LuaError
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.Varargs
import org.luaj.vm2.lib.VarArgFunction
import org.opentibia.canary.lua.registry.LuaTalkAction
import org.opentibia.canary.lua.runtime.LuaEnvironment

/**
 * Registers the global `TalkAction(words [, separator])` constructor used by
 * revscriptsys.lua scripts:
 *
 *   local t = TalkAction("!online")
 *   function t.onSay(player, words, param) ... end
 *   t:register()
 */
object TalkActionBindings {
    fun install(env: LuaEnvironment) {
        val registry = env.talkActions

        env.globals.set("TalkAction", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                val wordsValue = args.arg(1)
                if (!wordsValue.isstring()) {
                    throw LuaError("TalkAction: words must be a string")
                }
                val words = wordsValue.tojstring()
                var separator = if (args.narg() >= 2 && args.arg(2).isstring()) {
                    args.arg(2).tojstring()
                } else {
                    " "
                }

                val self = LuaTable()
                self.set("words", LuaValue.valueOf(words))

                self.set("separator", object : VarArgFunction() {
                    override fun invoke(a: Varargs): Varargs {
                        separator = a.arg(2).checkjstring()
                        return LuaValue.NONE
                    }
                })

                self.set("register", object : VarArgFunction() {
                    override fun invoke(a: Varargs): Varargs {
                        val onSay = a.arg1().get("onSay")
                        if (!onSay.isfunction()) {
                            throw LuaError("TalkAction:register() called without an onSay function")
                        }
                        registry.register(LuaTalkAction(words, separator, onSay))
                        return LuaValue.valueOf(words)
                    }
                })

                return self
            }
        })
    }
}
