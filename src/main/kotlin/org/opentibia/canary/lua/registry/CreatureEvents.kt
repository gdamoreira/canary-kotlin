package org.opentibia.canary.lua.registry

import org.luaj.vm2.LuaValue

data class LuaCreatureEvent(
    val name: String,
    val eventType: String,
    val callback: LuaValue
)

/**
 * Mirrors Canary's CreatureEvents registry (src/lua/creature/creatureevent.hpp):
 * named Lua handlers populated by `CreatureEvent(name):register()`.
 */
class CreatureEvents {
    private val events = LinkedHashMap<String, LuaCreatureEvent>()

    fun register(event: LuaCreatureEvent): Boolean {
        if (event.eventType.isEmpty() || events.containsKey(event.name)) {
            return false
        }
        events[event.name] = event
        return true
    }

    fun get(name: String): LuaCreatureEvent? = events[name]

    fun names(): Set<String> = events.keys.toSet()
}
