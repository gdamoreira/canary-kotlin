package org.opentibia.canary.lua.registry

import org.luaj.vm2.LuaValue
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemAttribute
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.Tile

data class LuaMoveEvent(
    val eventType: String,
    val itemIds: List<Int>,
    val actionIds: List<Int>,
    val uniqueIds: List<Int>,
    val callback: LuaValue,
    val requiredLevel: Int = 0,
    val requiredMagicLevel: Int = 0,
    val premium: Boolean = false,
    val slot: Int = 0,
    val positions: List<Position> = emptyList(),
    val vocations: List<String> = emptyList(),
)

/**
 * Mirrors Canary's MoveEvents registry (src/lua/creature/movement.hpp): Lua handlers
 * keyed by item id, action id, unique id and position, populated by
 * `MoveEvent():register()`.
 *
 * The port keys the registry with the Lua-visible type names; the C++
 * `MoveEvent_t` mapping (`src/lua/functions/events/move_event_functions.cpp:80-110`,
 * `src/utils/tools.cpp:1179-1197`) maps Lua `additem` to
 * `MOVE_EVENT_ADD_ITEM_ITEMTILE` and Lua `removeitem` to `MOVE_EVENT_REMOVE_ITEM`.
 */
class MoveEvents {
    private val events = mutableListOf<LuaMoveEvent>()
    private val itemIdMap = LinkedHashMap<Int, MutableList<LuaMoveEvent>>()
    private val actionIdMap = LinkedHashMap<Int, MutableList<LuaMoveEvent>>()
    private val uniqueIdMap = LinkedHashMap<Int, MutableList<LuaMoveEvent>>()
    private val positionMap = LinkedHashMap<Position, MutableList<LuaMoveEvent>>()

    fun register(event: LuaMoveEvent): Boolean {
        if (event.eventType.isEmpty()) {
            return false
        }
        if (event.itemIds.isEmpty() && event.actionIds.isEmpty() && event.uniqueIds.isEmpty() && event.positions.isEmpty()) {
            return false
        }
        event.itemIds.forEach { itemIdMap.getOrPut(it) { mutableListOf() }.add(event) }
        event.actionIds.forEach { actionIdMap.getOrPut(it) { mutableListOf() }.add(event) }
        event.uniqueIds.forEach { uniqueIdMap.getOrPut(it) { mutableListOf() }.add(event) }
        event.positions.forEach { positionMap.getOrPut(it) { mutableListOf() }.add(event) }
        events.add(event)
        return true
    }

    fun getByItemId(itemId: Int): List<LuaMoveEvent> = itemIdMap[itemId]?.toList() ?: emptyList()

    fun getByActionId(actionId: Int): List<LuaMoveEvent> = actionIdMap[actionId]?.toList() ?: emptyList()

    fun getByUniqueId(uniqueId: Int): List<LuaMoveEvent> = uniqueIdMap[uniqueId]?.toList() ?: emptyList()

    /** `MoveEvents::registerLuaPositionEvent` (movement.cpp:84-104) storage lookup. */
    fun getByPosition(position: Position): List<LuaMoveEvent> = positionMap[position]?.toList() ?: emptyList()

    fun all(): List<LuaMoveEvent> = events.toList()

    /**
     * `MoveEvents::getEvent(tile, eventType)` (movement.cpp:278-288): the first
     * position-registered event of [eventType] on the tile's position.
     */
    fun getEvent(tile: Tile, eventType: String): LuaMoveEvent? =
        positionMap[tile.position]?.firstOrNull { it.eventType == eventType }

    /**
     * `MoveEvents::getEvent(item, eventType)` (movement.cpp:230-276): the
     * unique-id registry wins over the action-id registry, which wins over the
     * item-id registry.
     */
    fun getEvent(item: Item, eventType: String): LuaMoveEvent? {
        if (item.hasAttribute(ItemAttribute.UNIQUEID)) {
            uniqueIdMap[item.getUniqueId().toInt()]
                ?.firstOrNull { it.eventType == eventType }
                ?.let { return it }
        }

        if (item.hasAttribute(ItemAttribute.ACTIONID)) {
            actionIdMap[item.getActionId()]
                ?.firstOrNull { it.eventType == eventType }
                ?.let { return it }
        }

        return itemIdMap[item.id]?.firstOrNull { it.eventType == eventType }
    }
}
