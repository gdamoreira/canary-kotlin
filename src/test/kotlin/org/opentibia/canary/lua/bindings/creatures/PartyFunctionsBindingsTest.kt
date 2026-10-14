package org.opentibia.canary.lua.bindings.creatures

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.vocations.Vocations
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.Position

/**
 * Transcript-style tests for the Canary `Party` metatable ported from
 * src/lua/functions/creatures/player/party_functions.cpp.
 */
class PartyFunctionsBindingsTest {

    private class FakePartyOperations : PartyOperations {
        var created: Any? = Any()
        var disbandResult: Boolean? = true
        var leader: Any? = Any()
        var boolResult: Boolean? = true
        var members: List<Any>? = null
        var memberCount: Int? = null
        var invitees: List<Any>? = null
        var inviteeCount: Int? = null
        var uniqueVocations: Int? = null
        var sharedActive: Boolean? = null
        var sharedEnabled: Boolean? = null
        var lastPlayer: Any? = null
        var lastExperience: Long? = null
        var lastActive: Boolean? = null
        var createCalls = 0

        override fun create(player: Any): Any? {
            createCalls++
            lastPlayer = player
            return created
        }

        override fun disband(party: Any): Boolean? = disbandResult

        override fun getLeader(party: Any): Any? = leader

        override fun setLeader(party: Any, player: Any?): Boolean? {
            lastPlayer = player
            return boolResult
        }

        override fun getMembers(party: Any): List<Any>? = members

        override fun getMemberCount(party: Any): Int? = memberCount

        override fun getInvitees(party: Any): List<Any>? = invitees

        override fun getInviteeCount(party: Any): Int? = inviteeCount

        override fun getUniqueVocationsCount(party: Any): Int? = uniqueVocations

        override fun addInvite(party: Any, player: Any?): Boolean? {
            lastPlayer = player
            return boolResult
        }

        override fun removeInvite(party: Any, player: Any?): Boolean? {
            lastPlayer = player
            return boolResult
        }

        override fun addMember(party: Any, player: Any?): Boolean? {
            lastPlayer = player
            return boolResult
        }

        override fun removeMember(party: Any, player: Any?): Boolean? {
            lastPlayer = player
            return boolResult
        }

        override fun isSharedExperienceActive(party: Any): Boolean? = sharedActive

        override fun isSharedExperienceEnabled(party: Any): Boolean? = sharedEnabled

        override fun shareExperience(party: Any, experience: Long): Boolean? {
            lastExperience = experience
            return boolResult
        }

        override fun setSharedExperience(party: Any, active: Boolean): Boolean? {
            lastActive = active
            return boolResult
        }
    }

    private fun player(id: Long, name: String): Player =
        Player(id = id, name = name, maxHealth = 150, health = 150, position = Position(10, 10, 7))

    private fun vocations(vararg ids: Pair<Int, Int>): Vocations {
        val vocations = Vocations()
        vocations.loadFromXml(
            buildString {
                append("<vocations>")
                for ((id, baseId) in ids) {
                    append("<vocation id=\"$id\" name=\"V$id\" clientid=\"$id\" baseid=\"$baseId\"/>")
                }
                append("</vocations>")
            },
        )
        return vocations
    }

    private fun env(): LuaEnvironment = LuaEnvironment().apply { initState() }

    private fun installed(fake: FakePartyOperations = FakePartyOperations()): Pair<LuaEnvironment, FakePartyOperations> {
        val environment = env()
        PartyFunctions.install(environment, fake)
        return environment to fake
    }

    @Test
    fun `Party registers the Canary global and method names`() {
        val environment = env()
        val methods = listOf(
            "disband", "getLeader", "setLeader", "getMembers", "getMemberCount",
            "getInvitees", "getInviteeCount", "getUniqueVocationsCount", "addInvite",
            "removeInvite", "addMember", "removeMember", "isSharedExperienceActive",
            "isSharedExperienceEnabled", "shareExperience", "setSharedExperience",
        )
        val checks = methods.joinToString(" and ") { "(rawgetmetatable('Party').__metatable.$it ~= nil)" }
        environment.loadChunk("callable = (type(Party) == 'table') and (getmetatable(Party).__call ~= nil)\nmethods = $checks")

        assertThat(environment.getGlobal("callable").toboolean()).isTrue()
        assertThat(environment.getGlobal("methods").toboolean()).isTrue()
    }

