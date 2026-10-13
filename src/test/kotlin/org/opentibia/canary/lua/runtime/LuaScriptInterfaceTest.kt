package org.opentibia.canary.lua.runtime

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.luaj.vm2.Globals
import org.luaj.vm2.LuaValue
import org.luaj.vm2.lib.ZeroArgFunction
import org.luaj.vm2.lib.jse.JsePlatform
import java.nio.file.Files
import java.nio.file.Path

/**
 * Transcribes `LuaScriptInterface` (`src/lua/scripts/luascript.cpp`): protected calls
 * with the traceback error handler, the event-table capture/clear flow, `loadFile`,
 * `getFileById`, `callFunction`/`callVoidFunction` and the reload/close lifecycle.
 */
class LuaScriptInterfaceTest {

    @TempDir
    lateinit var dir: Path

    @BeforeEach
    fun resetSharedScriptEnvironments() {
        while (LuaScriptInterface.scriptEnvironments.scriptEnvIndex >= 0) {
            LuaScriptInterface.resetScriptEnv()
        }
    }

    private fun debugState(): Globals = JsePlatform.debugGlobals()

    private fun interfaceFor(state: Globals): LuaScriptInterface =
        LuaScriptInterface("Test Interface", stateProvider = { state })

    private fun writeChunk(name: String, source: String): Path =
        dir.resolve(name).also { Files.writeString(it, source) }

    @Test
    fun `initState creates the event table and starts at EVENT_ID_USER`() {
        val scriptInterface = interfaceFor(debugState())

        assertThat(scriptInterface.initState()).isTrue()

        assertThat(scriptInterface.eventTableRef).isNotEqualTo(-1)
        assertThat(scriptInterface.runningEventId).isEqualTo(LuaScriptInterface.EVENT_ID_USER)
        assertThat(scriptInterface.getInterfaceName()).isEqualTo("Test Interface")
    }

    @Test
    fun `initState fails without a lua state`() {
        val scriptInterface = LuaScriptInterface("Test Interface")

        assertThat(scriptInterface.initState()).isFalse()
        assertThat(scriptInterface.getLuaState()).isNull()
    }

    @Test
    fun `closeState drops the cached files and the state`() {
        val state = debugState()
        val scriptInterface = interfaceFor(state)
        scriptInterface.initState()
        val file = writeChunk("close.lua", "onLogin = function() return true end")
        assertThat(scriptInterface.loadFile(file, "close.lua")).isEqualTo(0)
        val eventId = scriptInterface.getEvent("onLogin")

        assertThat(scriptInterface.closeState()).isTrue()

        assertThat(scriptInterface.getLuaState()).isNull()
        assertThat(scriptInterface.eventTableRef).isEqualTo(-1)
        assertThat(scriptInterface.getFileById(eventId)).isEqualTo("(Unknown scriptfile)")
        assertThat(scriptInterface.closeState()).isFalse()
        assertThat(scriptInterface.loadFile(file, "close.lua")).isEqualTo(-1)
    }

    @Test
    fun `reInitState rebuilds the event table over the shared state`() {
        val state = debugState()
        val scriptInterface = interfaceFor(state)
        scriptInterface.initState()
        val file = writeChunk("reload.lua", "onLogin = function() return true end")
        scriptInterface.loadFile(file, "reload.lua")
        val eventId = scriptInterface.getEvent("onLogin")

        assertThat(scriptInterface.reInitState()).isTrue()

        assertThat(scriptInterface.getLuaState()).isSameAs(state)
        assertThat(scriptInterface.runningEventId).isEqualTo(LuaScriptInterface.EVENT_ID_USER)
        assertThat(scriptInterface.getFileById(eventId)).isEqualTo("(Unknown scriptfile)")
        assertThat(scriptInterface.pushFunction(eventId).isnil()).isTrue()
    }

