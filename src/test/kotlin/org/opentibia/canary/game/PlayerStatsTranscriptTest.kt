package org.opentibia.canary.game

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

data class PlayerStatsSummary(
    val filteredOnlinePlayers: Int,
    val totalUniqueIPs: Int
)

class GamePlayerStatsTracker {
    private val players = mutableListOf<TestPlayerRecord>()

    data class TestPlayerRecord(val guid: Long, val ip: Long, val idleTimeMs: Long)

    fun addPlayer(guid: Long, ip: Long, idleTimeMs: Long) {
        players.add(TestPlayerRecord(guid, ip, idleTimeMs))
    }

    fun getPlayerStats(): PlayerStatsSummary {
        val idleThreshold = 15 * 60 * 1000L
        val maxPerIp = 4

        val validPlayers = players.filter { it.ip != 0L && it.idleTimeMs <= idleThreshold }
        val ipGroups = validPlayers.groupBy { it.ip }

        var cappedTotal = 0
        for ((_, group) in ipGroups) {
            cappedTotal += kotlin.math.min(maxPerIp, group.size)
        }

        return PlayerStatsSummary(
            filteredOnlinePlayers = cappedTotal,
            totalUniqueIPs = ipGroups.keys.size
        )
    }
}

class PlayerStatsTranscriptTest {

    @Test
    fun `filters inactive players and caps per IP online count matching Canary player_stats_test`() {
        val ipGroupA = 0x0A000001L
        val ipGroupB = 0x0A000002L
        val zeroIp = 0L
        val idleThresholdMs = 15 * 60 * 1000L

        val tracker = GamePlayerStatsTracker()

        tracker.addPlayer(1, ipGroupA, 0)
        tracker.addPlayer(2, ipGroupA, 0)
        tracker.addPlayer(3, ipGroupA, 0)
        tracker.addPlayer(4, ipGroupA, 0)
        tracker.addPlayer(5, ipGroupA, 0)
        tracker.addPlayer(6, ipGroupA, 0) // capped at 4 for group A

        tracker.addPlayer(7, ipGroupB, 0) // 1 in group B
        tracker.addPlayer(10, ipGroupB, idleThresholdMs) // 2 in group B (boundary: still counted)
        tracker.addPlayer(8, ipGroupB, idleThresholdMs + 1) // idle threshold breached -> excluded
        tracker.addPlayer(9, zeroIp, 0) // zero IP excluded

        val stats = tracker.getPlayerStats()

        // 4 (Group A cap) + 2 (Group B valid) = 6
        assertThat(stats.filteredOnlinePlayers).isEqualTo(6)
        assertThat(stats.totalUniqueIPs).isEqualTo(2)
    }
}
