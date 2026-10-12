package org.opentibia.canary.lua.registry

import org.luaj.vm2.LuaValue

data class LuaGlobalEvent(
    val name: String,
    val eventType: String,
    val interval: Int,
    val callback: LuaValue
)

/**
 * Mirrors Canary's GlobalEvents registry (src/lua/global/globalevent.hpp): named Lua
 * handlers populated by `GlobalEvent(name):register()`. An empty [eventType] mirrors
 * GLOBALEVENT_NONE; such an event is only accepted when an interval is scheduled.
 */
class GlobalEvents {
    private val events = LinkedHashMap<String, LuaGlobalEvent>()

    fun register(event: LuaGlobalEvent): Boolean {
        if (event.eventType.isEmpty() && event.interval == 0) {
            return false
        }
        if (events.containsKey(event.name)) {
            return false
        }
        events[event.name] = event
        return true
    }

    fun get(name: String): LuaGlobalEvent? = events[name]

    fun names(): Set<String> = events.keys.toSet()
}
