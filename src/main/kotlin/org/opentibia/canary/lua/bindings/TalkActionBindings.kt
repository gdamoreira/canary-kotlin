package org.opentibia.canary.lua.bindings

import org.luaj.vm2.LuaError
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.Varargs
import org.luaj.vm2.lib.VarArgFunction
import org.opentibia.canary.lua.bindings.events.CallbackMethods
import org.opentibia.canary.lua.registry.LuaTalkAction
import org.opentibia.canary.lua.runtime.LuaEnvironment

/**
 * Registers the global `TalkAction(words)` constructor used by revscriptsys.lua scripts:
 *
 *   local t = TalkAction("!online")
 *   function t.onSay(player, words, param) ... end
 *   t:register()
 *
 * Mirrors TalkActionFunctions::luaCreateTalkAction, `onSay`, `groupType`, `register`,
 * `separator`, `getName`, `getDescription`, `setDescription` and `getGroupType`.
 */
object TalkActionBindings {
    /** String names accepted by `luaTalkActionGroupType` (`src/enums/account_group_type.hpp`). */
    private val GROUP_TYPE_NAMES = mapOf(
        "normal" to 1,
        "tutor" to 2,
        "seniortutor" to 3,
        "senior tutor" to 3,
        "gamemaster" to 4,
        "gm" to 4,
        "communitymanager" to 5,
        "cm" to 5,
        "community manager" to 5,
        "god" to 6,
    )

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
                var description = ""
                var groupType = LuaTalkAction.GROUP_TYPE_NONE

                val self = LuaTable()
                self.set("words", LuaValue.valueOf(words))

                val callbacks = CallbackMethods()
                callbacks.install(self, "onSay")

                self.set("separator", object : VarArgFunction() {
                    override fun invoke(a: Varargs): Varargs {
                        separator = a.arg(2).checkjstring()
                        return LuaValue.NONE
                    }
                })

                self.set("groupType", object : VarArgFunction() {
                    override fun invoke(a: Varargs): Varargs {
                        val value = a.arg(2)
                        groupType = when {
                            value.isnumber() -> value.checkint()
                            value.isstring() -> GROUP_TYPE_NAMES[value.tojstring()] ?: return LuaValue.FALSE
                            else -> return LuaValue.FALSE
                        }
                        return LuaValue.TRUE
                    }
                })

                self.set("getName", object : VarArgFunction() {
                    override fun invoke(a: Varargs): Varargs = LuaValue.valueOf(words)
                })

                self.set("getDescription", object : VarArgFunction() {
                    override fun invoke(a: Varargs): Varargs = LuaValue.valueOf(description)
                })

                self.set("setDescription", object : VarArgFunction() {
                    override fun invoke(a: Varargs): Varargs {
                        description = a.arg(2).checkjstring()
                        return LuaValue.TRUE
                    }
                })

                self.set("getGroupType", object : VarArgFunction() {
                    override fun invoke(a: Varargs): Varargs = LuaValue.valueOf(groupType)
                })

                self.set("register", object : VarArgFunction() {
                    override fun invoke(a: Varargs): Varargs {
                        val onSay = callbacks.resolve(a.arg1(), "onSay")
                        if (!onSay.isfunction()) {
                            throw LuaError("TalkAction:register() called without an onSay function")
                        }
                        registry.register(LuaTalkAction(words, separator, onSay, groupType))
                        return LuaValue.valueOf(words)
                    }
                })

                return self
            }
        })
    }
}
