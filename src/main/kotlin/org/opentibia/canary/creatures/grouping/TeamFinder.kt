package org.opentibia.canary.creatures.grouping

import org.opentibia.canary.creatures.Player

/**
 * `TeamFinder` (src/creatures/players/grouping/team_finder.hpp:17-53), the team
 * assemble finder published in the client's team-finder window.
 *
 * The C++ class is a plain data holder with a default and a 14-argument
 * constructor plus the static `TeamFinderNull` empty reference (team_finder.hpp:55);
 * the Kotlin port keeps every field name and width (uint16/uint8/uint32 -> Int/Long)
 * and represents the null reference with `null`.
 *
 * The member-status values stored in [membersMap] are Canary's raw protocol
 * bytes: 1 = request to join, 2 = invited, 3 = accepted, 4 = denied. They are
 * produced by `ProtocolGame::createLeaderTeamFinder`,
 * `ProtocolGame::parseLeaderFinderWindow` and
 * `ProtocolGame::parseMemberFinderWindow`
 * (protocolgame.cpp:3840-4025).
 */
class TeamFinder(
    var minLevel: Int = 0,
    var maxLevel: Int = 0,
    var vocationIDs: Int = 0,
    var teamSlots: Int = 0,
    var freeSlots: Int = 0,
    var partyBool: Boolean = false,
    var timestamp: Long = 0,
    var teamType: Int = 0,
    var bossID: Int = 0,
    var hunt_type: Int = 0,
    var hunt_area: Int = 0,
    var questID: Int = 0,
    var leaderGuid: Long = 0,
    val membersMap: MutableMap<Long, Int> = linkedMapOf(),
) {
    /**
     * `ProtocolGame::createLeaderTeamFinder` party prefill
     * (protocolgame.cpp:3886-3897): when [partyBool] is set and the player has a
     * party, every party member and the party leader (excluding the finder's own
     * player) joins [membersMap] with the accepted status. The wire reads and
     * the remaining field assignments stay in the protocol layer.
     */
    fun addPartyMembers(player: Player) {
        if (!partyBool) {
            return
        }

        val party = player.getParty() ?: return
        for (member in party.getMembers()) {
            if (member.getGUID() != player.getGUID()) {
                membersMap.putIfAbsent(member.getGUID(), MEMBER_STATUS_ACCEPTED)
            }
        }

        val partyLeader = party.getLeader()
        if (partyLeader != null && partyLeader.getGUID() != player.getGUID()) {
            membersMap.putIfAbsent(partyLeader.getGUID(), MEMBER_STATUS_ACCEPTED)
        }
    }

    companion object {
        /** `membersMap` "request to join" status (protocolgame.cpp:4015). */
        const val MEMBER_STATUS_REQUEST = 1

        /** `membersMap` "invited" status (protocolgame.cpp:3963-3965). */
        const val MEMBER_STATUS_INVITED = 2

        /** `membersMap` "accepted" status (protocolgame.cpp:3890, 3967-3969). */
        const val MEMBER_STATUS_ACCEPTED = 3

        /** `membersMap` "denied" status (protocolgame.cpp:3971-3973). */
        const val MEMBER_STATUS_DENIED = 4
    }
}

/**
 * Mirror of the `Game::teamFinderMap` owner
 * (`src/game/game.hpp:140-148, 903`, `game.cpp:930-950`): finders are keyed by
 * the leader's GUID ([Game::getOrCreateTeamFinder], game.cpp:939-946).
 *
 * The protocol window handlers mutate the stored finder; their effects are
 * transcribed here (not in the network package) because the port's protocol
 * layer is outside this wave:
 * `ProtocolGame::parseLeaderFinderWindow` (protocolgame.cpp:3928-3991) and
 * `ProtocolGame::parseMemberFinderWindow` (protocolgame.cpp:3993-4026).
 *
 * PARITY GAPS: the `sendLeaderTeamFinder`/`sendTeamFinderList` packet builds
 * (protocolgame.cpp:3675-3743, 3745-3838) and the trailing list refreshes after
 * every window action are protocol sends and are not ported.
 *
 * @param getPlayerByGuid `g_game().getPlayerByGUID(guid)`
 *   (player lookup used to resolve the online member the C++ handlers require).
 */
class TeamFinderList(
    private val getPlayerByGuid: (Long) -> Player? = { null },
) {
    private val teamFinderMap = linkedMapOf<Long, TeamFinder>()

    /** `Game::getTeamFinderList` (game.hpp:140-142). */
    fun getTeamFinderList(): Map<Long, TeamFinder> = teamFinderMap

    /** `Game::getTeamFinder` (game.cpp:930-937); `null` is `TeamFinderNull`. */
    fun getTeamFinder(player: Player): TeamFinder? = teamFinderMap[player.getGUID()]

    /** `Game::getOrCreateTeamFinder` (game.cpp:939-946). */
    fun getOrCreateTeamFinder(player: Player): TeamFinder =
        teamFinderMap.getOrPut(player.getGUID()) { TeamFinder() }

    /** `Game::removeTeamFinderListed` (game.cpp:948-950). */
    fun removeTeamFinderListed(leaderGuid: Long) {
        teamFinderMap.remove(leaderGuid)
    }

    /**
     * `ProtocolGame::parseLeaderFinderWindow` action 2
     * (protocolgame.cpp:3944-3979): the leader updates a member's status and the
     * member receives Canary's status text. An offline member or a leader
     * without an assembler is a no-op, exactly like the C++ early returns.
     */
    fun setMemberStatus(leader: Player, memberId: Long, memberStatus: Int) {
        val member = getPlayerByGuid(memberId) ?: return
        val teamAssemble = getTeamFinder(leader) ?: return

        for (guid in teamAssemble.membersMap.keys) {
            if (guid == memberId) {
                teamAssemble.membersMap[guid] = memberStatus
            }
        }

        when (memberStatus) {
            TeamFinder.MEMBER_STATUS_INVITED -> member.sendTextMessage("You are invited to a new team.")
            TeamFinder.MEMBER_STATUS_ACCEPTED -> member.sendTextMessage("Your team finder request was accepted.")
            TeamFinder.MEMBER_STATUS_DENIED -> member.sendTextMessage("Your team finder request was denied.")
        }
    }

    /**
     * `ProtocolGame::parseMemberFinderWindow` action 1
     * (protocolgame.cpp:3998-4015): the request is stored on the *requesting*
     * player's assembler (Canary reads `g_game().getTeamFinder(player)`, not the
     * leader's), the online leader is notified, and an existing status is kept
     * (`std::map::insert` semantics).
     */
    fun requestToJoin(requester: Player, leaderId: Long) {
        val leader = getPlayerByGuid(leaderId) ?: return
        val teamAssemble = getTeamFinder(requester) ?: return

        leader.sendTextMessage("There is a new request to join your team.")
        teamAssemble.membersMap.putIfAbsent(requester.getGUID(), TeamFinder.MEMBER_STATUS_REQUEST)
    }

    /**
     * `ProtocolGame::parseMemberFinderWindow` action 2
     * (protocolgame.cpp:4016-4023): the requesting player erases their own
     * `membersMap` entry from their assembler.
     */
    fun cancelRequest(requester: Player) {
        val teamAssemble = getTeamFinder(requester) ?: return
        teamAssemble.membersMap.remove(requester.getGUID())
    }
}
