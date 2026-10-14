package org.opentibia.canary.lua.bindings.creatures

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.Position
import org.opentibia.canary.social.guild.Guild
import org.opentibia.canary.social.guild.GuildRank

/**
 * Transcript-style tests for the Canary `Guild` metatable ported from
 * src/lua/functions/creatures/player/guild_functions.cpp.
 */
class GuildFunctionsBindingsTest {

    private class FakeGuildOperations : GuildOperations {
        var lookup: (Long) -> Any? = { null }
        var members: List<Any>? = null
        var bankBalance: Long? = null
        var motd: String? = null
        var rank: GuildRank? = null
        var addRankResult: Boolean? = true
        var lastSetBankBalance: Long? = null
        var lastAddRank: List<Any?>? = null
        var lastMotd: String? = null

        override fun create(id: Long): Any? = lookup(id)

        override fun getId(guild: Any): Long? = (guild as? Guild)?.id

        override fun getName(guild: Any): String? = (guild as? Guild)?.name

        override fun getMembersOnline(guild: Any): List<Any>? = members

        override fun getBankBalance(guild: Any): Long? = bankBalance

        override fun setBankBalance(guild: Any, bankBalance: Long): Boolean? {
            lastSetBankBalance = bankBalance
            return true
        }

        override fun addRank(guild: Any, id: Long, name: String, level: Int): Boolean? {
            lastAddRank = listOf(id, name, level)
            return addRankResult
        }

        override fun getRankById(guild: Any, id: Long): GuildRank? = rank

        override fun getRankByLevel(guild: Any, level: Int): GuildRank? = rank

        override fun getMotd(guild: Any): String? = motd

        override fun setMotd(guild: Any, motd: String): Boolean? {
            lastMotd = motd
            return true
        }
    }

    private fun player(id: Long, name: String): Player =
        Player(id = id, name = name, maxHealth = 150, health = 150, position = Position(10, 10, 7))

    private fun env(): LuaEnvironment = LuaEnvironment().apply { initState() }

    private fun installed(fake: FakeGuildOperations = FakeGuildOperations()): Pair<LuaEnvironment, FakeGuildOperations> {
        val environment = env()
        GuildFunctions.install(environment, fake)
        return environment to fake
    }

    @Test
    fun `Guild registers the Canary global and method names`() {
        val environment = env()
        val methods = listOf(
            "getId", "getName", "getMembersOnline", "getBankBalance", "setBankBalance",
            "addRank", "getRankById", "getRankByLevel", "getMotd", "setMotd",
        )
        val checks = methods.joinToString(" and ") { "(rawgetmetatable('Guild').__metatable.$it ~= nil)" }
        environment.loadChunk("callable = (type(Guild) == 'table') and (getmetatable(Guild).__call ~= nil)\nmethods = $checks")

        assertThat(environment.getGlobal("callable").toboolean()).isTrue()
        assertThat(environment.getGlobal("methods").toboolean()).isTrue()
    }

    @Test
    fun `Guild factory and getters use the Canary names`() {
        val guild = Guild(7, "Red Rose")
        val member = player(21, "Hero")
        val (environment, fake) = installed()
        fake.lookup = { id -> guild.takeIf { it.id == id } }
        fake.members = listOf(member)
        fake.bankBalance = 100L
        fake.motd = "Welcome"
        environment.loadChunk(
            """
            g = Guild(7)
            missing = Guild(9)
            id = g:getId()
            name = g:getName()
            online = g:getMembersOnline()
            first = online[1]
            balance = g:getBankBalance()
            motd = g:getMotd()
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("g").touserdata()).isSameAs(guild)
        assertThat(environment.getGlobal("missing").isnil()).isTrue()
        assertThat(environment.getGlobal("id").toint()).isEqualTo(7)
        assertThat(environment.getGlobal("name").tojstring()).isEqualTo("Red Rose")
        assertThat(environment.getGlobal("online").istable()).isTrue()
        assertThat(environment.getGlobal("online").length()).isEqualTo(1)
        assertThat(environment.getGlobal("first").touserdata()).isSameAs(member)
        assertThat(environment.getGlobal("balance").toint()).isEqualTo(100)
        assertThat(environment.getGlobal("motd").tojstring()).isEqualTo("Welcome")
    }

    @Test
    fun `Guild setters and rank lookups forward the Canary arguments`() {
        val (environment, fake) = installed()
        fake.lookup = { Guild(7, "Red Rose") }
        fake.rank = GuildRank(3, "Vice-Leader", 2)
        environment.loadChunk(
            """
            g = Guild(7)
            bankSet = g:setBankBalance(5000)
            rankAdded = g:addRank(9, "Warden", 2)
            byId = g:getRankById(9)
            byLevel = g:getRankByLevel(2)
            id = byId.id
            name = byId.name
            level = byId.level
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("bankSet").toboolean()).isTrue()
        assertThat(fake.lastSetBankBalance).isEqualTo(5000L)
        assertThat(environment.getGlobal("rankAdded").toboolean()).isTrue()
        assertThat(fake.lastAddRank).containsExactly(9L, "Warden", 2)
        assertThat(environment.getGlobal("id").toint()).isEqualTo(3)
        assertThat(environment.getGlobal("name").tojstring()).isEqualTo("Vice-Leader")
        assertThat(environment.getGlobal("level").toint()).isEqualTo(2)
        assertThat(environment.getGlobal("byLevel").istable()).isTrue()
    }

