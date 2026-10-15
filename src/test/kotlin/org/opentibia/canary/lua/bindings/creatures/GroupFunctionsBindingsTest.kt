package org.opentibia.canary.lua.bindings.creatures

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.PlayerFlags_t
import org.opentibia.canary.creatures.grouping.Group
import org.opentibia.canary.lua.runtime.LuaEnvironment

/**
 * Transcript-style tests for the Canary `Group` metatable ported from
 * src/lua/functions/creatures/player/group_functions.cpp.
 *
 * The fake [GroupOperations] records exactly which primitive the Lua function
 * forwarded and returns the same nullable shapes as the C++ `lua_pushnil` guards.
 */
class GroupFunctionsBindingsTest {

    private val gamemaster = Group(
        name = "gamemaster",
        id = 4,
        access = true,
        flags = mutableSetOf(PlayerFlags_t.CannotUseCombat, PlayerFlags_t.CannotUseSpells),
        maxDepotItems = 500,
        maxVipEntries = 200,
    )

    private class FakeGroupOperations : GroupOperations {
        var lookup: (Int) -> Any? = { null }
        var flags: Long? = null
        var access: Boolean? = null
        var maxDepotItems: Int? = null
        var maxVipEntries: Int? = null
        var hasFlagResult: Boolean? = null
        var lastFlag: Int? = null

        override fun create(id: Int): Any? = lookup(id)

        override fun getId(group: Any): Int? = (group as? Group)?.id

        override fun getName(group: Any): String? = (group as? Group)?.name

        override fun getFlags(group: Any): Long? = flags

        override fun getAccess(group: Any): Boolean? = access

        override fun getMaxDepotItems(group: Any): Int? = maxDepotItems

        override fun getMaxVipEntries(group: Any): Int? = maxVipEntries

        override fun hasFlag(group: Any, flag: Int): Boolean? {
            lastFlag = flag
            return hasFlagResult
        }
    }

    private fun env(): LuaEnvironment = LuaEnvironment().apply { initState() }

    private fun installed(fake: FakeGroupOperations = FakeGroupOperations()): Pair<LuaEnvironment, FakeGroupOperations> {
        val environment = env()
        GroupFunctions.install(environment, fake)
        return environment to fake
    }

    @Test
    fun `Group registers the Canary global and method names`() {
        val environment = env()
        val methods = listOf(
            "getId", "getName", "getFlags", "getAccess", "getMaxDepotItems", "getMaxVipEntries", "hasFlag",
        )
        val checks = methods.joinToString(" and ") { "(rawgetmetatable('Group').__metatable.$it ~= nil)" }
        environment.loadChunk("callable = (type(Group) == 'table') and (getmetatable(Group).__call ~= nil)\nmethods = $checks")

        assertThat(environment.getGlobal("callable").toboolean()).isTrue()
        assertThat(environment.getGlobal("methods").toboolean()).isTrue()
    }

