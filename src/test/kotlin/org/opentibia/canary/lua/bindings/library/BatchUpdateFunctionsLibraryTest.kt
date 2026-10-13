package org.opentibia.canary.lua.bindings.library

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.items.Container
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.Position

/**
 * Transcript tests for the `BatchUpdate` userdata class, mirroring
 * `BatchUpdateFunctions` (src/lua/functions/core/game/batch_update_functions.cpp)
 * and `BatchUpdate::add` (src/utils/batch_update.cpp:41-62).
 */
class BatchUpdateFunctionsLibraryTest {

    private fun environment(): LuaEnvironment =
        LuaEnvironment().apply { initState() }.also { BatchUpdateFunctions.install(it) }

    @Test
    fun `BatchUpdate create requires a player and wraps the batch userdata`() {
        val environment = environment()
        environment.globals.set(
            "actor",
            environment.userdata.wrap(
                "Player",
                Player(1, "Actor", 100, 100, position = Position(100, 100, 7)),
            ),
        )

        environment.loadChunk("batch = BatchUpdate(actor)")

        assertThat(environment.getGlobal("batch").isuserdata()).isTrue()
    }

    @Test
    fun `BatchUpdate create returns nil without a player`() {
        val environment = environment()

        environment.loadChunk("batch = BatchUpdate('not a player')")

        assertThat(environment.getGlobal("batch").isnil()).isTrue()
    }

    @Test
    fun `BatchUpdate add caches each container once`() {
        val environment = environment()
        environment.globals.set(
            "actor",
            environment.userdata.wrap(
                "Player",
                Player(1, "Actor", 100, 100, position = Position(100, 100, 7)),
            ),
        )
        environment.globals.set("backpack", environment.userdata.wrap("Container", Container(id = 1988, capacity = 20)))

        environment.loadChunk(
            """
            batch = BatchUpdate(actor)
            first = batch:add(backpack)
            second = batch:add(backpack)
            batch:delete()
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("first").toboolean()).isTrue()
        assertThat(environment.getGlobal("second").toboolean()).isFalse()
    }

    @Test
    fun `BatchUpdate add rejects a missing container userdata with false`() {
        val environment = environment()
        environment.globals.set(
            "actor",
            environment.userdata.wrap(
                "Player",
                Player(1, "Actor", 100, 100, position = Position(100, 100, 7)),
            ),
        )

        environment.loadChunk(
            """
            batch = BatchUpdate(actor)
            result = batch:add('not a container')
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("result").toboolean()).isFalse()
    }
}
