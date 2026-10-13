package org.opentibia.canary.lua.registry

import org.luaj.vm2.LuaValue
import org.opentibia.canary.map.Position

/**
 * Mirrors the `Action` state captured by the Lua binding
 * (`src/lua/creature/actions.hpp`): the item/action/unique id vectors, the position
 * vector, the use-flag toggles and the `onUse` callback.
 */
data class LuaAction(
    val itemIds: List<Int>,
    val actionIds: List<Int>,
    val uniqueIds: List<Int>,
    val positions: List<Position>,
    val allowFarUse: Boolean,
    val checkLineOfSight: Boolean,
    val checkFloor: Boolean,
    val onUse: LuaValue,
)

/**
 * Mirrors Canary's Actions registry (src/lua/creature/actions.hpp): Lua action handlers
 * keyed by item id, unique id, action id and position, populated by
 * `Action():register()`. Mirrors `Actions::registerLuaEvent`'s
 * item → unique → action → position evaluation order; re-registering an id replaces the
 * previous handler (the Kotlin registry's established semantics).
 */
class Actions {
    private val itemEvents = LinkedHashMap<Int, LuaAction>()
    private val uniqueEvents = LinkedHashMap<Int, LuaAction>()
    private val actionEvents = LinkedHashMap<Int, LuaAction>()
    private val positionEvents = LinkedHashMap<Position, LuaAction>()

    fun register(action: LuaAction): Boolean =
        registerLuaItemEvent(action) ||
            registerLuaUniqueEvent(action) ||
            registerLuaActionEvent(action) ||
            registerLuaPositionEvent(action)

    fun get(id: Int): LuaAction? = itemEvents[id]

    fun getByUniqueId(uniqueId: Int): LuaAction? = uniqueEvents[uniqueId]

    fun getByActionId(actionId: Int): LuaAction? = actionEvents[actionId]

    fun getByPosition(position: Position): LuaAction? = positionEvents[position]

    fun ids(): Set<Int> = itemEvents.keys.toSet()

    private fun registerLuaItemEvent(action: LuaAction): Boolean {
        if (action.itemIds.isEmpty()) {
            return false
        }
        action.itemIds.forEach { itemEvents[it] = action }
        return true
    }

    private fun registerLuaUniqueEvent(action: LuaAction): Boolean {
        if (action.uniqueIds.isEmpty()) {
            return false
        }
        action.uniqueIds.forEach { uniqueEvents[it] = action }
        return true
    }

    private fun registerLuaActionEvent(action: LuaAction): Boolean {
        if (action.actionIds.isEmpty()) {
            return false
        }
        action.actionIds.forEach { actionEvents[it] = action }
        return true
    }

    private fun registerLuaPositionEvent(action: LuaAction): Boolean {
        if (action.positions.isEmpty()) {
            return false
        }
        action.positions.forEach { positionEvents[it] = action }
        return true
    }
}
