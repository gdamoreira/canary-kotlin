package org.opentibia.canary.game

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.map.Position

class DeathPenaltyTest {

    @Test
    fun `calculates experience loss reduction based on active blessings`() {
        val playerWithoutBlessings = Player(1, "Alice", 100, 0, position = Position(100, 100, 7), experience = 1000000, level = 50)

        // Without blessings: base loss is 10% (100,000 exp)
        val lostUnblessed = DeathPenaltyEngine.calculateExperienceLoss(playerWithoutBlessings, blessingsCount = 0)
        assertThat(lostUnblessed).isEqualTo(100000L)

        // With all 5 blessings: loss reduced by 8% per blessing -> 10% * (1 - 0.40) = 6% loss or 2% loss depending on promotion
        val lostBlessed = DeathPenaltyEngine.calculateExperienceLoss(playerWithoutBlessings, blessingsCount = 5)
        assertThat(lostBlessed).isLessThan(lostUnblessed)
        assertThat(lostBlessed).isEqualTo(20000L) // 2% with full blessings
    }

    @Test
    fun `handles player death and teleports to temple`() {
        val player = Player(1, "Alice", 100, 0, position = Position(500, 500, 7), experience = 100000, level = 20)
        val templePos = Position(1000, 1000, 7)

        val result = DeathPenaltyEngine.processDeath(player, templePosition = templePos, blessingsCount = 0)

        assertThat(player.health).isEqualTo(player.maxHealth) // restored on temple revive
        assertThat(player.position).isEqualTo(templePos)
        assertThat(result.corpseItemId).isGreaterThan(0)
    }
}
