package org.opentibia.canary.lua.runtime

import io.github.oshai.kotlinlogging.KotlinLogging
import org.luaj.vm2.Globals
import org.luaj.vm2.LuaError
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.Varargs
import java.nio.file.Files
import java.nio.file.Path

private val logger = KotlinLogging.logger {}

/**
 * Port of `lua_pcall`'s `(status, error)` contract for the stack-less LuaJ runtime:
 * [status] is [LuaScriptInterface.LUA_OK] or [LuaScriptInterface.LUA_ERRRUN], [value]
 * is the call's first result and [error] carries the error-handler message when the
 * call failed.
 */
class ProtectedCall internal constructor(
    val status: Int,
    val value: LuaValue,
    val error: String,
)

/**
 * Port of `class LuaScriptInterface` (`src/lua/scripts/luascript.{hpp,cpp}`): the
 * named interface over the shared [Globals], its event table, the file/event cache,
 * the protected-call error path with `debug.traceback` enrichment, the
 * `getEvent`/`getMetaEvent`/`pushFunction` capture flow and the reload lifecycle.
 *
 * Deviations forced by LuaJ (recorded in the wave PARITY GAPS):
 * - `lua_State` stacks do not exist, so [protectedCall]/[callFunction] take the
 *   function and arguments explicitly; the C++ "Stack size changed!" check has no
 *   counterpart.
 * - The bytecode cache (`lua_dump`/`luaL_loadbuffer` packs) and its filesystem
 *   lifecycle are not ported; [loadFile] always compiles the source.
 * - [closeState] cannot `lua_close` the shared state; it drops the interface's event
 *   table/cache and returns whether it had an open state ([LuaEnvironment] owns the
 *   actual state close).
 * - [validateDispatcherContext] receives the barrier-parallel state of the current
 *   dispatcher task through an injected provider instead of the `DispatcherContext`
 *   thread-local.
 *
 * @param interfaceName `LuaScriptInterface::interfaceName`.
 * @param stateProvider injected replacement for `g_luaEnvironment().getLuaState()`;
 *   a null result makes [initState] fail.
 * @param barrierParallel injected replacement for
 *   `DispatcherContext::isOn() && g_dispatcher().context().isBarrierParallel()`.
 */