    @Test
    fun `Party factory forwards the player userdata and guards non player arguments`() {
        val (environment, fake) = installed()
        val leader = player(1, "Leader")
        environment.globals.set("p", environment.userdata.wrap("Player", leader))
        environment.loadChunk(
            """
            party = Party(p)
            missing = Party(nil)
            nonPlayer = Party(5)
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("party").isuserdata()).isTrue()
        assertThat(environment.getGlobal("missing").isnil()).isTrue()
        assertThat(environment.getGlobal("nonPlayer").isnil()).isTrue()
        assertThat(fake.createCalls).isEqualTo(1)
        assertThat(fake.lastPlayer).isSameAs(leader)
    }

    @Test
    fun `Party member and invite methods use the Canary names and argument order`() {
        val (environment, fake) = installed()
        val leader = player(1, "Leader")
        val member = player(2, "Member")
        fake.members = listOf(member)
        fake.invitees = listOf(member)
        fake.memberCount = 1
        fake.inviteeCount = 1
        fake.uniqueVocations = 2
        fake.sharedActive = true
        fake.sharedEnabled = false
        fake.leader = leader
        environment.globals.set("party", environment.userdata.wrap("Party", Any()))
        environment.globals.set("m", environment.userdata.wrap("Player", member))
        environment.globals.set("l", environment.userdata.wrap("Player", leader))
        environment.loadChunk(
            """
            member = party:addMember(m)
            invited = party:addInvite(m)
            removedInvite = party:removeInvite(m)
            removedMember = party:removeMember(m)
            leaderSet = party:setLeader(m)
            members = party:getMembers()
            memberCount = party:getMemberCount()
            invitees = party:getInvitees()
            inviteeCount = party:getInviteeCount()
            unique = party:getUniqueVocationsCount()
            active = party:isSharedExperienceActive()
            enabled = party:isSharedExperienceEnabled()
            shared = party:shareExperience(1500)
            sharedSet = party:setSharedExperience(true)
            leader = party:getLeader()
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("member").toboolean()).isTrue()
        assertThat(environment.getGlobal("invited").toboolean()).isTrue()
        assertThat(environment.getGlobal("removedInvite").toboolean()).isTrue()
        assertThat(environment.getGlobal("removedMember").toboolean()).isTrue()
        assertThat(environment.getGlobal("leaderSet").toboolean()).isTrue()
        assertThat(environment.getGlobal("memberCount").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("members").length()).isEqualTo(1)
        assertThat(environment.getGlobal("members").get(1).touserdata()).isSameAs(member)
        assertThat(environment.getGlobal("inviteeCount").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("invitees").length()).isEqualTo(1)
        assertThat(environment.getGlobal("unique").toint()).isEqualTo(2)
        assertThat(environment.getGlobal("active").toboolean()).isTrue()
        assertThat(environment.getGlobal("enabled").toboolean()).isFalse()
        assertThat(environment.getGlobal("shared").toboolean()).isTrue()
        assertThat(fake.lastExperience).isEqualTo(1500L)
        assertThat(environment.getGlobal("sharedSet").toboolean()).isTrue()
        assertThat(fake.lastActive).isTrue()
        assertThat(environment.getGlobal("leader").touserdata()).isSameAs(leader)
    }

