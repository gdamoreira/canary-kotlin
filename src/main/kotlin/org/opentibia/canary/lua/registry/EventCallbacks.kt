package org.opentibia.canary.lua.registry

import org.luaj.vm2.LuaValue

/**
 * `EventCallback_t` (`src/lua/callbacks/callbacks_definitions.hpp`), order-preserving so
 * `Game.getEventCallbacks()` exposes the same keys Canary derives from
 * `magic_enum::enum_entries`.
 */
enum class EventCallbackType {
    none,
    creatureOnChangeOutfit,
    creatureOnAreaCombat,
    creatureOnTargetCombat,
    creatureOnDrainHealth,
    creatureOnCombat,
    partyOnJoin,
    partyOnLeave,
    partyOnDisband,
    partyOnShareExperience,
    playerOnBrowseField,
    playerOnLook,
    playerOnLookInBattleList,
    playerOnLookInTrade,
    playerOnLookInShop,
    playerOnMoveItem,
    playerOnItemMoved,
    playerOnChangeZone,
    playerOnChangeHazard,
    playerOnMoveCreature,
    playerOnReportRuleViolation,
    playerOnReportBug,
    playerOnTurn,
    playerOnTradeRequest,
    playerOnTradeAccept,
    playerOnGainExperience,
    playerOnLoseExperience,
    playerOnGainSkillTries,
    playerOnRequestQuestLog,
    playerOnRequestQuestLine,
    playerOnStorageUpdate,
    playerOnRemoveCount,
    playerOnCombat,
    playerOnInventoryUpdate,
    playerOnRotateItem,
    playerOnWalk,
    playerOnKill,
    playerOnLoginComplete,
    playerOnThink,
    monsterOnDropLoot,
    monsterPostDropLoot,
    zoneBeforeCreatureEnter,
    zoneBeforeCreatureLeave,
    zoneAfterCreatureEnter,
    zoneAfterCreatureLeave,
    mapOnLoad,
    last,
    ;

    companion object {
        /** Mirrors `luaEventCallbackType`'s `asLowerCaseString(enumName)` comparison. */
        fun fromName(name: String): EventCallbackType? {
            if (name.isEmpty()) {
                return null
            }
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
        }
    }
}

/**
 * Mirrors a registered Canary `EventCallback` (`src/lua/callbacks/event_callback.hpp`):
 * the callback name, resolved type, the script type name the script declared, the
 * duplication-check flag and the Lua callback itself.
 */
data class LuaEventCallback(
    val name: String,
    val type: EventCallbackType,
    val scriptTypeName: String,
    val skipDuplicationCheck: Boolean,
    val callback: LuaValue,
)

/**
 * Mirrors Canary's `EventsCallbacks` registry (`src/lua/callbacks/events_callbacks.hpp`):
 * callbacks grouped by type, with `isCallbackRegistered`/`addCallback` reproducing the
 * same-type + same-name duplicate guard unless the callback skips the duplication check.
 */
class EventCallbacks {
    private val callbacks = LinkedHashMap<EventCallbackType, MutableList<LuaEventCallback>>()

    fun isCallbackRegistered(callback: LuaEventCallback): Boolean {
        val registered = callbacks[callback.type] ?: return false
        return registered.any { it.name == callback.name } && !callback.skipDuplicationCheck
    }

    fun addCallback(callback: LuaEventCallback): Boolean {
        val registered = callbacks.getOrPut(callback.type) { mutableListOf() }
        if (registered.any { it.name == callback.name } && !callback.skipDuplicationCheck) {
            return false
        }
        registered.add(callback)
        return true
    }

    fun get(type: EventCallbackType): List<LuaEventCallback> = callbacks[type]?.toList() ?: emptyList()

    fun names(): Set<String> = callbacks.values.flatMap { entries -> entries.map { it.name } }.toSet()

    fun clear() {
        callbacks.clear()
    }
}
