package org.opentibia.canary.network.protocol.teamfinder

import io.netty.buffer.ByteBuf
import org.opentibia.canary.network.message.writeTibiaString

/**
 * Outbound team-finder serialization, ported from Canary
 * src/server/network/protocol/protocolgame.cpp:
 *
 *  - `ProtocolGame::sendTeamFinderList`   (0x2D, protocolgame.cpp:3675-3743)
 *  - `ProtocolGame::sendLeaderTeamFinder` (0x2C, protocolgame.cpp:3745-3838)
 *
 * The `!player`/`oldProtocol` guards and the `g_game()` player/team lookups
 * stay with the caller: [TeamFinderListingFrame] and [TeamFinderLeaderFrame]
 * carry the resolved leader/member rows, in the caller's `membersMap` order,
 * and only online members are passed in. A `null` listing or leader frame
 * models the C++ early return (`!leader`/`!teamAssemble`/`!leader`), which
 * discards the whole message; a `reset` leader frame writes the two-byte
 * `0x2C 0x01` packet. The `max(teamSlots - freeSlots, membersSize)` expression
 * and the `1 + online members` head count are applied here exactly. Every
 * multi-byte field is Little-Endian.
 */
object TeamFinderWindowWriter {

    const val PACKET_LEADER_TEAM_FINDER: Int = 0x2C
    const val PACKET_TEAM_FINDER_LIST: Int = 0x2D

    /** One resolved `sendTeamFinderList` row (`teamAssemble` plus the leader). */
    data class TeamFinderListingFrame(
        val leaderGuid: Long,
        val leaderName: String,
        val minLevel: Int,
        val maxLevel: Int,
        val vocationIds: Int,
        val teamSlots: Int,
        val freeSlots: Int,
        val timestamp: Long,
        val teamType: Int,
        val bossId: Int = 0,
        val huntType: Int = 0,
        val huntArea: Int = 0,
        val questId: Int = 0,
        val onlineMembers: Int,
        val status: Int,
    )

    /** One online team member row of `sendLeaderTeamFinder`. */
    data class TeamFinderMember(
        val guid: Long,
        val name: String,
        val level: Int,
        val vocationClientId: Int,
        val status: Int,
    )

    /** One resolved `sendLeaderTeamFinder` payload (leader plus online members). */
    data class TeamFinderLeaderFrame(
        val minLevel: Int,
        val maxLevel: Int,
        val vocationIds: Int,
        val teamSlots: Int,
        val freeSlots: Int,
        val timestamp: Long,
        val teamType: Int,
        val bossId: Int = 0,
        val huntType: Int = 0,
        val huntArea: Int = 0,
        val questId: Int = 0,
        val leaderGuid: Long,
        val leaderName: String,
        val leaderLevel: Int,
        val leaderVocationClientId: Int,
        val members: List<TeamFinderMember>,
    )

    /**
     * `sendTeamFinderList` (protocolgame.cpp:3675-3743). A `null` listing
     * models the C++ `!leader`/`!teamAssemble` early return and writes nothing
     * at all (Canary discards the whole message).
     */
    fun writeTeamFinderList(out: ByteBuf, listings: List<TeamFinderListingFrame?>) {
        if (listings.any { it == null }) {
            return
        }

        out.writeByte(PACKET_TEAM_FINDER_LIST)
        out.writeByte(0x00)
        out.writeShortLE(listings.size)

        for (listing in listings) {
            listing ?: continue

            out.writeIntLE(listing.leaderGuid.toInt())
            out.writeTibiaString(listing.leaderName)
            out.writeShortLE(listing.minLevel)
            out.writeShortLE(listing.maxLevel)
            out.writeByte(listing.vocationIds)
            out.writeShortLE(listing.teamSlots)
            out.writeShortLE(maxOf((listing.teamSlots - listing.freeSlots) and 0xFFFF, listing.onlineMembers))
            out.writeIntLE(listing.timestamp.toInt())
            out.writeByte(listing.teamType)

            when (listing.teamType) {
                1 -> out.writeShortLE(listing.bossId)
                2 -> {
                    out.writeShortLE(listing.huntType)
                    out.writeShortLE(listing.huntArea)
                }

                3 -> out.writeShortLE(listing.questId)
                else -> Unit
            }

            out.writeByte(listing.status)
        }
    }

    /**
     * `sendLeaderTeamFinder` (protocolgame.cpp:3745-3838). A `reset` frame
     * writes only `0x2C 0x01`; a `null` frame (no team assemble or no leader)
     * writes nothing.
     */
    fun writeLeaderTeamFinder(out: ByteBuf, reset: Boolean, frame: TeamFinderLeaderFrame?) {
        if (!reset && frame == null) {
            return
        }

        out.writeByte(PACKET_LEADER_TEAM_FINDER)
        out.writeByte(if (reset) 0x01 else 0x00)
        if (reset) {
            return
        }

        frame ?: return

        out.writeShortLE(frame.minLevel)
        out.writeShortLE(frame.maxLevel)
        out.writeByte(frame.vocationIds)
        out.writeShortLE(frame.teamSlots)
        out.writeShortLE(frame.freeSlots)
        out.writeIntLE(frame.timestamp.toInt())
        out.writeByte(frame.teamType)

        when (frame.teamType) {
            1 -> out.writeShortLE(frame.bossId)
            2 -> {
                out.writeShortLE(frame.huntType)
                out.writeShortLE(frame.huntArea)
            }

            3 -> out.writeShortLE(frame.questId)
            else -> Unit
        }

        out.writeShortLE(1 + frame.members.size)

        out.writeIntLE(frame.leaderGuid.toInt())
        out.writeTibiaString(frame.leaderName)
        out.writeShortLE(frame.leaderLevel)
        out.writeByte(frame.leaderVocationClientId)
        out.writeByte(0x03)

        out.writeByte(frame.members.size)
        for (member in frame.members) {
            out.writeIntLE(member.guid.toInt())
            out.writeTibiaString(member.name)
            out.writeShortLE(member.level)
            out.writeByte(member.vocationClientId)
            out.writeByte(member.status)
        }
    }
}
