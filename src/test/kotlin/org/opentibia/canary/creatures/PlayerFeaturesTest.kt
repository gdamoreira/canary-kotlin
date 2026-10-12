package org.opentibia.canary.creatures

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.map.Position
import org.opentibia.canary.systems.concoction.ConcoctionType
import org.opentibia.canary.systems.dailyreward.DailyRewardStatus
import org.opentibia.canary.systems.forge.ForgeHistory
import org.opentibia.canary.systems.offlinetraining.Skill

class PlayerFeaturesTest {

    private fun newPlayer(level: Int = 1) = Player(
        id = 42,
        name = "Feature Hero",
        maxHealth = 200,
        health = 200,
        position = Position(100, 100, 7),
        level = level,
    )

    @Test
    fun `player exposes a features holder tied to the player identity`() {
        val player = newPlayer()

        assertThat(player.features).isNotNull
        assertThat(player.features.playerId).isEqualTo(player.id)
    }

    @Test
    fun `each per-player feature sub-state is present and usable`() {
        val features = newPlayer().features

        assertThat(features.wheel.getWheelPoints()).isEqualTo(0)

        features.offlineTraining.setOfflineTrainingSkill(Skill.SKILL_SWORD)
        assertThat(features.offlineTraining.getOfflineTrainingSkill()).isEqualTo(Skill.SKILL_SWORD)
        features.offlineTraining.addOfflineTrainingTime(1_000)
        assertThat(features.offlineTraining.getOfflineTrainingTime()).isEqualTo(1_000)

        assertThat(features.mounts.isMounted()).isFalse()
        assertThat(features.mounts.getCurrentMount()).isEqualTo(0)

        features.concoctions.updateConcoction(ConcoctionType.FireResilience.itemId, 3_600)
        assertThat(features.concoctions.isConcoctionActive(ConcoctionType.FireResilience)).isTrue()

        assertThat(features.dailyReward.isDailyReward)
            .isEqualTo(DailyRewardStatus.DAILY_REWARD_NOTCOLLECTED.value)
        features.dailyReward.setDailyReward(DailyRewardStatus.DAILY_REWARD_COLLECTED.value)
        assertThat(features.dailyReward.isDailyReward)
            .isEqualTo(DailyRewardStatus.DAILY_REWARD_COLLECTED.value)

        features.familiars.addFamiliar(1_000)
        assertThat(features.familiars.familiars.map { it.lookType }).containsExactly(1_000)

        features.bestiary.addBestiaryKillCount(5, 3)
        assertThat(features.bestiary.getBestiaryKillCount(5)).isEqualTo(3)

        features.forge.setForgeDusts(123)
        assertThat(features.forge.getForgeDusts()).isEqualTo(123)

        features.forgeHistory.add(ForgeHistory(id = 9))
        assertThat(features.forgeHistory.get().map { it.id }).containsExactly(9)

        assertThat(features.hazard.getHazardSystemPoints()).isEqualTo(0)
    }

    @Test
    fun `wheel reads the player level on demand`() {
        val player = newPlayer(level = 250)
        assertThat(player.features.wheel.getWheelPoints()).isEqualTo(200)
    }

    @Test
    fun `level dependent features are created lazily and only once`() {
        var levelCalls = 0
        val features = PlayerFeatures(playerId = 7, levelProvider = {
            levelCalls++
            250
        })

        assertThat(levelCalls).isEqualTo(0)
        assertThat(features.wheel.getWheelPoints()).isEqualTo(200)
        assertThat(levelCalls).isEqualTo(1)
        assertThat(features.wheel.getWheelPoints()).isEqualTo(200)
        assertThat(levelCalls).isEqualTo(1)
    }

    @Test
    fun `existing positional player construction still compiles and exposes features`() {
        val player = Player(1, "Adventurer", 100, 100, position = Position(100, 100, 7))
        assertThat(player.features).isNotNull
    }
}
