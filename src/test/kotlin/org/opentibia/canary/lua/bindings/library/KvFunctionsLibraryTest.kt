package org.opentibia.canary.lua.bindings.library

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.lua.runtime.LuaEnvironment

/**
 * Transcript tests for the `kv` table and the `KV` userdata class, mirroring
 * `KVFunctions` (src/lua/functions/core/libs/kv_functions.cpp) over the ported
 * in-memory `KVStore`/`ScopedKV` pair (src/kv/kv.hpp).
 */
class KvFunctionsLibraryTest {

    private fun environment(operations: KvOperations = WorldKvOperations()): LuaEnvironment =
        LuaEnvironment().apply { initState() }.also { KvFunctions.install(it, operations) }

    @Test
    fun `kv set and get round-trip the supported value kinds`() {
        val environment = environment()

        environment.loadChunk(
            """
            kv.set('name', 'canary')
            kv.set('count', 12)
            kv.set('ratio', 2.5)
            kv.set('enabled', true)
            name = kv.get('name')
            count = kv.get('count')
            ratio = kv.get('ratio')
            enabled = kv.get('enabled')
            missing = kv.get('missing')
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("name").tojstring()).isEqualTo("canary")
        assertThat(environment.getGlobal("count").todouble()).isEqualTo(12.0)
        assertThat(environment.getGlobal("ratio").todouble()).isEqualTo(2.5)
        assertThat(environment.getGlobal("enabled").toboolean()).isTrue()
        assertThat(environment.getGlobal("missing").isnil()).isTrue()
    }

    @Test
    fun `kv tables round-trip as arrays and maps`() {
        val environment = environment()

        environment.loadChunk(
            """
            kv.set('array', {1, 'two', true})
            kv.set('map', {answer = 42, nested = {flag = true}})
            array = kv.get('array')
            map = kv.get('map')
            """.trimIndent(),
        )

        val array = environment.getGlobal("array")
        assertThat(array.get(1).toint()).isEqualTo(1)
        assertThat(array.get(2).tojstring()).isEqualTo("two")
        assertThat(array.get(3).toboolean()).isTrue()
        val map = environment.getGlobal("map")
        assertThat(map.get("answer").toint()).isEqualTo(42)
        assertThat(map.get("nested").get("flag").toboolean()).isTrue()
    }

    @Test
    fun `kv set rejects unsupported values with false`() {
        val environment = environment()

        environment.loadChunk(
            """
            stored = kv.set('fn', function() end)
            fetched = kv.get('fn')
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("stored").toboolean()).isFalse()
        assertThat(environment.getGlobal("fetched").isnil()).isTrue()
    }

    @Test
    fun `kv scoped stores keys under the scope prefix like ScopedKV`() {
        val environment = environment()

        environment.loadChunk(
            """
            scope = kv.scoped('player.1')
            scope:set('level', 10)
            indirect = kv.get('player.1.level')
            missing = kv.get('level')
            direct = scope:get('level')
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("indirect").toint()).isEqualTo(10)
        assertThat(environment.getGlobal("direct").toint()).isEqualTo(10)
        assertThat(environment.getGlobal("missing").isnil()).isTrue()
    }

    @Test
    fun `KV scoped global creates the userdata class scope`() {
        val environment = environment()

        environment.loadChunk(
            """
            scope = KV.scoped('guild.9')
            scope:set('name', 'Red Rose')
            name = kv.get('guild.9.name')
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("scope").isuserdata()).isTrue()
        assertThat(environment.getGlobal("name").tojstring()).isEqualTo("Red Rose")
    }

    @Test
    fun `kv keys filters by prefix and the scoped form lists its own keys`() {
        val environment = environment()

        environment.loadChunk(
            """
            kv.set('alpha', 1)
            kv.set('alpine', 2)
            kv.set('beta', 3)
            prefixed = kv.keys('al')
            all = kv.keys()
            scope = kv.scoped('al')
            scope:set('p', 1)
            scope:set('q', 2)
            scoped = scope:keys()
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("prefixed").length()).isEqualTo(2)
        assertThat(environment.getGlobal("all").length()).isEqualTo(3)
        assertThat(environment.getGlobal("scoped").length()).isEqualTo(2)
    }

    @Test
    fun `kv remove deletes the key and returns nil`() {
        val environment = environment()

        environment.loadChunk(
            """
            kv.set('gone', 1)
            kv.remove('gone')
            removed = kv.get('gone')
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("removed").isnil()).isTrue()
    }

    @Test
    fun `kv get with forceLoad keeps the second-argument shape`() {
        val environment = environment()

        environment.loadChunk(
            """
            kv.set('k', 'v')
            loaded = kv.get('k', true)
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("loaded").tojstring()).isEqualTo("v")
    }
}