    @Test
    fun `Guild getters push nil when the seam cannot resolve the field`() {
        val (environment, fake) = installed()
        fake.lookup = { Guild(7, "Red Rose") }
        environment.loadChunk(
            """
            g = Guild(7)
            online = g:getMembersOnline()
            balance = g:getBankBalance()
            motd = g:getMotd()
            byId = g:getRankById(1)
            byLevel = g:getRankByLevel(1)
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("online").isnil()).isTrue()
        assertThat(environment.getGlobal("balance").isnil()).isTrue()
        assertThat(environment.getGlobal("motd").isnil()).isTrue()
        assertThat(environment.getGlobal("byId").isnil()).isTrue()
        assertThat(environment.getGlobal("byLevel").isnil()).isTrue()
    }

    @Test
    fun `World Guild answers the full surface through the domain object`() {
        val guild = Guild(7, "Red Rose")
        val leader = player(21, "Leader")
        val member = player(22, "Member")
        guild.addMember(leader, GuildRank(1, "The Leader", 3))
        guild.addMember(member, GuildRank(2, "Member", 1))
        guild.setBankBalance(1234L)
        guild.setMotd("For the Rose")

        val environment = env()
        GuildFunctions.install(environment, WorldGuildOperations(guilds = { id -> guild.takeIf { it.id == id } }))
        environment.globals.set("g", environment.userdata.wrap("Guild", guild))
        environment.loadChunk(
            """
            byFactory = Guild(7) ~= nil
            unknown = Guild(8)
            online = g:getMembersOnline()
            count = #online
            balance = g:getBankBalance()
            motd = g:getMotd()
            bankSet = g:setBankBalance(4321)
            balanceAfter = g:getBankBalance()
            rankAdded = g:addRank(9, "Warden", 2)
            rank = g:getRankById(9)
            rankName = rank.name
            byLevel = g:getRankByLevel(2)
            level = byLevel.level
            motdSet = g:setMotd("Rose")
            motdAfter = g:getMotd()
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("byFactory").toboolean()).isTrue()
        assertThat(environment.getGlobal("unknown").isnil()).isTrue()
        assertThat(environment.getGlobal("count").toint()).isEqualTo(2)
        assertThat(environment.getGlobal("balance").toint()).isEqualTo(1234)
        assertThat(environment.getGlobal("motd").tojstring()).isEqualTo("For the Rose")
        assertThat(environment.getGlobal("bankSet").toboolean()).isTrue()
        assertThat(environment.getGlobal("balanceAfter").toint()).isEqualTo(4321)
        assertThat(environment.getGlobal("rankAdded").toboolean()).isTrue()
        assertThat(environment.getGlobal("rankName").tojstring()).isEqualTo("Warden")
        assertThat(environment.getGlobal("level").toint()).isEqualTo(2)
        assertThat(environment.getGlobal("motdSet").toboolean()).isTrue()
        assertThat(environment.getGlobal("motdAfter").tojstring()).isEqualTo("Rose")
    }

    @Test
    fun `Guild equality follows the Canary userdata identity comparison`() {
        val guild = Guild(7, "Red Rose")
        val (environment, _) = installed()
        fakeGuild(environment, guild)
        environment.loadChunk(
            """
            identical = (Guild(7) == g)
            unknown = (Guild(8) == g)
            nullComparison = (g == nil)
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("identical").toboolean()).isTrue()
        assertThat(environment.getGlobal("unknown").toboolean()).isFalse()
        assertThat(environment.getGlobal("nullComparison").toboolean()).isFalse()
    }

    private fun fakeGuild(environment: LuaEnvironment, guild: Guild, other: Guild? = null) {
        GuildFunctions.install(
            environment,
            WorldGuildOperations(guilds = { id ->
                when {
                    guild.id == id -> guild
                    other != null && other.id == id -> other
                    else -> null
                }
            }),
        )
        environment.globals.set("g", environment.userdata.wrap("Guild", guild))
    }
}
