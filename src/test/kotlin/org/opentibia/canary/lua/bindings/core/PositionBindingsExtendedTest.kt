package org.opentibia.canary.lua.bindings.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.Position

/**
 * Transcript-style tests for the extended `Position` Lua binding library. The engine
 * seam is fake, but the methods are invoked through real Lua chunks with the exact
 * Canary names (Position(1, 2, 3), position:getPathTo(target), position:getTile(), ...).
 */
class PositionBindingsExtendedTest {

    private class FakeTile

    private class FakePositionOperations : PositionOperations {
        override fun getDistance(position: Position, target: Position): Int =
            maxOf(
                kotlin.math.abs(position.x - target.x),
                kotlin.math.abs(position.y - target.y),
                kotlin.math.abs(position.z - target.z),
            )

        override fun sendMagicEffect(position: Position, magicEffect: Int, spectator: Any?): Boolean = true

        override fun removeMagicEffect(position: Position, magicEffect: Int, spectator: Any?): Boolean = true

        override fun getPathTo(
            position: Position,
            target: Position,
            minTargetDist: Int,
            maxTargetDist: Int,
            fullPathSearch: Boolean,
            clearSight: Boolean,
            maxSearchDist: Int,
        ): List<Int>? = if (target.x == 0) null else listOf(1, 2, 3)

        override fun isSightClear(position: Position, target: Position, sameFloor: Boolean): Boolean =
            !sameFloor || position.z == target.z

        override fun getTile(position: Position): Any? = if (position.x == 100) FakeTile() else null

        override fun getZones(position: Position): List<Any>? =
            if (position.x == 100) listOf("zone") else null

        override fun sendDistanceEffect(
            position: Position,
            target: Position,
            distanceEffect: Int,
            spectator: Any?,
        ): Boolean = true

        override fun sendSingleSoundEffect(position: Position, soundId: Int, actor: Any?): Boolean = true

        override fun sendDoubleSoundEffect(
            position: Position,
            mainSoundId: Int,
            secondarySoundId: Int,
            actor: Any?,
        ): Boolean = true
    }

    private fun env(): LuaEnvironment = LuaEnvironment().apply { initState() }

    @Test
    fun `Position registers every Canary method name and metamethod`() {
        val environment = env()
        PositionFunctions.install(environment, FakePositionOperations())

        environment.loadChunk(
            "checks = (rawgetmetatable('Position').__metatable.getDistance ~= nil) and " +
                "(rawgetmetatable('Position').__metatable.getPathTo ~= nil) and " +
                "(rawgetmetatable('Position').__metatable.isSightClear ~= nil) and " +
                "(rawgetmetatable('Position').__metatable.getTile ~= nil) and " +
                "(rawgetmetatable('Position').__metatable.getZones ~= nil) and " +
                "(rawgetmetatable('Position').__metatable.sendMagicEffect ~= nil) and " +
                "(rawgetmetatable('Position').__metatable.removeMagicEffect ~= nil) and " +
                "(rawgetmetatable('Position').__metatable.sendDistanceEffect ~= nil) and " +
                "(rawgetmetatable('Position').__metatable.sendSingleSoundEffect ~= nil) and " +
                "(rawgetmetatable('Position').__metatable.sendDoubleSoundEffect ~= nil) and " +
                "(rawgetmetatable('Position').__metatable.toString ~= nil) and " +
                "(rawgetmetatable('Position').__add ~= nil) and " +
                "(rawgetmetatable('Position').__sub ~= nil) and " +
                "(rawgetmetatable('Position').__eq ~= nil) and " +
                "(Position ~= nil)",
        )

        assertThat(environment.getGlobal("checks").toboolean()).isTrue()
    }

