package org.opentibia.canary.network.protocol.teamfinder

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.network.protocol.teamfinder.TeamFinderWindowWriter.TeamFinderLeaderFrame
import org.opentibia.canary.network.protocol.teamfinder.TeamFinderWindowWriter.TeamFinderListingFrame
import org.opentibia.canary.network.protocol.teamfinder.TeamFinderWindowWriter.TeamFinderMember

class TeamFinderWindowWriterTest {

    private fun ByteBuf.toByteArray(): ByteArray {
        val bytes = ByteArray(readableBytes())
        readBytes(bytes)
        return bytes
    }

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun `writes team finder list 0x2D with the hunt fields and status`() {
        val out = Unpooled.buffer()

        TeamFinderWindowWriter.writeTeamFinderList(
            out,
            listings = listOf(
                TeamFinderListingFrame(
                    leaderGuid = 1000L,
                    leaderName = "Leader",
                    minLevel = 8,
                    maxLevel = 100,
                    vocationIds = 3,
                    teamSlots = 5,
                    freeSlots = 3,
                    timestamp = 42L,
                    teamType = 2,
                    huntType = 7,
                    huntArea = 9,
                    onlineMembers = 1,
                    status = 2,
                ),
                TeamFinderListingFrame(
                    leaderGuid = 2000L,
                    leaderName = "Boss",
                    minLevel = 1,
                    maxLevel = 2,
                    vocationIds = 0,
                    teamSlots = 4,
                    freeSlots = 0,
                    timestamp = 1L,
                    teamType = 1,
                    bossId = 55,
                    onlineMembers = 4,
                    status = 0,
                ),
            ),
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x2D,
                0x00, // exceeded packets flag
                0x02, 0x00, // listing count
                0xE8, 0x03, 0x00, 0x00, // leader guid 1000
                0x06, 0x00, 0x4C, 0x65, 0x61, 0x64, 0x65, 0x72,
                0x08, 0x00,
                0x64, 0x00,
                0x03,
                0x05, 0x00,
                0x02, 0x00, // max(5 - 3, 1)
                0x2A, 0x00, 0x00, 0x00, // timestamp
                0x02, // team type 2
                0x07, 0x00,
                0x09, 0x00,
                0x02, // requester status
                0xD0, 0x07, 0x00, 0x00, // leader guid 2000
                0x04, 0x00, 0x42, 0x6F, 0x73, 0x73,
                0x01, 0x00,
                0x02, 0x00,
                0x00,
                0x04, 0x00,
                0x04, 0x00, // max(4 - 0, 4)
                0x01, 0x00, 0x00, 0x00,
                0x01,
                0x37, 0x00,
                0x00,
            )
        )
    }

    @Test
    fun `team finder list writes nothing when a leader or team is unresolved`() {
        val out = Unpooled.buffer()

        TeamFinderWindowWriter.writeTeamFinderList(
            out,
            listings = listOf(
                TeamFinderListingFrame(
                    leaderGuid = 1L,
                    leaderName = "A",
                    minLevel = 0,
                    maxLevel = 0,
                    vocationIds = 0,
                    teamSlots = 0,
                    freeSlots = 0,
                    timestamp = 0L,
                    teamType = 0,
                    onlineMembers = 0,
                    status = 0,
                ),
                null,
            ),
        )

        assertThat(out.toByteArray()).isEmpty()
    }

    @Test
    fun `writes the reset leader team finder 0x2C frame`() {
        val out = Unpooled.buffer()

        TeamFinderWindowWriter.writeLeaderTeamFinder(out, reset = true, frame = null)

        assertThat(out.toByteArray()).containsExactly(*bytes(0x2C, 0x01))
    }

    @Test
    fun `leader team finder without a team writes nothing`() {
        val out = Unpooled.buffer()

        TeamFinderWindowWriter.writeLeaderTeamFinder(out, reset = false, frame = null)

        assertThat(out.toByteArray()).isEmpty()
    }

    @Test
    fun `writes the leader team finder 0x2C frame with the leader and online members`() {
        val out = Unpooled.buffer()

        TeamFinderWindowWriter.writeLeaderTeamFinder(
            out,
            reset = false,
            frame = TeamFinderLeaderFrame(
                minLevel = 8,
                maxLevel = 100,
                vocationIds = 3,
                teamSlots = 5,
                freeSlots = 3,
                timestamp = 42L,
                teamType = 3,
                questId = 77,
                leaderGuid = 1000L,
                leaderName = "Leader",
                leaderLevel = 50,
                leaderVocationClientId = 4,
                members = listOf(
                    TeamFinderMember(guid = 2000L, name = "Member", level = 30, vocationClientId = 2, status = 3),
                ),
            ),
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x2C,
                0x00, // not a reset
                0x08, 0x00,
                0x64, 0x00,
                0x03,
                0x05, 0x00,
                0x03, 0x00,
                0x2A, 0x00, 0x00, 0x00,
                0x03, // team type 3
                0x4D, 0x00, // quest id 77
                0x02, 0x00, // members size = leader + one online member
                0xE8, 0x03, 0x00, 0x00,
                0x06, 0x00, 0x4C, 0x65, 0x61, 0x64, 0x65, 0x72,
                0x32, 0x00,
                0x04,
                0x03,
                0x01, // online member count
                0xD0, 0x07, 0x00, 0x00,
                0x06, 0x00, 0x4D, 0x65, 0x6D, 0x62, 0x65, 0x72,
                0x1E, 0x00,
                0x02,
                0x03,
            )
        )
    }
}
