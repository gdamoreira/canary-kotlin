package org.opentibia.canary.lua.registry

import org.luaj.vm2.LuaValue

data class LuaTalkAction(
    val words: String,
    val separator: String,
    val onSay: LuaValue
)

/**
 * Mirrors Canary's TalkActions registry (src/lua/creature/talkaction.hpp): command
 * words keyed Lua handlers populated by `TalkAction(words):register()`.
 */
class TalkActions {
    private val actions = LinkedHashMap<String, LuaTalkAction>()

    fun register(action: LuaTalkAction): String {
        val key = action.words.lowercase()
        require(key.isNotBlank()) { "talkaction words must not be blank" }
        actions[key] = action
        return key
    }

    fun get(words: String): LuaTalkAction? = actions[words.lowercase()]
}
