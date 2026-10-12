package org.opentibia.canary.game

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.map.Position

class VitalsRegenerationTest {

    @Test
    fun `regenerates player health and mana based on vocation rules`() {
        val player = Player(
            id = 1,
            name = "Mage",
            maxHealth = 200,
            health = 50,
            maxMana = 500,
            mana = 100,
            position = Position(100, 100, 7)
        )

        val regenRule = VitalsRegenRule(
            healthGain = 1,
            manaGain = 4,
            intervalSeconds = 2
        )

        // Simulate 1 tick
        VitalsRegenEngine.applyRegenerationTick(player, regenRule)

        assertThat(player.health).isEqualTo(51)
        assertThat(player.mana).isEqualTo(104)

        // Ensure does not exceed maximums
        player.health = 199
        player.mana = 498
        VitalsRegenEngine.applyRegenerationTick(player, regenRule)
        assertThat(player.health).isEqualTo(200)
        assertThat(player.mana).isEqualTo(500)
    }
}
