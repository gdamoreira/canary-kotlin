package org.opentibia.canary.lua.runtime

import org.luaj.vm2.Globals
import org.luaj.vm2.LuaError
import org.luaj.vm2.LuaValue
import org.luaj.vm2.lib.jse.JsePlatform
import org.opentibia.canary.lua.bindings.ActionBindings
import org.opentibia.canary.lua.bindings.TalkActionBindings
import org.opentibia.canary.lua.registry.Actions
import org.opentibia.canary.lua.registry.TalkActions
import org.opentibia.canary.lua.userdata.UserdataRegistry

/**
 * Mirrors Canary's LuaScriptInterface: owns a single LuaJ [Globals], preloads the
 * compatibility shim, and installs the Canary-named binding libraries.
 */
class LuaEnvironment {
    val globals: Globals = JsePlatform.standardGlobals()

    lateinit var userdata: UserdataRegistry
        private set

    val actions = Actions()
    val talkActions = TalkActions()

    private val libraries = mutableListOf<() -> LuaValue>()

    fun registerLib(name: String, fetch: () -> LuaValue) {
        libraries.add(fetch)
    }

    fun initState() {
        loadCompatShim()
        userdata = UserdataRegistry(globals)
        ActionBindings.install(this)
        TalkActionBindings.install(this)
        libraries.forEach { it() }
    }

    fun loadChunk(chunk: String, chunkName: String = "=(chunk)"): LuaValue {
        val compiled = try {
            globals.load(chunk, chunkName)
        } catch (e: LuaError) {
            throw toScriptError(e, chunkName)
        }
        return try {
            compiled.call()
        } catch (e: LuaError) {
            throw toScriptError(e, chunkName)
        }
    }

    fun getGlobal(name: String): LuaValue = globals.get(name)

    private fun loadCompatShim() {
        val stream = javaClass.classLoader.getResourceAsStream(COMPAT_RESOURCE)
            ?: throw IllegalStateException("Missing $COMPAT_RESOURCE on classpath")
        val source = stream.bufferedReader().use { it.readText() }
        try {
            globals.load(source, "@$COMPAT_RESOURCE").call()
        } catch (e: LuaError) {
            throw toScriptError(e, COMPAT_RESOURCE)
        }
    }

    private fun toScriptError(error: LuaError, chunkName: String): ScriptError {
        val message = error.message ?: "lua error"
        val line = LINE_PATTERN.find(message)?.groupValues?.get(1)?.toIntOrNull() ?: -1
        return ScriptError(message, chunkName, line)
    }

    private companion object {
        const val COMPAT_RESOURCE = "lua/compat.luaj.lua"
        val LINE_PATTERN = Regex(":(\\d+):")
    }
}
