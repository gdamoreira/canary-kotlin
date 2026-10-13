package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.grouping.TeamFinder
import org.opentibia.canary.creatures.grouping.TeamFinderList

/**
 * Leader window action (client opcode `0x2C`), mirroring the action byte read
 * by `ProtocolGame::parseLeaderFinderWindow` (protocolgame.cpp:3933). Actions
 * `0`/`1` only send the window/reset state, action `2` is followed by the
 * `uint32_t` member id and its new status byte, and action `3` carries the full
 * assembler payload of `ProtocolGame::createLeaderTeamFinder`.
 */
data class LeaderFinderWindowCommand(val action: Int) : TeamFinderCommand

/** Action `2`: member id and the status byte applied to the assembler. */
data class LeaderFinderMemberStatusCommand(
    val memberId: Long,
    val memberStatus: Int,
) : TeamFinderCommand

/**
 * Action `3`: the assembler payload of `ProtocolGame::createLeaderTeamFinder`
 * (protocolgame.cpp:3840-3898). [huntType]/[huntArea] are the C++ `hunt_type`
 * and `hunt_area` fields and are only consumed for `teamType` 2; [bossID] only
 * for 1 and [questID] only for 3.
 */
data class CreateLeaderTeamFinderCommand(
    val minLevel: Int,
    val maxLevel: Int,
    val vocationIDs: Int,
    val teamSlots: Int,
    val freeSlots: Int,
    val partyBool: Boolean,
    val timestamp: Long,
    val teamType: Int,
    val bossID: Int,
    val huntType: Int,
    val huntArea: Int,
    val questID: Int,
) : TeamFinderCommand

/** Member window action `0`: send the team finder list. */
data class MemberFinderListCommand(val action: Int) : TeamFinderCommand

/** Member window action `1`: request to join the leader's team. */
data class TeamFinderJoinRequestCommand(val leaderId: Long) : TeamFinderCommand

/** Member window action `2`: cancel the pending join request. */
data class TeamFinderCancelRequestCommand(val leaderId: Long) : TeamFinderCommand

/**
 * Decoder and applier for the team finder window opcodes.
 *
 *  - `0x2C` `ProtocolGame::parseLeaderFinderWindow` (protocolgame.cpp:3928)
 *  - `0x2D` `ProtocolGame::parseMemberFinderWindow` (protocolgame.cpp:3993)
 *
 * The assembler mutations live on the already-ported [TeamFinderList]
 * (`Game::teamFinderMap`, game.cpp:930-950):
 * [TeamFinderList.setMemberStatus], [TeamFinderList.removeTeamFinderListed],
 * [TeamFinderList.getOrCreateTeamFinder], [TeamFinderList.requestToJoin] and
 * [TeamFinderList.cancelRequest]. The window sends themselves are not ported.
 *
 * PARITY GAP: the router context has no online-player registry, so the shared
 * default [TeamFinderList] resolves every `g_game().getPlayerByGUID` lookup to
 * `null`; member status changes and join requests against online members stay
 * inert until a caller injects a list with a real resolver.
 */
object TeamFinderPacketHandler {

    /** Client opcode for `ProtocolGame::parseLeaderFinderWindow`. */
    const val OPCODE_LEADER_FINDER_WINDOW: Int = 0x2C

    /** Client opcode for `ProtocolGame::parseMemberFinderWindow`. */
    const val OPCODE_MEMBER_FINDER_WINDOW: Int = 0x2D

    /** `parseLeaderFinderWindow` action `0`: send the window. */
    const val LEADER_ACTION_SEND_WINDOW: Int = 0

    /** `parseLeaderFinderWindow` action `1`: send the reset and delist the leader. */
    const val LEADER_ACTION_RESET_WINDOW: Int = 1

    /** `parseLeaderFinderWindow` action `2`: set a member status. */
    const val LEADER_ACTION_SET_MEMBER_STATUS: Int = 2

    /** `parseLeaderFinderWindow` action `3`: create the assembler. */
    const val LEADER_ACTION_CREATE: Int = 3

    /** `parseMemberFinderWindow` action `0`: send the team finder list. */
    const val MEMBER_ACTION_SEND_LIST: Int = 0

    /** `parseMemberFinderWindow` action `1`: request to join a team. */
    const val MEMBER_ACTION_REQUEST_JOIN: Int = 1

    private val sharedTeamFinderList = TeamFinderList()

    /**
     * Decodes the payload of [opcode].
     *
     * @return the decoded command, or `null` when the payload is truncated or
     *   [opcode] is not a team finder window opcode.
     */
    fun parse(opcode: Int, buffer: ByteBuf): TeamFinderCommand? = when (opcode) {
        OPCODE_LEADER_FINDER_WINDOW -> parseLeaderFinderWindow(buffer)
        OPCODE_MEMBER_FINDER_WINDOW -> parseMemberFinderWindow(buffer)
        else -> null
    }

    /**
     * Applies the decoded command to [teamFinderList], mirroring the mutating
     * part of the two window handlers. The window/list sends
     * (`sendLeaderTeamFinder`, `sendTeamFinderList`) are not ported (PARITY GAP).
     */
    fun apply(
        player: Player,
        command: TeamFinderCommand,
        teamFinderList: TeamFinderList = sharedTeamFinderList,
    ) {
        when (command) {
            is LeaderFinderWindowCommand -> applyLeaderWindowAction(player, command, teamFinderList)
            is LeaderFinderMemberStatusCommand ->
                teamFinderList.setMemberStatus(player, command.memberId, command.memberStatus)

            is CreateLeaderTeamFinderCommand -> applyCreateLeader(player, command, teamFinderList)
            is MemberFinderListCommand -> Unit
            is TeamFinderJoinRequestCommand -> teamFinderList.requestToJoin(player, command.leaderId)
            is TeamFinderCancelRequestCommand -> teamFinderList.cancelRequest(player)
        }
    }

