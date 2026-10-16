package org.opentibia.canary.lua.bindings.library

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.Position
import org.opentibia.canary.lua.bindings.core.luaToPosition

/**
 * Transcript tests for the `GlobalFunctions` globals, mirroring
 * src/lua/functions/core/game/global_functions.cpp.
 */
class GlobalFunctionsLibraryTest {

    private class RecordingGlobalOperations : GlobalOperations {
        val errors = mutableListOf<String>()
        val calls = mutableListOf<String>()
        var timeNow = 1_000L
        var light: Pair<Int, Int> = 0 to 0
        var waypoint: Position? = null
        var cleanResult = 0L
        var upTime = 0L
        var saveResult = false
        var playerAddItemResult: Long? = null
        var channelResult = false
        var guildChannelResult = false
        val areaCalls = mutableListOf<Pair<List<List<Int>>, List<List<Int>>?>>()

        override fun cleanMap(): Long {
            calls += "cleanMap"
            return cleanResult
        }

        override fun createCombatArea(area: List<List<Int>>, extendedArea: List<List<Int>>?): Long? {
            areaCalls += area to extendedArea
            return 5L
        }

        override fun doPlayerAddItem(
            player: Any?,
            itemId: Int,
            count: Int,
            canDropOnMap: Boolean,
            subType: Int,
        ): Long? {
            calls += "doPlayerAddItem:$itemId:$count:$canDropOnMap:$subType"
            return playerAddItemResult
        }

        override fun getWaypointPositionByName(name: String): Position? = waypoint

        override fun getWorldLight(): Pair<Int, Int> = light

        override fun getWorldUpTime(): Long = upTime

        override fun saveServer(): Boolean {
            calls += "saveServer"
            return saveResult
        }

        override fun sendChannelMessage(channelId: Int, type: Int, message: String): Boolean {
            calls += "sendChannelMessage:$channelId:$type:$message"
            return channelResult
        }

        override fun sendGuildChannelMessage(guildId: Int, type: Int, message: String): Boolean {
            calls += "sendGuildChannelMessage:$guildId:$type:$message"
            return guildChannelResult
        }

        override fun reportError(message: String) {
            errors += message
        }

        override fun timeNowSeconds(): Long = timeNow
    }

    private fun environment(
        operations: GlobalOperations = object : GlobalOperations {},
    ): LuaEnvironment = LuaEnvironment().apply { initState() }.also { GlobalFunctions.install(it, operations) }

    @Test
    fun `constant globals match the C++ registrations`() {
        val environment = environment()

        environment.loadChunk(
            """
            wherever = INDEX_WHEREEVER
            virtual = VIRTUAL_PARENT
            now = systemTime()
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("wherever").toint()).isEqualTo(-1)
        assertThat(environment.getGlobal("virtual").toboolean()).isTrue()
        assertThat(environment.getGlobal("now").tolong()).isGreaterThan(0L)
    }

    @Test
    fun `createTable returns a Lua table`() {
        val environment = environment()

        environment.loadChunk("table = createTable(2, 3)")

        assertThat(environment.getGlobal("table").istable()).isTrue()
    }

    @Test
    fun `getFormattedTimeRemaining formats the remaining duration`() {
        val operations = RecordingGlobalOperations().apply { timeNow = 1_000L }
        val environment = environment(operations)

        environment.loadChunk(
            """
            days = getFormattedTimeRemaining(1000 + 180000)
            hours = getFormattedTimeRemaining(1000 + 3660)
            minute = getFormattedTimeRemaining(1000 + 30)
            expired = getFormattedTimeRemaining(1000 - 10)
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("days").tojstring()).isEqualTo("2 days")
        assertThat(environment.getGlobal("hours").tojstring()).isEqualTo("1 hour and 1 minute")
        assertThat(environment.getGlobal("minute").tojstring()).isEqualTo(" less than 1 minute")
        assertThat(environment.getGlobal("expired").tojstring()).isEmpty()
    }

    @Test
    fun `debugPrint reports the message and returns no values`() {
        val operations = RecordingGlobalOperations()
        val environment = environment(operations)

        environment.loadChunk("debugPrint('something happened')")

        assertThat(operations.errors).containsExactly("something happened")
    }

