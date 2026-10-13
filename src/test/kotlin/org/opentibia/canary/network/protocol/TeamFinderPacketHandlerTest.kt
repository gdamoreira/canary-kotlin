package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.grouping.Party
import org.opentibia.canary.creatures.grouping.TeamFinder
import org.opentibia.canary.creatures.grouping.TeamFinderList
import org.opentibia.canary.map.Position

/**
 * Decode and apply tests for the team-finder window opcodes, mirroring
 * `ProtocolGame::parseLeaderFinderWindow` (client opcode `0x2C`,
 * protocolgame.cpp:3928-3991), `ProtocolGame::createLeaderTeamFinder`
 * (protocolgame.cpp:3840-3898) and `ProtocolGame::parseMemberFinderWindow`
 * (client opcode `0x2D`, protocolgame.cpp:3993-4026).
 */
class TeamFinderPacketHandlerTest {

    @Test
    fun `parses the leader send window action`() {
        val payload = Unpooled.buffer()
        payload.writeByte(0)

        assertThat(TeamFinderPacketHandler.parse(TeamFinderPacketHandler.OPCODE_LEADER_FINDER_WINDOW, payload))
            .isEqualTo(LeaderFinderWindowCommand(action = 0))
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `parses the leader reset window action`() {
        val payload = Unpooled.buffer()
        payload.writeByte(1)

        assertThat(TeamFinderPacketHandler.parse(TeamFinderPacketHandler.OPCODE_LEADER_FINDER_WINDOW, payload))
            .isEqualTo(LeaderFinderWindowCommand(action = 1))
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `parses the leader member status action`() {
        val payload = Unpooled.buffer()
        payload.writeByte(2)
        payload.writeIntLE(77)
        payload.writeByte(3)

        assertThat(TeamFinderPacketHandler.parse(TeamFinderPacketHandler.OPCODE_LEADER_FINDER_WINDOW, payload))
            .isEqualTo(LeaderFinderMemberStatusCommand(memberId = 77L, memberStatus = 3))
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `parses the create leader assembler action for a hunt team`() {
        val payload = Unpooled.buffer()
        writeCreateLeaderPayload(
            payload,
            teamType = 2,
            tail = {
                it.writeShortLE(10) // hunt_type
                it.writeShortLE(20) // hunt_area
            },
        )

        val command = TeamFinderPacketHandler.parse(TeamFinderPacketHandler.OPCODE_LEADER_FINDER_WINDOW, payload)

        assertThat(command).isEqualTo(
            CreateLeaderTeamFinderCommand(
                minLevel = 100,
                maxLevel = 250,
                vocationIDs = 3,
                teamSlots = 5,
                freeSlots = 2,
                partyBool = true,
                timestamp = 1_700_000_000L,
                teamType = 2,
                bossID = 0,
                huntType = 10,
                huntArea = 20,
                questID = 0,
            ),
        )
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `parses the create leader assembler action for a boss team`() {
        val payload = Unpooled.buffer()
        writeCreateLeaderPayload(payload, teamType = 1) { it.writeShortLE(4040) }

        val command = TeamFinderPacketHandler.parse(TeamFinderPacketHandler.OPCODE_LEADER_FINDER_WINDOW, payload)

        assertThat(command).isEqualTo(
            CreateLeaderTeamFinderCommand(
                minLevel = 100,
                maxLevel = 250,
                vocationIDs = 3,
                teamSlots = 5,
                freeSlots = 2,
                partyBool = true,
                timestamp = 1_700_000_000L,
                teamType = 1,
                bossID = 4040,
                huntType = 0,
                huntArea = 0,
                questID = 0,
            ),
        )
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `parses the create leader assembler action for a quest team`() {
        val payload = Unpooled.buffer()
        writeCreateLeaderPayload(payload, teamType = 3) { it.writeShortLE(555) }

        val command = TeamFinderPacketHandler.parse(TeamFinderPacketHandler.OPCODE_LEADER_FINDER_WINDOW, payload)

        assertThat(command).isEqualTo(
            CreateLeaderTeamFinderCommand(
                minLevel = 100,
                maxLevel = 250,
                vocationIDs = 3,
                teamSlots = 5,
                freeSlots = 2,
                partyBool = true,
                timestamp = 1_700_000_000L,
                teamType = 3,
                bossID = 0,
                huntType = 0,
                huntArea = 0,
                questID = 555,
            ),
        )
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `rejects a truncated member status action`() {
        val payload = Unpooled.buffer()
        payload.writeByte(2)
        payload.writeShortLE(77)

        assertThat(TeamFinderPacketHandler.parse(TeamFinderPacketHandler.OPCODE_LEADER_FINDER_WINDOW, payload)).isNull()
    }

    @Test
    fun `parses the member list action`() {
        val payload = Unpooled.buffer()
        payload.writeByte(0)

        assertThat(TeamFinderPacketHandler.parse(TeamFinderPacketHandler.OPCODE_MEMBER_FINDER_WINDOW, payload))
            .isEqualTo(MemberFinderListCommand(action = 0))
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `parses the member join request action`() {
        val payload = Unpooled.buffer()
        payload.writeByte(1)
        payload.writeIntLE(42)

        assertThat(TeamFinderPacketHandler.parse(TeamFinderPacketHandler.OPCODE_MEMBER_FINDER_WINDOW, payload))
            .isEqualTo(TeamFinderJoinRequestCommand(leaderId = 42L))
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `parses any other member action as a cancel request`() {
        val payload = Unpooled.buffer()
        payload.writeByte(2)
        payload.writeIntLE(42)

        assertThat(TeamFinderPacketHandler.parse(TeamFinderPacketHandler.OPCODE_MEMBER_FINDER_WINDOW, payload))
            .isEqualTo(TeamFinderCancelRequestCommand(leaderId = 42L))
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `rejects a truncated member request`() {
        val payload = Unpooled.buffer()
        payload.writeByte(1)
        payload.writeShortLE(42)

        assertThat(TeamFinderPacketHandler.parse(TeamFinderPacketHandler.OPCODE_MEMBER_FINDER_WINDOW, payload)).isNull()
    }

    @Test
    fun `applies the create action to a fresh assembler with the leader guid`() {
        val list = TeamFinderList()
        val leader = hero(1, "Leader")

        TeamFinderPacketHandler.apply(leader, createCommand(partyBool = false), list)

        val finder = list.getTeamFinder(leader)
        assertThat(finder).isNotNull
        assertThat(finder!!.minLevel).isEqualTo(100)
        assertThat(finder.maxLevel).isEqualTo(250)
        assertThat(finder.vocationIDs).isEqualTo(3)
        assertThat(finder.teamSlots).isEqualTo(5)
        assertThat(finder.freeSlots).isEqualTo(2)
        assertThat(finder.partyBool).isFalse()
        assertThat(finder.timestamp).isEqualTo(1_700_000_000L)
        assertThat(finder.teamType).isEqualTo(2)
        assertThat(finder.hunt_type).isEqualTo(10)
        assertThat(finder.hunt_area).isEqualTo(20)
        assertThat(finder.leaderGuid).isEqualTo(1L)
    }

    @Test
    fun `create action prefills the assembler with the party members`() {
        val leader = hero(1, "Leader")
        val member = hero(2, "Member")
        val party = Party(leader)
        party.invitePlayer(member)
        party.joinParty(member)
        val list = TeamFinderList()

        TeamFinderPacketHandler.apply(leader, createCommand(partyBool = true), list)

        assertThat(list.getTeamFinder(leader)!!.membersMap)
            .containsExactlyEntriesOf(mapOf(member.getGUID() to TeamFinder.MEMBER_STATUS_ACCEPTED))
    }

    @Test
    fun `reset action drops an existing assembler and keeps an unknown leader untouched`() {
        val list = TeamFinderList()
        val leader = hero(1, "Leader")
        val other = hero(2, "Other")
        list.getOrCreateTeamFinder(leader)
        list.getOrCreateTeamFinder(other)

        TeamFinderPacketHandler.apply(other, LeaderFinderWindowCommand(action = 1), list)
        assertThat(list.getTeamFinder(other)).isNull()
        assertThat(list.getTeamFinder(leader)).isNotNull

        TeamFinderPacketHandler.apply(leader, LeaderFinderWindowCommand(action = 0), list)
        assertThat(list.getTeamFinder(leader)).isNotNull

        TeamFinderPacketHandler.apply(hero(9, "NoFinder"), LeaderFinderWindowCommand(action = 1), list)
    }

    @Test
    fun `member status action updates the assembler and messages the member`() {
        val leader = hero(1, "Leader")
        val member = hero(2, "Member")
        val list = TeamFinderList(getPlayerByGuid = { guid -> if (guid == member.getGUID()) member else null })
        val finder = list.getOrCreateTeamFinder(leader)
        finder.membersMap[member.getGUID()] = TeamFinder.MEMBER_STATUS_REQUEST
        val messages = mutableListOf<String>()
        member.sendTextMessage = { messages.add(it) }

        TeamFinderPacketHandler.apply(
            leader,
            LeaderFinderMemberStatusCommand(memberId = member.getGUID(), memberStatus = 3),
            list,
        )

        assertThat(finder.membersMap[member.getGUID()]).isEqualTo(TeamFinder.MEMBER_STATUS_ACCEPTED)
        assertThat(messages).containsExactly("Your team finder request was accepted.")
    }

    @Test
    fun `join request stores the request on the requester assembler`() {
        val requester = hero(1, "Requester")
        val leader = hero(2, "Leader")
        val list = TeamFinderList(getPlayerByGuid = { guid -> if (guid == leader.getGUID()) leader else null })
        val requesterFinder = list.getOrCreateTeamFinder(requester)

        TeamFinderPacketHandler.apply(requester, TeamFinderJoinRequestCommand(leaderId = leader.getGUID()), list)

        assertThat(requesterFinder.membersMap)
            .containsExactlyEntriesOf(mapOf(requester.getGUID() to TeamFinder.MEMBER_STATUS_REQUEST))
    }

    @Test
    fun `cancel request removes the requester entry`() {
        val requester = hero(1, "Requester")
        val list = TeamFinderList()
        val finder = list.getOrCreateTeamFinder(requester)
        finder.membersMap[requester.getGUID()] = TeamFinder.MEMBER_STATUS_REQUEST

        TeamFinderPacketHandler.apply(requester, TeamFinderCancelRequestCommand(leaderId = 2L), list)

        assertThat(finder.membersMap).isEmpty()
    }

    @Test
    fun `returns null for an unrelated opcode`() {
        assertThat(TeamFinderPacketHandler.parse(0x2B, Unpooled.buffer())).isNull()
    }

    private fun hero(id: Long, name: String): Player =
        Player(id, name, 100, 100, position = Position(100, 100, 7))

    private fun createCommand(partyBool: Boolean): CreateLeaderTeamFinderCommand = CreateLeaderTeamFinderCommand(
        minLevel = 100,
        maxLevel = 250,
        vocationIDs = 3,
        teamSlots = 5,
        freeSlots = 2,
        partyBool = partyBool,
        timestamp = 1_700_000_000L,
        teamType = 2,
        bossID = 0,
        huntType = 10,
        huntArea = 20,
        questID = 0,
    )

    private fun writeCreateLeaderPayload(payload: ByteBuf, teamType: Int, tail: (ByteBuf) -> Unit) {
        payload.writeByte(3)
        payload.writeShortLE(100)
        payload.writeShortLE(250)
        payload.writeByte(3)
        payload.writeShortLE(5)
        payload.writeShortLE(2)
        payload.writeByte(1)
        payload.writeIntLE(1_700_000_000)
        payload.writeByte(teamType)
        tail(payload)
    }
}
