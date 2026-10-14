package org.opentibia.canary.lua.bindings.creatures

import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.opentibia.canary.lua.bindings.core.luaFunction
import org.opentibia.canary.lua.bindings.core.luaUserdataCompare
import org.opentibia.canary.lua.runtime.LuaEnvironment

/**
 * Converts a nullable seam result to the Lua value Canary would push: booleans,
 * numbers and strings as themselves, everything else as `nil` (each binding only
 * forwards the primitive types its C++ counterpart pushes).
 */
internal fun luaResult(value: Any?): LuaValue = when (value) {
    null -> LuaValue.NIL
    is Boolean -> LuaValue.valueOf(value)
    is Int -> LuaValue.valueOf(value)
    is Long -> LuaValue.valueOf(value.toDouble())
    is Double -> LuaValue.valueOf(value)
    is String -> LuaValue.valueOf(value)
    else -> LuaValue.NIL
}

/**
 * Mirrors `Lua::registerMetaMethod(L, typeName, "__eq", Lua::luaUserdataCompare)`:
 * equal only when both operands are userdata of the same class wrapping the same
 * engine instance, exactly like the C++ raw-pointer comparison.
 */
internal fun registerUserdataEquality(env: LuaEnvironment, typeName: String) {
    env.userdata.registerMetaMethod(typeName, "__eq", luaUserdataCompare)
}

/**
 * Pushes a Canary `Player[]` table, mirroring the `lua_createtable` +
 * `pushUserdata<Player>` + `setMetatable(..., "Player")` sequence shared by
 * `GuildFunctions::luaGuildGetMembersOnline`, `PartyFunctions::luaPartyGetMembers`
 * and `PartyFunctions::luaPartyGetInvitees`.
 */
internal fun playerListTable(env: LuaEnvironment, players: List<Any>): LuaTable =
    LuaTable().also { table ->
        players.forEachIndexed { index, player ->
            table.set(index + 1, env.userdata.wrap("Player", player))
        }
    }
