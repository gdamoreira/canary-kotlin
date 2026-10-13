package org.opentibia.canary.lua.runtime

import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Port of the static script-environment pool of `Lua`
 * (`src/lua/functions/lua_functions_loader.hpp:344-366`): sixteen reusable
 * [ScriptEnvironment] slots, a stack index starting below zero, and the per-call
 * reserve/get/reset protocol. The depth cap is the sandbox guard against recursive
 * script calls; C++ logs and returns null on out-of-bounds access.
 */
class LuaScriptEnvironments(game: ScriptEnvironmentGame? = null) {

    /** `Lua::scriptEnv[16]`. */
    val scriptEnv: Array<ScriptEnvironment> = Array(SCRIPT_ENV_CAPACITY) { ScriptEnvironment(game) }

    /** `Lua::scriptEnvIndex`, starting at `-1` like the C++ static. */
    var scriptEnvIndex: Int = -1
        private set

    /** `Lua::getScriptEnv()`: null (and a logged error) outside the reserved range. */
    fun getScriptEnv(): ScriptEnvironment? {
        if (scriptEnvIndex < 0 || scriptEnvIndex >= SCRIPT_ENV_CAPACITY) {
            logger.error { "[getScriptEnv]: scriptEnvIndex out of bounds!" }
            return null
        }
        return scriptEnv[scriptEnvIndex]
    }

    /** `Lua::reserveScriptEnv()`: `return ++scriptEnvIndex < 16;`. */
    fun reserveScriptEnv(): Boolean = ++scriptEnvIndex < SCRIPT_ENV_CAPACITY

    /** `Lua::resetScriptEnv()`: resets the current environment and pops the index. */
    fun resetScriptEnv() {
        if (scriptEnvIndex < 0) {
            logger.error { "[resetScriptEnv]: scriptEnvIndex out of bounds!" }
            return
        }
        scriptEnv[scriptEnvIndex--].resetEnv()
    }

    companion object {
        const val SCRIPT_ENV_CAPACITY = 16
    }
}
