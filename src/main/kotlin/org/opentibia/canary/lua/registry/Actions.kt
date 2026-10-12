package org.opentibia.canary.lua.registry

import org.luaj.vm2.LuaValue

data class LuaAction(
    val id: Int,
    val allowFarUse: Boolean,
    val onUse: LuaValue
)

/**
 * Mirrors Canary's Actions registry (src/lua/creature/actions.hpp): item-id keyed
 * Lua action handlers populated by `Action():register()`.
 */
class Actions {
    private val actions = LinkedHashMap<Int, LuaAction>()

    fun register(action: LuaAction): Int {
        require(action.id > 0) { "action id must be positive" }
        actions[action.id] = action
        return action.id
    }

    fun get(id: Int): LuaAction? = actions[id]

    fun ids(): Set<Int> = actions.keys.toSet()
}