    @Test
    fun `loadFile executes the chunk under the loading script id`() {
        val state = debugState()
        val scriptInterface = interfaceFor(state)
        scriptInterface.initState()
        var observedScriptId = -1
        state.set("observe_script_id", object : ZeroArgFunction() {
            override fun call(): LuaValue {
                observedScriptId = LuaScriptInterface.getScriptEnv()?.getScriptId() ?: -1
                return LuaValue.NONE
            }
        })
        val file = writeChunk("loading.lua", "observe_script_id()")

        assertThat(scriptInterface.loadFile(file, "loading.lua")).isEqualTo(0)

        assertThat(observedScriptId).isEqualTo(LuaScriptInterface.EVENT_ID_LOADING)
        assertThat(LuaScriptInterface.scriptEnvironments.scriptEnvIndex).isEqualTo(-1)
    }

    @Test
    fun `loadFile records the loading file and script name`() {
        val state = debugState()
        val scriptInterface = interfaceFor(state)
        scriptInterface.initState()
        val file = writeChunk("named.lua", "loaded_value = 7")

        assertThat(scriptInterface.loadFile(file, "named.lua")).isEqualTo(0)

        assertThat(state.get("loaded_value").toint()).isEqualTo(7)
        assertThat(scriptInterface.getLoadingFile()).isEqualTo(file.toString())
        assertThat(scriptInterface.getLoadingScriptName()).isEqualTo("named.lua")
        assertThat(scriptInterface.getFileById(LuaScriptInterface.EVENT_ID_LOADING)).isEqualTo(file.toString())
    }

    @Test
    fun `loadFile stores the load error and returns minus one`() {
        val state = debugState()
        val scriptInterface = interfaceFor(state)
        scriptInterface.initState()
        val file = writeChunk("broken.lua", "function (")

        assertThat(scriptInterface.loadFile(file, "broken.lua")).isEqualTo(-1)

        assertThat(scriptInterface.getLastLuaError()).isNotEmpty()
        assertThat(scriptInterface.getLoadingFile()).isEmpty()
        assertThat(LuaScriptInterface.scriptEnvironments.scriptEnvIndex).isEqualTo(-1)
    }

    @Test
    fun `loadFile reports an execution error without touching lastLuaError`() {
        val state = debugState()
        val reports = mutableListOf<String>()
        val scriptInterface = object : LuaScriptInterface("Test Interface", { state }) {
            override fun reportError(function: String?, errorDesc: String, stackTrace: Boolean) {
                reports.add(errorDesc)
            }
        }
        scriptInterface.initState()
        val file = writeChunk("failing.lua", "error('kaboom')")

        assertThat(scriptInterface.loadFile(file, "failing.lua")).isEqualTo(-1)

        assertThat(scriptInterface.getLastLuaError()).isEmpty()
        assertThat(reports).hasSize(1)
        assertThat(reports.first()).contains("kaboom")
        assertThat(LuaScriptInterface.scriptEnvironments.scriptEnvIndex).isEqualTo(-1)
    }

    @Test
    fun `loadFile is ignored inside a barrier parallel dispatcher context`() {
        val state = debugState()
        val scriptInterface = LuaScriptInterface("Test Interface", { state }, { true })
        scriptInterface.initState()
        val file = writeChunk("gated.lua", "gated_load_ran = true")

        assertThat(scriptInterface.loadFile(file, "gated.lua")).isEqualTo(-1)

        assertThat(state.get("gated_load_ran").isnil()).isTrue()
    }

    @Test
    fun `getEvent captures a global function and clears it`() {
        val state = debugState()
        val scriptInterface = interfaceFor(state)
        scriptInterface.initState()
        val file = writeChunk("events.lua", "onLogin = function(player) return true end")
        scriptInterface.loadFile(file, "events.lua")

        val eventId = scriptInterface.getEvent("onLogin")

        assertThat(eventId).isEqualTo(LuaScriptInterface.EVENT_ID_USER)
        assertThat(state.get("onLogin").isnil()).isTrue()
        assertThat(scriptInterface.runningEventId).isEqualTo(LuaScriptInterface.EVENT_ID_USER + 1)
        assertThat(scriptInterface.pushFunction(eventId).isfunction()).isTrue()
        assertThat(scriptInterface.getFileById(eventId)).isEqualTo("$file:onLogin")
    }

