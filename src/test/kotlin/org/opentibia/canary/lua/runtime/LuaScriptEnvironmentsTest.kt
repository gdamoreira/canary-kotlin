package org.opentibia.canary.lua.runtime

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Transcribes the static script-environment pool of `Lua`
 * (`src/lua/functions/lua_functions_loader.hpp:344-366`): a 16-slot array with
 * `reserveScriptEnv`/`getScriptEnv`/`resetScriptEnv`, the nested-call depth cap and
 * the out-of-bounds guards.
 */
class LuaScriptEnvironmentsTest {

    @Test
    fun `reserve allows sixteen nested calls and refuses the seventeenth`() {
        val environments = LuaScriptEnvironments()

        repeat(LuaScriptEnvironments.SCRIPT_ENV_CAPACITY) {
            assertThat(environments.reserveScriptEnv()).isTrue()
        }

        assertThat(environments.scriptEnvIndex).isEqualTo(LuaScriptEnvironments.SCRIPT_ENV_CAPACITY - 1)
        assertThat(environments.reserveScriptEnv()).isFalse()
        assertThat(environments.scriptEnvIndex).isEqualTo(LuaScriptEnvironments.SCRIPT_ENV_CAPACITY)
    }

    @Test
    fun `getScriptEnv returns the reserved environment`() {
        val environments = LuaScriptEnvironments()

        assertThat(environments.getScriptEnv()).isNull()

        assertThat(environments.reserveScriptEnv()).isTrue()
        val reserved = environments.getScriptEnv()
        assertThat(reserved).isNotNull
        assertThat(environments.getScriptEnv()).isSameAs(reserved)
    }

    @Test
    fun `getScriptEnv reports the pool overflow and yields null`() {
        val environments = LuaScriptEnvironments()
        repeat(LuaScriptEnvironments.SCRIPT_ENV_CAPACITY + 1) { environments.reserveScriptEnv() }

        assertThat(environments.getScriptEnv()).isNull()
    }

    @Test
    fun `resetScriptEnv resets the top environment and decrements the index`() {
        val environments = LuaScriptEnvironments()
        environments.reserveScriptEnv()
        val environment = environments.getScriptEnv()!!
        environment.setScriptId(9, null)

        environments.resetScriptEnv()

        assertThat(environment.getScriptId()).isZero()
        assertThat(environments.scriptEnvIndex).isEqualTo(-1)
        assertThat(environments.getScriptEnv()).isNull()
    }

    @Test
    fun `resetScriptEnv below zero is ignored`() {
        val environments = LuaScriptEnvironments()

        environments.resetScriptEnv()

        assertThat(environments.scriptEnvIndex).isEqualTo(-1)
    }

    @Test
    fun `every reserved slot is a distinct environment`() {
        val environments = LuaScriptEnvironments()

        environments.reserveScriptEnv()
        val first = environments.getScriptEnv()
        environments.reserveScriptEnv()
        val second = environments.getScriptEnv()

        assertThat(first).isNotSameAs(second)
    }
}