    @Test
    fun `Group factory and getters use the Canary names`() {
        val (environment, fake) = installed()
        fake.lookup = { id -> gamemaster.takeIf { it.id == id } }
        fake.flags = 8193L
        fake.access = true
        fake.maxDepotItems = 500
        fake.maxVipEntries = 200
        environment.loadChunk(
            """
            g = Group(4)
            missing = Group(9)
            id = g:getId()
            name = g:getName()
            flags = g:getFlags()
            access = g:getAccess()
            depot = g:getMaxDepotItems()
            vip = g:getMaxVipEntries()
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("g").touserdata()).isSameAs(gamemaster)
        assertThat(environment.getGlobal("missing").isnil()).isTrue()
        assertThat(environment.getGlobal("id").toint()).isEqualTo(4)
        assertThat(environment.getGlobal("name").tojstring()).isEqualTo("gamemaster")
        assertThat(environment.getGlobal("flags").todouble()).isEqualTo(8193.0)
        assertThat(environment.getGlobal("access").toboolean()).isTrue()
        assertThat(environment.getGlobal("depot").toint()).isEqualTo(500)
        assertThat(environment.getGlobal("vip").toint()).isEqualTo(200)
    }

    @Test
    fun `Group hasFlag forwards the Canary flag number`() {
        val (environment, fake) = installed()
        fake.lookup = { gamemaster }
        fake.hasFlagResult = true
        environment.loadChunk("g = Group(4)\nhas = g:hasFlag(9)")

        assertThat(environment.getGlobal("has").toboolean()).isTrue()
        assertThat(fake.lastFlag).isEqualTo(9)
    }

    @Test
    fun `Group getters push nil when the seam cannot resolve the field`() {
        val (environment, fake) = installed()
        fake.lookup = { gamemaster }
        environment.loadChunk(
            """
            g = Group(4)
            flags = g:getFlags()
            access = g:getAccess()
            depot = g:getMaxDepotItems()
            vip = g:getMaxVipEntries()
            has = g:hasFlag(1)
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("flags").isnil()).isTrue()
        assertThat(environment.getGlobal("access").isnil()).isTrue()
        assertThat(environment.getGlobal("depot").isnil()).isTrue()
        assertThat(environment.getGlobal("vip").isnil()).isTrue()
        assertThat(environment.getGlobal("has").isnil()).isTrue()
    }

    @Test
    fun `Group equality follows the Canary userdata identity comparison`() {
        val (environment, fake) = installed()
        fake.lookup = { gamemaster }
        val other = Group(name = "player", id = 1)
        environment.globals.set("same", environment.userdata.wrap("Group", gamemaster))
        environment.globals.set("other", environment.userdata.wrap("Group", other))
        environment.loadChunk(
            """
            again = Group(4)
            identical = (again == same)
            different = (again == other)
            nullComparison = (again == nil)
            """.trimIndent(),
        )
        assertThat(environment.getGlobal("identical").toboolean()).isTrue()
        assertThat(environment.getGlobal("different").toboolean()).isFalse()
        assertThat(environment.getGlobal("nullComparison").toboolean()).isFalse()
    }

    @Test
    fun `World Group resolves the group catalog and computes the flag bitmask`() {
        val environment = env()
        GroupFunctions.install(environment, WorldGroupOperations(groups = { id -> gamemaster.takeIf { it.id == id } }))
        environment.loadChunk(
            """
            g = Group(4)
            flags = g:getFlags()
            combat = g:hasFlag(0)
            spells = g:hasFlag(13)
            other = g:hasFlag(1)
            invalid = g:hasFlag(99)
            access = g:getAccess()
            depot = g:getMaxDepotItems()
            vip = g:getMaxVipEntries()
            unknown = Group(9)
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("flags").todouble()).isEqualTo(8193.0)
        assertThat(environment.getGlobal("combat").toboolean()).isTrue()
        assertThat(environment.getGlobal("spells").toboolean()).isTrue()
        assertThat(environment.getGlobal("other").toboolean()).isFalse()
        assertThat(environment.getGlobal("invalid").toboolean()).isFalse()
        assertThat(environment.getGlobal("access").toboolean()).isTrue()
        assertThat(environment.getGlobal("depot").toint()).isEqualTo(500)
        assertThat(environment.getGlobal("vip").toint()).isEqualTo(200)
        assertThat(environment.getGlobal("unknown").isnil()).isTrue()
    }

    @Test
    fun `World Group getFlags is zero for a group without raised flags`() {
        val player = Group(name = "player", id = 1)
        val environment = env()
        GroupFunctions.install(environment, WorldGroupOperations(groups = { id -> player.takeIf { it.id == id } }))
        environment.loadChunk("g = Group(1)\nflags = g:getFlags()")

        assertThat(environment.getGlobal("flags").todouble()).isEqualTo(0.0)
    }
}