    @Test
    fun `Position constructor builds default coordinate and table positions`() {
        val environment = env()
        PositionFunctions.install(environment, FakePositionOperations())

        environment.loadChunk("default = Position()")
        environment.loadChunk("coordinates = Position(1, 2, 3)")
        environment.loadChunk("table = Position({ x = 4, y = 5, z = 6 })")
        environment.loadChunk("defaultText = default:toString()")
        environment.loadChunk("coordinatesText = coordinates:toString()")
        environment.loadChunk("tableText = table:toString()")

        assertThat(environment.getGlobal("defaultText").tojstring()).isEqualTo("( 0, 0, 0 )")
        assertThat(environment.getGlobal("coordinatesText").tojstring()).isEqualTo("( 1, 2, 3 )")
        assertThat(environment.getGlobal("tableText").tojstring()).isEqualTo("( 4, 5, 6 )")
    }

    @Test
    fun `Position getPathTo renders a direction table or false`() {
        val environment = env()
        PositionFunctions.install(environment, FakePositionOperations())
        environment.globals.set("from", environment.userdata.wrap("Position", Position(10, 20, 7)))

        environment.loadChunk("path = from:getPathTo({ x = 15, y = 25, z = 7 })")
        environment.loadChunk("length = #path")
        environment.loadChunk("first = path[1]")
        environment.loadChunk("blocked = from:getPathTo({ x = 0, y = 0, z = 7 })")

        assertThat(environment.getGlobal("length").toint()).isEqualTo(3)
        assertThat(environment.getGlobal("first").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("blocked").isboolean()).isTrue()
        assertThat(environment.getGlobal("blocked").toboolean()).isFalse()
    }

    @Test
    fun `Position sight tile and zone queries delegate to the seam`() {
        val environment = env()
        PositionFunctions.install(environment, FakePositionOperations())
        environment.globals.set("here", environment.userdata.wrap("Position", Position(100, 100, 7)))
        environment.globals.set("elsewhere", environment.userdata.wrap("Position", Position(200, 200, 7)))

        environment.loadChunk("clear = here:isSightClear({ x = 101, y = 101, z = 7 })")
        environment.loadChunk("floors = here:isSightClear({ x = 101, y = 101, z = 6 }, true)")
        environment.loadChunk("tile = here:getTile()")
        environment.loadChunk("noTile = elsewhere:getTile()")
        environment.loadChunk("zones = here:getZones()")
        environment.loadChunk("zoneCount = #zones")
        environment.loadChunk("noZones = elsewhere:getZones()")

        assertThat(environment.getGlobal("clear").toboolean()).isTrue()
        assertThat(environment.getGlobal("floors").toboolean()).isFalse()
        assertThat(environment.getGlobal("tile").isuserdata()).isTrue()
        assertThat(environment.getGlobal("noTile").isnil()).isTrue()
        assertThat(environment.getGlobal("zoneCount").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("noZones").isnil()).isTrue()
    }

    @Test
    fun `Position effect and sound methods delegate to the seam`() {
        val environment = env()
        PositionFunctions.install(environment, FakePositionOperations())
        environment.globals.set("here", environment.userdata.wrap("Position", Position(100, 100, 7)))
        environment.globals.set("actor", environment.userdata.wrap("Creature", Any()))

        environment.loadChunk("effect = here:sendMagicEffect(3)")
        environment.loadChunk("removed = here:removeMagicEffect(3)")
        environment.loadChunk("distance = here:sendDistanceEffect({ x = 101, y = 100, z = 7 }, 4, actor)")
        environment.loadChunk("single = here:sendSingleSoundEffect(5, actor)")
        environment.loadChunk("double = here:sendDoubleSoundEffect(5, 6, actor)")

        assertThat(environment.getGlobal("effect").toboolean()).isTrue()
        assertThat(environment.getGlobal("removed").toboolean()).isTrue()
        assertThat(environment.getGlobal("distance").toboolean()).isTrue()
        assertThat(environment.getGlobal("single").toboolean()).isTrue()
        assertThat(environment.getGlobal("double").toboolean()).isTrue()
    }
}