    @Test
    fun `Party getters push nil when the seam cannot resolve the party`() {
        val (environment, fake) = installed()
        val leader = player(1, "Leader")
        fake.created = null
        fake.disbandResult = null
        fake.boolResult = null
        fake.leader = null
        environment.globals.set("l", environment.userdata.wrap("Player", leader))
        environment.globals.set("party", environment.userdata.wrap("Party", Any()))
        environment.loadChunk(
            """
            missing = Party(l)
            members = party:getMembers()
            memberCount = party:getMemberCount()
            invitees = party:getInvitees()
            inviteeCount = party:getInviteeCount()
            unique = party:getUniqueVocationsCount()
            active = party:isSharedExperienceActive()
            enabled = party:isSharedExperienceEnabled()
            shared = party:shareExperience(1)
            sharedSet = party:setSharedExperience(false)
            leader = party:getLeader()
            disband = party:disband()
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("missing").isnil()).isTrue()
        assertThat(environment.getGlobal("members").isnil()).isTrue()
        assertThat(environment.getGlobal("memberCount").isnil()).isTrue()
        assertThat(environment.getGlobal("invitees").isnil()).isTrue()
        assertThat(environment.getGlobal("inviteeCount").isnil()).isTrue()
        assertThat(environment.getGlobal("unique").isnil()).isTrue()
        assertThat(environment.getGlobal("active").isnil()).isTrue()
        assertThat(environment.getGlobal("enabled").isnil()).isTrue()
        assertThat(environment.getGlobal("shared").isnil()).isTrue()
        assertThat(environment.getGlobal("sharedSet").isnil()).isTrue()
        assertThat(environment.getGlobal("leader").isnil()).isTrue()
        assertThat(environment.getGlobal("disband").isnil()).isTrue()
    }

    @Test
    fun `World Party creates one party per player and invalidates it on disband`() {
        val environment = env()
        val leader = player(1, "Leader")
        environment.globals.set("p", environment.userdata.wrap("Player", leader))
        environment.loadChunk(
            """
            party = Party(p)
            again = Party(p)
            created = party ~= nil
            leaderFound = (party:getLeader() == p)
            disbanded = party:disband()
            disbandedAgain = party:disband()
            countAfter = party:getMemberCount()
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("created").toboolean()).isTrue()
        assertThat(environment.getGlobal("again").isnil()).isTrue()
        assertThat(environment.getGlobal("leaderFound").toboolean()).isTrue()
        assertThat(environment.getGlobal("disbanded").toboolean()).isTrue()
        assertThat(environment.getGlobal("disbandedAgain").isnil()).isTrue()
        assertThat(environment.getGlobal("countAfter").isnil()).isTrue()
        assertThat(leader.getParty()).isNull()
    }

