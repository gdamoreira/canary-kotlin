package org.opentibia.canary.systems

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.map.Position

class WheelOfDestinyTest {

    @Test
    fun `calculates available promotion points based on player level`() {
        val playerLevel49 = Player(1, "Hero", 100, 100, level = 49, position = Position(100, 100, 7))
        assertThat(WheelOfDestinyEngine.getAvailablePoints(playerLevel49)).isEqualTo(0)

        val playerLevel50 = Player(2, "Hero", 100, 100, level = 50, position = Position(100, 100, 7))
        assertThat(WheelOfDestinyEngine.getAvailablePoints(playerLevel50)).isEqualTo(0)

        val playerLevel100 = Player(3, "Hero", 100, 100, level = 100, position = Position(100, 100, 7))
        assertThat(WheelOfDestinyEngine.getAvailablePoints(playerLevel100)).isEqualTo(50) // 100 - 50 = 50
    }

    @Test
    fun `allocates points into dedication perks and grants passive stats`() {
        val player = Player(1, "Knight", maxHealth = 1000, health = 1000, maxMana = 300, mana = 300, level = 150, position = Position(100, 100, 7))
        val engine = WheelOfDestinyEngine()

        // Allocate 50 points into Red quadrant (Combat/Mitigation)
        val allocated = engine.allocatePoints(player, quadrant = WheelQuadrant.TopRight, points = 50)
        assertThat(allocated).isTrue()
        assertThat(engine.getAllocatedPoints(player, WheelQuadrant.TopRight)).isEqualTo(50)

        val bonusStats = engine.computeBonusStats(player)
        assertThat(bonusStats.bonusHealth).isGreaterThan(0)
        assertThat(bonusStats.mitigationPercent).isGreaterThan(0.0)
    }

    @Test
    fun `rejects allocation when exceeding total promotion points budget`() {
        val player = Player(1, "Knight", 100, 100, level = 60, position = Position(100, 100, 7)) // Budget = 10 points
        val engine = WheelOfDestinyEngine()

        val allocated = engine.allocatePoints(player, quadrant = WheelQuadrant.TopLeft, points = 15)
        assertThat(allocated).isFalse()
        assertThat(engine.getAllocatedPoints(player, WheelQuadrant.TopLeft)).isEqualTo(0)
    }
}
