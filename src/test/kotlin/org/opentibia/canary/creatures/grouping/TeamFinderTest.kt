package org.opentibia.canary.creatures.grouping

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.map.Position

/**
 * Transcript tests for the team assemble finder:
 * `TeamFinder` (src/creatures/players/grouping/team_finder.hpp:17-55), the
 * `Game::teamFinderMap` accessors (src/game/game.cpp:930-950) and the
 * `ProtocolGame` parsing paths that mutate the finder
 * (src/server/network/protocol/protocolgame.cpp:3840-4025).
 */
class TeamFinderTest {

    private fun player(id: Long, name: String): Player =
        Player(id, name, 100, 100, position = Position(100, 100, 7))

    @Test
    fun `default assembler mirrors the zero-initialised canary fields`() {
        val finder = TeamFinder()

        assertThat(finder.minLevel).isZero()
        assertThat(finder.maxLevel).isZero()
        assertThat(finder.vocationIDs).isZero()
        assertThat(finder.teamSlots).isZero()
        assertThat(finder.freeSlots).isZero()
        assertThat(finder.partyBool).isFalse()
        assertThat(finder.timestamp).isZero()
        assertThat(finder.teamType).isZero()
        assertThat(finder.bossID).isZero()
        assertThat(finder.hunt_type).isZero()
        assertThat(finder.hunt_area).isZero()
        assertThat(finder.questID).isZero()
        assertThat(finder.leaderGuid).isZero()
        assertThat(finder.membersMap).isEmpty()
    }

    @Test
    fun `full constructor stores every parsed field`() {
        val finder = TeamFinder(
            minLevel = 100,
            maxLevel = 250,
            vocationIDs = 3,
            teamSlots = 5,
            freeSlots = 2,
            partyBool = true,
            timestamp = 1_700_000_000,
            teamType = 2,
            bossID = 0,
            hunt_type = 10,
            hunt_area = 20,
            questID = 0,
            leaderGuid = 42,
            membersMap = linkedMapOf(7L to TeamFinder.MEMBER_STATUS_ACCEPTED),
        )

        assertThat(finder.minLevel).isEqualTo(100)
        assertThat(finder.maxLevel).isEqualTo(250)
        assertThat(finder.vocationIDs).isEqualTo(3)
        assertThat(finder.teamSlots).isEqualTo(5)
        assertThat(finder.freeSlots).isEqualTo(2)
        assertThat(finder.partyBool).isTrue()
        assertThat(finder.timestamp).isEqualTo(1_700_000_000)
        assertThat(finder.teamType).isEqualTo(2)
        assertThat(finder.hunt_type).isEqualTo(10)
        assertThat(finder.hunt_area).isEqualTo(20)
        assertThat(finder.leaderGuid).isEqualTo(42)
        assertThat(finder.membersMap).containsExactlyEntriesOf(mapOf(7L to TeamFinder.MEMBER_STATUS_ACCEPTED))
    }

    @Test
    fun `list keys assemblers by leader guid and creates them on demand`() {
        val list = TeamFinderList()
        val leader = player(42, "Leader")

        assertThat(list.getTeamFinder(leader)).isNull()

        val created = list.getOrCreateTeamFinder(leader)

        assertThat(list.getOrCreateTeamFinder(leader)).isSameAs(created)
        assertThat(list.getTeamFinder(leader)).isSameAs(created)
        assertThat(list.getTeamFinderList()).containsOnlyKeys(42L)
    }

    @Test
    fun `removeTeamFinderListed drops only the given leader`() {
        val list = TeamFinderList()
        val first = player(1, "First")
        val second = player(2, "Second")
        list.getOrCreateTeamFinder(first)
        list.getOrCreateTeamFinder(second)

        list.removeTeamFinderListed(first.getGUID())

        assertThat(list.getTeamFinder(first)).isNull()
        assertThat(list.getTeamFinder(second)).isNotNull
        assertThat(list.getTeamFinderList()).containsOnlyKeys(2L)
    }

    @Test
    fun `party prefill adds party members and the party leader excluding the creator`() {
        val partyLeader = player(1, "PartyLeader")
        val member = player(2, "Member")
        val party = Party(partyLeader)
        party.invitePlayer(member)
        party.joinParty(member)

        val byLeader = TeamFinder(partyBool = true)
        byLeader.addPartyMembers(partyLeader)

        assertThat(byLeader.membersMap)
            .containsExactlyEntriesOf(mapOf(member.getGUID() to TeamFinder.MEMBER_STATUS_ACCEPTED))

        val byMember = TeamFinder(partyBool = true)
        byMember.addPartyMembers(member)

        assertThat(byMember.membersMap)
            .containsExactlyEntriesOf(mapOf(partyLeader.getGUID() to TeamFinder.MEMBER_STATUS_ACCEPTED))
    }

    @Test
    fun `party prefill is skipped without the party flag or an actual party`() {
        val solo = player(1, "Solo")
        val noFlag = TeamFinder(partyBool = false)
        noFlag.addPartyMembers(solo)
        assertThat(noFlag.membersMap).isEmpty()

        val withFlag = TeamFinder(partyBool = true)
        withFlag.addPartyMembers(solo)
        assertThat(withFlag.membersMap).isEmpty()
    }

    @Test
    fun `party prefill does not overwrite an existing member status`() {
        val partyLeader = player(1, "PartyLeader")
        val member = player(2, "Member")
        val party = Party(partyLeader)
        party.invitePlayer(member)
        party.joinParty(member)

        val finder = TeamFinder(partyBool = true, membersMap = linkedMapOf(member.getGUID() to TeamFinder.MEMBER_STATUS_REQUEST))
        finder.addPartyMembers(partyLeader)

        assertThat(finder.membersMap)
            .containsExactlyEntriesOf(mapOf(member.getGUID() to TeamFinder.MEMBER_STATUS_REQUEST))
    }

