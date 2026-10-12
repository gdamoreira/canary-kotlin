package org.opentibia.canary.lua.bindings

import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.Varargs
import org.luaj.vm2.lib.VarArgFunction
import org.luaj.vm2.lib.ZeroArgFunction
import org.opentibia.canary.lua.registry.LuaMoveEvent
import org.opentibia.canary.lua.registry.MoveEvents
import org.opentibia.canary.lua.runtime.LuaEnvironment

/**
 * Registers the global `MoveEvent()` constructor used by revscriptsys.lua scripts:
 *
 *   local trap = MoveEvent()
 *   function trap.onStepIn(creature, item, position, fromPosition) ... end
 *   trap:type("stepin")
 *   trap:id(2145, 2148)
 *   trap:register()
 *
 * Mirrors MoveEventFunctions::luaCreateMoveEvent, `id`/`aid`/`uid`, and the
 * revscriptsys `on<Callback>` -> `type` mapping.
 */
object MoveEventBindings {
    private val CALLBACK_TYPES = linkedMapOf(
        "onEquip" to "equip",
        "onDeEquip" to "deequip",
        "onAddItem" to "additem",
        "onRemoveItem" to "removeitem",
        "onStepIn" to "stepin",
        "onStepOut" to "stepout"
    )

    fun install(env: LuaEnvironment): MoveEvents {
        val events = MoveEvents()

        env.globals.set("MoveEvent", object : ZeroArgFunction() {
            override fun call(): LuaValue {
                val self = LuaTable()
                var explicitType = ""
                val itemIds = mutableListOf<Int>()
                val actionIds = mutableListOf<Int>()
                val uniqueIds = mutableListOf<Int>()

                self.set("type", object : VarArgFunction() {
                    override fun invoke(a: Varargs): Varargs {
                        explicitType = a.arg(2).tojstring().lowercase()
                        return LuaValue.NONE
                    }
                })

                self.set("id", idCollector(itemIds))
                self.set("aid", idCollector(actionIds))
                self.set("uid", idCollector(uniqueIds))

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
                        val event = LuaMoveEvent(
                            eventType,
                            itemIds.toList(),
                            actionIds.toList(),
                            uniqueIds.toList(),
                            callback
                        )
                        return LuaValue.valueOf(events.register(event))
                    }
                })

                return self
            }
        })

        return events
    }

    private fun idCollector(target: MutableList<Int>): LuaValue = object : VarArgFunction() {
        override fun invoke(a: Varargs): Varargs {
            for (i in 2..a.narg()) {
                target.add(a.arg(i).checkint())
            }
            return LuaValue.TRUE
        }
    }
}