    @Test
    fun `reportError reports the description and returns it`() {
        val operations = RecordingGlobalOperations()
        val environment = environment(operations)

        environment.loadChunk("returned = reportError('script failed')")

        assertThat(operations.errors).containsExactly("script failed")
        assertThat(environment.getGlobal("returned").tojstring()).isEqualTo("script failed")
    }

    @Test
    fun `combat and world helpers delegate to the operations seam`() {
        val operations = RecordingGlobalOperations().apply {
            light = 40 to 215
            upTime = 3600L
            saveResult = true
            playerAddItemResult = 77L
            channelResult = true
            guildChannelResult = true
            cleanResult = 9L
            waypoint = Position(50, 60, 7)
        }
        val environment = environment(operations)
        environment.globals.set(
            "caster",
            environment.userdata.wrap(
                "Player",
                Player(1, "Caster", 100, 100, position = Position(100, 100, 7)),
            ),
        )

        environment.loadChunk(
            """
            level, color = getWorldLight()
            uptime = getWorldUpTime()
            saved = saveServer()
            item = doPlayerAddItem(caster, 2160, 5, false, 1)
            waypoint = getWaypointPositionByName('temple')
            channel = sendChannelMessage(1, 1, 'hello')
            guildChannel = sendGuildChannelMessage(2, 1, 'guild hello')
            cleaned = cleanMap()
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("level").toint()).isEqualTo(40)
        assertThat(environment.getGlobal("color").toint()).isEqualTo(215)
        assertThat(environment.getGlobal("uptime").tolong()).isEqualTo(3600L)
        assertThat(environment.getGlobal("saved").toboolean()).isTrue()
        assertThat(environment.getGlobal("item").toint()).isEqualTo(77)
        assertThat(luaToPosition(environment.getGlobal("waypoint"))).isEqualTo(Position(50, 60, 7))
        assertThat(environment.getGlobal("channel").toboolean()).isTrue()
        assertThat(environment.getGlobal("guildChannel").toboolean()).isTrue()
        assertThat(environment.getGlobal("cleaned").toint()).isEqualTo(9)
        assertThat(operations.calls).contains(
            "doPlayerAddItem:2160:5:false:1",
            "sendChannelMessage:1:1:hello",
            "sendGuildChannelMessage:2:1:guild hello",
            "saveServer",
            "cleanMap",
        )
    }

    @Test
    fun `createCombatArea parses the area table and returns the engine id`() {
        val operations = RecordingGlobalOperations()
        val environment = environment(operations)

        environment.loadChunk("areaId = createCombatArea({{0, 1, 0}, {1, 1, 1}}, {{0, 0}})")
        assertThat(environment.getGlobal("areaId").toint()).isEqualTo(5)
        assertThat(operations.areaCalls).containsExactly(
            listOf(listOf(0, 1, 0), listOf(1, 1, 1)) to listOf(listOf(0, 0)),
        )

        environment.loadChunk("invalid = createCombatArea({{0, 'x'}})")
        assertThat(environment.getGlobal("invalid").toboolean()).isFalse()
    }

    @Test
    fun `rawgetmetatable resolves registered userdata metatables`() {
        val environment = environment()

        environment.loadChunk(
            """
            playerMeta = rawgetmetatable('Player')
            unknown = rawgetmetatable('NotACanaryClass')
            rawgetmetatable('NotACanaryClass').marker = 7
            shared = rawgetmetatable('NotACanaryClass').marker
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("playerMeta").istable()).isTrue()
        assertThat(environment.getGlobal("unknown").istable()).isTrue()
        assertThat(environment.getGlobal("shared").toint()).isEqualTo(7)
    }

    @Test
    fun `isType accepts an object of the same metatable and rejects others`() {
        val environment = environment()

        environment.loadChunk(
            """
            local derived = setmetatable({}, { __index = {} })
            same = isType(derived, derived)
            different = isType(derived, {})
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("same").toboolean()).isTrue()
        assertThat(environment.getGlobal("different").toboolean()).isFalse()
    }
}