    @Test
    fun `World Party requires an invitation before joining and passes leadership`() {
        val environment = env()
        val leader = player(1, "Leader")
        val member = player(2, "Member")
        val stranger = player(3, "Stranger")
        environment.globals.set("p", environment.userdata.wrap("Player", leader))
        environment.globals.set("m", environment.userdata.wrap("Player", member))
        environment.globals.set("s", environment.userdata.wrap("Player", stranger))
        environment.loadChunk(
            """
            party = Party(p)
            notInvited = party:addMember(s)
            invited = party:addInvite(m)
            inviteeCount = party:getInviteeCount()
            firstInvitee = party:getInvitees()[1]
            memberAdded = party:addMember(m)
            memberCount = party:getMemberCount()
            firstMember = party:getMembers()[1]
            invitedAgain = party:addInvite(m)
            leaderSet = party:setLeader(m)
            newLeader = party:getLeader()
            oldLeaderIsMember = (party:getMembers()[1] == p)
            removed = party:removeMember(p)
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("notInvited").toboolean()).isFalse()
        assertThat(environment.getGlobal("invited").toboolean()).isTrue()
        assertThat(environment.getGlobal("inviteeCount").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("firstInvitee").touserdata()).isSameAs(member)
        assertThat(environment.getGlobal("memberAdded").toboolean()).isTrue()
        assertThat(environment.getGlobal("memberCount").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("firstMember").touserdata()).isSameAs(member)
        assertThat(environment.getGlobal("invitedAgain").toboolean()).isTrue()
        assertThat(environment.getGlobal("leaderSet").toboolean()).isTrue()
        assertThat(environment.getGlobal("newLeader").touserdata()).isSameAs(member)
        assertThat(environment.getGlobal("oldLeaderIsMember").toboolean()).isTrue()
        assertThat(environment.getGlobal("removed").toboolean()).isTrue()
        assertThat(stranger.getParty()).isNull()
    }

    @Test
    fun `World Party counts unique base vocations including the leader`() {
        val registry = vocations(1 to 1, 2 to 2, 3 to 2)
        val environment = env()
        val leader = player(1, "Leader").apply {
            vocations = registry
            vocationId = 1
        }
        val druid = player(2, "Druid").apply {
            vocations = registry
            vocationId = 2
        }
        val twin = player(3, "Twin").apply {
            vocations = registry
            vocationId = 3
        }
        val noVocation = player(4, "None")
        environment.globals.set("p", environment.userdata.wrap("Player", leader))
        environment.globals.set("d", environment.userdata.wrap("Player", druid))
        environment.globals.set("t", environment.userdata.wrap("Player", twin))
        environment.globals.set("n", environment.userdata.wrap("Player", noVocation))
        environment.loadChunk(
            """
            party = Party(p)
            party:addInvite(d)
            party:addMember(d)
            uniqueAfterDruid = party:getUniqueVocationsCount()
            party:addInvite(t)
            party:addMember(t)
            uniqueAfterTwin = party:getUniqueVocationsCount()
            party:addInvite(n)
            party:addMember(n)
            uniqueAfterNone = party:getUniqueVocationsCount()
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("uniqueAfterDruid").toint()).isEqualTo(2)
        assertThat(environment.getGlobal("uniqueAfterTwin").toint()).isEqualTo(2)
        assertThat(environment.getGlobal("uniqueAfterNone").toint()).isEqualTo(2)
    }

    @Test
    fun `World Party shared experience and error guards follow Canary`() {
        val environment = env()
        val leader = player(1, "Leader")
        environment.globals.set("p", environment.userdata.wrap("Player", leader))
        environment.loadChunk(
            """
            party = Party(p)
            active = party:isSharedExperienceActive()
            enabled = party:isSharedExperienceEnabled()
            disabled = party:setSharedExperience(false)
            activeAfterDisable = party:isSharedExperienceActive()
            enabledAfterDisable = party:isSharedExperienceEnabled()
            enabledAgain = party:setSharedExperience(true)
            shared = party:shareExperience(250)
            badLeader = party:setLeader(nil)
            badInvite = party:addInvite(nil)
            badMember = party:addMember(nil)
            badRemove = party:removeMember(nil)
            badRemoveInvite = party:removeInvite(nil)
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("active").toboolean()).isTrue()
        assertThat(environment.getGlobal("enabled").toboolean()).isFalse()
        assertThat(environment.getGlobal("disabled").toboolean()).isTrue()
        assertThat(environment.getGlobal("activeAfterDisable").toboolean()).isFalse()
        assertThat(environment.getGlobal("enabledAfterDisable").toboolean()).isFalse()
        assertThat(environment.getGlobal("enabledAgain").toboolean()).isTrue()
        assertThat(environment.getGlobal("shared").toboolean()).isTrue()
        assertThat(environment.getGlobal("badLeader").isnil()).isTrue()
        assertThat(environment.getGlobal("badInvite").isnil()).isTrue()
        assertThat(environment.getGlobal("badMember").isnil()).isTrue()
        assertThat(environment.getGlobal("badRemove").isnil()).isTrue()
        assertThat(environment.getGlobal("badRemoveInvite").isnil()).isTrue()
    }

    @Test
    fun `Party equality follows the Canary userdata identity comparison`() {
        val environment = env()
        val leader = player(1, "Leader")
        environment.globals.set("p", environment.userdata.wrap("Player", leader))
        environment.loadChunk(
            """
            party = Party(p)
            identical = (party == party)
            nullComparison = (party == nil)
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("identical").toboolean()).isTrue()
        assertThat(environment.getGlobal("nullComparison").toboolean()).isFalse()
    }
}
