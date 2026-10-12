package org.opentibia.canary.scripting

import org.opentibia.canary.creatures.Player
import org.opentibia.canary.items.Item
import org.opentibia.canary.map.Position

typealias ActionCallback = (player: Player, item: Item, position: Position) -> Boolean
typealias TalkActionCallback = (player: Player, words: String, param: String) -> Boolean

class ScriptRegistry {
    private val actionScripts = mutableMapOf<Int, ActionCallback>()
    private val talkActions = mutableMapOf<String, TalkActionCallback>()

    fun registerAction(itemId: Int, callback: ActionCallback) {
        actionScripts[itemId] = callback
    }

    fun executeAction(player: Player, item: Item, position: Position): Boolean {
        val callback = actionScripts[item.id] ?: return false
        return callback(player, item, position)
    }

    fun registerTalkAction(words: String, callback: TalkActionCallback) {
        talkActions[words.lowercase()] = callback
    }

    fun executeTalkAction(player: Player, words: String, param: String): Boolean {
        val callback = talkActions[words.lowercase()] ?: return false
        return callback(player, words, param)
    }
}
