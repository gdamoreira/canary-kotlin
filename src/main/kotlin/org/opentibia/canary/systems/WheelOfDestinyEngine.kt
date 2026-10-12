package org.opentibia.canary.systems

import org.opentibia.canary.creatures.Player
import java.util.concurrent.ConcurrentHashMap

enum class WheelQuadrant {
    TopLeft,
    TopRight,
    BottomLeft,
    BottomRight
}

data class WheelBonusStats(
    val bonusHealth: Int = 0,
    val bonusMana: Int = 0,
    val mitigationPercent: Double = 0.0
)

class WheelOfDestinyEngine {

    companion object {
        fun getAvailablePoints(player: Player): Int {
            return kotlin.math.max(0, player.level - 50)
        }
    }

    // Key: (playerId, quadrant) -> points allocated
    private val allocations = ConcurrentHashMap<Pair<Long, WheelQuadrant>, Int>()

    fun getAllocatedPoints(player: Player, quadrant: WheelQuadrant): Int {
        return allocations.getOrDefault(player.id to quadrant, 0)
    }

    fun getTotalAllocatedPoints(player: Player): Int {
        return WheelQuadrant.values().sumOf { getAllocatedPoints(player, it) }
    }

    fun allocatePoints(player: Player, quadrant: WheelQuadrant, points: Int): Boolean {
        if (points < 0) return false
        val currentTotal = getTotalAllocatedPoints(player)
        val currentInQuadrant = getAllocatedPoints(player, quadrant)
        val budget = getAvailablePoints(player)

        val newTotal = currentTotal - currentInQuadrant + points
        if (newTotal > budget) return false

        allocations[player.id to quadrant] = points
        return true
    }

    fun computeBonusStats(player: Player): WheelBonusStats {
        val total = getTotalAllocatedPoints(player)
        return WheelBonusStats(
            bonusHealth = total * 5,
            bonusMana = total * 3,
            mitigationPercent = (total * 0.05)
        )
    }
}