open class LuaScriptInterface(
    private val interfaceName: String,
    private val stateProvider: () -> Globals? = { null },
    private val barrierParallel: () -> Boolean = { false },
) {
    /** `LuaScriptInterface::luaState`; null while the interface is closed. */
    private var state: Globals? = null

    /** `LuaScriptInterface::eventTableRef`; `-1` while no event table is installed. */
    var eventTableRef: Int = EVENT_TABLE_REF_NONE
        private set

    /** `LuaScriptInterface::runningEventId`, starting at [EVENT_ID_USER]. */
    var runningEventId: Int = EVENT_ID_USER
        private set

    private val cacheFiles = mutableMapOf<Int, String>()
    private var eventTable: LuaTable? = null
    private var lastLuaError: String = ""
    private var loadingFile: String = ""
    private var loadedScriptName: String = ""

    /** `LuaScriptInterface::getInterfaceName`. */
    fun getInterfaceName(): String = interfaceName

    /** `LuaScriptInterface::getLastLuaError`. */
    fun getLastLuaError(): String = lastLuaError

    /** `LuaScriptInterface::getLoadingFile`. */
    fun getLoadingFile(): String = loadingFile

    /** `LuaScriptInterface::setLoadingScriptName`. */
    fun setLoadingScriptName(scriptName: String) {
        loadedScriptName = scriptName
    }

    /** `LuaScriptInterface::getLoadingScriptName`, warning when no name was set. */
    fun getLoadingScriptName(): String {
        if (loadedScriptName.isEmpty()) {
            logger.warn { "[LuaScriptInterface::getLoadingScriptName] - Script name is empty" }
        }
        return loadedScriptName
    }

    /** `LuaScriptInterface::getLuaState`. */
    open fun getLuaState(): Globals? = state

    /** `LuaScriptInterface::initState` (luascript.cpp:731-741). */
    open fun initState(): Boolean {
        state = stateProvider() ?: return false
        eventTable = LuaTable()
        eventTableRef = EVENT_TABLE_REF
        runningEventId = EVENT_ID_USER
        return true
    }

    /**
     * `LuaScriptInterface::reInitState` (luascript.cpp:497-502): close, then
     * re-initialize over the shared state. The `clearAreaObjects(this)` half needs the
     * `LuaEnvironment` combat-area registry, which is not ported (PARITY GAP).
     */
    open fun reInitState(): Boolean {
        closeState()
        return initState()
    }

    /** `LuaScriptInterface::closeState` (luascript.cpp:743-760). */
    open fun closeState(): Boolean {
        if (state == null) {
            return false
        }

        cacheFiles.clear()
        eventTable = null
        eventTableRef = EVENT_TABLE_REF_NONE
        state = null
        return true
    }

    /**
     * `LuaScriptInterface::loadFile` (luascript.cpp:517-596): compile the source at
     * `@file`, run it under a reserved [ScriptEnvironment] whose script id is
     * [EVENT_ID_LOADING], and return `0` on success or `-1` on any error. A load
     * (syntax/IO) error is stored in [getLastLuaError]; an execution error is reported
     * through [reportError] and leaves [getLastLuaError] untouched, matching C++.
     */
    open fun loadFile(file: Path, scriptName: String = ""): Int {
        val lua = state ?: return -1

        val source: String = try {
            Files.readString(file)
        } catch (e: Exception) {
            lastLuaError = e.message ?: ""
            return -1
        }

        val chunk: LuaValue = try {
            lua.load(source, "@$file")
        } catch (e: Exception) {
            lastLuaError = e.message ?: ""
            return -1
        }

        if (!chunk.isfunction()) {
            return -1
        }

        loadingFile = file.toString()
        setLoadingScriptName(scriptName)

        if (!reserveScriptEnv()) {
            return -1
        }
        getScriptEnv()?.setScriptId(EVENT_ID_LOADING, this)

        val call = protectedCall(chunk)
        if (call.status != LUA_OK) {
            reportError(null, call.error)
            resetScriptEnv()
            return -1
        }

        resetScriptEnv()
        return 0
    }

    /** `LuaScriptInterface::getFileById` (luascript.cpp:678-689). */
    fun getFileById(scriptId: Int): String {
        if (scriptId == EVENT_ID_LOADING) {
            return loadingFile
        }
        return cacheFiles[scriptId] ?: UNKNOWN_SCRIPT_FILE
    }

    /**
     * `LuaScriptInterface::getEvent(eventName)` (luascript.cpp:598-624): move the
     * global function into the interface event table under the next running id, clear
     * the global and cache `loadingFile:eventName` for [getFileById].
     */
    fun getEvent(eventName: String): Int {
        val table = eventTable ?: return -1
        val callback = state?.get(eventName) ?: return -1
        if (!callback.isfunction()) {
            return -1
        }

        table.set(runningEventId, callback)
        state?.set(eventName, LuaValue.NIL)
        cacheFiles[runningEventId] = "$loadingFile:$eventName"
        return runningEventId++
    }

    /**
     * `LuaScriptInterface::getEvent()` (luascript.cpp:626-646): capture a callback
     * already resolved by the caller; the C++ version reads it from the Lua stack.
     */
    fun getEvent(callback: LuaValue): Int {
        if (!callback.isfunction()) {
            return -1
        }
        val table = eventTable ?: return -1

        table.set(runningEventId, callback)
        cacheFiles[runningEventId] = "$loadingFile:callback"
        return runningEventId++
    }

    /**
     * `LuaScriptInterface::getMetaEvent` (luascript.cpp:648-676): capture the named
     * field of the global table, clear the field, and cache
     * `loadingFile:globalName@eventName`. A missing global resolves to `-1` (the C++
     * `lua_getfield` on a non-table aborts the load; the port refuses the capture).
     */
    fun getMetaEvent(globalName: String, eventName: String): Int {
        val table = eventTable ?: return -1
        val global = state?.get(globalName) ?: return -1
        if (!global.istable()) {
            return -1
        }

        val callback = global.get(eventName)
        if (!callback.isfunction()) {
            return -1
        }

        table.set(runningEventId, callback)
        global.set(eventName, LuaValue.NIL)
        cacheFiles[runningEventId] = "$loadingFile:$globalName@$eventName"
        return runningEventId++
    }

    /**
     * `LuaScriptInterface::pushFunction` (luascript.cpp:720-729): return the event
     * registered under `functionId`, or nil when the id is unknown/not a function.
     * The C++ version pushes it onto the Lua stack.
     */
    open fun pushFunction(functionId: Int): LuaValue {
        val function = eventTable?.get(functionId) ?: return LuaValue.NIL
        return if (function.isfunction()) function else LuaValue.NIL
    }

    /**
     * `LuaScriptInterface::callFunction` (luascript.cpp:793-810): invoke the function
     * in `params.arg1()` with the remaining varargs as its arguments, return the
     * callback's boolean result (nil counts as false, like `lua_toboolean`), report an
     * execution error and reset the script environment afterwards.
     */
    open fun callFunction(params: Varargs): Boolean {
        val call = protectedCall(params.arg1(), params.subargs(2))
        val result = if (call.status != LUA_OK) {
            reportError(null, call.error)
            false
        } else {
            call.value.toboolean()
        }
        resetScriptEnv()
        return result
    }

    /** `LuaScriptInterface::callVoidFunction` (luascript.cpp:812-824). */
    open fun callVoidFunction(params: Varargs) {
        val call = protectedCall(params.arg1(), params.subargs(2))
        if (call.status != LUA_OK) {
            reportError(null, call.error)
        }
        resetScriptEnv()
    }

    /**
     * `Lua::protectedCall` (lua_functions_loader.cpp:97-109) plus `Lua::luaErrorHandler`
     * (lua_functions_loader.cpp:149-160): refuse the call while the dispatcher task is
     * barrier-parallel, otherwise run the function and pass a raised error through
     * [luaErrorHandler] so the returned message carries the traceback.
     */
    open fun protectedCall(function: LuaValue, args: Varargs = LuaValue.NONE): ProtectedCall {
        val refusal = validateDispatcherContext("protectedCall")
        if (refusal != 0) {
            return ProtectedCall(refusal, LuaValue.NIL, "")
        }

        return try {
            ProtectedCall(LUA_OK, function.invoke(args).arg1(), "")
        } catch (e: LuaError) {
            ProtectedCall(LUA_ERRRUN, LuaValue.NIL, luaErrorHandler(e.message ?: "lua error"))
        } catch (e: Exception) {
            ProtectedCall(LUA_ERRRUN, LuaValue.NIL, luaErrorHandler(e.message ?: e.toString()))
        }
    }

    /**
     * `Lua::luaErrorHandler` (lua_functions_loader.cpp:149-160): enrich the error with
     * the owning interface's stack trace. Without a reserved script environment the
     * C++ handler pushes nothing; the port returns the raw message with an error log.
     */
    open fun luaErrorHandler(errorMessage: String): String {
        val scriptInterface = getScriptEnv()?.getScriptInterface()
        if (scriptInterface == null) {
            logger.error { "[luaErrorHandler]: LuaScriptInterface not found, error: $errorMessage" }
            return errorMessage
        }
        return scriptInterface.getStackTrace(errorMessage)
    }

    /**
     * `LuaScriptInterface::getStackTrace` (luascript.cpp:691-718): run
     * `debug.traceback(error_desc)` protected and fall back to the raw description when
     * the debug library is unavailable or the traceback itself fails.
     */
    open fun getStackTrace(errorDesc: String): String {
        val lua = state ?: return errorDesc

        val debug = lua.get("debug")
        if (!debug.istable()) {
            logger.error { "Lua debug table not found." }
            return errorDesc
        }

        val traceback = debug.get("traceback")
        if (!traceback.isfunction()) {
            logger.error { "Lua traceback function not found." }
            return errorDesc
        }

        return try {
            traceback.call(LuaValue.valueOf(errorDesc)).tojstring()
        } catch (e: Exception) {
            logger.error { "Error running Lua traceback: ${e.message}" }
            "Lua traceback failed: ${e.message}"
        }
    }

    /**
     * `Lua::reportError` (lua_functions_loader.cpp:111-147): format and log the script
     * error banner from the current script-environment info. `function` is nullable to
     * mirror the `nullptr` callers; `stackTrace` appends [getStackTrace].
     */
    open fun reportError(function: String?, errorDesc: String, stackTrace: Boolean = false) {
        val info = getScriptEnv()?.getEventInfo()

        val logMsg = StringBuilder()
        logMsg.append("Lua Script Error Detected\n")
        logMsg.append("---------------------------------------\n")
        val scriptInterface = info?.scriptInterface
        if (info != null && scriptInterface != null) {
            logMsg.append("Interface: ${scriptInterface.getInterfaceName()}\n")
            if (info.scriptId != 0) {
                logMsg.append("Script ID: ${scriptInterface.getFileById(info.scriptId)}\n")
            }
            if (info.timerEvent) {
                logMsg.append("Timer Event: Yes\n")
            }
            if (info.callbackId != 0) {
                logMsg.append("Callback ID: ${scriptInterface.getFileById(info.callbackId)}\n")
            }
        }
        if (!function.isNullOrEmpty() && function != "N/A") {
            logMsg.append("Function: $function\n")
        }
        logMsg.append("Error Description: $errorDesc\n")
        if (stackTrace && scriptInterface != null) {
            val trace = scriptInterface.getStackTrace(errorDesc)
            if (trace.isNotEmpty() && trace != "N/A") {
                logMsg.append("Stack Trace:\n$trace\n")
            }
        }
        logMsg.append("---------------------------------------\n")

        logger.error { logMsg.toString() }
    }

    /**
     * `Lua::validateDispatcherContext` (lua_functions_loader.cpp:909-916): while the
     * current dispatcher task runs barrier-parallel, communicating with Lua is ignored
     * with `LUA_ERRRUN` and a warning.
     */
    fun validateDispatcherContext(functionName: String): Int {
        if (barrierParallel()) {
            logger.warn {
                "[$functionName] The call to lua was ignored because the current task " +
                    "is trying to communicate while in barrier-parallel mode."
            }
            return LUA_ERRRUN
        }
        return 0
    }

    companion object {
        /** `EVENT_ID_LOADING` (src/lua/lua_definitions.hpp:16). */
        const val EVENT_ID_LOADING = 1

        /** `EVENT_ID_USER` (src/lua/lua_definitions.hpp:17). */
        const val EVENT_ID_USER = 1000

        /** `LUA_OK` (lua.h). */
        const val LUA_OK = 0

        /** `LUA_ERRRUN` (lua.h). */
        const val LUA_ERRRUN = 2

        private const val EVENT_TABLE_REF_NONE = -1
        private const val EVENT_TABLE_REF = 1
        private const val UNKNOWN_SCRIPT_FILE = "(Unknown scriptfile)"

        /** `Lua::scriptEnv[16]` shared by every [LuaScriptInterface]. */
        val scriptEnvironments = LuaScriptEnvironments()

        /** `Lua::reserveScriptEnv()`. */
        fun reserveScriptEnv(): Boolean = scriptEnvironments.reserveScriptEnv()

        /** `Lua::getScriptEnv()`. */
        fun getScriptEnv(): ScriptEnvironment? = scriptEnvironments.getScriptEnv()

        /** `Lua::resetScriptEnv()`. */
        fun resetScriptEnv() {
            scriptEnvironments.resetScriptEnv()
        }
    }
}
