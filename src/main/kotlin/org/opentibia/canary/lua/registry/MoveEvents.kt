package org.opentibia.canary.lua.registry

import org.luaj.vm2.LuaValue

data class LuaMoveEvent(
    val eventType: String,
    val itemIds: List<Int>,
    val actionIds: List<Int>,
    val uniqueIds: List<Int>,
    val callback: LuaValue
)

/**
 * Mirrors Canary's MoveEvents registry (src/lua/creature/movement.hpp): Lua handlers
 * keyed by item id, action id and unique id, populated by `MoveEvent():register()`.
 */
class MoveEvents {
    private val events = mutableListOf<LuaMoveEvent>()
    private val itemIdMap = LinkedHashMap<Int, MutableList<LuaMoveEvent>>()
    private val actionIdMap = LinkedHashMap<Int, MutableList<LuaMoveEvent>>()
    private val uniqueIdMap = LinkedHashMap<Int, MutableList<LuaMoveEvent>>()

    fun register(event: LuaMoveEvent): Boolean {
        if (event.eventType.isEmpty()) {
            return false
        }
        if (event.itemIds.isEmpty() && event.actionIds.isEmpty() && event.uniqueIds.isEmpty()) {
            return false
        }
        event.itemIds.forEach { itemIdMap.getOrPut(it) { mutableListOf() }.add(event) }
        event.actionIds.forEach { actionIdMap.getOrPut(it) { mutableListOf() }.add(event) }
        event.uniqueIds.forEach { uniqueIdMap.getOrPut(it) { mutableListOf() }.add(event) }
        events.add(event)
        return true
    }

    fun getByItemId(itemId: Int): List<LuaMoveEvent> = itemIdMap[itemId]?.toList() ?: emptyList()

    fun getByActionId(actionId: Int): List<LuaMoveEvent> = actionIdMap[actionId]?.toList() ?: emptyList()

    fun getByUniqueId(uniqueId: Int): List<LuaMoveEvent> = uniqueIdMap[uniqueId]?.toList() ?: emptyList()

    fun all(): List<LuaMoveEvent> = events.toList()
}
