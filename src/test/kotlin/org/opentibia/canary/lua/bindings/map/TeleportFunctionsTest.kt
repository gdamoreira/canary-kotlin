package org.opentibia.canary.lua.bindings.map

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.game.movement.Teleport
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.Position

/**
 * Transcript-style tests for the `Teleport` Lua binding library, mirroring
 * `src/lua/functions/map/teleport_functions.cpp`.
 */
class TeleportFunctionsTest {

    private class FakeTeleportOperations(
        private val teleports: Map<Int, Teleport> = emptyMap(),
    ) : TeleportOperations {
        override fun getItemByUid(uid: Int): Teleport? = teleports[uid]
    }

    private fun environment(operations: TeleportOperations): LuaEnvironment =
        LuaEnvironment().apply { initState() }.also { TeleportFunctions.install(it, operations) }

    @Test
    fun `Teleport constructor resolves the script env item and returns nil for an unknown uid`() {
        val teleport = Teleport(1387, Position(50, 60, 7))
        val environment = environment(FakeTeleportOperations(mapOf(7 to teleport)))

        environment.loadChunk("teleport = Teleport(7)")
        environment.loadChunk("missing = Teleport(8)")

        assertThat(environment.getGlobal("teleport").isuserdata()).isTrue()
        assertThat(environment.getGlobal("missing").isnil()).isTrue()
    }

    @Test
    fun `Teleport destination methods mirror the domain`() {
        val teleport = Teleport(1387, Position(50, 60, 7))
        val environment = environment(FakeTeleportOperations(mapOf(7 to teleport)))

        environment.loadChunk(
            """
            teleport = Teleport(7)
            before = teleport:getDestination():toString()
            moved = teleport:setDestination({ x = 10, y = 20, z = 7 })
            after = teleport:getDestination():toString()
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("before").tojstring()).isEqualTo("( 50, 60, 7 )")
        assertThat(environment.getGlobal("moved").toboolean()).isTrue()
        assertThat(environment.getGlobal("after").tojstring()).isEqualTo("( 10, 20, 7 )")
        assertThat(teleport.getDestPos()).isEqualTo(Position(10, 20, 7))
    }

    @Test
    fun `Teleport methods are registered on the Teleport metatable`() {
        val environment = environment(FakeTeleportOperations())

        environment.loadChunk(
            "checks = (rawgetmetatable('Teleport').__metatable.getDestination ~= nil) and " +
                "(rawgetmetatable('Teleport').__metatable.setDestination ~= nil) and " +
                "(rawgetmetatable('Teleport').__eq ~= nil) and " +
                "(Teleport ~= nil)",
        )

        assertThat(environment.getGlobal("checks").toboolean()).isTrue()
    }
}