    @Test
    fun `getEvent captures a function value as an anonymous callback`() {
        val state = debugState()
        val scriptInterface = interfaceFor(state)
        scriptInterface.initState()
        val file = writeChunk("callback.lua", "return true")
        scriptInterface.loadFile(file, "callback.lua")
        val callback = state.load("return function() return 1 end", "=(callback)").call()

        val eventId = scriptInterface.getEvent(callback)

        assertThat(eventId).isEqualTo(LuaScriptInterface.EVENT_ID_USER)
        assertThat(scriptInterface.getFileById(eventId)).isEqualTo("$file:callback")
        assertThat(scriptInterface.pushFunction(eventId).isfunction()).isTrue()
    }

    @Test
    fun `getEvent rejects missing or non function globals`() {
        val state = debugState()
        val scriptInterface = interfaceFor(state)
        scriptInterface.initState()
        state.set("notFunction", LuaValue.valueOf(1))

        assertThat(scriptInterface.getEvent("missing")).isEqualTo(-1)
        assertThat(scriptInterface.getEvent("notFunction")).isEqualTo(-1)
        assertThat(scriptInterface.getEvent(LuaValue.valueOf(1))).isEqualTo(-1)
    }

    @Test
    fun `getMetaEvent captures a table field and clears it`() {
        val state = debugState()
        val scriptInterface = interfaceFor(state)
        scriptInterface.initState()
        val file = writeChunk("meta.lua", "Meta = { onWhat = function() return 1 end }")
        scriptInterface.loadFile(file, "meta.lua")

        val eventId = scriptInterface.getMetaEvent("Meta", "onWhat")

        assertThat(eventId).isEqualTo(LuaScriptInterface.EVENT_ID_USER)
        assertThat(state.get("Meta").get("onWhat").isnil()).isTrue()
        assertThat(scriptInterface.getFileById(eventId)).isEqualTo("$file:Meta@onWhat")
        assertThat(scriptInterface.pushFunction(eventId).isfunction()).isTrue()
        assertThat(scriptInterface.getMetaEvent("Meta", "missing")).isEqualTo(-1)
        assertThat(scriptInterface.getMetaEvent("Missing", "onWhat")).isEqualTo(-1)
    }

    @Test
    fun `pushFunction reports ids that were never stored`() {
        val scriptInterface = interfaceFor(debugState())
        scriptInterface.initState()

        assertThat(scriptInterface.pushFunction(4242).isnil()).isTrue()
    }

    @Test
    fun `getStackTrace appends the lua traceback when the debug library exists`() {
        val scriptInterface = interfaceFor(debugState())
        scriptInterface.initState()

        val trace = scriptInterface.getStackTrace("boom")

        assertThat(trace).contains("boom")
        assertThat(trace).contains("stack traceback")
    }

    @Test
    fun `getStackTrace falls back to the raw message without the debug library`() {
        val scriptInterface = interfaceFor(JsePlatform.standardGlobals())
        scriptInterface.initState()

        assertThat(scriptInterface.getStackTrace("boom")).isEqualTo("boom")
    }

    @Test
    fun `callFunction returns the callback boolean and resets the script env`() {
        val state = debugState()
        val scriptInterface = interfaceFor(state)
        scriptInterface.initState()
        val callback = state.load("return function(value) return value > 1 end", "=(fn)").call()
        assertThat(LuaScriptInterface.reserveScriptEnv()).isTrue()
        LuaScriptInterface.getScriptEnv()!!.setScriptId(LuaScriptInterface.EVENT_ID_USER, scriptInterface)

        val result = scriptInterface.callFunction(LuaValue.varargsOf(callback, LuaValue.valueOf(2)))

        assertThat(result).isTrue()
        assertThat(LuaScriptInterface.scriptEnvironments.scriptEnvIndex).isEqualTo(-1)
    }

    @Test
    fun `callFunction treats a nil result as false`() {
        val state = debugState()
        val scriptInterface = interfaceFor(state)
        scriptInterface.initState()
        val callback = state.load("return function() end", "=(fn)").call()
        assertThat(LuaScriptInterface.reserveScriptEnv()).isTrue()
        LuaScriptInterface.getScriptEnv()!!.setScriptId(LuaScriptInterface.EVENT_ID_USER, scriptInterface)

        val result = scriptInterface.callFunction(LuaValue.varargsOf(arrayOf(callback)))

        assertThat(result).isFalse()
        assertThat(LuaScriptInterface.scriptEnvironments.scriptEnvIndex).isEqualTo(-1)
    }

