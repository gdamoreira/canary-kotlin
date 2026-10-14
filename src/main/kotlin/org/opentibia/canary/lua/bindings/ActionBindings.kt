package org.opentibia.canary.lua.bindings

import org.luaj.vm2.LuaError
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.Varargs
import org.luaj.vm2.lib.VarArgFunction
import org.luaj.vm2.lib.ZeroArgFunction
import org.opentibia.canary.lua.bindings.core.luaToPosition
import org.opentibia.canary.lua.bindings.events.CallbackMethods
import org.opentibia.canary.lua.registry.LuaAction
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.Position

/**
 * Registers the global `Action()` constructor used by revscriptsys.lua scripts:
 *
 *   local a = Action()
 *   a:id(3483)
 *   function a.onUse(player, item, fromPosition, target, toPosition, isHotkey) ... end
 *   a:register()
 *
 * Mirrors ActionFunctions::luaCreateAction, `onUse`, `register`, `id`, `aid`, `uid`,
 * `position`, `allowFarUse`, `blockWalls` and `checkFloor`.
 *
 * PARITY GAP: `Action:position(positions, itemIdOrName)` does not reproduce Canary's
 * `g_game().setCreateLuaItems(position, itemId)` item creation for a trailing id/name
 * argument; the position is stored for the position index instead.
 */
object ActionBindings {
    fun install(env: LuaEnvironment) {
        val actions = env.actions

        env.userdata.registerClass("Action", "", object : ZeroArgFunction() {
            override fun call(): LuaValue {
                val self = LuaTable()
                val itemIds = mutableListOf<Int>()
                val actionIds = mutableListOf<Int>()
                val uniqueIds = mutableListOf<Int>()
                val positions = mutableListOf<Position>()
                var allowFarUse = false
                var checkLineOfSight = true
                var checkFloor = true
                val callbacks = CallbackMethods()

                self.set("id", idCollector(itemIds))
                self.set("aid", idCollector(actionIds))
                self.set("uid", idCollector(uniqueIds))

                self.set("position", object : VarArgFunction() {
                    override fun invoke(a: Varargs): Varargs {
                        val first = a.arg(2)
                        if (first.isnumber() && a.narg() > 2) {
                            for (i in 2..a.narg()) {
                                positions.add(luaToPosition(a.arg(i)) ?: return LuaValue.FALSE)
                            }
                        } else {
                            positions.add(luaToPosition(first) ?: return LuaValue.FALSE)
                        }
                        return LuaValue.TRUE
                    }
                })

                callbacks.install(self, "onUse")

                self.set("allowFarUse", object : VarArgFunction() {
                    override fun invoke(a: Varargs): Varargs {
                        allowFarUse = a.arg(2).checkboolean()
                        return LuaValue.TRUE
                    }
                })

                self.set("blockWalls", object : VarArgFunction() {
                    override fun invoke(a: Varargs): Varargs {
                        checkLineOfSight = a.arg(2).checkboolean()
                        return LuaValue.TRUE
                    }
                })

                self.set("checkFloor", object : VarArgFunction() {
                    override fun invoke(a: Varargs): Varargs {
                        checkFloor = a.arg(2).checkboolean()
                        return LuaValue.TRUE
                    }
                })

                self.set("register", object : VarArgFunction() {
                    override fun invoke(a: Varargs): Varargs {
                        val owner = a.arg1()
                        val onUse = callbacks.resolve(owner, "onUse")
                        if (!onUse.isfunction()) {
                            throw LuaError("Action:register() called without an onUse function")
                        }
                        if (itemIds.isEmpty() && actionIds.isEmpty() && uniqueIds.isEmpty() && positions.isEmpty()) {
                            throw LuaError("Action:register() called without a valid id")
                        }
                        val registered = actions.register(
                            LuaAction(
                                itemIds = itemIds.toList(),
                                actionIds = actionIds.toList(),
                                uniqueIds = uniqueIds.toList(),
                                positions = positions.toList(),
                                allowFarUse = allowFarUse,
                                checkLineOfSight = checkLineOfSight,
                                checkFloor = checkFloor,
                                onUse = onUse,
                            )
                        )
                        return LuaValue.varargsOf(LuaValue.valueOf(registered), LuaValue.TRUE)
                    }
                })

                return self
            }
        })
    }

    /** Mirrors `Action:id`/`aid`/`uid`: every argument is appended to the id vector. */
    private fun idCollector(target: MutableList<Int>): LuaValue = object : VarArgFunction() {
        override fun invoke(a: Varargs): Varargs {
            if (a.narg() < 2) {
                throw LuaError("Action id requires at least one number")
            }
            for (i in 2..a.narg()) {
                target.add(a.arg(i).checkint())
            }
            return LuaValue.TRUE
        }
    }
}
