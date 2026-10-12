package org.opentibia.canary.systems.bosstiary

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class BosstiaryTest {

    @Test
    fun `tracks boss kills and unlocks mastery progression tiers`() {
        val manager = BosstiaryManager()
        val playerId = 1L
        val bossName = "Scarlett Etzel"

        // Bane boss thresholds: 25, 100, 300 kills
        assertThat(manager.getMasteryLevel(playerId, bossName)).isEqualTo(0)

        manager.recordBossKill(playerId, bossName, count = 25)
        assertThat(manager.getKillCount(playerId, bossName)).isEqualTo(25)
        assertThat(manager.getMasteryLevel(playerId, bossName)).isEqualTo(1)

        manager.recordBossKill(playerId, bossName, count = 75)
        assertThat(manager.getKillCount(playerId, bossName)).isEqualTo(100)
        assertThat(manager.getMasteryLevel(playerId, bossName)).isEqualTo(2)

        manager.recordBossKill(playerId, bossName, count = 200)
        assertThat(manager.getKillCount(playerId, bossName)).isEqualTo(300)
        assertThat(manager.getMasteryLevel(playerId, bossName)).isEqualTo(3)
    }

    @Test
    fun `manages daily boss lockout cooldown`() {
        val manager = BosstiaryManager()
        val playerId = 1L
        val bossName = "Drume"

        assertThat(manager.canFightBoss(playerId, bossName)).isTrue()

        manager.setBossCooldown(playerId, bossName, cooldownSeconds = 72000) // 20 hours
        assertThat(manager.canFightBoss(playerId, bossName)).isFalse()
    }
}
