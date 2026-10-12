package org.opentibia.canary.lua.userdata

import org.luaj.vm2.Globals
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue

/**
 * Registry of Canary-named userdata method tables ("Player", "Monster", "Npc",
 * "Item", "Container", "Teleport"). Each metatable stores its methods directly and
 * sets `__index` to itself, matching how Canary's Lua userdata behaves so that
 * `getmetatable(self)` returns the method table used by revscriptsys.lua.
 */
class UserdataRegistry(private val globals: Globals) {
    private val metatables: LuaTable = (globals.get("__metatables__") as? LuaTable)
        ?: LuaTable().also { globals.set("__metatables__", it) }

    fun metatable(typeName: String): LuaTable {
        val existing = metatables.get(typeName)
        if (existing is LuaTable) {
            return existing
        }
        val table = LuaTable()
        table.set("__index", table)
        metatables.set(typeName, table)
        return table
    }

    fun registerMethod(typeName: String, methodName: String, fn: LuaValue) {
        metatable(typeName).set(methodName, fn)
    }

    fun wrap(typeName: String, obj: Any): LuaValue = LuaValue.userdataOf(obj, metatable(typeName))
}
