package org.opentibia.canary.lua

import org.luaj.vm2.Globals
import org.luaj.vm2.LuaValue
import org.luaj.vm2.lib.OneArgFunction
import org.luaj.vm2.lib.TwoArgFunction
import org.luaj.vm2.lib.jse.CoerceJavaToLua
import org.luaj.vm2.lib.jse.JsePlatform
import org.opentibia.canary.creatures.Player

class LuaScriptBridge {
    private val globals: Globals = JsePlatform.standardGlobals()

    init {
        // Expose helper functions or userdata metatables
    }

    fun loadScript(scriptContent: String) {
        val chunk = globals.load(scriptContent)
        chunk.call()
    }

    fun invokeOnUse(player: Player): Boolean {
        val onUseFunc = globals.get("onUse")
        if (onUseFunc.isfunction()) {
            val playerLua = CoerceJavaToLua.coerce(player)
            val result = onUseFunc.call(playerLua)
            return result.toboolean()
        }
        return false
    }
}