    /**
     * `ProtocolGame::parseLeaderFinderWindow` (protocolgame.cpp:3928-3991):
     * action `1` delists the leader's assembler through
     * `Game::removeTeamFinderListed`, actions `0`/`2`/`3` only send (and action
     * `2`/`3` mutate through their own commands).
     */
    private fun applyLeaderWindowAction(
        player: Player,
        command: LeaderFinderWindowCommand,
        teamFinderList: TeamFinderList,
    ) {
        if (command.action != LEADER_ACTION_RESET_WINDOW) {
            return
        }
        if (teamFinderList.getTeamFinder(player) != null) {
            teamFinderList.removeTeamFinderListed(player.getGUID())
        }
    }

    /**
     * `ProtocolGame::createLeaderTeamFinder` (protocolgame.cpp:3840-3898): every
     * decoded field is stored on the leader's assembler, the leader guid is set
     * and the party members are prefilled with the accepted status when
     * `partyBool` is set.
     */
    private fun applyCreateLeader(
        player: Player,
        command: CreateLeaderTeamFinderCommand,
        teamFinderList: TeamFinderList,
    ) {
        val teamAssemble = teamFinderList.getOrCreateTeamFinder(player)
        teamAssemble.minLevel = command.minLevel
        teamAssemble.maxLevel = command.maxLevel
        teamAssemble.vocationIDs = command.vocationIDs
        teamAssemble.teamSlots = command.teamSlots
        teamAssemble.freeSlots = command.freeSlots
        teamAssemble.partyBool = command.partyBool
        teamAssemble.timestamp = command.timestamp
        teamAssemble.teamType = command.teamType
        teamAssemble.bossID = command.bossID
        teamAssemble.hunt_type = command.huntType
        teamAssemble.hunt_area = command.huntArea
        teamAssemble.questID = command.questID
        teamAssemble.leaderGuid = player.getGUID()
        teamAssemble.addPartyMembers(player)
    }

    /** `ProtocolGame::parseLeaderFinderWindow` payload reader. */
    private fun parseLeaderFinderWindow(buffer: ByteBuf): TeamFinderCommand? {
        if (buffer.readableBytes() < 1) {
            return null
        }
        val action = buffer.readUnsignedByte().toInt()
        return when (action) {
            LEADER_ACTION_SET_MEMBER_STATUS -> {
                if (buffer.readableBytes() < 5) {
                    return null
                }
                LeaderFinderMemberStatusCommand(
                    memberId = buffer.readUnsignedIntLE(),
                    memberStatus = buffer.readUnsignedByte().toInt(),
                )
            }
            LEADER_ACTION_CREATE -> parseCreateLeader(buffer)
            else -> LeaderFinderWindowCommand(action = action)
        }
    }

    /** `ProtocolGame::createLeaderTeamFinder` payload reader (protocolgame.cpp:3846-3883). */
    private fun parseCreateLeader(buffer: ByteBuf): CreateLeaderTeamFinderCommand? {
        if (buffer.readableBytes() < 15) {
            return null
        }
        val minLevel = buffer.readUnsignedShortLE()
        val maxLevel = buffer.readUnsignedShortLE()
        val vocationIDs = buffer.readUnsignedByte().toInt()
        val teamSlots = buffer.readUnsignedShortLE()
        val freeSlots = buffer.readUnsignedShortLE()
        val partyBool = buffer.readUnsignedByte().toInt() == 1
        val timestamp = buffer.readUnsignedIntLE()
        val teamType = buffer.readUnsignedByte().toInt()

        var bossID = 0
        var huntType = 0
        var huntArea = 0
        var questID = 0
        when (teamType) {
            1 -> {
                if (buffer.readableBytes() < 2) {
                    return null
                }
                bossID = buffer.readUnsignedShortLE()
            }
            2 -> {
                if (buffer.readableBytes() < 4) {
                    return null
                }
                huntType = buffer.readUnsignedShortLE()
                huntArea = buffer.readUnsignedShortLE()
            }
            3 -> {
                if (buffer.readableBytes() < 2) {
                    return null
                }
                questID = buffer.readUnsignedShortLE()
            }
        }

        return CreateLeaderTeamFinderCommand(
            minLevel = minLevel,
            maxLevel = maxLevel,
            vocationIDs = vocationIDs,
            teamSlots = teamSlots,
            freeSlots = freeSlots,
            partyBool = partyBool,
            timestamp = timestamp,
            teamType = teamType,
            bossID = bossID,
            huntType = huntType,
            huntArea = huntArea,
            questID = questID,
        )
    }

    /**
     * `ProtocolGame::parseMemberFinderWindow` payload reader
     * (protocolgame.cpp:3993-4025): action `0` carries nothing, every other
     * action carries the `uint32_t` leader id and is a join request only when it
     * equals `1`.
     */
    private fun parseMemberFinderWindow(buffer: ByteBuf): TeamFinderCommand? {
        if (buffer.readableBytes() < 1) {
            return null
        }
        val action = buffer.readUnsignedByte().toInt()
        if (action == MEMBER_ACTION_SEND_LIST) {
            return MemberFinderListCommand(action = action)
        }
        if (buffer.readableBytes() < 4) {
            return null
        }
        val leaderId = buffer.readUnsignedIntLE()
        return if (action == MEMBER_ACTION_REQUEST_JOIN) {
            TeamFinderJoinRequestCommand(leaderId = leaderId)
        } else {
            TeamFinderCancelRequestCommand(leaderId = leaderId)
        }
    }
}

/** Marker interface for the commands produced by [TeamFinderPacketHandler]. */
sealed interface TeamFinderCommand
