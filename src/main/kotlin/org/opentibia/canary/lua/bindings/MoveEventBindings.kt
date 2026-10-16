package org.opentibia.canary.lua.bindings

import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.Varargs
import org.luaj.vm2.lib.VarArgFunction
import org.luaj.vm2.lib.ZeroArgFunction
import org.opentibia.canary.lua.bindings.events.CallbackMethods
import org.opentibia.canary.lua.registry.LuaMoveEvent
import org.opentibia.canary.lua.registry.MoveEvents
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.Position
import org.opentibia.canary.lua.bindings.core.luaToPosition

/**
 * Registers the global `MoveEvent()` constructor used by revscriptsys.lua scripts:
 *
 *   local trap = MoveEvent()
 *   function trap.onStepIn(creature, item, position, fromPosition) ... end
 *   trap:type("stepin")
 *   trap:id(2145, 2148)
 *   trap:level(10)
 *   trap:magicLevel(3)
 *   trap:slot("head")
 *   trap:vocation("knight")
 *   trap:position(Position(10, 20, 7))
 *   trap:register()
 *
 * Mirrors MoveEventFunctions::luaCreateMoveEvent, `id`/`aid`/`uid`, the
 * `level`/`magicLevel`/`slot`/`vocation`/`position` setters, and the
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

    /**
     * Mirrors the slot-name mapping in MoveEventFunctions::luaMoveEventSlot, using the
     * `SlotPositionBits` values from src/items/items_definitions.hpp.
     */
    private val SLOT_BITS = mapOf(
        "head" to (1 shl 0),
        "necklace" to (1 shl 1),
        "backpack" to (1 shl 2),
        "armor" to (1 shl 3),
        "body" to (1 shl 3),
        "right-hand" to (1 shl 4),
        "left-hand" to (1 shl 5),
        "hand" to ((1 shl 4) or (1 shl 5)),
        "shield" to ((1 shl 4) or (1 shl 5)),
        "legs" to (1 shl 6),
        "feet" to (1 shl 7),
        "ring" to (1 shl 8),
        "ammo" to (1 shl 9),
    )

    fun install(env: LuaEnvironment): MoveEvents {
        val events = MoveEvents()

        env.userdata.registerClass("MoveEvent", "", object : ZeroArgFunction() {
            override fun call(): LuaValue {
                val self = LuaTable()
                var explicitType = ""
                var requiredLevel = 0
                var requiredMagicLevel = 0
                var premium = false
                var slot = 0
                val itemIds = mutableListOf<Int>()
                val actionIds = mutableListOf<Int>()
                val uniqueIds = mutableListOf<Int>()
                val positions = mutableListOf<Position>()
                val vocations = mutableListOf<String>()
                val callbacks = CallbackMethods(env.globals)

                self.set("type", object : VarArgFunction() {
                    override fun invoke(a: Varargs): Varargs {
                        explicitType = a.arg(2).tojstring().lowercase()
                        return LuaValue.NONE
                    }
                })

                for (field in CALLBACK_TYPES.keys) {
                    callbacks.install(self, field)
                }

                self.set("id", idCollector(itemIds))
                self.set("aid", idCollector(actionIds))
                self.set("uid", idCollector(uniqueIds))

                self.set("level", object : VarArgFunction() {
                    override fun invoke(a: Varargs): Varargs {
                        requiredLevel = a.arg(2).toint()
                        return LuaValue.TRUE
                    }
                })

                self.set("magicLevel", object : VarArgFunction() {
                    override fun invoke(a: Varargs): Varargs {
                        requiredMagicLevel = a.arg(2).toint()
                        return LuaValue.TRUE
                    }
                })

                self.set("premium", object : VarArgFunction() {
                    override fun invoke(a: Varargs): Varargs {
                        premium = a.arg(2).checkboolean()
                        return LuaValue.TRUE
                    }
                })

                self.set("slot", object : VarArgFunction() {
                    override fun invoke(a: Varargs): Varargs {
                        val bits = SLOT_BITS[a.arg(2).tojstring().lowercase()] ?: return LuaValue.FALSE
                        slot = bits
                        return LuaValue.TRUE
                    }
                })

                self.set("position", object : VarArgFunction() {
                    override fun invoke(a: Varargs): Varargs {
                        for (i in 2..a.narg()) {
                            val position = luaToPosition(a.arg(i)) ?: return LuaValue.FALSE
                            positions.add(position)
                        }
                        return LuaValue.TRUE
                    }
                })

                self.set("vocation", object : VarArgFunction() {
                    override fun invoke(a: Varargs): Varargs {
                        for (i in 2..a.narg()) {
                            vocations.add(a.arg(i).tojstring())
                        }
                        return LuaValue.TRUE
                    }
                })

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
                        val event = LuaMoveEvent(
                            eventType,
                            itemIds.toList(),
                            actionIds.toList(),
                            uniqueIds.toList(),
                            callback,
                            requiredLevel,
                            requiredMagicLevel,
                            premium,
                            slot,
                            positions.toList(),
                            vocations.toList()
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