    @Test
    fun `setMemberStatus updates the matching entry and messages the member`() {
        val leader = player(1, "Leader")
        val member = player(2, "Member")
        val list = TeamFinderList(getPlayerByGuid = { guid -> if (guid == member.getGUID()) member else null })
        val finder = list.getOrCreateTeamFinder(leader)
        finder.membersMap[member.getGUID()] = TeamFinder.MEMBER_STATUS_REQUEST

        val messages = mutableListOf<String>()
        member.sendTextMessage = { messages.add(it) }

        list.setMemberStatus(leader, member.getGUID(), TeamFinder.MEMBER_STATUS_INVITED)
        assertThat(finder.membersMap[member.getGUID()]).isEqualTo(TeamFinder.MEMBER_STATUS_INVITED)
        assertThat(messages).containsExactly("You are invited to a new team.")

        list.setMemberStatus(leader, member.getGUID(), TeamFinder.MEMBER_STATUS_ACCEPTED)
        assertThat(finder.membersMap[member.getGUID()]).isEqualTo(TeamFinder.MEMBER_STATUS_ACCEPTED)
        assertThat(messages).containsExactly(
            "You are invited to a new team.",
            "Your team finder request was accepted.",
        )

        list.setMemberStatus(leader, member.getGUID(), TeamFinder.MEMBER_STATUS_DENIED)
        assertThat(finder.membersMap[member.getGUID()]).isEqualTo(TeamFinder.MEMBER_STATUS_DENIED)
        assertThat(messages).containsExactly(
            "You are invited to a new team.",
            "Your team finder request was accepted.",
            "Your team finder request was denied.",
        )
    }

    @Test
    fun `setMemberStatus ignores an offline member or a leader without an assembler`() {
        val leader = player(1, "Leader")
        val onlineMember = player(2, "Online")
        val list = TeamFinderList(getPlayerByGuid = { guid -> if (guid == onlineMember.getGUID()) onlineMember else null })
        val finder = list.getOrCreateTeamFinder(leader)
        finder.membersMap[onlineMember.getGUID()] = TeamFinder.MEMBER_STATUS_REQUEST

        val messages = mutableListOf<String>()
        onlineMember.sendTextMessage = { messages.add(it) }

        list.setMemberStatus(leader, 99L, TeamFinder.MEMBER_STATUS_ACCEPTED)
        assertThat(finder.membersMap[onlineMember.getGUID()]).isEqualTo(TeamFinder.MEMBER_STATUS_REQUEST)
        assertThat(messages).isEmpty()

        list.setMemberStatus(player(3, "NoFinder"), onlineMember.getGUID(), TeamFinder.MEMBER_STATUS_ACCEPTED)
        assertThat(finder.membersMap[onlineMember.getGUID()]).isEqualTo(TeamFinder.MEMBER_STATUS_REQUEST)
        assertThat(messages).isEmpty()
    }

    @Test
    fun `requestToJoin stores the request on the requesting player's assembler and notifies the leader`() {
        val requester = player(1, "Requester")
        val leader = player(2, "Leader")
        val list = TeamFinderList(getPlayerByGuid = { guid -> if (guid == leader.getGUID()) leader else null })
        val requesterFinder = list.getOrCreateTeamFinder(requester)

        val messages = mutableListOf<String>()
        leader.sendTextMessage = { messages.add(it) }

        list.requestToJoin(requester, leader.getGUID())

        assertThat(requesterFinder.membersMap)
            .containsExactlyEntriesOf(mapOf(requester.getGUID() to TeamFinder.MEMBER_STATUS_REQUEST))
        assertThat(messages).containsExactly("There is a new request to join your team.")
    }

    @Test
    fun `requestToJoin keeps an existing status and ignores an offline leader or missing assembler`() {
        val requester = player(1, "Requester")
        val leader = player(2, "Leader")
        val list = TeamFinderList(getPlayerByGuid = { guid -> if (guid == leader.getGUID()) leader else null })
        val requesterFinder = list.getOrCreateTeamFinder(requester)
        requesterFinder.membersMap[requester.getGUID()] = TeamFinder.MEMBER_STATUS_ACCEPTED

        list.requestToJoin(requester, leader.getGUID())
        assertThat(requesterFinder.membersMap[requester.getGUID()]).isEqualTo(TeamFinder.MEMBER_STATUS_ACCEPTED)

        list.requestToJoin(player(9, "NoFinder"), leader.getGUID())
        assertThat(requesterFinder.membersMap).containsOnlyKeys(requester.getGUID())

        list.requestToJoin(requester, 404L)
        assertThat(requesterFinder.membersMap).containsOnlyKeys(requester.getGUID())
    }

    @Test
    fun `cancelRequest removes the requesting player's entry`() {
        val requester = player(1, "Requester")
        val list = TeamFinderList()
        val finder = list.getOrCreateTeamFinder(requester)
        finder.membersMap[requester.getGUID()] = TeamFinder.MEMBER_STATUS_REQUEST

        list.cancelRequest(requester)
        assertThat(finder.membersMap).isEmpty()

        list.cancelRequest(requester)
        assertThat(finder.membersMap).isEmpty()

        list.cancelRequest(player(9, "NoFinder"))
        assertThat(finder.membersMap).isEmpty()
    }
}