    @Test
    fun `callFunction reports a runtime error and returns false`() {
        val state = debugState()
        val reports = mutableListOf<String>()
        val scriptInterface = object : LuaScriptInterface("Test Interface", { state }) {
            override fun reportError(function: String?, errorDesc: String, stackTrace: Boolean) {
                reports.add(errorDesc)
            }
        }
        scriptInterface.initState()
        val callback = state.load("return function() error('call failed') end", "=(fn)").call()
        assertThat(LuaScriptInterface.reserveScriptEnv()).isTrue()
        LuaScriptInterface.getScriptEnv()!!.setScriptId(LuaScriptInterface.EVENT_ID_USER, scriptInterface)

        val result = scriptInterface.callFunction(LuaValue.varargsOf(arrayOf(callback)))

        assertThat(result).isFalse()
        assertThat(reports).hasSize(1)
        assertThat(reports.first()).contains("call failed")
        assertThat(LuaScriptInterface.scriptEnvironments.scriptEnvIndex).isEqualTo(-1)
    }

    @Test
    fun `callVoidFunction invokes the callback and resets the script env`() {
        val state = debugState()
        val scriptInterface = interfaceFor(state)
        scriptInterface.initState()
        val callback = state.load("return function() void_marker = 5 end", "=(fn)").call()
        assertThat(LuaScriptInterface.reserveScriptEnv()).isTrue()
        LuaScriptInterface.getScriptEnv()!!.setScriptId(LuaScriptInterface.EVENT_ID_USER, scriptInterface)

        scriptInterface.callVoidFunction(LuaValue.varargsOf(arrayOf(callback)))

        assertThat(state.get("void_marker").toint()).isEqualTo(5)
        assertThat(LuaScriptInterface.scriptEnvironments.scriptEnvIndex).isEqualTo(-1)
    }

    @Test
    fun `protectedCall returns LUA_ERRRUN with the traceback for a runtime error`() {
        val state = debugState()
        val scriptInterface = interfaceFor(state)
        scriptInterface.initState()
        val callback = state.load("return function() error('kaboom') end", "=(fn)").call()
        assertThat(LuaScriptInterface.reserveScriptEnv()).isTrue()
        LuaScriptInterface.getScriptEnv()!!.setScriptId(LuaScriptInterface.EVENT_ID_USER, scriptInterface)

        val result = scriptInterface.protectedCall(callback)

        assertThat(result.status).isEqualTo(LuaScriptInterface.LUA_ERRRUN)
        assertThat(result.error).contains("kaboom")
        assertThat(result.value.isnil()).isTrue()
        LuaScriptInterface.resetScriptEnv()
    }

    @Test
    fun `protectedCall returns LUA_OK with the first result`() {
        val scriptInterface = interfaceFor(debugState())
        scriptInterface.initState()
        val callback = scriptInterface.getLuaState()!!.load("return function() return 41 + 1 end", "=(fn)").call()

        val result = scriptInterface.protectedCall(callback)

        assertThat(result.status).isEqualTo(LuaScriptInterface.LUA_OK)
        assertThat(result.value.toint()).isEqualTo(42)
        assertThat(result.error).isEmpty()
    }

    @Test
    fun `luaErrorHandler falls back to the raw message without a script environment`() {
        val scriptInterface = interfaceFor(debugState())
        scriptInterface.initState()

        assertThat(scriptInterface.luaErrorHandler("raw error")).isEqualTo("raw error")
    }

    @Test
    fun `protectedCall is ignored inside a barrier parallel dispatcher context`() {
        val state = debugState()
        val scriptInterface = LuaScriptInterface("Test Interface", { state }, { true })
        scriptInterface.initState()
        val callback = state.load("return function() gated_ran = true end", "=(gated)").call()

        val result = scriptInterface.protectedCall(callback)

        assertThat(result.status).isEqualTo(LuaScriptInterface.LUA_ERRRUN)
        assertThat(state.get("gated_ran").isnil()).isTrue()
    }
}
